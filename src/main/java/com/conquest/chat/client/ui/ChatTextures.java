package com.conquest.chat.client.ui;

import net.minecraft.resources.ResourceLocation;

public final class ChatTextures {
    private ChatTextures() {}

    public static final String MODID = "conquestchat";

    public static final ResourceLocation PANEL_9 = rl("textures/gui/chat/panel_9.png");
    public static final ResourceLocation CONTEXT_9 = rl("textures/gui/chat/context_9.png");

    public static final ResourceLocation TAB_INACTIVE_9 = rl("textures/gui/chat/tab_inactive_9.png");
    public static final ResourceLocation TAB_ACTIVE_9 = rl("textures/gui/chat/tab_active_9.png");

    public static final ResourceLocation TAB_ROW_BG_9 = rl("textures/gui/chat/tab_row_bg_9.png");
    public static final ResourceLocation TAB_DIVIDER = rl("textures/gui/chat/tab_divider.png");

    public static final ResourceLocation INPUT_9 = rl("textures/gui/chat/input_9.png");
    public static final ResourceLocation INPUT_FIELD_9 = rl("textures/gui/chat/input_field_9.png");

    public static final ResourceLocation LINE_1PX = rl("textures/gui/chat/line_1px.png");

    public static final ResourceLocation SCROLL_TRACK = rl("textures/gui/chat/scroll_track.png");
    public static final ResourceLocation SCROLL_THUMB = rl("textures/gui/chat/scroll_thumb.png");

    public static final ResourceLocation PICKER_PANEL_9 = rl("textures/gui/chat/picker_panel_9.png");
    public static final ResourceLocation PICKER_BUTTON_9 = rl("textures/gui/chat/picker_button_9.png");
    public static final ResourceLocation PICKER_SLOT_BG = rl("textures/gui/chat/slot_bg.png");

    public static final ResourceLocation CORNER_12 = rl("textures/gui/chat/corner_12.png");

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(MODID, path);
    }
}
