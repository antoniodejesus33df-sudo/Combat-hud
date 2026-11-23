package com.combathud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

public class CombatHudClient implements ClientModInitializer {
    private static final long TIMEOUT_MS = 6000;
    private static final int W = 210, H = 80;
    private static final int VIOLET = 0xFFB14CFF, CYAN = 0xFF00E5FF;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private static LivingEntity target;
    private static long lastCombat, flashStart, lastFrameNs;
    private static float shownHealth, lastHealth, anim;

    @Override
    public void onInitializeClient() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && entity instanceof LivingEntity living && living != player) {
                setTarget(living);
            }
            return ActionResult.PASS;
        });
        HudRenderCallback.EVENT.register(CombatHudClient::render);
    }

    private static void setTarget(LivingEntity e) {
        if (target != e) {
            shownHealth = lastHealth = e.getHealth();
        }
        target = e;
        lastCombat = System.currentTimeMillis();
    }

    private static int a(int argb, float alpha) {
        int al = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private static int lerp(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f, p = x - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    private static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden) return;

        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0 ? 0f : Math.min((nowNs - lastFrameNs) / 1e9f, 0.1f);
        lastFrameNs = nowNs;
        long now = System.currentTimeMillis();
        float t = (now % 100000L) / 1000f;

        LivingEntity attacker = p.getAttacker();
        if (attacker != null && p.age - p.getLastAttackedTime() < 100) setTarget(attacker);

        if (target == null) return;
        if (target.isRemoved()) { target = null; return; }

        if (!target.isAlive() && now - lastCombat < TIMEOUT_MS - 1200) {
            lastCombat = now - (TIMEOUT_MS - 1200);
        }
        boolean closing = now - lastCombat > TIMEOUT_MS;
        anim = closing ? Math.max(0f, anim - dt * 3.5f) : Math.min(1f, anim + dt * 4f);
        if (closing && anim <= 0f) { target = null; return; }

        float alpha = Math.min(1f, anim * 1.6f);
        float e = easeOutBack(anim);

        TextRenderer font = mc.textRenderer;
        int x = (ctx.getScaledWindowWidth() - W) / 2;
        int y = 8 - (int) ((1f - e) * (H + 16));

        float mix = (float) (Math.sin(t * 2.0) * 0.5 + 0.5);
        int accent = lerp(VIOLET, CYAN, mix);

        float max = Math.max(target.getMaxHealth(), 1f);
        float hp = Math.max(target.getHealth(), 0f);
        if (hp < lastHealth - 0.01f) flashStart = now;
        lastHealth = hp;
        shownHealth += (hp - shownHealth) * Math.min(1f, dt * 6f);
        float ratio = Math.min(hp / max, 1f);

        ctx.fill(x - 3, y - 3, x + W + 3, y + H + 3, a(accent, 0.18f * alpha));
        ctx.fill(x - 1, y - 1, x + W + 1, y + H + 1, a(accent, 0.95f * alpha));
        ctx.fillGradient(x, y, x + W, y + H, a(0xF01A0B2E, alpha), a(0xF00A1630, alpha));

        ctx.fill(x, y, x + W, y + 2, a(accent, alpha));
        int sx = x + (int) (((t * 0.7f) % 1f) * (W + 60)) - 40;
        int s0 = Math.max(x, sx), s1 = Math.min(x + W, sx + 40);
        if (s1 > s0) ctx.fill(s0, y, s1, y + 2, a(0xFFFFFFFF, 0.8f * alpha));

        long since = now - flashStart;
        if (since < 300) {
            float f = 1f - since / 300f;
            ctx.fill(x, y, x + W, y + H, a(0xFFFF2020, 0.35f * f * alpha));
        }

        int cs = 3;
        ctx.fill(x, y, x + cs, y + cs, a(0xFFFFFFFF, alpha));
        ctx.fill(x + W - cs, y, x + W, y + cs, a(0xFFFFFFFF, alpha));
        ctx.fill(x, y + H - cs, x + cs, y + H, a(0xFFFFFFFF, alpha));
        ctx.fill(x + W - cs, y + H - cs, x + W, y + H, a(0xFFFFFFFF, alpha));

        if (alpha < 0.2f) return;

        ctx.fill(x + 4, y + 4, x + 58, y + H - 4, a(accent, 0.7f));
        ctx.fillGradient(x + 5, y + 5, x + 57, y + H - 5, 0xFF2A1450, 0xFF0A0A1E);
        float big = Math.max(target.getHeight(), target.getWidth() * 1.2f);
        int size = Math.max(5, (int) (50f / Math.max(big, 0.5f)));
        float lookX = x + 31 + (float) Math.sin(t * 1.5) * 25f;
        InventoryScreen.drawEntity(ctx, x + 5, y + 5, x + 57, y + H - 5, size, 0.0625f,
                lookX, y + 22, target);

        int bx = x + 64, bw = W - 64 - 8;
        String name = font.trimToWidth(target.getName().getString(), bw);
        ctx.drawTextWithShadow(font, name, bx, y + 7, 0xFFFFFFFF);

        int by = y + 20, bh = 10;
        ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        ctx.fill(bx, by, bx + bw, by + bh, 0xFF2B2B35);
        ctx.fill(bx, by, bx + (int) (bw * Math.min(shownHealth / max, 1f)), by + bh, 0x8CFFFFFF);
        int hi, lo;
        if (ratio > 0.5f) { hi = 0xFF7CFF6B; lo = 0xFF1E9E3A; }
        else if (ratio > 0.25f) { hi = 0xFFFFE066; lo = 0xFFC79A12; }
        else { hi = 0xFFFF6B6B; lo = 0xFFB01E1E; }
        float pulse = ratio <= 0.25f ? (float) (0.75 + 0.25 * Math.sin(t * 8)) : 1f;
        int fw = (int) (bw * ratio);
        ctx.fillGradient(bx, by, bx + fw, by + bh, a(hi, pulse), a(lo, pulse));
        int hx = bx + (int) (((t * 0.8f) % 1f) * (fw + 20)) - 20;
        int h0 = Math.max(bx, hx), h1 = Math.min(bx + fw, hx + 14);
        if (h1 > h0) ctx.fill(h0, by, h1, by + bh, 0x59FFFFFF);
        String hpText = String.format("%.1f / %.1f", hp, max);
        ctx.drawTextWithShadow(font, hpText, bx + (bw - font.getWidth(hpText)) / 2, by + 1, 0xFFFFFFFF);

        int ay = y + 35;
        for (int i = 0; i < 4; i++) {
            int ax = bx + i * 19;
            ctx.fill(ax, ay, ax + 18, ay + 18, 0x90000000);
            ItemStack piece = target.getEquippedStack(ARMOR[i]);
            if (piece.isEmpty()) ctx.fill(ax + 7, ay + 7, ax + 11, ay + 11, 0x40FFFFFF);
            else ctx.drawItem(piece, ax + 1, ay + 1);
        }
        ctx.drawTextWithShadow(font, "DEF " + target.getArmor(), bx + 4 * 19 + 4, ay + 5, 0xFF9FD8FF);

        int wy = y + 56;
        ItemStack weapon = target.getMainHandStack();
        ctx.fill(bx, wy, bx + 18, wy + 18, 0x90000000);
        if (weapon.isEmpty()) {
            ctx.drawTextWithShadow(font, "Sin arma", bx + 23, wy + 5, 0xFFAAAAAA);
        } else {
            ctx.drawItem(weapon, bx + 1, wy + 1);
            String wn = font.trimToWidth(weapon.getName().getString(), bw - 23);
            ctx.drawTextWithShadow(font, wn, bx + 23, wy + 5, 0xFFFFD27F);
        }
    }
}
