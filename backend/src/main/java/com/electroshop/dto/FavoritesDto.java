package com.electroshop.dto;

import java.util.List;

/**
 * The admin routes one administrator has pinned to the top of the navigation rail.
 *
 * <p>Answers {@code GET} and {@code PUT} on {@code /api/admin/favorites}.</p>
 *
 * <p>Favourites are stored as routes rather than as labels, because a label is a
 * translation and a route is an address. Renaming a section in the interface must not
 * orphan everyone's pinned links.</p>
 *
 * <p>Every stored route is validated to begin with {@code /admin/} before it is written
 * and again when it is read. A favourites list is user-controlled data that the
 * interface turns into a link, which makes it precisely the kind of value that must
 * never be able to carry {@code javascript:} or an off-site address. Validating on read
 * as well as on write means a row that predates the check, or one inserted directly into
 * the database, still cannot become a link.</p>
 *
 * @param items the pinned routes, in the order the administrator arranged them
 * @param max   the largest number of favourites accepted, so the interface can disable
 *              the pin control at the limit rather than letting a save fail
 */
public record FavoritesDto(
        List<Favorite> items,
        int max
) {

    /**
     * One pinned route.
     *
     * @param route the admin path, always beginning with {@code /admin/}
     * @param label what to show, resolved from the navigation registry at save time so
     *              the rail does not have to look it up on every render
     * @param icon  the icon name from the navigation registry
     * @param order zero-based position in the favourites list
     */
    public record Favorite(String route, String label, String icon, int order) {}
}
