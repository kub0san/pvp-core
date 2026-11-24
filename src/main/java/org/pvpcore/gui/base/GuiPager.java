package org.pvpcore.gui.base;


import java.util.List;

/**
 * Pure paging state: clamps the page and slices lists. It draws nothing;
 * {@link GuiDock} renders the controls.
 *
 * <pre>{@code
 * GuiPager pager = GuiPager.of(page, kits.size(), 9);
 * for (Kit k : pager.slice(kits)) { ... }
 * GuiDock.of(this).pager(pager, t -> { page = t; refresh(); });
 * }</pre>
 */
public final class GuiPager {
    private final int currentPage;
    private final int maxPages;
    private final int perPage;

    public GuiPager(int currentPage, int maxPages) {
        this(currentPage, maxPages, 0);
    }

    private GuiPager(int currentPage, int maxPages, int perPage) {
        this.maxPages = Math.max(1, maxPages);
        this.currentPage = Math.max(0, Math.min(currentPage, this.maxPages - 1));
        this.perPage = perPage;
    }

    public static GuiPager of(int requestedPage, int totalItems, int perPage) {
        int per = Math.max(1, perPage);
        int pages = Math.max(1, (totalItems + per - 1) / per);
        return new GuiPager(requestedPage, pages, per);
    }

    public int currentPage() {
        return currentPage;
    }

    public int maxPages() {
        return maxPages;
    }

    /** First index (inclusive) of the current page. */
    public int from() {
        return currentPage * perPage;
    }

    /** Last index (exclusive) of the current page for a list of {@code total} items. */
    public int to(int total) {
        return perPage <= 0 ? total : Math.min(total, from() + perPage);
    }

    public <T> List<T> slice(List<T> items) {
        if (perPage <= 0) return items;
        int start = Math.min(from(), items.size());
        return items.subList(start, to(items.size()));
    }
}
