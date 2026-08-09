package com.electroshop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One administrator's dashboard arrangement.
 *
 * <p>Answers {@code GET} and {@code PUT} on {@code /api/admin/dashboard/layout}.</p>
 *
 * <p><b>The server stores the layout; it does not interpret it.</b> Which panels exist,
 * what they are called and how wide they can be are decisions the frontend owns, and
 * duplicating that knowledge in the backend would mean a schema change every time a card
 * is added. What the backend does enforce is what it must: every panel id is one it
 * recognises, spans stay inside the grid, and the payload is bounded. An unknown id is
 * dropped rather than rejected, so a layout saved by a newer frontend does not become
 * unloadable after a rollback.</p>
 *
 * <p><b>Defaults are the server's answer, not an empty response.</b> An administrator
 * who has never customised anything gets the full default arrangement with
 * {@code customised} set to false, so the frontend renders one code path whether or not
 * a layout was ever saved, and "Reset layout" is simply a delete followed by this same
 * response.</p>
 *
 * @param panels     the panels in display order
 * @param density    {@code COMPACT} or {@code COMFORTABLE}
 * @param customised whether this arrangement was saved by the administrator or is the
 *                   default
 * @param updatedAt  when it was last saved, null when it is the default
 * @param version    schema version of the stored payload, so a future change to the
 *                   panel model can migrate old rows instead of discarding them
 */
public record DashboardLayoutDto(
        List<PanelState> panels,
        String density,
        boolean customised,
        LocalDateTime updatedAt,
        int version
) {

    /**
     * One panel's placement.
     *
     * @param id     the panel identifier, matching the frontend's panel registry
     * @param order  zero-based position in the grid
     * @param span   how many of the twelve grid columns it occupies
     * @param hidden whether the administrator has hidden it
     */
    public record PanelState(String id, int order, int span, boolean hidden) {}
}
