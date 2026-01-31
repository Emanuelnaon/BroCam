package com.example.brocam

enum class UserProfile {
    STANDARD, SOLO_TRAVELER
}

data class SessionState(
    val role: String? = null, // "LENTE" o "CONTROL"
    val profile: UserProfile = UserProfile.STANDARD,
    val isConnected: Boolean = false,
    val remoteDeviceName: String? = null
)