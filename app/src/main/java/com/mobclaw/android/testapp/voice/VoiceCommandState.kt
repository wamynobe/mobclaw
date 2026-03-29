package com.mobclaw.android.testapp.voice

enum class VoiceMode {
    OFF,
    STANDBY,
    ACTIVATED,
    LISTENING,
    PROCESSING,
    ERROR,
}

data class VoiceCommandEvent(
    val mode: VoiceMode = VoiceMode.OFF,
    val transcription: String? = null,
    val error: String? = null,
)
