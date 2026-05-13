package com.example.timetable.util

import java.util.concurrent.atomic.AtomicBoolean

internal class OneTimeAction {
    private val fired = AtomicBoolean(false)

    fun run(action: () -> Unit) {
        if (fired.compareAndSet(false, true)) {
            action()
        }
    }
}
