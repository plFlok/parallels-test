package org.example

import java.util.concurrent.atomic.AtomicInteger

class TryIncrement(private val threshold: Int): Limiter {
    private val counter = AtomicInteger(0)

    fun get() = counter.get()

    override fun hasAccess(): Boolean =
        tryIncrement()

    private fun tryIncrement(): Boolean {
        val current = counter.get()
        return if (current < threshold) {
            counter.compareAndSet(current, current + 1)
        } else {
            false
        }
    }
}
