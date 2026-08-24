package com.choivoo.jarvis.companion;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class JarvisNotificationListener extends NotificationListenerService {
    private static final String ACTION = "com.choivoo.jarvis.COMPANION_NOTIFICATION";
    private static final String MAIN_PACKAGE = "com.choivoo.jarvis";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (MAIN_PACKAGE.equals(sbn.getPackageName()) || getPackageName().equals(sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        CharSequence titleCs = n.extras.getCharSequence(Notification.EXTRA_TITLE, "");
        CharSequence textCs = n.extras.getCharSequence(Notification.EXTRA_TEXT, "");
        String title = titleCs == null ? "" : titleCs.toString();
        String text = textCs == null ? "" : textCs.toString();

        if (title.isEmpty() && text.isEmpty()) return;

        Intent bridge = new Intent(ACTION);
        bridge.setPackage(MAIN_PACKAGE);
        bridge.putExtra("packageName", sbn.getPackageName());
        bridge.putExtra("title", truncate(title, 240));
        bridge.putExtra("text", truncate(text, 600));
        bridge.putExtra("timestamp", sbn.getPostTime());
        sendBroadcast(bridge, "com.choivoo.jarvis.permission.COMPANION_BRIDGE");
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
