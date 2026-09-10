package com.x2x.wallet;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny background-task helper (network calls must not run on the UI thread). */
public final class Bg {
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Work<T> { T run() throws Exception; }
    public interface Ok<T> { void run(T value); }
    public interface Err { void run(Exception e); }

    private Bg() {}

    public static <T> void run(Activity activity, Work<T> work, Ok<T> ok, Err err) {
        POOL.execute(() -> {
            try {
                T result = work.run();
                MAIN.post(() -> { if (!activity.isFinishing()) ok.run(result); });
            } catch (Exception e) {
                MAIN.post(() -> { if (!activity.isFinishing()) err.run(e); });
            }
        });
    }
}
