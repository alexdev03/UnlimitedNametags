package org.alexdev.unlimitednametags.hook;

/**
 * Set by the plugin placeholder manager around helmet-offset
 * computation so hat hooks can emit matching diagnostics without redundant toggles.
 */
public final class HelmetDebugContext {

    private static final ThreadLocal<Boolean> VERBOSE = new ThreadLocal<>();

    private HelmetDebugContext() {
    }

    public static void setVerbose(final boolean verbose) {
        if (verbose) {
            VERBOSE.set(Boolean.TRUE);
        } else {
            VERBOSE.remove();
        }
    }

    public static boolean isVerbose() {
        return Boolean.TRUE.equals(VERBOSE.get());
    }

    public static void clear() {
        VERBOSE.remove();
    }
}
