package org.pvpcore.mechanic.fastercrystals;

import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.jspecify.annotations.NonNull;
import org.pvpcore.PvPCorePlugin;

import java.util.Collection;
import java.util.Set;

import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.ADVENTURE;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.BAD_BASE;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.CROSS_WORLD;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.ENTITY_PLACE_CANCELLED;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.NO_BLOCK;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.NO_CRYSTAL;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.NO_RANGE;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.NOT_CONSUMED;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.OCCUPIED;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.OUTSIDE_BORDER;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.PLACED;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.PLACE_CANCELLED;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.SPACE_BLOCKED;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.SPECTATOR;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.WRONG_BASE;
import static org.pvpcore.mechanic.fastercrystals.FasterCrystalsMechanic.Outcome.WRONG_ITEM;

/**
 * Places a crystal from the client's interact packet instead of waiting for the block
 * placement packet. The spawn rules match FasterCrystals 2.2.1: only obsidian and bedrock
 * are accepted, the clicked block must be the crystal's base, and the spawn space must be
 * empty.
 *
 * <p>Unlike upstream, PvPCore keeps its protection-plugin hooks: it fires
 * {@link FastCrystalPlaceEvent} before spawning and synthesizes Bukkit's
 * {@link EntityPlaceEvent} so existing protection listeners can veto the packet path.</p>
 *
 * <p>Like the attack path, the main-thread work runs on the next-tick Bukkit scheduler.</p>
 */
final class CrystalPlaceListener extends SimplePacketListenerAbstract {

    private static final Set<Material> ALLOWED_BLOCKS = Set.of(Material.OBSIDIAN, Material.BEDROCK);
    private static final Set<Material> AIR_TYPES = Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    private final PvPCorePlugin plugin;
    private final FasterCrystalsMechanic mechanic;
    private final CrystalTracker tracker;

    CrystalPlaceListener(PvPCorePlugin plugin, FasterCrystalsMechanic mechanic, CrystalTracker tracker) {
        this.plugin = plugin;
        this.mechanic = mechanic;
        this.tracker = tracker;
    }

    @Override
    public void onPacketPlayReceive(@NonNull PacketPlayReceiveEvent event) {
        try {
            handle(event);
        } catch (Throwable t) {
            // A malformed packet must never reach the server.
            mechanic.reportException("placement packet handling", t);
        }
    }

    private void handle(PacketPlayReceiveEvent event) {
        if (!mechanic.isActive()) {
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }

        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        int entityId = packet.getEntityId();
        // Cheap network-thread prefilter: only crystals can start this path. This is the
        // equivalent of upstream's crystalRepository.get(entityId) early return.
        if (!tracker.contains(entityId)) {
            return;
        }
        InteractionHand hand = packet.getHand();
        Bukkit.getScheduler().runTask(plugin, () -> handlePlace(player, entityId, hand));
    }

    private void handlePlace(Player player, int entityId, InteractionHand hand) {
        try {
            handlePlaceUnsafe(player, entityId, hand);
        } catch (Throwable t) {
            // Never let a scheduled task break the mechanic.
            mechanic.reportException("placement task", t);
        }
    }

