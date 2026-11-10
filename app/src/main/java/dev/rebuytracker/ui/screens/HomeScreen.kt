package dev.rebuytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Phase
import dev.rebuytracker.logic.Leaderboard
import dev.rebuytracker.logic.Settlement
import dev.rebuytracker.ui.UiState
import dev.rebuytracker.ui.components.BottomActionBar
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PrimaryAction
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.moneyColor
import dev.rebuytracker.ui.components.tabular
import dev.rebuytracker.util.formatDate
import dev.rebuytracker.util.formatEuros
import dev.rebuytracker.util.formatSignedEuros

@Composable
fun HomeScreen(
    state: UiState,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onDiscardCurrent: () -> Unit,
    onOpenHistory: (Game) -> Unit,
    onDeleteHistory: (Game) -> Unit,
    onLeaderboard: () -> Unit,
    onRoster: () -> Unit,
) {
    var confirmNew by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Game?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomActionBar {
                PrimaryAction("Nueva partida", onClick = { if (state.current != null) confirmNew = true else onNewGame() })
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text("♠  RebuyTracker", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Registra entradas y rebuys, cuenta fichas y salda con el mínimo de bizums.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.current?.let { game ->
                item { SectionLabel("Partida en curso") }
                item { CurrentGameCard(game, onContinue = onContinue, onDiscard = { confirmDiscard = true }) }
            }

            item { SectionLabel("General") }
            item {
                val leader = remember(state.history, state.profiles) {
                    Leaderboard.compute(state.history, state.profiles).firstOrNull()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Panel(modifier = Modifier.weight(1f), onClick = onLeaderboard) {
                        Text("Clasificación", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            leader?.let { "1º ${it.name} ${formatSignedEuros(it.netCents)}" } ?: "Sin partidas aún",
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = leader?.let { moneyColor(it.netCents) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Panel(modifier = Modifier.weight(1f), onClick = onRoster) {
                        Text("Jugadores", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${state.profiles.size} habituales",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionLabel("Historial") }
            if (state.history.isEmpty()) {
                item { Hint("Aún no hay partidas cerradas. Aparecerán aquí al terminar cada una.") }
            }
            items(state.history, key = { it.id }) { game ->
                HistoryCard(game, onClick = { onOpenHistory(game) }, onDelete = { toDelete = game })
            }
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Hay una partida en curso") },
            text = { Text("Si empiezas una nueva, la partida actual se descartará sin guardarse.") },
            confirmButton = {
                TextButton(onClick = { confirmNew = false; onDiscardCurrent(); onNewGame() }) { Text("Descartar y empezar") }
            },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancelar") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar la partida?") },
            text = { Text("Se perderán todos los movimientos registrados.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscardCurrent() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } },
        )
    }
    toDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar del historial?") },
            text = { Text("La partida del ${formatDate(game.createdAt)} se eliminará definitivamente.") },
            confirmButton = { TextButton(onClick = { toDelete = null; onDeleteHistory(game) }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CurrentGameCard(game: Game, onContinue: () -> Unit, onDiscard: () -> Unit) {
    val phase = when (game.phase) {
        Phase.PLAYING -> "Jugando"
        Phase.COUNTING -> "Contando fichas"
        Phase.FINISHED -> "Cuentas listas, sin cerrar"
    }
    Panel(onClick = onContinue) {
        Text(phase, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            game.players.joinToString(" · ") { it.name },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${formatEuros(game.tableValueCents)} en la mesa · ${game.rebuys.size} rebuys",
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onContinue) { Text("Continuar") }
            TextButton(onClick = onDiscard) { Text("Descartar") }
        }
    }
}

@Composable
private fun HistoryCard(game: Game, onClick: () -> Unit, onDelete: () -> Unit) {
    val top = Settlement.compute(game).players.firstOrNull()
    Panel(
        modifier = Modifier.padding(bottom = 8.dp),
        onClick = onClick,
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(game.createdAt), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (game.isManual) "${game.players.size} jugadores · registrada a mano" else "${game.players.size} jugadores · ${formatEuros(game.tableValueCents)} en la mesa",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (top != null && top.netCents > 0) {
                    Text(
                        "Mejor: ${top.player.name} ${formatSignedEuros(top.netCents)}",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = moneyColor(top.netCents),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
