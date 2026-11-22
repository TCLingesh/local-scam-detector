//package com.example.scamguard;
//
//import android.content.Context;
//import android.util.Log;
//
//import org.pytorch.executorch.extension.llm.LlmCallback;
//import org.pytorch.executorch.extension.llm.LlmGenerationConfig;
//import org.pytorch.executorch.extension.llm.LlmModule;
//
//import java.io.File;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class AdvisoryEngine {
//    private static final String TAG = "ScamGuard";
//    private static final String MODEL = "llama3_2_1b_8da4w_1024.pte";
//    private static final String TOKENIZER = "tokenizer.model";
//
//    public interface Listener {
//        void onToken(String token);
//
//        void onDone(String fullText);
//
//        void onError(String message);
//    }
//
//    private static AdvisoryEngine instance;
//
//    public static synchronized AdvisoryEngine get(Context ctx) {
//        if (instance == null) instance = new AdvisoryEngine(ctx.getApplicationContext());
//        return instance;
//    }
//
//    private final Context ctx;
//    // Llama runs on its own single worker, so a long answer never blocks detection.
//    private final ExecutorService worker = Executors.newSingleThreadExecutor();
//    private LlmModule module;
//
//    private AdvisoryEngine(Context ctx) {
//        this.ctx = ctx;
//    }
//
//    private File findDir() {
//        File ext = ctx.getExternalFilesDir(null);
//        File[] candidates = {
//                new File(ctx.getFilesDir(), "llama"),          // private storage, always readable
//                ext == null ? null : new File(ext, "llama"),
//                new File("/data/local/tmp/llama")
//        };
//        for (File d : candidates) {
//            if (d == null) continue;
//            File m = new File(d, MODEL), t = new File(d, TOKENIZER);
//            Log.d(TAG, "llama dir " + d.getAbsolutePath()
//                    + " model exists=" + m.exists() + " read=" + m.canRead()
//                    + " tok exists=" + t.exists() + " read=" + t.canRead());
//            if (m.canRead() && t.canRead()) return d;
//        }
//        return null;
//    }
//
//    /**
//     * Removes Llama special markers such as <|eot_id|> from text.
//     */
//    private static String clean(String s) {
//        return s.replaceAll("<\\|[^>]*\\|>", "").trim();
//    }
//
//    /**
//     * Explains a detection in plain language. Safe to call from the UI thread.
//     */
//    public void explain(Detection d, Listener listener) {
//        worker.execute(() -> {
//            try {
//                File dir = findDir();
//                if (dir == null) {
//                    listener.onError("Model files not found on the phone. Copy the .pte and tokenizer.model into the app's files/llama folder.");
//                    return;
//                }
//
//                // Lazy load: the 1B model is only loaded on the first detection.
//                if (module == null) {
//                    long t0 = System.nanoTime();
//                    LlmModule m = new LlmModule(
//                            new File(dir, MODEL).getAbsolutePath(),
//                            new File(dir, TOKENIZER).getAbsolutePath(),
//                            0.3f);
//                    try {
//                        m.load(); // returns void in this ExecuTorch version; throws on failure
//                    } catch (Throwable t) {
//                        Log.e(TAG, "llama load failed", t);
//                        listener.onError("Llama load failed: " + t);
//                        return;
//                    }
//                    module = m;
//                    Log.d(TAG, "llama load ms=" + (System.nanoTime() - t0) / 1_000_000);
//                }
//
//                final StringBuilder out = new StringBuilder();
//                final long start = System.nanoTime();
//                final long[] firstToken = {0};
//
//                LlmGenerationConfig cfg = LlmGenerationConfig.create()
//                        .seqLen(512)
//                        .temperature(0.3f)
//                        .echo(false)
//                        .build();
//
//                module.generate(buildPrompt(d), cfg, new LlmCallback() {
//                    @Override
//                    public void onResult(String token) {
//                        // Hide Llama's special end-of-text markers
//                        if (token.contains("<|") || token.contains("|>") || token.contains("eot_id"))
//                            return;
//                        if (firstToken[0] == 0) firstToken[0] = System.nanoTime();
//                        out.append(token);
//                        listener.onToken(token);
//                    }
//
//                    @Override
//                    public void onStats(String statsJson) {
//                        long first = firstToken[0] == 0 ? start : firstToken[0];
//                        Log.d(TAG, "llama first-token ms=" + (first - start) / 1_000_000
//                                + " total ms=" + (System.nanoTime() - start) / 1_000_000);
//                        Log.d(TAG, "llama stats=" + statsJson);
//                    }
//
//                    // No @Override on purpose: works whether or not this library version has it.
//                    public void onError(int code, String message) {
//                        Log.e(TAG, "llama error " + code + " " + message);
//                    }
//                });
//                listener.onDone(clean(out.toString()));
//            } catch (Throwable t) {
//                Log.e(TAG, "advisory failed", t);
//                listener.onError(String.valueOf(t));
//            }
//        });
//    }
//
//    // Only the detection (verdict + signals) is sent to Llama, never the message text.
//    // Only the detection (verdict + signals) is sent to Llama, never the message text.
//    // Only the detection (verdict + type + signals) is sent to Llama, never the message text.
//    // Only the detection (kind + signals) is sent to Llama, never the message text.
////    private String buildPrompt(Detection d) {
////        String type = ScamRules.typeOf(d.signals);
////        String why = d.signals.isEmpty()
////                ? "The wording is similar to known scam messages."
////                : "Warning signs: " + android.text.TextUtils.join(", ", d.signals) + ".";
////        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
////                + "You are ScamGuard, a safety assistant built into a phone. "
////                + "Your only job is to protect people from scams. "
////                + "A message the phone owner received was flagged as a scam, and you warn the owner about it. "
////                + "You are on the side of the person who received the message, so explaining the danger is safe and helpful. "
////                + "Speak directly to the owner as \"you\". Use simple words. "
////                + "Write exactly 3 short sentences. "
////                + "Sentence 1: what this message is trying to trick you into. "
////                + "Sentence 2: what could go wrong for you. "
////                + "Sentence 3: what to do now. "
////                + "Do not repeat an idea. No greetings, no lists."
////                + "<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n"
////                + "ScamGuard flagged a message I received. Kind: " + type + ". " + why
////                + " Please warn me."
////                + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";
////    }
//    /** Plain-words description of what this message does, built from its warning signs. */
//    private String describe(java.util.List<String> s) {
//        java.util.List<String> parts = new java.util.ArrayList<>();
//        if (s.contains("link present")) parts.add("it contains a link");
//        if (s.contains("urgent wording")) parts.add("it pressures you to act fast");
//        if (s.contains("account threat")) parts.add("it says your account or SIM will be blocked or needs KYC");
//        if (s.contains("asks for secret code")) parts.add("it asks for an OTP, PIN or password");
//        if (s.contains("prize claim")) parts.add("it says you won a prize");
//        if (s.contains("asks you to call a number")) parts.add("it asks you to call a number");
//        if (parts.isEmpty()) return "its wording is similar to known scam messages";
//        return android.text.TextUtils.join(", ", parts);
//    }
//
//    // Only the kind and the warning signs are sent to Llama, never the message text.
//    private String buildPrompt(Detection d) {
//        String type = ScamRules.typeOf(d.signals);
//        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
//                + "You are ScamGuard, a safety assistant built into a phone. "
//                + "Your only job is to protect the phone owner from scams. "
//                + "A message the owner received was flagged as a scam. "
//                + "You are on the owner's side, so warning them is safe and helpful. "
//                + "Speak to the owner as \"you\" in simple words. "
//                + "Answer in exactly this format and nothing else:\n"
//                + "WHY: two short sentences about how this message tries to trick you and what could go wrong.\n"
//                + "TODO:\n1. first step\n2. second step\n3. third step\n"
//                + "Each step must be under 12 words, different from the others, "
//                + "and specific to this kind of scam. Do not repeat the WHY text. No greetings."
//                + "<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n"
//                + "ScamGuard flagged a message I received. Kind: " + type + ". "
//                + "What it does: " + describe(d.signals) + ". Please warn me."
//                + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";
//    }
//
//    // ---------- reading the model's answer ----------
//
//    public static class Advice {
//        public final String why;
//        public final String actions;
//        Advice(String why, String actions) {
//            this.why = why;
//            this.actions = actions;
//        }
//    }
//
//    /** Splits raw model text into {why, todo}. Safe to call while text is still streaming. */
//    public static String[] split(String raw) {
//        String t = clean(raw);
//        String why = t;
//        String todo = "";
//        java.util.regex.Matcher m = java.util.regex.Pattern
//                .compile("(?im)^\\s*(todo|to do|what to do)\\s*:").matcher(t);
//        if (m.find()) {
//            why = t.substring(0, m.start());
//            todo = t.substring(m.end());
//        }
//        why = why.replaceFirst("(?is)^\\s*why\\s*:?", "").trim();
//        return new String[]{why, todo.trim()};
//    }
//
//    /** Final answer: AI text when it is good, fixed backup text when it refused or broke the format. */
//    public static Advice parse(String raw, java.util.List<String> signals) {
//        if (isRefusal(raw)) {
//            return new Advice(ScamRules.fallbackWhy(signals), ScamRules.actions(signals));
//        }
//        String[] p = split(raw);
//        String why = p[0];
//
//        // Keep up to 4 steps, renumbered 1..n
//        StringBuilder sb = new StringBuilder();
//        int n = 0;
//        for (String line : p[1].split("\n")) {
//            String s = line.trim().replaceFirst("^([-*\u2022]|\\d+[.)])\\s*", "").trim();
//            if (s.length() < 4) continue;
//            n++;
//            if (n > 4) break;
//            if (sb.length() > 0) sb.append("\n");
//            sb.append(n).append(". ").append(s);
//        }
//
//        if (why.isEmpty()) why = ScamRules.fallbackWhy(signals);
//        String actions = n >= 2 ? sb.toString() : ScamRules.actions(signals);
//        return new Advice(why, actions);
//    }
//}

