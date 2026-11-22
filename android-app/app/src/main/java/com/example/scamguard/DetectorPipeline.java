package com.example.scamguard;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class DetectorPipeline {
    private static final String TAG = "ScamGuard";

    private static ModelSpec spec;
    private static Preprocessor pre;
    private static float threshold = 0.5f;

    private static synchronized void init(Context ctx) throws Exception {
        if (spec != null) return;
        ModelSpec s = new ModelSpec(ctx);
        Preprocessor p = new Preprocessor(ctx, s);
        threshold = readThreshold(ctx);
        spec = s;
        pre = p;
        Log.d(TAG, "pipeline ready, threshold=" + threshold);
    }

    // Reads "threshold" straight from model_spec.json so the value is never hardcoded.
    private static float readThreshold(Context ctx) throws Exception {
        try (InputStream in = ctx.getAssets().open("model_spec.json")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONObject o = new JSONObject(bos.toString("UTF-8"));
            return (float) o.getDouble("threshold");
        }
    }

    public static Detection analyze(Context ctx, String text) {
        List<String> signals = ScamRules.signals(text);
        try {
            init(ctx.getApplicationContext());
            float[] out = ModelRunner.get(ctx).run(pre.encode(text));
            if (out.length < 2) throw new IllegalStateException("bad output length " + out.length);

            float ham = out[0], spam = out[1];
            // If outputs are not already probabilities, apply softmax.
            boolean looksLikeProb = ham >= 0 && spam >= 0 && Math.abs(ham + spam - 1f) < 0.01f;
            if (!looksLikeProb) {
                double m = Math.max(ham, spam);
                double eh = Math.exp(ham - m), es = Math.exp(spam - m);
                ham = (float) (eh / (eh + es));
                spam = (float) (es / (eh + es));
            }

            // Hybrid decision: ML model OR two or more rule-based warning signs
            boolean scam = ScamRules.isScam(spam, threshold, signals);
            float conf = scam ? ScamRules.confidence(spam, signals) : ham;
            Log.d(TAG, "model ham=" + ham + " spam=" + spam
                    + " ruleSignals=" + signals.size() + " scam=" + scam);
            return new Detection(scam, conf, signals);
        } catch (Exception e) {
            throw new RuntimeException("model inference failed", e);
        }
    }
}