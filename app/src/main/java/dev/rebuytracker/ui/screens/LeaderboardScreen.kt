@file:OptIn(ExperimentalMaterial3Api::class)

package dev.rebuytracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Profile
import dev.rebuytracker.logic.Leaderboard
import dev.rebuytracker.logic.LeaderboardEntry
import dev.rebuytracker.ui.components.BottomActionBar
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PlayerAvatar
import dev.rebuytracker.ui.components.ScreenTitle
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.Stat
import dev.rebuytracker.ui.components.appBarColors
import dev.rebuytracker.ui.components.moneyColor
import dev.rebuytracker.ui.components.tabular
import dev.rebuytracker.util.formatSignedEuros

@Composable
fun LeaderboardScreen(
    history: List<Game>,
    profiles: List<Profile>,
    onBack: () -> Unit,
    onAddManual: () -> Unit,
) {
    val board = remember(history, profiles) { Leaderboard.compute(history, profiles) }
    val best = board.maxByOrNull { it.bestCents }
    val context = LocalContext.current
    val share = {
        val text = buildString {
            appendLine("♠ Clasificación · ${history.size} partidas")
            appendLine()
            board.forEachIndexed { i, e ->
                appendLine("${i + 1}. ${e.name}: ${formatSignedEuros(e.netCents)} (${e.games} partidas)")
            }
        }.trimEnd()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir clasificación"))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Clasificación global", "${history.size} partidas cerradas") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inicio") }
                },
                actions = {
                    if (board.isNotEmpty()) {
                        IconButton(onClick = share) { Icon(Icons.Filled.Share, contentDescription = "Compartir") }
                    }
                },
                colors = appBarColors(),
            )
        },
        bottomBar = {
            BottomActionBar {
                OutlinedButton(onClick = onAddManual, modifier = Modifier.fillMaxWidth()) { Text("Añadir partida pasada") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            if (board.isEmpty()) {
                item {
                    Hint("Todavía no hay partidas cerradas. Al cerrar una partida, sus resultados se suman aquí. También puedes añadir partidas jugadas antes de usar la app.")
                }
                return@LazyColumn
            }

            item {
                Panel {
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Partidas", history.size.toString(), Modifier.weight(0.8f))
                        Stat("Jugadores", board.size.toString(), Modifier.weight(0.8f))
                        Stat(
                            "Mejor noche",
                            best?.let { formatSignedEuros(it.bestCents) } ?: "—",
                            Modifier.weight(1.2f),
                            sub = best?.name,
                            valueColor = best?.let { moneyColor(it.bestCents) } ?: MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            item { SectionLabel("Ranking") }
            item {
                Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                    board.forEachIndexed { index, entry ->
                        LeaderRow(index + 1, entry)
                        if (index < board.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 96.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            item { Hint("Suma de lo ganado y perdido en todas las partidas cerradas. Los jugadores se agrupan por su nombre de jugador habitual.") }
        }
    }
}

@Composable
private fun LeaderRow(position: Int, e: LeaderboardEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            position.toString(),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = if (position == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        PlayerAvatar(e.name)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${e.games} partidas · ${e.wins} en positivo · media ${formatSignedEuros(e.averageCents)}",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatSignedEuros(e.netCents),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = moneyColor(e.netCents),
            )
            Text(
                "mejor ${formatSignedEuros(e.bestCents)}",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
