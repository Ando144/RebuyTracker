package dev.rebuytracker.logic

import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Profile

data class LeaderboardEntry(
    val key: String,
    val name: String,
    val profileId: String?,
    val games: Int,
    val wins: Int,
    val netCents: Long,
    val bestCents: Long,
    val worstCents: Long,
) {
    val averageCents: Long get() = if (games == 0) 0 else netCents / games
}

object Leaderboard {

    /**
     * Suma los resultados de todas las partidas cerradas por jugador.
     * Se agrupa por jugador habitual; si un jugador de una partida no está vinculado, se agrupa por nombre.
     */
    fun compute(history: List<Game>, profiles: List<Profile>): List<LeaderboardEntry> {
        val byId = profiles.associateBy { it.id }
        val byName = profiles.associateBy { it.name.trim().lowercase() }

        data class Acc(var name: String, val profileId: String?, var games: Int = 0, var wins: Int = 0,
                       var net: Long = 0, var best: Long = Long.MIN_VALUE, var worst: Long = Long.MAX_VALUE)

        val acc = linkedMapOf<String, Acc>()
        history.sortedBy { it.createdAt }.forEach { game ->
            val result = Settlement.compute(game)
            result.players.forEach { r ->
                val profile = r.player.profileId?.let(byId::get) ?: byName[r.player.name.trim().lowercase()]
                val key = profile?.id ?: ("name:" + r.player.name.trim().lowercase())
                val a = acc.getOrPut(key) { Acc(profile?.name ?: r.player.name, profile?.id) }
                if (profile == null) a.name = r.player.name
                a.games += 1
                if (r.netCents > 0) a.wins += 1
                a.net += r.netCents
                a.best = maxOf(a.best, r.netCents)
                a.worst = minOf(a.worst, r.netCents)
            }
        }
        return acc.map { (key, a) ->
            LeaderboardEntry(key, a.name, a.profileId, a.games, a.wins, a.net, a.best, a.worst)
        }.sortedWith(compareByDescending<LeaderboardEntry> { it.netCents }.thenBy { it.name.lowercase() })
    }

    /** Partidas jugadas por cada jugador habitual. */
    fun gamesByProfile(history: List<Game>, profiles: List<Profile>): Map<String, Int> {
        val entries = compute(history, profiles)
        return entries.mapNotNull { e -> e.profileId?.let { it to e.games } }.toMap()
    }
}
