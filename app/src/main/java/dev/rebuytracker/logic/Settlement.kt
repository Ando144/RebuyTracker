package dev.rebuytracker.logic

import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Player
import kotlin.math.roundToLong

data class PlayerResult(
    val player: Player,
    val investedCents: Long,
    val soldCents: Long,
    val finalChips: Long,
    val finalValueCents: Long,
    val netCents: Long,
)

data class Transfer(val from: Player, val to: Player, val cents: Long)

data class SettlementResult(
    val players: List<PlayerResult>,
    val transfers: List<Transfer>,
    val expectedChips: Long,
    val countedChips: Long,
) {
    val chipDifference: Long get() = countedChips - expectedChips
    val adjusted: Boolean get() = countedChips > 0 && chipDifference != 0L
}

object Settlement {

    /**
     * Balance de cada jugador = valor de sus fichas finales − lo que ha puesto + lo que le deben por fichas cedidas.
     * Si el recuento no cuadra con las fichas repartidas, se reescala el valor de las fichas para que las cuentas sumen cero.
     */
    fun compute(game: Game): SettlementResult {
        val expected = game.chipsDistributed
        val counted = game.countedChips
        val factor = if (counted > 0 && counted != expected) expected.toDouble() / counted else 1.0

        val values = game.players.map { p -> game.centsForChips(game.finalChips[p.id] ?: 0L) * factor }
        val exactNets = game.players.mapIndexed { i, p ->
            values[i] - game.investedCents(p.id) + game.soldCents(p.id)
        }
        val target = if (counted > 0) 0L else exactNets.sum().roundToLong()
        val nets = roundPreservingSum(exactNets, target)

        val results = game.players.mapIndexed { i, p ->
            PlayerResult(
                player = p,
                investedCents = game.investedCents(p.id),
                soldCents = game.soldCents(p.id),
                finalChips = game.finalChips[p.id] ?: 0L,
                finalValueCents = values[i].roundToLong(),
                netCents = nets[i],
            )
        }.sortedByDescending { it.netCents }

        val transfers = minimizeTransfers(game.players.mapIndexed { i, p -> p to nets[i] })
        return SettlementResult(results, transfers, expected, counted)
    }

    /** Redondea a céntimos garantizando que la suma sea exactamente [target]. */
    fun roundPreservingSum(values: List<Double>, target: Long): List<Long> {
        val rounded = values.map { it.roundToLong() }.toMutableList()
        var diff = target - rounded.sum()
        while (diff != 0L && rounded.isNotEmpty()) {
            if (diff > 0) {
                val i = values.indices.maxBy { values[it] - rounded[it] }
                rounded[i] += 1; diff -= 1
            } else {
                val i = values.indices.minBy { values[it] - rounded[it] }
                rounded[i] -= 1; diff += 1
            }
        }
        return rounded
    }

    /**
     * Minimiza el número de bizums: primero empareja deudas y créditos idénticos,
     * y si no hay, cruza la mayor deuda con el mayor crédito. Como mucho n−1 bizums.
     */
    fun minimizeTransfers(balances: List<Pair<Player, Long>>): List<Transfer> {
        class Bal(val player: Player, var amount: Long)

        val debtors = balances.filter { it.second < 0 }.map { Bal(it.first, -it.second) }.toMutableList()
        val creditors = balances.filter { it.second > 0 }.map { Bal(it.first, it.second) }.toMutableList()
        val out = mutableListOf<Transfer>()

        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val exact = debtors.firstNotNullOfOrNull { d ->
                creditors.firstOrNull { it.amount == d.amount }?.let { d to it }
            }
            val (d, c) = exact ?: (debtors.maxBy { it.amount } to creditors.maxBy { it.amount })
            val amount = minOf(d.amount, c.amount)
            out += Transfer(d.player, c.player, amount)
            d.amount -= amount
            c.amount -= amount
            if (d.amount == 0L) debtors.remove(d)
            if (c.amount == 0L) creditors.remove(c)
        }
        return out.sortedWith(compareBy<Transfer> { it.from.name.lowercase() }.thenByDescending { it.cents })
    }
}
