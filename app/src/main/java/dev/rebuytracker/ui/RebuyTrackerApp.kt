package dev.rebuytracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rebuytracker.logic.Leaderboard
import dev.rebuytracker.ui.screens.CountScreen
import dev.rebuytracker.ui.screens.GameScreen
import dev.rebuytracker.ui.screens.HomeScreen
import dev.rebuytracker.ui.screens.LeaderboardScreen
import dev.rebuytracker.ui.screens.ManualGameScreen
import dev.rebuytracker.ui.screens.PublishScreen
import dev.rebuytracker.ui.screens.ResultsScreen
import dev.rebuytracker.ui.screens.RosterScreen
import dev.rebuytracker.ui.screens.SetupScreen

@Composable
fun RebuyTrackerApp(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.screen != Screen.HOME) { vm.back() }

    Crossfade(targetState = state.screen, label = "screen") { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                state = state,
                onNewGame = vm::openSetup,
                onContinue = vm::continueGame,
                onDiscardCurrent = vm::discardGame,
                onOpenHistory = vm::openHistory,
                onDeleteHistory = vm::deleteHistory,
                onLeaderboard = vm::openLeaderboard,
                onRoster = vm::openRoster,
                onPublish = vm::openPublish,
            )

            Screen.SETUP -> SetupScreen(profiles = state.profiles, onBack = vm::goHome, onStart = vm::startGame)

            Screen.GAME -> state.current?.let { game ->
                GameScreen(
                    game = game,
                    profiles = state.profiles,
                    onBack = vm::goHome,
                    onRebuy = vm::rebuy,
                    onRemoveRebuy = vm::removeRebuy,
                    onAddPlayer = vm::addPlayer,
                    onRenamePlayer = vm::renamePlayer,
                    onRemovePlayer = vm::removePlayer,
                    onFinish = vm::startCount,
                    onDiscard = vm::discardGame,
                )
            }

            Screen.COUNT -> state.current?.let { game ->
                CountScreen(
                    game = game,
                    onBack = vm::backToGame,
                    onChipsChange = vm::setChips,
                    onCalculate = vm::calculate,
                )
            }

            Screen.RESULTS -> (state.viewing ?: state.current)?.let { game ->
                val isHistory = state.viewing != null
                ResultsScreen(
                    game = game,
                    isHistory = isHistory,
                    onBack = if (isHistory) vm::goHome else vm::backToCount,
                    onClose = vm::closeGame,
                )
            }

            Screen.LEADERBOARD -> LeaderboardScreen(
                history = state.history,
                profiles = state.profiles,
                onBack = vm::goHome,
                onAddManual = vm::openManual,
            )

            Screen.ROSTER -> {
                val games = remember(state.history, state.profiles) {
                    Leaderboard.gamesByProfile(state.history, state.profiles)
                }
                RosterScreen(
                    profiles = state.profiles,
                    gamesByProfile = games,
                    onBack = vm::goHome,
                    onAdd = vm::addProfile,
                    onRename = vm::renameProfile,
                    onDelete = vm::deleteProfile,
                )
            }

            Screen.PUBLISH -> PublishScreen(
                config = state.publishConfig,
                status = state.publishStatus,
                gamesCount = state.history.size,
                onBack = vm::goHome,
                onSave = vm::savePublishConfig,
                onPublish = vm::publishNow,
            )

            Screen.MANUAL -> ManualGameScreen(
                profiles = state.profiles,
                onBack = vm::openLeaderboard,
                onSave = vm::saveManualGame,
            )
        }
    }
}
