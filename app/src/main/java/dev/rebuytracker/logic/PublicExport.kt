package dev.rebuytracker.logic

import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Profile
import kotlinx.serialization.Serializable

/**
 * Datos que se publican para la web: solo partidas cerradas, con los jugadores
 * identificados por su jugador habitual para poder agrupar estadísticas.
 */
@Serializable
data class PublicData(
    val version: Int = 1,
    val generatedAt: Long,
    val players: List<PublicPlayer>,
    val games: List<PublicGame>,
)

@Serializable
data class PublicPlayer(val id: String, val name: String)

@Serializable
data class PublicGame(
    val id: Long,
    val date: Long,
    val manual: Boolean,
    val buyInCents: Long? = null,
    val chipsPerBuyIn: Long? = null,
    val tableCents: Long? = null,
    val results: List<PublicResult>,
    val transfers: List<PublicTransfer>,
)

@Serializable
data class PublicResult(
    val playerId: String,
    val netCents: Long,
    val investedCents: Long? = null,
    val rebuys: Int = 0,
    val rebuyCents: Long = 0,
    val soldCents: Long = 0,
    val finalChips: Long? = null,
)

@Serializable
data class PublicTransfer(val from: String, val to: String, val cents: Long)

object PublicExport {

    fun build(history: List<Game>, profiles: List<Profile>, now: Long): PublicData {
        val resolver = Leaderboard.Resolver(profiles)
        val names = linkedMapOf<String, String>()

        val games = history.sortedBy { it.createdAt }.map { game ->
            val settlement = Settlement.compute(game)
            game.players.forEach { names[resolver.key(it)] = resolver.displayName(it) }
            PublicGame(
                id = game.id,
                date = game.createdAt,
                manual = game.isManual,
                buyInCents = game.buyInCents.takeUnless { game.isManual },
                chipsPerBuyIn = game.chipsPerBuyIn.takeUnless { game.isManual },
                tableCents = game.tableValueCents.takeUnless { game.isManual },
                results = settlement.players.map { r ->
                    val rebuys = game.rebuysOf(r.player.id)
                    PublicResult(
                        playerId = resolver.key(r.player),
                        netCents = r.netCents,
                        investedCents = r.investedCents.takeUnless { game.isManual },
                        rebuys = rebuys.size,
                        rebuyCents = rebuys.sumOf { it.cents },
                        soldCents = r.soldCents,
                        finalChips = r.finalChips.takeUnless { game.isManual },
                    )
                },
                transfers = settlement.transfers.map { PublicTransfer(resolver.key(it.from), resolver.key(it.to), it.cents) },
            )
        }
        return PublicData(
            generatedAt = now,
            players = names.map { (id, name) -> PublicPlayer(id, name) },
            games = games,
        )
    }
}
