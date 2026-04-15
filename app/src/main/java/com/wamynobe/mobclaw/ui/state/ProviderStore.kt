package com.wamynobe.mobclaw.ui.state

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted persistent storage for provider API keys and configuration.
 */
class ProviderStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mobclaw_provider_config",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveProviderConfig(config: ProviderConfig) {
        val prefix = config.type.name
        prefs.edit()
            .putString("${prefix}_api_key", config.apiKey)
            .putString("${prefix}_model", config.model)
            .putString("${prefix}_base_url", config.baseUrl)
            .putBoolean("${prefix}_active", config.isActive)
            .apply()
    }

    fun loadProviderConfig(type: ProviderType): ProviderConfig {
        val prefix = type.name
        return ProviderConfig(
            type = type,
            apiKey = prefs.getString("${prefix}_api_key", "") ?: "",
            model = prefs.getString("${prefix}_model", type.defaultModel) ?: type.defaultModel,
            baseUrl = prefs.getString("${prefix}_base_url", "") ?: "",
            isActive = prefs.getBoolean("${prefix}_active", false),
        )
    }

    fun loadAllProviders(): List<ProviderConfig> {
        return ProviderType.entries.map { loadProviderConfig(it) }
    }

    fun getActiveProvider(): ProviderType {
        val active = prefs.getString("active_provider", ProviderType.GEMINI.name)
        return try {
            ProviderType.valueOf(active ?: ProviderType.GEMINI.name)
        } catch (_: IllegalArgumentException) {
            ProviderType.GEMINI
        }
    }

    fun setActiveProvider(type: ProviderType) {
        prefs.edit().putString("active_provider", type.name).apply()
    }
}
