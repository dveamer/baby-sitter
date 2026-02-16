package com.dveamer.babysitter.soothing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SequentialSoothingCoordinator(
    private val listeners: List<SoothingListener>
) {

    private val mutex = Mutex()

    suspend fun soothe(request: SootheRequest) {
        mutex.withLock {
            listeners.forEach { listener ->
                runCatching {
                    listener.soothe(request)
                }
            }
        }
    }
}
