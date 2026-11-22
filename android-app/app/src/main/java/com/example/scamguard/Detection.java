package com.example.scamguard;

import java.util.List;

public class Detection {
    public final boolean isScam;
    public final float confidence;
    public final List<String> signals;

    public Detection(boolean isScam, float confidence, List<String> signals) {
        this.isScam = isScam;
        this.confidence = confidence;
        this.signals = signals;
    }
}