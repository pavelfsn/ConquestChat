package com.conquest.chat.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class ChatNineSlice {
    private ChatNineSlice() {}

    public static void blit9(GuiGraphics g, ResourceLocation tex,
                             int x, int y, int w, int h,
                             int border, int texW, int texH) {
        if (w <= 0 || h <= 0) return;

        int b = Math.min(border, Math.min(w / 2, h / 2));

        int srcMidW = texW - b - b;
        int srcMidH = texH - b - b;

        int dstMidW = w - b - b;
        int dstMidH = h - b - b;

        // corners (no scale)
        blitScaled(g, tex, x, y, 0, 0, b, b, 1f, 1f, texW, texH);
        blitScaled(g, tex, x + w - b, y, texW - b, 0, b, b, 1f, 1f, texW, texH);
        blitScaled(g, tex, x, y + h - b, 0, texH - b, b, b, 1f, 1f, texW, texH);
        blitScaled(g, tex, x + w - b, y + h - b, texW - b, texH - b, b, b, 1f, 1f, texW, texH);

        // edges (scale 1 axis)
        if (dstMidW > 0) {
            float sx = (float) dstMidW / (float) srcMidW;
            blitScaled(g, tex, x + b, y, b, 0, srcMidW, b, sx, 1f, texW, texH);
            blitScaled(g, tex, x + b, y + h - b, b, texH - b, srcMidW, b, sx, 1f, texW, texH);
        }
        if (dstMidH > 0) {
            float sy = (float) dstMidH / (float) srcMidH;
            blitScaled(g, tex, x, y + b, 0, b, b, srcMidH, 1f, sy, texW, texH);
            blitScaled(g, tex, x + w - b, y + b, texW - b, b, b, srcMidH, 1f, sy, texW, texH);
        }

        // center (scale both axes)
        if (dstMidW > 0 && dstMidH > 0) {
            float sx = (float) dstMidW / (float) srcMidW;
            float sy = (float) dstMidH / (float) srcMidH;
            blitScaled(g, tex, x + b, y + b, b, b, srcMidW, srcMidH, sx, sy, texW, texH);
        }
    }

    private static void blitScaled(GuiGraphics g, ResourceLocation tex,
                                   int dstX, int dstY,
                                   int srcU, int srcV,
                                   int srcW, int srcH,
                                   float scaleX, float scaleY,
                                   int texW, int texH) {
        g.pose().pushPose();
        g.pose().translate(dstX, dstY, 0);
        g.pose().scale(scaleX, scaleY, 1f);
        g.blit(tex, 0, 0, (float) srcU, (float) srcV, srcW, srcH, texW, texH);
        g.pose().popPose();
    }
}
