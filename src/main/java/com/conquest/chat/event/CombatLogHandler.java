package com.conquest.chat.event;

import com.conquest.chat.ConquestChatMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Mod.EventBusSubscriber(modid = ConquestChatMod.MOD_ID)
public class CombatLogHandler {

    private static final DecimalFormat DMG_FORMAT = new DecimalFormat("#.##");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        LivingEntity victim = event.getEntity();
        float damage = event.getAmount();

        // 1. Если атакует игрок -> пишем ему (Вы нанесли урон)
        if (attacker instanceof ServerPlayer playerAttacker) {
            String victimName = victim.getDisplayName().getString();
            String msg = String.format("[COMBAT] Вы нанесли %s урона по %s",
                    DMG_FORMAT.format(damage), victimName);

            // Отправляем системное сообщение, которое перехватит ChatEventHandler
            playerAttacker.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY));
        }

        // 2. Если бьют игрока -> пишем ему (Вам нанесли урон)
        if (victim instanceof ServerPlayer playerVictim) {
            String attackerName = (attacker != null) ? attacker.getDisplayName().getString() : "Unknown";
            String msg = String.format("[COMBAT] Вы получили %s урона от %s",
                    DMG_FORMAT.format(damage), attackerName);

            playerVictim.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();

        // Если убил игрок
        if (killer instanceof ServerPlayer playerKiller) {
            String msg = String.format("[COMBAT] Вы убили %s", victim.getDisplayName().getString());
            playerKiller.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN));
        }

        // Если умер игрок
        if (victim instanceof ServerPlayer playerVictim) {
            String killerName = (killer != null) ? killer.getDisplayName().getString() : "Environment";
            String msg = String.format("[COMBAT] Вы были убиты %s", killerName);
            playerVictim.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.DARK_RED));
        }
    }
}
