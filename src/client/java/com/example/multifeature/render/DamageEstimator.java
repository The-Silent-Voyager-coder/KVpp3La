// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.render;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Client-side damage math, mirroring the vanilla formula best-effort.
 * Pure functions — no state, no tick coupling. Called only when the overlay
 * estimate is recomputed (target/health/weapon change), never per frame.
 *
 * Honest limits: weapon base + enchants + strength + armor/toughness +
 * resistance are all client-readable, so this is close on vanilla. Server
 * multipliers, plugins and scripted damage rules are invisible — the estimate
 * will be wrong there. Enchant bonuses below are the classic Sharpness/Smite/
 * Bane shapes (documented approximation, not the effect-component graph).
 */
public final class DamageEstimator {
    private DamageEstimator() {
    }

    public static double estimateDamagePerHit(LocalPlayer player, ItemStack weapon, LivingEntity target) {
        double base = 1.0;
        double[] acc = new double[]{0.0};
        weapon.forEachModifier(EquipmentSlot.MAINHAND,
                (Holder<Attribute> holder, AttributeModifier modifier) -> {
                    if (holder.value() == Attributes.ATTACK_DAMAGE) {
                        acc[0] += modifier.amount();
                    }
                });
        if (acc[0] != 0.0) {
            base = acc[0];
        }

        ItemEnchantments ench = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        // Enchantments.* are ResourceKeys; resolve to Holders once per recompute (rare path, not per frame).
        HolderLookup.RegistryLookup<Enchantment> lookup =
                player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int sharp = ench.getLevel(lookup.getOrThrow(Enchantments.SHARPNESS));
        int smite = ench.getLevel(lookup.getOrThrow(Enchantments.SMITE));
        int bane = ench.getLevel(lookup.getOrThrow(Enchantments.BANE_OF_ARTHROPODS));
        EntityType<?> type = target.getType();
        double bonus = 0.0;
        if (sharp > 0) {
            bonus += 1.0 + 0.5 * (sharp - 1);
        }
        if (smite > 0 && type.is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            bonus += 2.5 * smite;
        }
        if (bane > 0 && type.is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            bonus += 2.5 * bane;
        }

        MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
        double strengthBonus = strength != null ? 3.0 * (strength.getAmplifier() + 1) : 0.0;

        // KillAura only attacks on full cooldown, so the cooldown factor is 1.0.
        double total = base + bonus + strengthBonus;

        double armor = target.getAttributeValue(Attributes.ARMOR);
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double reduction = Math.min(20.0, Math.max(armor / 5.0, armor - total / (2.0 + toughness / 4.0))) / 25.0;
        double effective = total * (1.0 - reduction);

        MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            effective *= 1.0 - 0.2 * (resistance.getAmplifier() + 1);
        }
        return Math.max(0.0, effective);
    }

    /** Seconds between KillAura attacks: cooldown period only (no artificial delay). */
    public static double attackIntervalSeconds(LocalPlayer player) {
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        return speed > 0.0 ? 1.0 / speed : 5.0;
    }

    /** ceil(health / dph) hits times interval. Infinity when nothing lands. */
    public static double estimateSeconds(double health, double damagePerHit, double intervalSeconds) {
        if (damagePerHit <= 0.0 || health <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.ceil(health / damagePerHit) * intervalSeconds;
    }
}
