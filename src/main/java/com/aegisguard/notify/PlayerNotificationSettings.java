package com.aegisguard.notify;

public class PlayerNotificationSettings {

    private NotificationMode mode;
    private boolean greetings;
    private boolean adminUpdates;

    public PlayerNotificationSettings(NotificationMode mode, boolean greetings, boolean adminUpdates) {
        this.mode = mode;
        this.greetings = greetings;
        this.adminUpdates = adminUpdates;
    }

    public NotificationMode getMode() {
        return mode;
    }

    public void setMode(NotificationMode mode) {
        this.mode = mode;
    }

    public boolean greetingsEnabled() {
        return greetings;
    }

    public void setGreetings(boolean value) {
        this.greetings = value;
    }

    public boolean adminUpdatesEnabled() {
        return adminUpdates;
    }

    public void setAdminUpdates(boolean value) {
        this.adminUpdates = value;
    }
}
