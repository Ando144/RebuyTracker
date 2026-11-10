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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rebuytracker.data.Game
import dev.rebuytracker.logic.PlayerResult
import dev.rebuytracker.logic.Settlement
import dev.rebuytracker.logic.SettlementResult
import dev.rebuytracker.logic.Transfer
import dev.rebuytracker.ui.components.BottomActionBar
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PlayerAvatar
import dev.rebuytracker.ui.components.PrimaryAction
import dev.rebuytracker.ui.components.ScreenTitle
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.Stat
import dev.rebuytracker.ui.components.appBarColors
import dev.rebuytracker.ui.components.moneyColor
import dev.rebuytracker.ui.components.tabular
import dev.rebuytracker.util.formatChips
import dev.rebuytracker.util.formatDate
import dev.rebuytracker.util.formatDay
import dev.rebuytracker.util.formatEuros
import dev.rebuytracker.util.formatSignedEuros

@Composable
fun ResultsScreen(
    game: Game,
    isHistory: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val result = remember(game) { Settlement.compute(game) }
    val context = LocalContext.current
    val share = {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildShareText(game, result))
        }
        context.startActivity(Intent.createChooser(intent, "Compartir cuentas"))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Resultados", formatDate(game.createdAt)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (isHistory) "Inicio" else "Volver al recuento")
                    }
                },
                actions = {
                    IconButton(onClick = share) { Icon(Icons.Filled.Share, contentDescription = "Compartir") }
                },
                colors = appBarColors(),
            )
        },
        bottomBar = {
            BottomActionBar {
                if (isHistory) {
                    OutlinedButton(onClick = share, modifier = Modifier.fillMaxWidth()) { Text("Compartir en el grupo") }
                } else {
                    PrimaryAction("Cerrar partida y guardar", onClick = onClose)
                    TextButton(onClick = onBack) { Text("Corregir recuento") }
                }
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
            item {
                Panel {
                    Row(Modifier.fillMaxWidth()) {
                        Stat("En la mesa", formatEuros(game.tableValueCents), Modifier.weight(1.2f))
                        Stat("Rebuys", game.rebuys.size.toString(), Modifier.weight(0.8f))
                        Stat("Bizums", result.transfers.size.toString(), Modifier.weight(0.8f))
                    }
                }
            }

            if (result.adjusted) {
                item {
                    Panel(modifier = Modifier.padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Recuento ajustado: se contaron ${formatChips(result.countedChips)} fichas y había ${formatChips(result.expectedChips)} en juego. " +
                                    "El valor de las fichas se ha escalado para que las cuentas cuadren.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Clasificación") }
            item {
                Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                    result.players.forEachIndexed { index, r ->
                        RankRow(index + 1, r)
                        if (index < result.players.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            item { SectionLabel("Bizums") }
            item {
                Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                    if (result.transfers.isEmpty()) {
                        Text(
                            "Todos quedan a cero. No hace falta ningún bizum.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    result.transfers.forEachIndexed { index, t ->
                        TransferRow(t)
                        if (index < result.transfers.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            item {
                Hint("Rebuys entre jugadores incluidos: lo que se debían durante la partida ya está compensado aquí.")
            }
        }
    }
}

@Composable
private fun RankRow(position: Int, r: PlayerResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(r.player.name)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$position. ${r.player.name}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Puso ${formatEuros(r.investedCents)} · ${formatChips(r.finalChips)} fichas (${formatEuros(r.finalValueCents)})",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (r.soldCents > 0) {
                Text(
                    "+ ${formatEuros(r.soldCents)} por fichas cedidas",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatSignedEuros(r.netCents),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = moneyColor(r.netCents),
        )
    }
}

@Composable
private fun TransferRow(t: Transfer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t.from.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "paga a",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(18.dp),
        )
        Text(t.to.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(formatEuros(t.cents), style = MaterialTheme.typography.titleMedium.tabular(), color = MaterialTheme.colorScheme.primary)
    }
}

private fun buildShareText(game: Game, r: SettlementResult): String = buildString {
    appendLine("♠ Partida del ${formatDay(game.createdAt)}")
    appendLine()
    appendLine("RESULTADOS")
    r.players.forEach { appendLine("${it.player.name}: ${formatSignedEuros(it.netCents)}") }
    appendLine()
    appendLine("BIZUMS")
    if (r.transfers.isEmpty()) appendLine("Nadie debe nada.")
    r.transfers.forEach { appendLine("${it.from.name} → ${it.to.name}: ${formatEuros(it.cents)}") }
}.trimEnd()
