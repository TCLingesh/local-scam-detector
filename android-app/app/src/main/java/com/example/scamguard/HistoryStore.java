package com.example.scamguard;

import android.content.Context;
import android.content.SharedPreferences;

public class HistoryStore {
    private static final String PREFS = "history";
    private static final String KEY = "entries";
    private static final int MAX = 20;

    // Stores only verdict, confidence, source and time. NOT the message text.
    public static void add(Context ctx, String line) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String old = sp.getString(KEY, "");
        String[] parts = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < parts.length && i < MAX - 1; i++) {
            sb.append("\n").append(parts[i]);
        }
        sp.edit().putString(KEY, sb.toString()).apply();
    }

    public static String get(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "");
    }
    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }
}