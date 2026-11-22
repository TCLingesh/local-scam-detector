package com.example.scamguard;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ModelSpec {
    public final int maxLen;
    public final int unkIndex;
    public final int padIndex;
    public final float threshold;
    public final String[] labels;

    public ModelSpec(Context ctx) throws Exception {
        JSONObject o = new JSONObject(readAsset(ctx, "model_spec.json"));
        JSONObject in = o.optJSONObject("input");
        JSONObject out = o.optJSONObject("output");

        // max_len, unk_index, pad_index live inside "input" in the real spec
        maxLen = findInt("max_len", in, o);
        unkIndex = findInt("unk_index", in, o);
        padIndex = findInt("pad_index", in, o);

        // threshold is top-level
        threshold = (float) o.getDouble("threshold");

        // labels may be top-level or inside "output"; fall back to ham/spam
        JSONArray arr = o.optJSONArray("labels");
        if (arr == null && out != null) arr = out.optJSONArray("labels");
        if (arr == null && in != null) arr = in.optJSONArray("labels");
        if (arr != null) {
            labels = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) labels[i] = arr.getString(i);
        } else {
            labels = new String[]{"ham", "spam"};
        }
    }

    // Looks for the key in each object in order and returns the first one found.
    private static int findInt(String key, JSONObject... places) throws Exception {
        for (JSONObject p : places) {
            if (p != null && p.has(key)) return p.getInt(key);
        }
        throw new Exception("model_spec.json: missing field " + key);
    }

    static String readAsset(Context ctx, String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}