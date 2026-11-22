package com.example.scamguard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

public class DetailActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "detail_id";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int id;
    private int tries = 0;
    private TextView tvTitle, tvMeta, tvType, tvMessage, tvSignals, tvWhy, tvActions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        tvTitle = findViewById(R.id.tvDTitle);
        tvMeta = findViewById(R.id.tvDMeta);
        tvType = findViewById(R.id.tvDType);
        tvMessage = findViewById(R.id.tvDMessage);
        tvSignals = findViewById(R.id.tvDSignals);
        tvWhy = findViewById(R.id.tvDWhy);
        tvActions = findViewById(R.id.tvDActions);

        id = getIntent().getIntExtra(EXTRA_ID, 0);
        findViewById(R.id.btnDOpen).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        load();
    }

    private String sourceName(String pkg) {
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("telegram")) return "Telegram";
        if (pkg.contains("messaging") || pkg.contains("mms")) return "SMS";
        if (pkg.contains("shell")) return "Test message";
        return pkg;
    }

    private void load() {
        JSONObject o = DetailStore.get(this, id);
        if (o == null) {
            tvTitle.setText("Details not available");
            tvMeta.setText("This detection is no longer saved on the phone.");
            return;
        }
        String signals = o.optString("signals", "");
        List<String> list = signals.isEmpty()
                ? java.util.Collections.<String>emptyList()
                : Arrays.asList(signals.split(","));

        tvTitle.setText("Scam detected");
        tvMeta.setText("Confidence " + o.optInt("pct") + "%  ·  "
                + sourceName(o.optString("source")) + "  ·  " + o.optString("time"));
        tvType.setText(o.optString("type"));
        tvMessage.setText(o.optString("message"));
        tvSignals.setText(list.isEmpty()
                ? "No specific rule matched. The AI model judged the overall wording as scam-like."
                : "• " + android.text.TextUtils.join("\n• ", list));
        String savedActions = o.optString("actions", "");
        tvActions.setText(savedActions.isEmpty() ? "Preparing steps for this message..." : savedActions);

        String why = o.optString("explanation", "");
        if (why.isEmpty()) {
            tvWhy.setText("The on-device AI is preparing an explanation. This can take up to a minute...");
            if (tries++ < 30) handler.postDelayed(this::load, 2000);
        } else {
            tvWhy.setText(why);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}