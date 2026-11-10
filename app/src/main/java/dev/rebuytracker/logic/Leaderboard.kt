package dev.rebuytracker.logic

import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Player
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

    /** Resuelve a qué jugador habitual corresponde un jugador de partida (por id o, si no, por nombre). */
    class Resolver(profiles: List<Profile>) {
        private val byId = profiles.associateBy { it.id }
        private val byName = profiles.associateBy { it.name.trim().lowercase() }

        fun profile(player: Player): Profile? =
            player.profileId?.let(byId::get) ?: byName[player.name.trim().lowercase()]

        fun key(player: Player): String = profile(player)?.id ?: ("name:" + player.name.trim().lowercase())

        fun displayName(player: Player): String = profile(player)?.name ?: player.name
    }

    /**
     * Suma los resultados de todas las partidas cerradas por jugador.
     * Se agrupa por jugador habitual; si un jugador de una partida no está vinculado, se agrupa por nombre.
     */
    fun compute(history: List<Game>, profiles: List<Profile>): List<LeaderboardEntry> {
        val resolver = Resolver(profiles)

        data class Acc(var name: String, val profileId: String?, var games: Int = 0, var wins: Int = 0,
                       var net: Long = 0, var best: Long = Long.MIN_VALUE, var worst: Long = Long.MAX_VALUE)

        val acc = linkedMapOf<String, Acc>()
        history.sortedBy { it.createdAt }.forEach { game ->
            val result = Settlement.compute(game)
            result.players.forEach { r ->
                val profile = resolver.profile(r.player)
                val key = resolver.key(r.player)
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
