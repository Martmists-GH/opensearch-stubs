package com.martmists.opensearch.stubs.util

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SuspendingLazy<T : Any>(
    private val generator: suspend () -> T
) {
    private val mutex = Mutex()
    private var isInitialized = false
    private lateinit var value: T

    suspend fun get(): T {
        if (!isInitialized) {  // quick non-mutex check for performance
            mutex.withLock {
                if (!isInitialized) {  // check again in case of multiple attempts to get
                    value = generator()
                    isInitialized = true
                }
            }
        }

        return value
    }
}

fun <T : Any> suspendLazy(block: suspend () -> T) = SuspendingLazy(block)
fun <T : Any> suspendLazyFlow(block: suspend FlowCollector<T>.() -> Unit) = SuspendingLazy<List<T>> {
    mutableListOf<T>().also {
        flow(block).collect(it::add)
    }
}
