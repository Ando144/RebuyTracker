package dev.rebuytracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rebuytracker.ui.screens.CountScreen
import dev.rebuytracker.ui.screens.GameScreen
import dev.rebuytracker.ui.screens.HomeScreen
import dev.rebuytracker.ui.screens.ResultsScreen
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
            )

            Screen.SETUP -> SetupScreen(onBack = vm::goHome, onStart = vm::startGame)

            Screen.GAME -> state.current?.let { game ->
                GameScreen(
                    game = game,
                    onBack = vm::goHome,
                    onRebuy = vm::rebuy,
                    onRemoveRebuy = vm::removeRebuy,
                    onAddPlayer = vm::addPlayer,
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
        }
    }
}
