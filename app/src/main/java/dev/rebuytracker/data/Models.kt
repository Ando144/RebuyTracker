package dev.rebuytracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
enum class Phase { PLAYING, COUNTING, FINISHED }

@Serializable
enum class MovementType { ENTRY, REBUY }

/** Jugador habitual, independiente de las partidas. Sirve para la clasificación global. */
@Serializable
data class Profile(val id: String, val name: String)

@Serializable
data class Player(val id: Int, val name: String, val profileId: String? = null)

/**
 * Compra de fichas: una entrada o un rebuy. Las fichas salen primero de la banca; si no llega,
 * el resto lo cede [sellerId]. [cents] es lo que paga el jugador; de eso, [centsToSeller] se lo
 * debe al jugador que cedió fichas y el resto va al bote. Todo se compensa al final.
 */
@Serializable
data class Movement(
    val id: Long,
    val playerId: Int,
    val cents: Long,
    val chipsFromBank: Long,
    val sellerId: Int? = null,
    val chipsFromSeller: Long = 0,
    val centsToSeller: Long = 0,
    val timestamp: Long = 0,
    val type: MovementType = MovementType.REBUY,
) {
    val totalChips: Long get() = chipsFromBank + chipsFromSeller
}

data class PurchaseSplit(
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
    @SerialName("rebuys") val movements: List<Movement> = emptyList(),
    val finalChips: Map<Int, Long> = emptyMap(),
    val phase: Phase = Phase.PLAYING,
    val finishedAt: Long? = null,
    /** Las versiones antiguas no guardaban las entradas como movimientos. */
    val entriesExplicit: Boolean = false,
    /** Partida registrada a mano (solo resultados): jugador → neto en céntimos. */
    val manualNets: Map<Int, Long>? = null,
) {
    val isManual: Boolean get() = manualNets != null

    /** Convierte partidas guardadas por versiones antiguas al formato con entradas explícitas. */
    fun migrated(): Game {
        if (entriesExplicit || isManual) return this
        val base = movements.maxOfOrNull { it.id } ?: 0L
        val entries = players.mapIndexed { i, p ->
            Movement(
                id = base + 1 + i,
                playerId = p.id,
                cents = buyInCents,
                chipsFromBank = chipsPerBuyIn,
                timestamp = createdAt,
                type = MovementType.ENTRY,
            )
        }
        return copy(movements = entries + movements, entriesExplicit = true)
    }

    fun chipsForCents(cents: Long): Long =
        (cents.toDouble() * chipsPerBuyIn / buyInCents).roundToLong()

    fun centsForChips(chips: Long): Double =
        chips.toDouble() * buyInCents / chipsPerBuyIn

    /** Céntimos que vale una ficha (puede tener decimales, p. ej. 0,25 céntimos). */
    val centsPerChip: Double get() = buyInCents.toDouble() / chipsPerBuyIn

    val rebuys: List<Movement> get() = movements.filter { it.type == MovementType.REBUY }

    /** Fichas que han salido de la banca = fichas que debe haber en la mesa. */
    val chipsDistributed: Long get() = movements.sumOf { it.chipsFromBank }

    val bankRemaining: Long get() = bankTotalChips - chipsDistributed

    val tableValueCents: Long get() = centsForChips(chipsDistributed).roundToLong()

    val countedChips: Long get() = players.sumOf { finalChips[it.id] ?: 0L }

    fun player(id: Int?): Player? = players.firstOrNull { it.id == id }

    fun rebuysOf(playerId: Int): List<Movement> = rebuys.filter { it.playerId == playerId }

    fun entryOf(playerId: Int): Movement? =
        movements.firstOrNull { it.playerId == playerId && it.type == MovementType.ENTRY }

    /** Dinero total que ha puesto el jugador (entrada + rebuys, vengan de donde vengan las fichas). */
    fun investedCents(playerId: Int): Long = movements.filter { it.playerId == playerId }.sumOf { it.cents }

    /** Dinero que le deben al jugador por fichas que ha cedido. */
    fun soldCents(playerId: Int): Long = movements.filter { it.sellerId == playerId }.sumOf { it.centsToSeller }

    /** Solo se puede quitar a alguien que no ha hecho rebuys ni ha cedido fichas a nadie. */
    fun canRemove(playerId: Int): Boolean = movements.none {
        (it.playerId == playerId && it.type == MovementType.REBUY) || it.sellerId == playerId
    }

    /** Reparte una compra de fichas: primero banca y, lo que falte, de otro jugador. */
    fun splitPurchase(cents: Long): PurchaseSplit {
        val chips = chipsForCents(cents)
        val fromBank = chips.coerceAtMost(bankRemaining.coerceAtLeast(0))
        val fromSeller = chips - fromBank
        val centsToSeller = when {
            fromSeller == 0L -> 0L
            fromBank == 0L -> cents
            else -> centsForChips(fromSeller).roundToLong().coerceIn(0, cents)
        }
        return PurchaseSplit(chips, fromBank, fromSeller, cents - centsToSeller, centsToSeller)
    }

    fun nextMovementId(): Long = (movements.maxOfOrNull { it.id } ?: 0L) + 1

    fun nextPlayerId(): Int = (players.maxOfOrNull { it.id } ?: 0) + 1

    /** Añade una compra (entrada o rebuy) aplicando el reparto banca/jugador. Devuelve null si falta el vendedor. */
    fun withPurchase(playerId: Int, cents: Long, sellerId: Int?, type: MovementType, now: Long): Game? {
        val split = splitPurchase(cents)
        if (split.chips <= 0 || (split.fromSeller > 0 && (sellerId == null || sellerId == playerId))) return null
        val movement = Movement(
            id = nextMovementId(),
            playerId = playerId,
            cents = cents,
            chipsFromBank = split.fromBank,
            sellerId = if (split.fromSeller > 0) sellerId else null,
            chipsFromSeller = split.fromSeller,
            centsToSeller = split.centsToSeller,
            timestamp = now,
            type = type,
        )
        return copy(movements = movements + movement)
    }

    companion object {
        fun new(
            now: Long,
            buyInCents: Long,
            chipsPerBuyIn: Long,
            bankTotalChips: Long,
            players: List<Player>,
        ): Game {
            val entries = players.mapIndexed { i, p ->
                Movement(
                    id = i + 1L,
                    playerId = p.id,
                    cents = buyInCents,
                    chipsFromBank = chipsPerBuyIn,
                    timestamp = now,
                    type = MovementType.ENTRY,
                )
            }
            return Game(
                id = now,
                createdAt = now,
                buyInCents = buyInCents,
                chipsPerBuyIn = chipsPerBuyIn,
                bankTotalChips = bankTotalChips,
                players = players,
                movements = entries,
                entriesExplicit = true,
            )
        }

        /** Partida registrada solo con resultados (para partidas pasadas o jugadas sin la app). */
        fun manual(now: Long, playedAt: Long, players: List<Player>, nets: Map<Int, Long>): Game = Game(
            id = now,
            createdAt = playedAt,
            buyInCents = 1,
            chipsPerBuyIn = 1,
            bankTotalChips = 0,
            players = players,
            phase = Phase.FINISHED,
            finishedAt = now,
            entriesExplicit = true,
            manualNets = nets,
        )
    }
}
