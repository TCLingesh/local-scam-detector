package com.example.scamguard;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ScamNotificationListener extends NotificationListenerService {
    private static final String TAG = "ScamListener";

    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.xiaomi.mms",
            "com.samsung.android.messaging",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.android.shell"   // for adb tests, remove before the final demo
    ));

    // The same notification can be posted twice in a row; skip repeats within 5 seconds.
    private int lastHash;
    private long lastTime;

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "listener CONNECTED");
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "listener DISCONNECTED");
        try {
            requestRebind(new ComponentName(this, ScamNotificationListener.class));
        } catch (Exception e) {
            Log.e(TAG, "rebind failed", e);
        }
    }

    // Reads the message text. Never log or store what this returns.
    private static String extractText(Bundle ex) {
        CharSequence cs = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (cs == null || cs.length() == 0) cs = ex.getCharSequence(Notification.EXTRA_TEXT);
        if (cs != null && cs.length() > 0) return cs.toString();

        // Chat apps (WhatsApp, Telegram) may put the message in a list instead
        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof Bundle) {
            CharSequence m = ((Bundle) msgs[msgs.length - 1]).getCharSequence("text");
            if (m != null && m.length() > 0) return m.toString();
        }
        return "";
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        final String pkg = sbn.getPackageName();
        if (getPackageName().equals(pkg)) return;          // never scan our own alerts
        Log.d(TAG, "GOT pkg=" + pkg);                       // package name only

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) {
            Log.d(TAG, "skip: no notification data pkg=" + pkg);
            return;
        }
        if (!ALLOWED.contains(pkg)) {
            Log.d(TAG, "skip: package not allowed pkg=" + pkg);
            return;
        }
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d(TAG, "skip: group summary pkg=" + pkg);
            return;
        }

        final String text = extractText(n.extras);
        Log.d(TAG, "text length=" + text.length() + " pkg=" + pkg);   // length only, never the text
        if (text.trim().isEmpty()) {
            Log.d(TAG, "skip: empty text pkg=" + pkg);
            return;
        }

        int h = (pkg + "|" + text).hashCode();
        long now = SystemClock.elapsedRealtime();
        if (h == lastHash && now - lastTime < 5000) {
            Log.d(TAG, "skip: duplicate pkg=" + pkg);
            return;
        }
        lastHash = h;
        lastTime = now;

        final int id = sbn.getKey().hashCode();
        final Context app = getApplicationContext();

        // Model inference must not run on the service's main thread
//        Background.DETECT.execute(() -> {
//            try {
//                Detection d = DetectorPipeline.analyze(app, text);
//                final int pct = Math.round(d.confidence * 100);
//                Log.d(TAG, "verdict scam=" + d.isScam + " conf=" + pct
//                        + " signals=" + d.signals + " pkg=" + pkg);
//
//                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
//                if (!d.isScam) {
//                    // Safe messages: only verdict, time and app are kept. Never the text.
//                    HistoryStore.add(app, time + " | SAFE " + pct + "% | " + pkg);
//                    return;
//                }
//
//                final String type = ScamRules.typeOf(d.signals);
//                DetailStore.save(app, id, type, pct,
//                        android.text.TextUtils.join(",", d.signals), pkg, time, text);
//                HistoryStore.add(app, time + " | SCAM " + pct + "% | " + pkg + " | " + id);
//
//                final String title = "Possible scam: " + type;
//
//                // 1) Immediate alert
//                AlertNotifier.show(app, id, title, "Tap to see the message and why it is risky.");
//                Log.d(TAG, "alert posted");
//
//                // 2) Llama explanation replaces the alert text when ready
//                AdvisoryEngine.get(app).explain(d, new AdvisoryEngine.Listener() {
//                    @Override public void onToken(String token) { }
//                    @Override public void onDone(String fullText) {
//                        String t = fullText.trim();
//                        if (!t.isEmpty()) {
//                            DetailStore.setExplanation(app, id, t);
//                            AlertNotifier.show(app, id, title, t);
//                            Log.d(TAG, "alert updated with Llama text");
//                        }
//                    }
//                    @Override public void onError(String message) {
//                        Log.e(TAG, "advisory error: " + message);
//                    }
//                });
//            } catch (Throwable t) {
//                Log.e(TAG, "analyze failed", t);
//            }
//        });
        Background.DETECT.execute(() -> {
            try {
                Detection d = DetectorPipeline.analyze(app, text);
                final int pct = Math.round(d.confidence * 100);
                Log.d(TAG, "verdict scam=" + d.isScam + " conf=" + pct
                        + " signals=" + d.signals + " pkg=" + pkg);

                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                if (!d.isScam) {
                    // Safe messages: only verdict, time and app are kept. Never the text.
                    HistoryStore.add(app, time + " | SAFE " + pct + "% | " + pkg);
                    return;
                }

                final String type = ScamRules.typeOf(d.signals);
                DetailStore.save(app, id, type, pct,
                        android.text.TextUtils.join(",", d.signals), pkg, time, text);
                HistoryStore.add(app, time + " | SCAM " + pct + "% | " + pkg + " | " + id);

                final String title = "Possible scam: " + type;

                // 1) Immediate alert
                AlertNotifier.show(app, id, title, "Tap to see the message and why it is risky.");
                Log.d(TAG, "alert posted");

                // 2) Llama writes the explanation and the steps, the alert text is replaced when ready
                AdvisoryEngine.get(app).explain(d, new AdvisoryEngine.Listener() {
                    @Override public void onToken(String token) { }

                    @Override public void onDone(String fullText) {
                        AdvisoryEngine.Advice a = AdvisoryEngine.parse(fullText, d.signals);
                        DetailStore.setActions(app, id, a.actions);
                        DetailStore.setExplanation(app, id, a.why);
                        AlertNotifier.show(app, id, title, a.why);
                        Log.d(TAG, "alert updated with Llama text");
                    }

                    @Override public void onError(String err) {
                        Log.e(TAG, "advisory error: " + err);
                        DetailStore.setActions(app, id, ScamRules.actions(d.signals));
                        DetailStore.setExplanation(app, id, ScamRules.fallbackWhy(d.signals));
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "analyze failed", t);
            }
        });
    }
}