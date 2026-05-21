package com.industrialhub.backend.common.application;

import java.util.List;
import java.util.UUID;

/**
 * ThreadLocal holder for the current request's plant scope.
 *
 * States:
 * - NOT_SET (ThreadLocal absent) = filter not yet run (e.g. unauthenticated request)
 * - admin=true (no plant IDs) = ADMIN context, no filter (sees all data)
 * - admin=false, empty plantIds = user with no plant association (sees nothing)
 * - admin=false, non-empty plantIds = user's associated plant IDs
 *
 * Always call clear() in a finally block to prevent thread pool leaks.
 */
public class PlantContext {

    private record Context(boolean admin, List<UUID> plantIds) {}

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private PlantContext() {}

    /**
     * Sets plant IDs for the current request.
     * Pass null to indicate ADMIN context (no filter).
     */
    public static void set(List<UUID> plantIds) {
        if (plantIds == null) {
            HOLDER.set(new Context(true, List.of()));
        } else {
            HOLDER.set(new Context(false, List.copyOf(plantIds)));
        }
    }

    /**
     * Explicit API for ADMIN context — no plant filter needed.
     * Prefer this over set(null) for clarity.
     */
    public static void setAdmin() {
        HOLDER.set(new Context(true, List.of()));
    }

    /**
     * Returns the current plant IDs, or empty list if not set.
     */
    public static List<UUID> current() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.plantIds() : List.of();
    }

    /**
     * Returns true if the current context is an ADMIN without plant filter.
     */
    public static boolean isAdminContext() {
        Context ctx = HOLDER.get();
        return ctx != null && ctx.admin();
    }

    /**
     * Clears the ThreadLocal — MUST be called in finally block.
     */
    public static void clear() {
        HOLDER.remove();
    }
}
