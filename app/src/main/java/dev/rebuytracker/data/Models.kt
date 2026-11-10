package dev.rebuytracker.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
enum class Phase { PLAYING, COUNTING, FINISHED }

@Serializable
data class Player(val id: Int, val name: String)

/**
 * Un rebuy. Las fichas salen primero de la banca; si no llega, el resto lo cede [sellerId].
 * [cents] es lo que paga el jugador en total; de eso, [centsToSeller] se lo debe al jugador
 * que cedió fichas y el resto va al bote. Todo se compensa al final con los bizums.
 */
@Serializable
data class Rebuy(
    val id: Long,
    val playerId: Int,
    val cents: Long,
    val chipsFromBank: Long,
    val sellerId: Int? = null,
    val chipsFromSeller: Long = 0,
    val centsToSeller: Long = 0,
    val timestamp: Long = 0,
) {
    val totalChips: Long get() = chipsFromBank + chipsFromSeller
}

data class RebuySplit(
    val chips: Long,
    val fromBank: Long,
    val fromSeller: Long,
    val centsToBank: Long,
    val centsToSeller: Long,
)

@Serializable
data class Game(
    val id: Long,
    val createdAt: Long,
    val buyInCents: Long,
    val chipsPerBuyIn: Long,
    val bankTotalChips: Long,
    val players: List<Player>,
    val rebuys: List<Rebuy> = emptyList(),
    val finalChips: Map<Int, Long> = emptyMap(),
    val phase: Phase = Phase.PLAYING,
    val finishedAt: Long? = null,
) {
    fun chipsForCents(cents: Long): Long =
        (cents.toDouble() * chipsPerBuyIn / buyInCents).roundToLong()

    fun centsForChips(chips: Long): Double =
        chips.toDouble() * buyInCents / chipsPerBuyIn

    /** Fichas que han salido de la banca (entradas + rebuys de banca) = fichas que debe haber en la mesa. */
    val chipsDistributed: Long
        get() = players.size * chipsPerBuyIn + rebuys.sumOf { it.chipsFromBank }

    val bankRemaining: Long get() = bankTotalChips - chipsDistributed

    val tableValueCents: Long get() = centsForChips(chipsDistributed).roundToLong()

    val countedChips: Long get() = players.sumOf { finalChips[it.id] ?: 0L }

    fun player(id: Int?): Player? = players.firstOrNull { it.id == id }

    fun rebuysOf(playerId: Int): List<Rebuy> = rebuys.filter { it.playerId == playerId }

    /** Dinero total que ha puesto el jugador (entrada + rebuys, vengan de donde vengan las fichas). */
    fun investedCents(playerId: Int): Long = buyInCents + rebuysOf(playerId).sumOf { it.cents }

    /** Dinero que le deben al jugador por fichas que ha cedido en rebuys. */
    fun soldCents(playerId: Int): Long =
        rebuys.filter { it.sellerId == playerId }.sumOf { it.centsToSeller }

    /** Reparte un rebuy: primero banca, y lo que falte, de otro jugador. */
    fun splitRebuy(cents: Long): RebuySplit {
        val chips = chipsForCents(cents)
        val fromBank = chips.coerceAtMost(bankRemaining.coerceAtLeast(0))
        val fromSeller = chips - fromBank
        val centsToSeller = when {
            fromSeller == 0L -> 0L
            fromBank == 0L -> cents
            else -> centsForChips(fromSeller).roundToLong().coerceIn(0, cents)
        }
        return RebuySplit(chips, fromBank, fromSeller, cents - centsToSeller, centsToSeller)
    }
}
