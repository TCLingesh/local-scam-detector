package com.example.scamguard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private EditText etMessage;
    private TextView tvVerdict, tvConfidence, tvExplanation, tvActions, tvStatCount, tvEmpty;
    private View resultCard;
    private ProgressBar pbConfidence;
    private LinearLayout alertsList;

    private View pageHome, pageScan, pageAlerts;
    private TextView tabHome, tabScan, tabAlerts;
    private TextView tvFilterAll, tvFilterScam, tvFilterSafe;
    private int filter = 0; // 0 all, 1 scam, 2 safe

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AlertNotifier.ensureChannel(this);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etMessage = findViewById(R.id.etMessage);
        tvVerdict = findViewById(R.id.tvVerdict);
        tvConfidence = findViewById(R.id.tvConfidence);
        tvExplanation = findViewById(R.id.tvExplanation);
        tvActions = findViewById(R.id.tvActions);
        tvStatCount = findViewById(R.id.tvStatCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        resultCard = findViewById(R.id.resultCard);
        pbConfidence = findViewById(R.id.pbConfidence);
        alertsList = findViewById(R.id.alertsList);

        pageHome = findViewById(R.id.pageHome);
        pageScan = findViewById(R.id.pageScan);
        pageAlerts = findViewById(R.id.pageAlerts);
        tabHome = findViewById(R.id.tabHome);
        tabScan = findViewById(R.id.tabScan);
        tabAlerts = findViewById(R.id.tabAlerts);
        tvFilterAll = findViewById(R.id.tvFilterAll);
        tvFilterScam = findViewById(R.id.tvFilterScam);
        tvFilterSafe = findViewById(R.id.tvFilterSafe);

        tabHome.setOnClickListener(v -> showTab(0));
        tabScan.setOnClickListener(v -> showTab(1));
        tabAlerts.setOnClickListener(v -> showTab(2));

        tvFilterAll.setOnClickListener(v -> setFilter(0));
        tvFilterScam.setOnClickListener(v -> setFilter(1));
        tvFilterSafe.setOnClickListener(v -> setFilter(2));

        Button btnEnable = findViewById(R.id.btnEnableAccess);
        Button btnGoScan = findViewById(R.id.btnGoScan);
        Button btnCheck = findViewById(R.id.btnCheck);
        Button btnPaste = findViewById(R.id.btnPaste);
        Button btnClear = findViewById(R.id.btnClear);

        btnEnable.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        btnGoScan.setOnClickListener(view -> showTab(1));
        btnCheck.setOnClickListener(view -> runCheck());
        btnPaste.setOnClickListener(view -> pasteFromClipboard());

        btnClear.setOnClickListener(view -> {
            HistoryStore.clear(this);
            DetailStore.clear(this);
            refreshHistory();
        });

        showTab(0);
        setFilter(0);
    }

    private void showTab(int index) {
        pageHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageScan.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageAlerts.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        int on = ContextCompat.getColor(this, R.color.sg_primary);
        int off = ContextCompat.getColor(this, R.color.sg_text2);
        tabHome.setTextColor(index == 0 ? on : off);
        tabScan.setTextColor(index == 1 ? on : off);
        tabAlerts.setTextColor(index == 2 ? on : off);

        tabHome.setCompoundDrawableTintList(
                android.content.res.ColorStateList.valueOf(index == 0 ? on : off));
        tabScan.setCompoundDrawableTintList(
                android.content.res.ColorStateList.valueOf(index == 1 ? on : off));
        tabAlerts.setCompoundDrawableTintList(
                android.content.res.ColorStateList.valueOf(index == 2 ? on : off));

        tabHome.setBackgroundResource(index == 0 ? R.drawable.bg_tab_active : 0);
        tabScan.setBackgroundResource(index == 1 ? R.drawable.bg_tab_active : 0);
        tabAlerts.setBackgroundResource(index == 2 ? R.drawable.bg_tab_active : 0);
    }

    private void setFilter(int f) {
        filter = f;
        int on = ContextCompat.getColor(this, R.color.sg_primary);
        int off = ContextCompat.getColor(this, R.color.sg_text2);
        tvFilterAll.setBackgroundResource(f == 0 ? R.drawable.bg_chip : R.drawable.bg_chip_off);
        tvFilterScam.setBackgroundResource(f == 1 ? R.drawable.bg_chip : R.drawable.bg_chip_off);
        tvFilterSafe.setBackgroundResource(f == 2 ? R.drawable.bg_chip : R.drawable.bg_chip_off);
        tvFilterAll.setTextColor(f == 0 ? on : off);
        tvFilterScam.setTextColor(f == 1 ? on : off);
        tvFilterSafe.setTextColor(f == 2 ? on : off);
        refreshHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = NotificationManagerCompat
                .getEnabledListenerPackages(this).contains(getPackageName());
        if (on) {
            // Reconnect the listener after a reinstall
            NotificationListenerService.requestRebind(
                    new ComponentName(this, ScamNotificationListener.class));
        }
        TextView status = findViewById(R.id.tvNotifStatus);
        status.setText(on ? "Protection active" : "Protection off");
        Button b = findViewById(R.id.btnEnableAccess);
        b.setText(on ? "Manage notification access" : "Turn on notification access");
        refreshHistory();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String sourceName(String pkg) {
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("telegram")) return "Telegram";
        if (pkg.contains("messaging") || pkg.contains("mms")) return "SMS";
        if (pkg.contains("shell")) return "Test message";
        if (pkg.equals("pasted")) return "Pasted message";
        return pkg;
    }

    // History lines look like: "HH:mm | SCAM 99% | source | detailId"
    private void refreshHistory() {
        String h = HistoryStore.get(this);
        String[] lines = h.isEmpty() ? new String[0] : h.split("\n");

        alertsList.removeAllViews();
        int shown = 0;
        int scamCount = 0;
        for (String line : lines) {
            String[] p = line.split(" \\| ");
            if (p.length < 3) continue;
            boolean scam = p[1].startsWith("SCAM");
            if (scam) scamCount++;
            if (filter == 1 && !scam) continue;
            if (filter == 2 && scam) continue;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            // Scam cards open the detail screen (type, message, why, what to do)
            if (scam && p.length >= 4) {
                try {
                    final int detailId = Integer.parseInt(p[3].trim());
                    card.setOnClickListener(v -> {
                        Intent i = new Intent(this, DetailActivity.class);
                        i.putExtra(DetailActivity.EXTRA_ID, detailId);
                        startActivity(i);
                    });
                } catch (NumberFormatException ignored) { }
            }

            TextView title = new TextView(this);
            String pct = p[1].replace("SCAM ", "").replace("SAFE ", "");
            title.setText((scam ? "Scam detected  " : "Looks safe  ") + pct);
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(ContextCompat.getColor(
                    this, scam ? R.color.sg_danger : R.color.sg_safe));

            TextView sub = new TextView(this);
            sub.setText(sourceName(p[2]) + "  ·  " + p[0]
                    + (scam && p.length >= 4 ? "  ·  tap for details" : ""));
            sub.setTextSize(13);
            sub.setTextColor(ContextCompat.getColor(this, R.color.sg_text2));

            card.addView(title);
            card.addView(sub);
            alertsList.addView(card);
            shown++;
        }
        tvEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        tvStatCount.setText(String.valueOf(scamCount));
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
            CharSequence t = item.coerceToText(this);
            if (t != null) etMessage.setText(t);
        }
    }

    // Runs the model on a background thread, then updates the screen.
    private void runCheck() {
        final String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) {
            etMessage.setError("Paste a message first");
            return;
        }
        tvVerdict.setText("Scanning...");
        final Context app = getApplicationContext();
        Background.DETECT.execute(() -> {
            long t0 = System.nanoTime();
            try {
                Detection d = DetectorPipeline.analyze(app, text);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                android.util.Log.d("ScamGuard", "detect latency ms=" + ms);
                runOnUiThread(() -> showResult(d, text));
            } catch (Exception e) {
                android.util.Log.e("ScamGuard", "analyze failed", e);
                runOnUiThread(() -> {
                    tvVerdict.setText("MODEL ERROR");
                    tvConfidence.setText("");
                    tvExplanation.setText(String.valueOf(e.getCause() != null ? e.getCause() : e));
                    tvActions.setText("");
                });
            }
        });
    }

