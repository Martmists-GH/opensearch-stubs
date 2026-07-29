package com.martmists.opensearch.stubs.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration

class Ratelimiter(
    private val permits: Int,
    private val period: Duration
) {
    private val mutex = Mutex()
    private var availablePermits = permits
    private var windowStart = Clock.System.now()

    suspend operator fun <R> invoke(block: suspend () -> R): R {
        mutex.withLock {
            val now = Clock.System.now()
            if (now - windowStart >= period) {
                windowStart = now
                availablePermits = permits
            }

            if (availablePermits <= 0) {
                val waitTime = period - (now - windowStart)
                if (waitTime.isPositive()) {
                    delay(waitTime)
                    windowStart = Clock.System.now()
                    availablePermits = permits
                }
            }
            availablePermits--
        }
        return block()
    }
}
