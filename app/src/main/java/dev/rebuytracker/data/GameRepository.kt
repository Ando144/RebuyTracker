package dev.rebuytracker.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Guarda en local la partida en curso, el historial, los jugadores habituales y la configuración de la web. */
class GameRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rebuy_tracker", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCurrent(): Game? =
        prefs.getString(KEY_CURRENT, null)
            ?.let { runCatching { json.decodeFromString<Game>(it) }.getOrNull() }
            ?.migrated()

    fun saveCurrent(game: Game?) {
        prefs.edit().apply {
            if (game == null) remove(KEY_CURRENT) else putString(KEY_CURRENT, json.encodeToString(game))
        }.apply()
    }

    fun loadHistory(): List<Game> =
        prefs.getString(KEY_HISTORY, null)
            ?.let { runCatching { json.decodeFromString<List<Game>>(it) }.getOrNull() }
            ?.map { it.migrated() }
            ?: emptyList()

    fun saveHistory(history: List<Game>) {
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(history)).apply()
    }

    fun loadProfiles(): List<Profile> =
        prefs.getString(KEY_PROFILES, null)
            ?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() }
            ?: emptyList()

    fun saveProfiles(profiles: List<Profile>) {
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(profiles)).apply()
    }

    fun loadPublishConfig(): PublishConfig =
        prefs.getString(KEY_PUBLISH, null)
            ?.let { runCatching { json.decodeFromString<PublishConfig>(it) }.getOrNull() }
            ?: PublishConfig()

    fun savePublishConfig(config: PublishConfig) {
        prefs.edit().putString(KEY_PUBLISH, json.encodeToString(config)).apply()
    }

    private companion object {
        const val KEY_CURRENT = "current_game"
        const val KEY_HISTORY = "history"
        const val KEY_PROFILES = "profiles"
        const val KEY_PUBLISH = "publish_config"
    }
}
