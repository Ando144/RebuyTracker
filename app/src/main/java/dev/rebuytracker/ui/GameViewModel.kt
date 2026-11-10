package dev.rebuytracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.rebuytracker.data.Game
import dev.rebuytracker.data.GameRepository
import dev.rebuytracker.data.MovementType
import dev.rebuytracker.data.Phase
import dev.rebuytracker.data.Player
import dev.rebuytracker.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class Screen { HOME, SETUP, GAME, COUNT, RESULTS, LEADERBOARD, ROSTER, MANUAL }

data class UiState(
    val screen: Screen = Screen.HOME,
    val current: Game? = null,
    val history: List<Game> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    /** Partida del historial que se está consultando (solo lectura). */
    val viewing: Game? = null,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GameRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val current = repo.loadCurrent()
        val history = repo.loadHistory()
        var profiles = repo.loadProfiles()
        if (profiles.isEmpty() && history.isNotEmpty()) {
            // Primera vez con jugadores habituales: se crean a partir del historial.
            profiles = history.flatMap { g -> g.players.map { it.name.trim() } }
                .distinctBy { it.lowercase() }
                .map { Profile(UUID.randomUUID().toString(), it) }
            repo.saveProfiles(profiles)
        }
        _state.value = UiState(
            screen = current?.let { screenFor(it.phase) } ?: Screen.HOME,
            current = current,
            history = history,
            profiles = profiles,
        )
    }

    private fun screenFor(phase: Phase) = when (phase) {
        Phase.PLAYING -> Screen.GAME
        Phase.COUNTING -> Screen.COUNT
        Phase.FINISHED -> Screen.RESULTS
    }

    private fun now() = System.currentTimeMillis()

    private fun updateCurrent(screen: Screen? = null, transform: (Game) -> Game?) {
        val game = _state.value.current ?: return
        val updated = transform(game) ?: return
        repo.saveCurrent(updated)
        _state.update { it.copy(current = updated, screen = screen ?: it.screen) }
    }

    // --- Jugadores habituales ---

    /** Devuelve el perfil con ese nombre, creándolo si no existe. */
    private fun profileFor(name: String): Profile {
        val clean = name.trim()
        val existing = _state.value.profiles.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (existing != null) return existing
        val profile = Profile(UUID.randomUUID().toString(), clean)
        val profiles = _state.value.profiles + profile
        repo.saveProfiles(profiles)
        _state.update { it.copy(profiles = profiles) }
        return profile
    }

    fun addProfile(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || _state.value.profiles.any { it.name.equals(clean, ignoreCase = true) }) return false
        profileFor(clean)
        return true
    }

    fun renameProfile(id: String, name: String): Boolean {
        val clean = name.trim()
        val profiles = _state.value.profiles
        if (clean.isEmpty() || profiles.any { it.id != id && it.name.equals(clean, ignoreCase = true) }) return false
        val updated = profiles.map { if (it.id == id) it.copy(name = clean) else it }
        repo.saveProfiles(updated)
        _state.update { it.copy(profiles = updated) }
        return true
    }

    fun deleteProfile(id: String) {
        val updated = _state.value.profiles.filterNot { it.id == id }
        repo.saveProfiles(updated)
        _state.update { it.copy(profiles = updated) }
    }

    // --- Navegación ---

    fun goHome() = _state.update { it.copy(screen = Screen.HOME, viewing = null) }

    fun openSetup() = _state.update { it.copy(screen = Screen.SETUP, viewing = null) }

    fun openLeaderboard() = _state.update { it.copy(screen = Screen.LEADERBOARD, viewing = null) }

    fun openRoster() = _state.update { it.copy(screen = Screen.ROSTER, viewing = null) }

    fun openManual() = _state.update { it.copy(screen = Screen.MANUAL, viewing = null) }

    fun continueGame() {
        val game = _state.value.current ?: return
        _state.update { it.copy(screen = screenFor(game.phase), viewing = null) }
    }

    fun back() {
        val s = _state.value
        when (s.screen) {
            Screen.HOME -> Unit
            Screen.SETUP, Screen.GAME, Screen.LEADERBOARD, Screen.ROSTER -> goHome()
            Screen.MANUAL -> openLeaderboard()
            Screen.COUNT -> backToGame()
            Screen.RESULTS -> if (s.viewing != null) goHome() else backToCount()
        }
    }

    // --- Partida ---

    fun startGame(buyInCents: Long, chipsPerBuyIn: Long, bankTotalChips: Long, names: List<String>) {
        val players = names.mapIndexed { i, n -> Player(i + 1, n.trim(), profileFor(n).id) }
        val game = Game.new(now(), buyInCents, chipsPerBuyIn, bankTotalChips, players)
        repo.saveCurrent(game)
        _state.update { it.copy(current = game, screen = Screen.GAME) }
    }

    /** Jugador que se incorpora tarde. Las fichas salen de la banca y, si no llega, de [sellerId]. */
    fun addPlayer(name: String, sellerId: Int?): Boolean {
        val game = _state.value.current ?: return false
        val clean = name.trim()
        if (clean.isEmpty() || game.players.any { it.name.equals(clean, ignoreCase = true) }) return false
        val player = Player(game.nextPlayerId(), clean, profileFor(clean).id)
        val updated = game.copy(players = game.players + player)
            .withPurchase(player.id, game.buyInCents, sellerId, MovementType.ENTRY, now())
            ?: return false
        updateCurrent { updated }
        return true
    }

    fun renamePlayer(playerId: Int, name: String): Boolean {
        val game = _state.value.current ?: return false
        val clean = name.trim()
        if (clean.isEmpty() || game.players.any { it.id != playerId && it.name.equals(clean, ignoreCase = true) }) return false
        val profileId = profileFor(clean).id
        updateCurrent { g -> g.copy(players = g.players.map { if (it.id == playerId) it.copy(name = clean, profileId = profileId) else it }) }
        return true
    }

    fun removePlayer(playerId: Int) = updateCurrent { g ->
        if (!g.canRemove(playerId) || g.players.size <= 2) return@updateCurrent null
        g.copy(
            players = g.players.filterNot { it.id == playerId },
            movements = g.movements.filterNot { it.playerId == playerId },
            finalChips = g.finalChips - playerId,
        )
    }

    fun rebuy(playerId: Int, cents: Long, sellerId: Int?) = updateCurrent { game ->
        game.withPurchase(playerId, cents, sellerId, MovementType.REBUY, now())
    }

    fun removeRebuy(id: Long) = updateCurrent { g ->
        g.copy(movements = g.movements.filterNot { it.id == id && it.type == MovementType.REBUY })
    }

    fun startCount() = updateCurrent(Screen.COUNT) { it.copy(phase = Phase.COUNTING) }

    fun backToGame() = updateCurrent(Screen.GAME) { it.copy(phase = Phase.PLAYING) }

    fun setChips(playerId: Int, chips: Long?) = updateCurrent { g ->
        g.copy(finalChips = if (chips == null) g.finalChips - playerId else g.finalChips + (playerId to chips))
    }

    fun calculate() = updateCurrent(Screen.RESULTS) { it.copy(phase = Phase.FINISHED) }

    fun backToCount() = updateCurrent(Screen.COUNT) { it.copy(phase = Phase.COUNTING) }

    private fun archive(game: Game) {
        // Se vinculan todos los jugadores a un jugador habitual para la clasificación global.
        val linked = game.copy(players = game.players.map { p -> p.copy(profileId = p.profileId ?: profileFor(p.name).id) })
        val history = (listOf(linked) + _state.value.history).sortedByDescending { it.createdAt }
        repo.saveHistory(history)
        _state.update { it.copy(history = history) }
    }

    fun closeGame() {
        val game = _state.value.current ?: return
        archive(game.copy(phase = Phase.FINISHED, finishedAt = now()))
        repo.saveCurrent(null)
        _state.update { it.copy(current = null, screen = Screen.HOME) }
    }

    fun discardGame() {
        repo.saveCurrent(null)
        _state.update { it.copy(current = null, screen = Screen.HOME) }
    }

    /** Registra una partida jugada sin la app, solo con el resultado de cada jugador. */
    fun saveManualGame(playedAt: Long, results: List<Pair<String, Long>>) {
        val players = results.mapIndexed { i, (name, _) -> Player(i + 1, name.trim()) }
        val nets = results.mapIndexed { i, (_, cents) -> (i + 1) to cents }.toMap()
        archive(Game.manual(now(), playedAt, players, nets))
        _state.update { it.copy(screen = Screen.LEADERBOARD) }
    }

    // --- Historial ---

    fun openHistory(game: Game) = _state.update { it.copy(viewing = game, screen = Screen.RESULTS) }

    fun deleteHistory(game: Game) {
        val history = _state.value.history.filterNot { it.id == game.id }
        repo.saveHistory(history)
        _state.update { it.copy(history = history) }
    }
}
