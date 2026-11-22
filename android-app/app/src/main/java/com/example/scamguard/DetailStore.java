package com.example.scamguard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

// Keeps the last 20 SCAM detections on this phone only, in private app storage.
public class DetailStore {
    private static final String PREFS = "details";
    private static final String KEY = "records";
    private static final int MAX = 20;

    public static synchronized void save(Context c, int id, String type, int pct,
                                         String signals, String source, String time, String message) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray old = new JSONArray(sp.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("type", type);
            o.put("pct", pct);
            o.put("signals", signals);
            o.put("source", source);
            o.put("time", time);
            o.put("message", message.length() > 500 ? message.substring(0, 500) + "..." : message);
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < old.length() && out.length() < MAX; i++) {
                JSONObject x = old.getJSONObject(i);
                if (x.getInt("id") != id) out.put(x);
            }
            sp.edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static synchronized void setExplanation(Context c, int id, String text) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                if (x.getInt("id") == id) {
                    x.put("explanation", text);
                    break;
                }
            }
            sp.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static synchronized JSONObject get(Context c, int id) {
        try {
            JSONArray arr = new JSONArray(
                    c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                if (x.getInt("id") == id) return x;
            }
        } catch (Exception ignored) { }
        return null;
    }

    public static synchronized void clear(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }
    public static synchronized void setActions(Context c, int id, String text) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                if (x.getInt("id") == id) {
                    x.put("actions", text);
                    break;
                }
            }
            sp.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }
}