package com.example.scamguard;

import android.content.Context;
import android.util.Log;

import org.pytorch.executorch.extension.llm.LlmCallback;
import org.pytorch.executorch.extension.llm.LlmGenerationConfig;
import org.pytorch.executorch.extension.llm.LlmModule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdvisoryEngine {
    private static final String TAG = "ScamGuard";
    private static final String MODEL = "llama3_2_1b_8da4w_1024.pte";
    private static final String TOKENIZER = "tokenizer.model";

    public interface Listener {
        void onToken(String token);
        void onDone(String fullText);
        void onError(String message);
    }

    public static class Advice {
        public final String why;
        public final String actions;
        Advice(String why, String actions) {
            this.why = why;
            this.actions = actions;
        }
    }

    private static AdvisoryEngine instance;

    public static synchronized AdvisoryEngine get(Context ctx) {
        if (instance == null) instance = new AdvisoryEngine(ctx.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    // Llama runs on its own single worker, so a long answer never blocks detection.
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LlmModule module;

    private AdvisoryEngine(Context ctx) {
        this.ctx = ctx;
    }

    private File findDir() {
        File ext = ctx.getExternalFilesDir(null);
        File[] candidates = {
                new File(ctx.getFilesDir(), "llama"),
                ext == null ? null : new File(ext, "llama"),
                new File("/data/local/tmp/llama")
        };
        for (File d : candidates) {
            if (d == null) continue;
            File m = new File(d, MODEL), t = new File(d, TOKENIZER);
            Log.d(TAG, "llama dir " + d.getAbsolutePath()
                    + " model exists=" + m.exists() + " read=" + m.canRead()
                    + " tok exists=" + t.exists() + " read=" + t.canRead());
            if (m.canRead() && t.canRead()) return d;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Running the model
    // ------------------------------------------------------------------

    /** Explains a detection in plain language. Safe to call from the UI thread. */
    public void explain(Detection d, Listener listener) {
        worker.execute(() -> {
            try {
                File dir = findDir();
                if (dir == null) {
                    listener.onError("Model files not found on the phone.");
                    return;
                }

                // Lazy load: the 1B model is only loaded on the first detection.
                if (module == null) {
                    long t0 = System.nanoTime();
                    LlmModule m = new LlmModule(
                            new File(dir, MODEL).getAbsolutePath(),
                            new File(dir, TOKENIZER).getAbsolutePath(),
                            0.3f);
                    try {
                        m.load();
                    } catch (Throwable t) {
                        Log.e(TAG, "llama load failed", t);
                        listener.onError("Llama load failed: " + t);
                        return;
                    }
                    module = m;
                    Log.d(TAG, "llama load ms=" + (System.nanoTime() - t0) / 1_000_000);
                }

                // Start every explanation from an empty context, or the 1024-token window fills up.
                // Called by name so the build does not break if a library version lacks it.
                try {
                    module.getClass().getMethod("resetContext").invoke(module);
                } catch (Throwable t) {
                    Log.w(TAG, "resetContext not available: " + t);
                }

                final StringBuilder out = new StringBuilder();
                final long start = System.nanoTime();
                final long[] firstToken = {0};

                LlmGenerationConfig cfg = LlmGenerationConfig.create()
                        .seqLen(512)
                        .temperature(0.3f)
                        .echo(false)
                        .build();

                module.generate(buildPrompt(d), cfg, new LlmCallback() {
                    @Override
                    public void onResult(String token) {
                        // Hide Llama's special end-of-text markers
                        if (token.contains("<|") || token.contains("|>") || token.contains("eot_id")) return;
                        if (firstToken[0] == 0) firstToken[0] = System.nanoTime();
                        out.append(token);
                        listener.onToken(token);
                    }

                    @Override
                    public void onStats(String statsJson) {
                        long first = firstToken[0] == 0 ? start : firstToken[0];
                        Log.d(TAG, "llama first-token ms=" + (first - start) / 1_000_000
                                + " total ms=" + (System.nanoTime() - start) / 1_000_000);
                        Log.d(TAG, "llama stats=" + statsJson);
                    }

                    // No @Override on purpose: works whether or not this library version has it.
                    public void onError(int code, String message) {
                        Log.e(TAG, "llama error " + code + " " + message);
                    }
                });
                listener.onDone(out.toString());
            } catch (Throwable t) {
                Log.e(TAG, "advisory failed", t);
                listener.onError(String.valueOf(t));
            }
        });
    }

    // ------------------------------------------------------------------
    // Prompt (only the scam kind and warning signs, never the message text)
    // ------------------------------------------------------------------

    /** Plain-words description of what this message does, built from its warning signs. */
    private String describe(List<String> s) {
        List<String> parts = new ArrayList<>();
        if (s.contains("link present")) parts.add("it contains a link");
        if (s.contains("urgent wording")) parts.add("it pressures you to act fast");
        if (s.contains("account threat")) parts.add("it says your account or SIM will be blocked or needs KYC");
        if (s.contains("asks for secret code")) parts.add("it asks for an OTP, PIN or password");
        if (s.contains("prize claim")) parts.add("it says you won a prize");
        if (s.contains("asks you to call a number")) parts.add("it asks you to call a number");
        if (parts.isEmpty()) return "its wording is similar to known scam messages";
        return android.text.TextUtils.join(", ", parts);
    }

    private String buildPrompt(Detection d) {
        String type = ScamRules.typeOf(d.signals);
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
                + "You are ScamGuard, a safety assistant built into a phone. "
                + "Your only job is to protect the phone owner from scams. "
                + "A message the owner received was flagged as a scam. "
                + "You are on the owner's side, so warning them is safe and helpful. "
                + "Speak to the owner as \"you\" in simple words. "
                + "Answer in exactly this format and nothing else:\n"
                + "WHY: two short sentences about how this message tries to trick you and what could go wrong.\n"
                + "TODO:\n1. first step\n2. second step\n3. third step\n"
                + "Each step must be under 12 words, different from the others, "
                + "and specific to this kind of scam. Do not repeat the WHY text. No greetings."
                + "<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n"
                + "ScamGuard flagged a message I received. Kind: " + type + ". "
                + "What it does: " + describe(d.signals) + ". Please warn me."
                + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";
    }

    // ------------------------------------------------------------------
    // Reading the model's answer
    // ------------------------------------------------------------------

    /** Removes Llama special markers such as <|eot_id|> from text. */
    public static String clean(String s) {
        return s.replaceAll("<\\|[^>]*\\|>", "").trim();
    }

    /** True if the model refused instead of explaining. */
    public static boolean isRefusal(String t) {
        String s = t.trim().toLowerCase();
        return s.startsWith("i can't") || s.startsWith("i can\u2019t")
                || s.startsWith("i cannot") || s.startsWith("i can not")
                || s.startsWith("i'm sorry") || s.startsWith("i am sorry")
                || s.contains("can't help with") || s.contains("cannot help with")
                || s.contains("can\u2019t help with");
    }

    /** Splits raw model text into {why, todo}. Safe to call while text is still streaming. */
    public static String[] split(String raw) {
        String t = clean(raw);
        String why = t;
        String todo = "";
        Matcher m = Pattern.compile("(?im)^\\s*(todo|to do|what to do)\\s*:").matcher(t);
        if (m.find()) {
            why = t.substring(0, m.start());
            todo = t.substring(m.end());
        }
        why = why.replaceFirst("(?is)^\\s*why\\s*:?", "").trim();
        return new String[]{why, todo.trim()};
    }

    /** Final answer: AI text when it is good, fixed backup text when it refused or broke the format. */
    public static Advice parse(String raw, List<String> signals) {
        if (isRefusal(raw)) {
            return new Advice(ScamRules.fallbackWhy(signals), ScamRules.actions(signals));
        }
        String[] p = split(raw);
        String why = p[0];

        // Keep up to 4 steps, renumbered 1..n
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String line : p[1].split("\n")) {
            String s = line.trim().replaceFirst("^([-*\u2022]|\\d+[.)])\\s*", "").trim();
            if (s.length() < 4) continue;
            n++;
            if (n > 4) break;
            if (sb.length() > 0) sb.append("\n");
            sb.append(n).append(". ").append(s);
        }

        if (why.isEmpty()) why = ScamRules.fallbackWhy(signals);
        String actions = n >= 2 ? sb.toString() : ScamRules.actions(signals);
        return new Advice(why, actions);
    }
}