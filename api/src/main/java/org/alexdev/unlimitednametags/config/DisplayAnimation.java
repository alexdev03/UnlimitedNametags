package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Polymorphic;
import de.exlll.configlib.PolymorphicTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional per-display-group animation. YAML under {@code animation:} with
 * {@code type: rotate | bob | dvd_bounce | pulse_scale | wiggle | orbit | custom}.
 * Optional {@code cullBeyondBlocks} (on any type) skips pose updates when no viewer is within range.
 * From code, prefer {@link NametagDisplayAnimations} to build instances.
 * <p>
 * {@link CustomDisplayAnimation} dispatches to handlers registered on the plugin via
 * {@link org.alexdev.unlimitednametags.api.UnlimitedNameTagsPlugin#registerNametagCustomAnimation}.
 */
@Configuration
@Getter
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
@Polymorphic
@PolymorphicTypes({
        @PolymorphicTypes.Type(type = DisplayAnimation.RotateDisplayAnimation.class, alias = "rotate"),
        @PolymorphicTypes.Type(type = DisplayAnimation.BobDisplayAnimation.class, alias = "bob"),
        @PolymorphicTypes.Type(type = DisplayAnimation.DvdBounceDisplayAnimation.class, alias = "dvd_bounce"),
        @PolymorphicTypes.Type(type = DisplayAnimation.PulseScaleDisplayAnimation.class, alias = "pulse_scale"),
        @PolymorphicTypes.Type(type = DisplayAnimation.WiggleDisplayAnimation.class, alias = "wiggle"),
        @PolymorphicTypes.Type(type = DisplayAnimation.OrbitDisplayAnimation.class, alias = "orbit"),
        @PolymorphicTypes.Type(type = DisplayAnimation.CustomDisplayAnimation.class, alias = "custom")
})
public abstract class DisplayAnimation {

    @Comment("If false, this animation does nothing.")
    private boolean enabled = true;

    @Comment("Tempo multiplier (1 = default speed for the chosen type).")
    private double speed = 1.0;

    @Comment({
            "If > 0, animation pose updates are skipped when every current viewer is farther than this (blocks) from the nametag owner (same world).",
            "Clears motion while nobody is close enough to see it meaningfully; resumes smoothly when someone enters range. 0 = no distance culling."
    })
    private double cullBeyondBlocks = 0.0;

    @Comment({
            "Optional string map for add-ons or future use (readable via API; built-in animators ignore unknown keys).",
            "YAML example: customProperties: { my_plugin_mode: sparkle }"
    })
    private Map<String, String> customProperties = new LinkedHashMap<>();

    public boolean isAnimating() {
        return enabled && speed > 0;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class RotateDisplayAnimation extends DisplayAnimation {

        @Comment("Y = spin like a turntable; X / Z = tilt plane; XYZ = slow tumble (combined axes).")
        private String axis = "Y";

        @Comment("Degrees per second at speed 1.")
        private double degreesPerSecond = 90.0;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class BobDisplayAnimation extends DisplayAnimation {

        @Comment("Vertical motion amplitude in blocks (local Y).")
        private double amplitude = 0.06;

        @Comment("Full up-down cycles per second at speed 1.")
        private double bobsPerSecond = 1.0;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class DvdBounceDisplayAnimation extends DisplayAnimation {

        @Comment("Half-width of the bounce box along local X (blocks).")
        private double halfWidth = 0.12;

        @Comment("Half-height along local Z (blocks); classic DVD screensaver in the horizontal plane above the tag stack.")
        private double halfDepth = 0.1;

        @Comment("Travel speed scale (blocks / second at speed 1).")
        private double pace = 0.35;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class PulseScaleDisplayAnimation extends DisplayAnimation {

        @Comment("Minimum scale multiplier (relative to the nametag line scale).")
        private double minMultiplier = 0.92;

        @Comment("Maximum scale multiplier.")
        private double maxMultiplier = 1.08;

        @Comment("Full breathe cycles per second at speed 1.")
        private double pulsesPerSecond = 1.0;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class WiggleDisplayAnimation extends DisplayAnimation {

        @Comment("Peak tilt amplitude in degrees.")
        private double amplitudeDegrees = 10.0;

        @Comment("Wiggle cycles per second at speed 1.")
        private double wigglesPerSecond = 2.0;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class OrbitDisplayAnimation extends DisplayAnimation {

        @Comment("Orbit radius in blocks (local XZ plane).")
        private double radius = 0.1;

        @Comment("Full orbits per second at speed 1.")
        private double rotationsPerSecond = 0.5;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class CustomDisplayAnimation extends DisplayAnimation {

        @Comment("Must match a handler id passed to registerNametagCustomAnimation (UNTAPI / UnlimitedNameTagsPlugin).")
        private String id = "";
    }
}
