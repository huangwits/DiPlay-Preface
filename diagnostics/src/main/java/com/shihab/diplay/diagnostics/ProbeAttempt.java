package com.shihab.diplay.diagnostics;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One attempt owns its socket, including a socket returned after cancellation. */
final class ProbeAttempt {
    interface Connection extends Closeable {
        void connect() throws IOException;
    }
    interface Factory {
        Connection open() throws IOException;
    }
    interface Listener {
        void onResult(Result result);
    }
    enum Outcome { CONNECTED, FAILED, TIMED_OUT, CANCELLED }
    static final class Result {
        final Outcome outcome;
        final Exception failure;
        Result(Outcome outcome, Exception failure) {
            this.outcome = outcome;
            this.failure = failure;
        }
    }

    private final AtomicBoolean finished = new AtomicBoolean();
    private final Listener listener;
    private Connection connection;

    ProbeAttempt(Listener listener) { this.listener = listener; }

    void run(Factory factory) {
        if (finished.get()) return;
        try {
            Connection opened = factory.open();
            synchronized (this) {
                if (finished.get()) {
                    closeQuietly(opened);
                    return;
                }
                connection = opened;
            }
            if (finished.get()) return;
            opened.connect();
            finish(Outcome.CONNECTED, null);
        } catch (Exception failure) {
            finish(Outcome.FAILED, failure);
        } finally {
            closeConnection();
        }
    }

    void timeout() { finish(Outcome.TIMED_OUT, null); }
    void cancel() { finish(Outcome.CANCELLED, null); }

    private void finish(Outcome outcome, Exception failure) {
        if (!finished.compareAndSet(false, true)) return;
        closeConnection();
        listener.onResult(new Result(outcome, failure));
    }

    private synchronized void closeConnection() {
        Connection owned = connection;
        connection = null;
        if (owned != null) closeQuietly(owned);
    }

    private static void closeQuietly(Connection connection) {
        try { connection.close(); } catch (Exception ignored) { }
    }
}
