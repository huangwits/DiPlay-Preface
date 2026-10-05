package com.shihab.diplay.diagnostics;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ProbeAttemptTest {
    @Test public void successClosesSocketAndCannotLaterBecomeTimeout() {
        List<ProbeAttempt.Result> results = new ArrayList<>();
        ProbeAttempt attempt = new ProbeAttempt(results::add);
        FakeConnection socket = new FakeConnection();
        attempt.run(() -> socket);
        attempt.timeout();
        assertEquals(1, results.size());
        assertEquals(ProbeAttempt.Outcome.CONNECTED, results.get(0).outcome);
        assertEquals(1, socket.closed.get());
    }

    @Test public void ioFailureIsPreservedAndSocketClosed() {
        List<ProbeAttempt.Result> results = new ArrayList<>();
        ProbeAttempt attempt = new ProbeAttempt(results::add);
        FakeConnection socket = new FakeConnection();
        socket.failure = new IOException("read failed");
        attempt.run(() -> socket);
        assertEquals(ProbeAttempt.Outcome.FAILED, results.get(0).outcome);
        assertSame(socket.failure, results.get(0).failure);
        assertEquals(1, socket.closed.get());
    }

    @Test public void permissionOrVendorFailureDuringOpenIsReported() {
        List<ProbeAttempt.Result> results = new ArrayList<>();
        ProbeAttempt attempt = new ProbeAttempt(results::add);
        attempt.run(() -> { throw new SecurityException("permission denied"); });
        assertEquals(ProbeAttempt.Outcome.FAILED, results.get(0).outcome);
        assertTrue(results.get(0).failure instanceof SecurityException);
    }

    @Test public void cancellingBeforeWorkerStartsDoesNotOpenSocket() {
        List<ProbeAttempt.Result> results = new ArrayList<>();
        ProbeAttempt attempt = new ProbeAttempt(results::add);
        attempt.cancel();
        attempt.run(() -> { fail("Must not open"); return null; });
        assertEquals(1, results.size());
        assertEquals(ProbeAttempt.Outcome.CANCELLED, results.get(0).outcome);
    }

    @Test public void timeoutWhileOpeningClosesTheLateSocket() throws Exception {
        List<ProbeAttempt.Result> results = new ArrayList<>();
        ProbeAttempt attempt = new ProbeAttempt(results::add);
        FakeConnection socket = new FakeConnection();
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch allowOpen = new CountDownLatch(1);
        Thread worker = new Thread(() -> attempt.run(() -> {
            opening.countDown();
            await(allowOpen);
            return socket;
        }));
        worker.setDaemon(true);
        worker.start();
        try {
            assertTrue(opening.await(2, TimeUnit.SECONDS));
            attempt.timeout();
        } finally { allowOpen.countDown(); }
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertEquals(1, results.size());
        assertEquals(ProbeAttempt.Outcome.TIMED_OUT, results.get(0).outcome);
        assertEquals(1, socket.closed.get());
        assertEquals(0, socket.connects.get());
    }

    @Test public void timeoutClosesBlockedConnectionAndWinsOverSubsequentFailure() throws Exception {
        List<ProbeAttempt.Result> results = new ArrayList<>();
        ProbeAttempt attempt = new ProbeAttempt(results::add);
        CountDownLatch connecting = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        Thread worker = new Thread(() -> attempt.run(() -> new ProbeAttempt.Connection() {
            @Override public void connect() throws IOException {
                connecting.countDown();
                await(closed);
                throw new IOException("closed");
            }
            @Override public void close() { closed.countDown(); }
        }));
        worker.setDaemon(true);
        worker.start();
        assertTrue(connecting.await(2, TimeUnit.SECONDS));
        attempt.timeout();
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertEquals(1, results.size());
        assertEquals(ProbeAttempt.Outcome.TIMED_OUT, results.get(0).outcome);
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IOException("test latch timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        }
    }

    private static class FakeConnection implements ProbeAttempt.Connection {
        final AtomicInteger closed = new AtomicInteger();
        final AtomicInteger connects = new AtomicInteger();
        IOException failure;
        @Override public void connect() throws IOException {
            connects.incrementAndGet();
            if (failure != null) throw failure;
        }
        @Override public void close() { closed.incrementAndGet(); }
    }
}
