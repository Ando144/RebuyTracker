@file:OptIn(ExperimentalMaterial3Api::class)

package dev.rebuytracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rebuytracker.data.Game
import dev.rebuytracker.ui.components.BottomActionBar
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PlayerAvatar
import dev.rebuytracker.ui.components.PrimaryAction
import dev.rebuytracker.ui.components.ScreenTitle
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.Stat
import dev.rebuytracker.ui.components.appBarColors
import dev.rebuytracker.ui.components.tabular
import dev.rebuytracker.ui.theme.Gain
import dev.rebuytracker.ui.theme.Loss
import dev.rebuytracker.util.formatChips
import dev.rebuytracker.util.formatEuros
import dev.rebuytracker.util.sanitizeChips
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun CountScreen(
    game: Game,
    onBack: () -> Unit,
    onChipsChange: (playerId: Int, chips: Long?) -> Unit,
    onCalculate: () -> Unit,
) {
    val texts = remember(game.id) {
        mutableStateMapOf<Int, String>().apply {
            game.players.forEach { p -> put(p.id, game.finalChips[p.id]?.toString() ?: "") }
        }
    }
    var confirmMismatch by remember { mutableStateOf(false) }

    val expected = game.chipsDistributed
    val counted = game.countedChips
    val diff = counted - expected
    val pending = game.players.count { game.finalChips[it.id] == null }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Recuento de fichas", "Cada jugador dice cuántas fichas tiene") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a la partida") }
                },
                colors = appBarColors(),
            )
        },
        bottomBar = {
            BottomActionBar {
                PrimaryAction(
                    "Calcular cuentas",
                    enabled = counted > 0,
                    onClick = { if (diff != 0L) confirmMismatch = true else onCalculate() },
                )
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
                        Stat("En la mesa", formatChips(expected), Modifier.weight(1f))
                        Stat("Contadas", formatChips(counted), Modifier.weight(1f))
                        Stat(
                            "Diferencia",
                            when {
                                diff == 0L -> "Cuadra"
                                diff > 0 -> "+" + formatChips(diff)
                                else -> "−" + formatChips(abs(diff))
                            },
                            Modifier.weight(1f),
                            valueColor = if (diff == 0L) Gain else Loss,
                        )
                    }
                    if (pending > 0) {
                        Spacer(Modifier.padding(top = 8.dp))
                        Text(
                            "Faltan $pending por apuntar (cuentan como 0 fichas).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionLabel("Fichas de cada jugador") }
            item {
                Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                    game.players.forEachIndexed { index, player ->
                        val chips = game.finalChips[player.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerAvatar(player.name)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(player.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    chips?.let { "≈ ${formatEuros(game.centsForChips(it).roundToLong())}" } ?: "Sin apuntar",
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedTextField(
                                value = texts[player.id] ?: "",
                                onValueChange = { v ->
                                    val clean = sanitizeChips(v)
                                    texts[player.id] = clean
                                    onChipsChange(player.id, clean.toLongOrNull())
                                },
                                placeholder = { Text("0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleMedium.tabular().copy(textAlign = TextAlign.End),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = if (index < game.players.lastIndex) ImeAction.Next else ImeAction.Done,
                                ),
                                modifier = Modifier.width(132.dp),
                            )
                        }
                        if (index < game.players.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            item { Hint("Si alguien se ha quedado sin fichas, déjalo en blanco o pon 0. Puedes volver atrás para añadir rebuys olvidados.") }
        }
    }

    if (confirmMismatch) {
        AlertDialog(
            onDismissRequest = { confirmMismatch = false },
            title = { Text("El recuento no cuadra") },
            text = {
                Text(
                    (if (diff > 0) "Hay ${formatChips(diff)} fichas más de las que hay en juego. "
                    else "Faltan ${formatChips(-diff)} fichas respecto a las que hay en juego. ") +
                        "Revisa el recuento o calcula aplicando un ajuste proporcional al valor de las fichas para que las cuentas cuadren."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmMismatch = false; onCalculate() }) { Text("Calcular con ajuste") }
            },
            dismissButton = { TextButton(onClick = { confirmMismatch = false }) { Text("Revisar") } },
        )
    }
}
