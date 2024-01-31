// Subpackaged to prevent conflicts with other plugins
package com.onesignal.onesignal.notification.internal.badges.impl.shortcutbadger;

public class ShortcutBadgeException extends Exception {
    public ShortcutBadgeException(String message) {
        super(message);
    }

    public ShortcutBadgeException(String message, Exception e) {
        super(message, e);
    }

}
