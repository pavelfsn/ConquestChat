package com.conquest.chat.client;

import com.conquest.chat.channel.ChannelType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ChatTabManager {
    private static final ChatTabManager INSTANCE = new ChatTabManager();
    private final List<ChatTab> tabs;
    private int currentTabIndex = 0;

    private ChatTabManager() {
        tabs = new ArrayList<>();

        // Вкладка "Общий" - все каналы кроме боя
        tabs.add(new ChatTab("Общий", EnumSet.of(
                ChannelType.ALL,
                ChannelType.GLOBAL,
                ChannelType.TRADE,
                ChannelType.WHISPER
        )));

        // Вкладка "Торг" - только торговля
        tabs.add(new ChatTab("Торг", EnumSet.of(ChannelType.TRADE)));

        // Вкладка "Бой" - боевые события
        tabs.add(new ChatTab("Бой", EnumSet.of(ChannelType.COMBAT)));

        // Вкладка "Личное" - личные сообщения
        tabs.add(new ChatTab("Личное", EnumSet.of(ChannelType.WHISPER)));
    }

    public static ChatTabManager getInstance() {
        return INSTANCE;
    }

    public List<ChatTab> getTabs() {
        return tabs;
    }

    public ChatTab getCurrentTab() {
        return tabs.get(currentTabIndex);
    }

    public void setCurrentTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            currentTabIndex = index;
        }
    }

    public int getCurrentTabIndex() {
        return currentTabIndex;
    }

    public static class ChatTab {
        private final String name;
        private final Set<ChannelType> visibleChannels;

        public ChatTab(String name, Set<ChannelType> visibleChannels) {
            this.name = name;
            this.visibleChannels = visibleChannels;
        }

        public String getName() {
            return name;
        }

        public boolean isChannelVisible(ChannelType channel) {
            return visibleChannels.contains(channel);
        }

        public Set<ChannelType> getVisibleChannels() {
            return visibleChannels;
        }
    }
}
