package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Configuration
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
@Getter
public class Advanced {

    @Comment({
            "Optional rules to raise the nametag when the worn helmet matches.",
            "Higher priority is evaluated first. The first matching rule wins (same behavior as other hat hooks).",
            "Create advanced.yml in the plugin folder manually; it is never auto-generated."
    })
    private List<HelmetHeightRule> helmetHeightRules = new ArrayList<>();

    @Comment("Converts hat-hook helmet height into extra nametag yOffset. Increase if the nametag still clips tall hats.")
    private float helmetHeightYOffsetMultiplier = 0.25f / 14f;

    @Configuration
    @Getter
    @NoArgsConstructor
    public static class HelmetHeightRule {

        @Comment("Higher value = checked before lower priority rules.")
        private int priority;

        @Comment("Vertical offset (same unit as Nexo/Oraxen hat hooks). Must be > 0.")
        private double height;

        @Comment("Bukkit material name, e.g. LEATHER_HELMET. Omit to ignore material.")
        private String material;

        @Comment("Exact CustomModelData. Ignored if custom-model-data range below is set.")
        private Integer customModelData;

        @Comment("Inclusive CMD range; both must be set to use a range.")
        private Integer customModelDataMin;

        private Integer customModelDataMax;

        @Comment("Equippable model key (1.21.3+), e.g. mypack:item/my_hat")
        private String equippableModel;

        @Comment("If non-empty, the player must be in one of these world names.")
        private List<String> worlds = new ArrayList<>();

        @Comment("If set, the player must have this permission.")
        private String permission;

        public boolean definesItemMatch() {
            final boolean mat = material != null && !material.isEmpty();
            final boolean eq = equippableModel != null && !equippableModel.isEmpty();
            final boolean cmdExact = customModelData != null;
            final boolean cmdRange = customModelDataMin != null && customModelDataMax != null;
            return mat || eq || cmdExact || cmdRange;
        }
    }
}
