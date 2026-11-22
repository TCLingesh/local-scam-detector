package com.example.scamguard;

import android.content.Context;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class Preprocessor {
    private final ModelSpec spec;
    private final Map<Integer, Integer> vocab = new HashMap<>();

    public Preprocessor(Context ctx, ModelSpec spec) throws Exception {
        this.spec = spec;
        JSONObject o = new JSONObject(ModelSpec.readAsset(ctx, "vocab.json"));
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (k.isEmpty()) continue;
            vocab.put(k.codePointAt(0), o.getInt(k));
        }
    }

    // Returns token ids, length = spec.maxLen
    public long[] encode(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        long[] ids = new long[spec.maxLen];
        java.util.Arrays.fill(ids, spec.padIndex);
        int n = 0;
        int i = 0;
        while (i < lower.length() && n < spec.maxLen) {
            int cp = lower.codePointAt(i);          // code point, not UTF-16 unit
            Integer id = vocab.get(cp);
            ids[n++] = (id != null) ? id : spec.unkIndex;
            i += Character.charCount(cp);
        }
        return ids;
    }
}