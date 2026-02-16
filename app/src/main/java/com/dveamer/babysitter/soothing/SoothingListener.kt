package com.dveamer.babysitter.soothing

interface SoothingListener {
    val id: String

    suspend fun soothe(request: SootheRequest): SootheResult
}
