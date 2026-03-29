package com.mobclaw.android.testapp.voice

import android.util.Log

/**
 * Detects the "hey claw" wake phrase in speech recognizer output.
 *
 * Handles common misrecognitions from Google STT. The detector
 * checks both exact variant matching and a loose phonetic fallback.
 */
object WakeWordDetector {

    private const val TAG = "WakeWordDetector"

    private val WAKE_VARIANTS = listOf(
        "hey claw",
        "hey clock",
        "hey clah",
        "hey clo",
        "hey cla ",
        "hey claws",
        "hey klah",
        "hey klaw",
        "hey claude",
        "he claw",
        "hey clog",
        "hey craw",
        "heyc law",
        "a claw",
        "ey claw",
        "hey clow",
        "hey chloe",
        "hey claw",
        "hey clore",
        "hey clor",
        "hey cla",
    )

    data class DetectionResult(
        val detected: Boolean,
        val trailingCommand: String? = null,
    )

    /**
     * Check a list of recognition candidates for the wake phrase.
     * Returns a [DetectionResult] indicating whether the wake word was found
     * and any trailing text that may be the start of a command.
     */
    fun check(candidates: List<String>): DetectionResult {
        for (candidate in candidates) {
            val lower = candidate.lowercase().trim()

            for (variant in WAKE_VARIANTS) {
                val idx = lower.indexOf(variant)
                if (idx != -1) {
                    val trailing = lower.substring(idx + variant.length).trim()
                    Log.d(TAG, "Matched variant \"$variant\" in \"$lower\"")
                    return DetectionResult(
                        detected = true,
                        trailingCommand = trailing.ifEmpty { null },
                    )
                }
            }

            if (loosePhoneticMatch(lower)) {
                val trailing = extractTrailingAfterWake(lower)
                Log.d(TAG, "Loose phonetic match in \"$lower\"")
                return DetectionResult(
                    detected = true,
                    trailingCommand = trailing,
                )
            }
        }
        return DetectionResult(detected = false)
    }

    /**
     * Phonetic fallback: checks for "hey"/"he"/"a" followed by a word
     * starting with a "cl/kl/chl/craw/clow" consonant cluster.
     */
    private fun loosePhoneticMatch(text: String): Boolean {
        val prefixes = listOf("hey ", "he ", "a ")
        for (prefix in prefixes) {
            val idx = text.indexOf(prefix)
            if (idx == -1) continue
            val afterPrefix = text.substring(idx + prefix.length).trimStart()
            if (afterPrefix.startsWith("cl") ||
                afterPrefix.startsWith("kl") ||
                afterPrefix.startsWith("chl") ||
                afterPrefix.startsWith("craw") ||
                afterPrefix.startsWith("clow")
            ) return true
        }
        return false
    }

    private fun extractTrailingAfterWake(text: String): String? {
        val prefixes = listOf("hey ", "he ", "a ")
        for (prefix in prefixes) {
            val idx = text.indexOf(prefix)
            if (idx == -1) continue
            val afterPrefix = text.substring(idx + prefix.length).trimStart()
            val spaceIdx = afterPrefix.indexOf(' ')
            if (spaceIdx == -1) return null
            val trailing = afterPrefix.substring(spaceIdx).trim()
            return trailing.ifEmpty { null }
        }
        return null
    }
}