    private void handlePlaceUnsafe(Player player, int entityId, InteractionHand hand) {
        if (!mechanic.isActive() || !player.isOnline()) {
            return;
        }
        if (mechanic.isDebug()) {
            mechanic.debug("interact_at: player=" + player.getName() + " entityId=" + entityId
                    + " hand=" + hand + " gamemode=" + player.getGameMode());
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            mechanic.count(SPECTATOR);
            return;
        }
        if (player.getGameMode() == GameMode.ADVENTURE) {
            mechanic.count(ADVENTURE);
            return;
        }

        EnderCrystal crystal = tracker.get(entityId);
        if (crystal == null) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: crystal id=" + entityId + " is not tracked.");
            }
            mechanic.count(NO_CRYSTAL);
            return;
        }
        if (!player.getWorld().equals(crystal.getWorld())) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: crystal id=" + entityId + " is in "
                        + crystal.getWorld().getName() + ", player is in "
                        + player.getWorld().getName() + ".");
            }
            mechanic.count(CROSS_WORLD);
            return;
        }

        ItemStack item = hand == InteractionHand.MAIN_HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (item.getType() != Material.END_CRYSTAL) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: holding " + item.getType() + ".");
            }
            mechanic.count(WRONG_ITEM);
            return;
        }

        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (range == null) {
            mechanic.count(NO_RANGE);
            return;
        }

        Location eye = player.getEyeLocation();
        RayTraceResult hit = eye.getWorld().rayTraceBlocks(eye, eye.getDirection(), range.getValue());
        if (hit == null || hit.getHitBlock() == null) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: no block in range.");
            }
            mechanic.count(NO_BLOCK);
            return;
        }

        Block block = hit.getHitBlock();
        if (!ALLOWED_BLOCKS.contains(block.getType())) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: " + block.getType() + " at "
                        + block.getLocation().toVector() + " is not a valid base.");
            }
            mechanic.count(BAD_BASE);
            return;
        }

        // The crystal sits on top of the base block; make sure the player clicked that block.
        Location base = crystal.getLocation().subtract(0.5, 1.0, 0.5);
        if (block.getX() != base.getBlockX()
                || block.getY() != base.getBlockY()
                || block.getZ() != base.getBlockZ()) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: clicked " + block.getLocation().toVector()
                        + " but crystal base is " + base.toVector() + ".");
            }
            mechanic.count(WRONG_BASE);
            return;
        }

        // A removed crystal is retained for one placement packet. Retire it after a successful
        // placement so a duplicate packet cannot place and consume a second crystal.
        boolean retained = tracker.isRetained(entityId);
        spawnCrystal(crystal.getLocation(), player, item, block, hand, entityId, retained);
    }

    private void spawnCrystal(Location crystalLocation, Player player, ItemStack item,
                              Block baseBlock, InteractionHand hand,
                              int sourceEntityId, boolean retained) {
        Location spawnLocation = crystalLocation.clone();
        if (!spawnLocation.getWorld().getWorldBorder().isInside(spawnLocation)) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: spawn location " + spawnLocation.toVector()
                        + " is outside the world border.");
            }
            mechanic.count(OUTSIDE_BORDER);
            return;
        }
        if (!AIR_TYPES.contains(spawnLocation.getBlock().getType())) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: spawn space at " + spawnLocation.toVector()
                        + " is " + spawnLocation.getBlock().getType() + ".");
            }
            mechanic.count(SPACE_BLOCKED);
            return;
        }

        // The reference plugin checks the space one block above the crystal location.
        Location scan = spawnLocation.clone().add(0.0, 1.0, 0.0);
        Collection<Entity> nearby = spawnLocation.getWorld().getNearbyEntities(
                scan, 0.5, 1.0, 0.5,
                entity -> !(entity instanceof Player other)
                        || (other.getGameMode() != GameMode.SPECTATOR && player.canSee(other)));
        if (!nearby.isEmpty()) {
            if (mechanic.isDebug()) {
                mechanic.debug("place skipped: " + nearby.size()
                        + " entity/entities occupy the spawn space.");
            }
            mechanic.count(OCCUPIED);
            return;
        }

        FastCrystalPlaceEvent placeEvent = new FastCrystalPlaceEvent(
                player, spawnLocation.clone(), item);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            if (mechanic.isDebug()) {
                mechanic.debug("place cancelled by FastCrystalPlaceEvent.");
            }
            mechanic.count(PLACE_CANCELLED);
            return;
        }

        EnderCrystal spawned = spawnLocation.getWorld().spawn(
                spawnLocation, EnderCrystal.class,
                crystal -> crystal.setShowingBottom(false));
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        EntityPlaceEvent entityPlaceEvent = new EntityPlaceEvent(
                spawned, player, baseBlock, BlockFace.UP, slot);
        try {
            Bukkit.getPluginManager().callEvent(entityPlaceEvent);
        } catch (Throwable t) {
            tracker.remove(spawned.getEntityId());
            spawned.remove();
            mechanic.reportException("EntityPlaceEvent", t);
            if (mechanic.isDebug()) {
                mechanic.debug("place failed while firing EntityPlaceEvent: " + t);
            }
            return;
        }
        if (entityPlaceEvent.isCancelled()) {
            tracker.remove(spawned.getEntityId());
            spawned.remove();
            if (mechanic.isDebug()) {
                mechanic.debug("place cancelled by EntityPlaceEvent.");
            }
            mechanic.count(ENTITY_PLACE_CANCELLED);
            return;
        }

        GameMode mode = player.getGameMode();
        if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
            if (!consumeCrystal(player, hand)) {
                mechanic.count(NOT_CONSUMED);
                if (mechanic.isDebug()) {
                    mechanic.debug("placed crystal but could not consume the held crystal.");
                }
            }
        }
        mechanic.count(PLACED);
        if (mechanic.isDebug()) {
            mechanic.debug("placed crystal at " + spawnLocation.toVector() + ".");
        }
        if (retained) {
            tracker.remove(sourceEntityId);
        }
    }

    private static boolean consumeCrystal(Player player, InteractionHand hand) {
        ItemStack current = hand == InteractionHand.MAIN_HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (current.getType() != Material.END_CRYSTAL || current.getAmount() <= 0) {
            return false;
        }
        if (current.getAmount() <= 1) {
            current = ItemStack.empty();
        } else {
            current.setAmount(current.getAmount() - 1);
        }
        if (hand == InteractionHand.MAIN_HAND) {
            player.getInventory().setItemInMainHand(current);
        } else {
            player.getInventory().setItemInOffHand(current);
        }
        return true;
    }
}