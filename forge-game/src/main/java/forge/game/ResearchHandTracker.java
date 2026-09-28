package forge.game;

import forge.util.ResearchMode;

/**
 * Marks the synchronous zone move performed by Player.doDraw().
 *
 * <p>This lets the research logger distinguish a rules draw from other
 * Library -> Hand effects without guessing from zones alone.</p>
 */
public final class ResearchHandTracker {
    private static final ThreadLocal<Integer> DRAW_DEPTH = ThreadLocal.withInitial(() -> 0);

    private ResearchHandTracker() {
    }

    public static void beginDraw() {
        if (ResearchMode.isEnabled()) {
            DRAW_DEPTH.set(DRAW_DEPTH.get() + 1);
        }
    }

    public static void endDraw() {
        if (!ResearchMode.isEnabled()) {
            return;
        }
        int depth = DRAW_DEPTH.get() - 1;
        if (depth <= 0) {
            DRAW_DEPTH.remove();
        } else {
            DRAW_DEPTH.set(depth);
        }
    }

    public static boolean isDrawMove() {
        return ResearchMode.isEnabled() && DRAW_DEPTH.get() > 0;
    }
}
