package com.dveamer.babysitter.soothing

data class SootheRequest(
    val awakeSinceMs: Long,
    val reason: String,
    val requestedAtMs: Long = System.currentTimeMillis()
)

enum class SootheResult {
    STARTED,
    IGNORED,
    FAILED
}
