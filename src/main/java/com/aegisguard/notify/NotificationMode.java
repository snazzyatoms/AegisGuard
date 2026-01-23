package com.aegisguard.notify;

public enum NotificationMode {
    CHAT,
    ACTION_BAR,
    TITLE;

    public static NotificationMode fromString(String value) {
        try {
            return NotificationMode.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return CHAT;
        }
    }
}
