package com.geelyplay.preface

import java.util.concurrent.atomic.AtomicBoolean

/** Keeps an application-exit request even when session teardown already started. */
internal class CarPlayShutdownGate {
    private val started = AtomicBoolean(false)
    private val terminateProcess = AtomicBoolean(false)

    fun get(): Boolean = started.get()

    fun begin(terminate: Boolean): Boolean {
        if (terminate) terminateProcess.set(true)
        return started.compareAndSet(false, true)
    }

    fun shouldTerminateProcess(): Boolean = terminateProcess.get()
}
