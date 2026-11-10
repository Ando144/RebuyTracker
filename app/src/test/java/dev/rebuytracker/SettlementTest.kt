package dev.rebuytracker

import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Movement
import dev.rebuytracker.data.MovementType
import dev.rebuytracker.data.Player
import dev.rebuytracker.data.Profile
import dev.rebuytracker.logic.Leaderboard
import dev.rebuytracker.logic.PublicExport
import dev.rebuytracker.logic.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementTest {

    private val players = listOf(Player(1, "Ana"), Player(2, "Bea"), Player(3, "Carlos"), Player(4, "Dani"))

    private fun base(bank: Long = 5000) = Game.new(
        now = 0, buyInCents = 1000, chipsPerBuyIn = 1000, bankTotalChips = bank, players = players,
    )

    private fun Game.rebuy(playerId: Int, cents: Long, sellerId: Int? = null): Game =
        withPurchase(playerId, cents, sellerId, MovementType.REBUY, now = 1)!!

    private fun nets(g: Game) = Settlement.compute(g).players.associate { it.player.name to it.netCents }

    @Test
    fun rebuyFromBankThenFromPlayer() {
        var g = base().rebuy(1, 1000)
        assertEquals(0L, g.bankRemaining)
        g = g.rebuy(2, 1000, sellerId = 3)
        assertEquals(0L, g.rebuys[1].chipsFromBank)
        assertEquals(1000L, g.rebuys[1].centsToSeller)

        g = g.copy(finalChips = mapOf(1 to 0L, 2 to 2500L, 3 to 500L, 4 to 2000L))
        val r = Settlement.compute(g)
        assertEquals(mapOf("Ana" to -2000L, "Bea" to 500L, "Carlos" to 500L, "Dani" to 1000L), nets(g))
        assertEquals(3, r.transfers.size)
        assertTrue(r.transfers.all { it.from.name == "Ana" })
    }

    @Test
    fun rebuyNeedsSellerWhenBankIsEmpty() {
        val g = base(bank = 4000)
        assertNull(g.withPurchase(1, 1000, null, MovementType.REBUY, 1))
        assertNull(g.withPurchase(1, 1000, 1, MovementType.REBUY, 1))
    }

    @Test
    fun mixedRebuySplitsBankAndPlayer() {
        val rb = base(bank = 4500).rebuy(1, 1000, sellerId = 2).rebuys.single()
        assertEquals(500L, rb.chipsFromBank)
        assertEquals(500L, rb.chipsFromSeller)
        assertEquals(500L, rb.centsToSeller)
    }

    /** El caso real: Adrián se queda las fichas de la banca y Pablo llega tarde y compra a otro jugador. */
    @Test
    fun lateJoinerBuysChipsFromAPlayer() {
        var g = base(bank = 5000).rebuy(1, 1000)        // la banca se queda vacía
        val pablo = Player(g.nextPlayerId(), "Pablo")
        g = g.copy(players = g.players + pablo)
            .withPurchase(pablo.id, g.buyInCents, sellerId = 4, type = MovementType.ENTRY, now = 2)!!
        assertEquals(1000L, g.entryOf(pablo.id)!!.chipsFromSeller)
        assertEquals(5000L, g.chipsDistributed)          // las fichas de Pablo no salen de la banca

        g = g.copy(finalChips = mapOf(1 to 1500L, 2 to 500L, 3 to 1000L, 4 to 500L, pablo.id to 1500L))
        val n = nets(g)
        assertEquals(-500L, n["Ana"])
        assertEquals(500L, n["Dani"])                    // cobra los 10 € de las fichas cedidas
        assertEquals(500L, n["Pablo"])
        assertEquals(0L, n.values.sum())
    }

    @Test
    fun removalOnlyAllowedForUninvolvedPlayers() {
        val g = base(bank = 4000).rebuy(1, 1000, sellerId = 2)
        assertFalse(g.canRemove(1))
        assertFalse(g.canRemove(2))
        assertTrue(g.canRemove(3))
    }

    @Test
    fun legacyGamesMigrateWithSameResults() {
        val legacy = Game(
            id = 1, createdAt = 0, buyInCents = 1000, chipsPerBuyIn = 1000, bankTotalChips = 5000,
            players = players,
            movements = listOf(
                Movement(id = 1, playerId = 1, cents = 1000, chipsFromBank = 1000),
                Movement(id = 2, playerId = 2, cents = 1000, chipsFromBank = 0, sellerId = 3, chipsFromSeller = 1000, centsToSeller = 1000),
            ),
            finalChips = mapOf(1 to 0L, 2 to 2500L, 3 to 500L, 4 to 2000L),
        )
        val g = legacy.migrated()
        assertEquals(6, g.movements.size)
        assertEquals(5000L, g.chipsDistributed)
        assertEquals(mapOf("Ana" to -2000L, "Bea" to 500L, "Carlos" to 500L, "Dani" to 1000L), nets(g))
        assertEquals(g, g.migrated())
    }

    @Test
    fun netsAlwaysSumToZeroEvenWithWrongCount() {
        val g = base().rebuy(4, 500).copy(finalChips = mapOf(1 to 1333L, 2 to 1777L, 3 to 0L, 4 to 1400L))
        val r = Settlement.compute(g)
        assertTrue(r.adjusted)
        assertEquals(0L, r.players.sumOf { it.netCents })
        val paid = r.transfers.groupBy { it.from }.mapValues { e -> e.value.sumOf { it.cents } }
        r.players.filter { it.netCents < 0 }.forEach { assertEquals(-it.netCents, paid[it.player]) }
    }

    @Test
    fun leaderboardAggregatesByProfileAndName() {
        val profiles = listOf(Profile("p-ana", "Ana María"))
        val g1 = base().copy(
            players = listOf(Player(1, "Ana", "p-ana"), Player(2, "Bea"), Player(3, "Carlos"), Player(4, "Dani")),
            finalChips = mapOf(1 to 2000L, 2 to 0L, 3 to 1000L, 4 to 1000L),
        )
        val g2 = Game.manual(
            now = 10, playedAt = 10,
            players = listOf(Player(1, "ana maría"), Player(2, "Bea")),
            nets = mapOf(1 to -300L, 2 to 300L),
        )
        val board = Leaderboard.compute(listOf(g1, g2), profiles)
        val ana = board.first { it.profileId == "p-ana" }
        assertEquals("Ana María", ana.name)
        assertEquals(2, ana.games)
        assertEquals(700L, ana.netCents)
        assertEquals(1, ana.wins)
        assertEquals(1000L, ana.bestCents)
        val bea = board.first { it.name == "Bea" }
        assertEquals(-700L, bea.netCents)
        assertEquals(0L, board.sumOf { it.netCents })
    }

    @Test
    fun publicExportGroupsPlayersAndKeepsZeroSum() {
        val profiles = listOf(Profile("p-ana", "Ana"))
        val g = base().rebuy(2, 1000).copy(
            players = listOf(Player(1, "Ana", "p-ana"), Player(2, "Bea"), Player(3, "Carlos"), Player(4, "Dani")),
            finalChips = mapOf(1 to 3000L, 2 to 0L, 3 to 1000L, 4 to 1000L),
        )
        val manual = Game.manual(now = 5, playedAt = 5, players = listOf(Player(1, "ana"), Player(2, "Bea")), nets = mapOf(1 to 250L, 2 to -250L))
        val data = PublicExport.build(listOf(manual, g), profiles, now = 99)
        assertEquals(listOf(0L, 5L), data.games.map { it.date })
        assertTrue(data.games.all { game -> game.results.sumOf { it.netCents } == 0L })
        val anaIds = data.games.flatMap { it.results }.filter { it.playerId == "p-ana" }
        assertEquals(2, anaIds.size)
        assertEquals("Ana", data.players.first { it.id == "p-ana" }.name)
        val bea = data.games[0].results.first { it.playerId == "name:bea" }
        assertEquals(1, bea.rebuys)
        assertEquals(2000L, bea.investedCents)
        assertNull(data.games[1].tableCents)
    }
}
