package com.example.scamguard;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Background {
    public static final ExecutorService DETECT = Executors.newSingleThreadExecutor();
}