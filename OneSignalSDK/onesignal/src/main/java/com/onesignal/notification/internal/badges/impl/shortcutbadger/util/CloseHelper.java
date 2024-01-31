// Subpackaged to prevent conflicts with other plugins
package com.onesignal.notification.internal.badges.impl.shortcutbadger.util;

import android.database.Cursor;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author leolin
 */
public class CloseHelper {

    public static void close(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }


    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException var2) {

        }
    }
}