//    private void showResult(Detection d, String message) {
//        int pct = Math.round(d.confidence * 100);
//        int color = ContextCompat.getColor(
//                this, d.isScam ? R.color.sg_danger : R.color.sg_safe);
//        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
//                .format(new java.util.Date());
//
//        resultCard.setBackgroundResource(
//                d.isScam ? R.drawable.bg_result_scam : R.drawable.bg_result_safe);
//        tvVerdict.setText(d.isScam ? "SCAM DETECTED" : "LOOKS SAFE");
//        tvVerdict.setTextColor(color);
//        pbConfidence.setProgress(pct);
//        pbConfidence.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
//
//        if (!d.isScam) {
//            tvConfidence.setText("Model confidence " + pct + "%");
//            tvExplanation.setText("No strong scam signals found.");
//            tvActions.setText("No action needed. Stay careful with unexpected links.");
//            HistoryStore.add(this, time + " | SAFE " + pct + "% | pasted");
//            refreshHistory();
//            return;
//        }
//
//        final int id = (int) (System.currentTimeMillis() & 0x7fffffff);
//        final String type = ScamRules.typeOf(d.signals);
//        tvConfidence.setText(type + "  ·  confidence " + pct + "%");
//        tvActions.setText(ScamRules.actions(d.signals));
//
//        // Instant fallback text, then the on-device Llama explanation replaces it
//        final String fallback = d.signals.isEmpty()
//                ? "The AI model found that the wording is similar to known scam messages."
//                : "Warning signs: " + android.text.TextUtils.join(", ", d.signals) + ".";
//        tvExplanation.setText("Asking the on-device AI...\n\n" + fallback);
//
//        // Details for the detail screen (scam messages only, stays on this phone)
//        DetailStore.save(this, id, type, pct,
//                android.text.TextUtils.join(",", d.signals), "pasted", time, message);
//        HistoryStore.add(this, time + " | SCAM " + pct + "% | pasted | " + id);
//        refreshHistory();
//
//        final StringBuilder sb = new StringBuilder();
//        final Context app = getApplicationContext();
//        AdvisoryEngine.get(app).explain(d, new AdvisoryEngine.Listener() {
//            @Override
//            public void onToken(String token) {
//                sb.append(token);
//                runOnUiThread(() -> tvExplanation.setText(sb.toString()));
//            }
//
//            @Override
//            public void onDone(String fullText) {
//                DetailStore.setExplanation(app, id, fullText.trim());
//            }
//
//            @Override
//            public void onError(String message) {
//                runOnUiThread(() -> tvExplanation.setText(
//                        fallback + "\n\n(AI explanation unavailable: " + message + ")"));
//            }
//        });
//    }
private void showResult(Detection d, String message) {
    int pct = Math.round(d.confidence * 100);
    int color = ContextCompat.getColor(
            this, d.isScam ? R.color.sg_danger : R.color.sg_safe);
    String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(new java.util.Date());

    resultCard.setBackgroundResource(
            d.isScam ? R.drawable.bg_result_scam : R.drawable.bg_result_safe);
    tvVerdict.setText(d.isScam ? "SCAM DETECTED" : "LOOKS SAFE");
    tvVerdict.setTextColor(color);
    pbConfidence.setProgress(pct);
    pbConfidence.setProgressTintList(android.content.res.ColorStateList.valueOf(color));

    if (!d.isScam) {
        tvConfidence.setText("Model confidence " + pct + "%");
        tvExplanation.setText("No strong scam signals found.");
        tvActions.setText("No action needed. Stay careful with unexpected links.");
        HistoryStore.add(this, time + " | SAFE " + pct + "% | pasted");
        refreshHistory();
        return;
    }

    final int id = (int) (System.currentTimeMillis() & 0x7fffffff);
    final String type = ScamRules.typeOf(d.signals);
    tvConfidence.setText(type + "  ·  confidence " + pct + "%");
    tvExplanation.setText("Asking the on-device AI...");
    tvActions.setText("Preparing steps for this message...");

    // Details for the detail screen (scam messages only, stays on this phone)
    DetailStore.save(this, id, type, pct,
            android.text.TextUtils.join(",", d.signals), "pasted", time, message);
    HistoryStore.add(this, time + " | SCAM " + pct + "% | pasted | " + id);
    refreshHistory();

    final StringBuilder sb = new StringBuilder();
    final Context app = getApplicationContext();
    AdvisoryEngine.get(app).explain(d, new AdvisoryEngine.Listener() {
        @Override
        public void onToken(String token) {
            sb.append(token);
            final String[] p = AdvisoryEngine.split(sb.toString());
            runOnUiThread(() -> {
                tvExplanation.setText(p[0].isEmpty() ? "Thinking..." : p[0]);
                tvActions.setText(p[1].isEmpty() ? "Preparing steps..." : p[1]);
            });
        }

        @Override
        public void onDone(String fullText) {
            final AdvisoryEngine.Advice a = AdvisoryEngine.parse(fullText, d.signals);
            DetailStore.setActions(app, id, a.actions);
            DetailStore.setExplanation(app, id, a.why);
            runOnUiThread(() -> {
                tvExplanation.setText(a.why);
                tvActions.setText(a.actions);
            });
        }

        @Override
        public void onError(String err) {
            final String why = ScamRules.fallbackWhy(d.signals);
            final String acts = ScamRules.actions(d.signals);
            DetailStore.setActions(app, id, acts);
            DetailStore.setExplanation(app, id, why);
            runOnUiThread(() -> {
                tvExplanation.setText(why);
                tvActions.setText(acts);
            });
        }
    });
}


}