package dev.rebuytracker

import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Player
import dev.rebuytracker.data.Rebuy
import dev.rebuytracker.logic.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementTest {

    private val players = listOf(Player(1, "Ana"), Player(2, "Bea"), Player(3, "Carlos"), Player(4, "Dani"))

    private fun base() = Game(
        id = 1, createdAt = 0,
        buyInCents = 1000, chipsPerBuyIn = 1000, bankTotalChips = 5000,
        players = players,
    )

    private fun Game.withRebuy(playerId: Int, cents: Long, sellerId: Int? = null): Game {
        val s = splitRebuy(cents)
        return copy(
            rebuys = rebuys + Rebuy(
                id = rebuys.size + 1L, playerId = playerId, cents = cents,
                chipsFromBank = s.fromBank, sellerId = if (s.fromSeller > 0) sellerId else null,
                chipsFromSeller = s.fromSeller, centsToSeller = s.centsToSeller,
            )
        )
    }

    @Test
    fun rebuyFromBankThenFromPlayer() {
        var g = base().withRebuy(1, 1000)            // banca: 1000 → 0
        assertEquals(0L, g.bankRemaining)
        g = g.withRebuy(2, 1000, sellerId = 3)        // todo de Carlos
        assertEquals(0L, g.rebuys[1].chipsFromBank)
        assertEquals(1000L, g.rebuys[1].centsToSeller)

        g = g.copy(finalChips = mapOf(1 to 0L, 2 to 2500L, 3 to 500L, 4 to 2000L))
        val r = Settlement.compute(g)
        val net = r.players.associate { it.player.name to it.netCents }
        assertEquals(-2000L, net["Ana"])
        assertEquals(500L, net["Bea"])
        assertEquals(500L, net["Carlos"])
        assertEquals(1000L, net["Dani"])
        assertEquals(3, r.transfers.size)
        assertTrue(r.transfers.all { it.from.name == "Ana" })
    }

    @Test
    fun mixedRebuySplitsBankAndPlayer() {
        val g = base().copy(bankTotalChips = 4500).withRebuy(1, 1000, sellerId = 2)
        val rb = g.rebuys.single()
        assertEquals(500L, rb.chipsFromBank)
        assertEquals(500L, rb.chipsFromSeller)
        assertEquals(500L, rb.centsToSeller)
    }

    @Test
    fun netsAlwaysSumToZeroEvenWithWrongCount() {
        val g = base().withRebuy(4, 500).copy(finalChips = mapOf(1 to 1333L, 2 to 1777L, 3 to 0L, 4 to 1400L))
        val r = Settlement.compute(g)
        assertTrue(r.adjusted)
        assertEquals(0L, r.players.sumOf { it.netCents })
        val paid = r.transfers.groupBy { it.from }.mapValues { e -> e.value.sumOf { it.cents } }
        r.players.filter { it.netCents < 0 }.forEach { assertEquals(-it.netCents, paid[it.player]) }
    }
}
