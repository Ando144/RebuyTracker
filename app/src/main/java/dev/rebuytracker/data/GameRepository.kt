package dev.rebuytracker.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Guarda la partida en curso y el historial en local (se conservan aunque cierres la app). */
class GameRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rebuy_tracker", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCurrent(): Game? =
        prefs.getString(KEY_CURRENT, null)?.let { runCatching { json.decodeFromString<Game>(it) }.getOrNull() }

    fun saveCurrent(game: Game?) {
        prefs.edit().apply {
            if (game == null) remove(KEY_CURRENT) else putString(KEY_CURRENT, json.encodeToString(game))
        }.apply()
    }

    fun loadHistory(): List<Game> =
        prefs.getString(KEY_HISTORY, null)
            ?.let { runCatching { json.decodeFromString<List<Game>>(it) }.getOrNull() }
            ?: emptyList()

    fun saveHistory(history: List<Game>) {
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(history)).apply()
    }

    private companion object {
        const val KEY_CURRENT = "current_game"
        const val KEY_HISTORY = "history"
    }
}
