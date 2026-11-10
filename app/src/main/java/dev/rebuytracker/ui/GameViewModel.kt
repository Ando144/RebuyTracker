package dev.rebuytracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.rebuytracker.data.Game
import dev.rebuytracker.data.GameRepository
import dev.rebuytracker.data.Phase
import dev.rebuytracker.data.Player
import dev.rebuytracker.data.Rebuy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Screen { HOME, SETUP, GAME, COUNT, RESULTS }

data class UiState(
    val screen: Screen = Screen.HOME,
    val current: Game? = null,
    val history: List<Game> = emptyList(),
    /** Partida del historial que se está consultando (solo lectura). */
    val viewing: Game? = null,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GameRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val current = repo.loadCurrent()
        _state.value = UiState(
            screen = current?.let { screenFor(it.phase) } ?: Screen.HOME,
            current = current,
            history = repo.loadHistory(),
        )
    }

    private fun screenFor(phase: Phase) = when (phase) {
        Phase.PLAYING -> Screen.GAME
        Phase.COUNTING -> Screen.COUNT
        Phase.FINISHED -> Screen.RESULTS
    }

    private fun updateCurrent(screen: Screen? = null, transform: (Game) -> Game) {
        val game = _state.value.current ?: return
        val updated = transform(game)
        repo.saveCurrent(updated)
        _state.update { it.copy(current = updated, screen = screen ?: it.screen) }
    }

    // --- Navegación ---

    fun goHome() = _state.update { it.copy(screen = Screen.HOME, viewing = null) }

    fun openSetup() = _state.update { it.copy(screen = Screen.SETUP, viewing = null) }

    fun continueGame() {
        val game = _state.value.current ?: return
        _state.update { it.copy(screen = screenFor(game.phase), viewing = null) }
    }

    fun back() {
        val s = _state.value
        when (s.screen) {
            Screen.HOME -> Unit
            Screen.SETUP, Screen.GAME -> goHome()
            Screen.COUNT -> backToGame()
            Screen.RESULTS -> if (s.viewing != null) goHome() else backToCount()
        }
    }

    // --- Partida ---

    fun startGame(buyInCents: Long, chipsPerBuyIn: Long, bankTotalChips: Long, names: List<String>) {
        val now = System.currentTimeMillis()
        val game = Game(
            id = now,
            createdAt = now,
            buyInCents = buyInCents,
            chipsPerBuyIn = chipsPerBuyIn,
            bankTotalChips = bankTotalChips,
            players = names.mapIndexed { i, n -> Player(i + 1, n.trim()) },
        )
        repo.saveCurrent(game)
        _state.update { it.copy(current = game, screen = Screen.GAME) }
    }

    /** Jugador que se incorpora tarde con una entrada de la banca. */
    fun addPlayer(name: String): Boolean {
        val game = _state.value.current ?: return false
        if (game.bankRemaining < game.chipsPerBuyIn) return false
        val nextId = (game.players.maxOfOrNull { it.id } ?: 0) + 1
        updateCurrent { it.copy(players = it.players + Player(nextId, name.trim())) }
        return true
    }

    fun rebuy(playerId: Int, cents: Long, sellerId: Int?) = updateCurrent { game ->
        val split = game.splitRebuy(cents)
        if (split.chips <= 0 || (split.fromSeller > 0 && sellerId == null)) return@updateCurrent game
        val rebuy = Rebuy(
            id = (game.rebuys.maxOfOrNull { it.id } ?: 0L) + 1,
            playerId = playerId,
            cents = cents,
            chipsFromBank = split.fromBank,
            sellerId = if (split.fromSeller > 0) sellerId else null,
            chipsFromSeller = split.fromSeller,
            centsToSeller = split.centsToSeller,
            timestamp = System.currentTimeMillis(),
        )
        game.copy(rebuys = game.rebuys + rebuy)
    }

    fun removeRebuy(id: Long) = updateCurrent { g -> g.copy(rebuys = g.rebuys.filterNot { it.id == id }) }

    fun startCount() = updateCurrent(Screen.COUNT) { it.copy(phase = Phase.COUNTING) }

    fun backToGame() = updateCurrent(Screen.GAME) { it.copy(phase = Phase.PLAYING) }

    fun setChips(playerId: Int, chips: Long?) = updateCurrent { g ->
        g.copy(finalChips = if (chips == null) g.finalChips - playerId else g.finalChips + (playerId to chips))
    }

    fun calculate() = updateCurrent(Screen.RESULTS) { it.copy(phase = Phase.FINISHED) }

    fun backToCount() = updateCurrent(Screen.COUNT) { it.copy(phase = Phase.COUNTING) }

    fun closeGame() {
        val game = _state.value.current ?: return
        val finished = game.copy(phase = Phase.FINISHED, finishedAt = System.currentTimeMillis())
        val history = listOf(finished) + _state.value.history
        repo.saveHistory(history)
        repo.saveCurrent(null)
        _state.update { it.copy(current = null, history = history, screen = Screen.HOME) }
    }

    fun discardGame() {
        repo.saveCurrent(null)
        _state.update { it.copy(current = null, screen = Screen.HOME) }
    }

    // --- Historial ---

    fun openHistory(game: Game) = _state.update { it.copy(viewing = game, screen = Screen.RESULTS) }

    fun deleteHistory(game: Game) {
        val history = _state.value.history.filterNot { it.id == game.id }
        repo.saveHistory(history)
        _state.update { it.copy(history = history) }
    }
}
