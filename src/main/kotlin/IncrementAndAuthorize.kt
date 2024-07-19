package org.example

import java.util.concurrent.atomic.AtomicInteger

class IncrementAndAuthorize(private val threshold: Int): Limiter {
    private val counter = AtomicInteger(0)

    override fun hasAccess(): Boolean =
        incrementAndAuthorize()

    private fun incrementAndAuthorize(): Boolean {
        val current = counter.addAndGet(1)
        return current <= threshold
    }

}
