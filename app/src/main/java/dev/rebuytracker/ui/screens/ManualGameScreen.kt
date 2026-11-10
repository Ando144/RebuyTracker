@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.rebuytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rebuytracker.data.Profile
import dev.rebuytracker.ui.components.BottomActionBar
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PrimaryAction
import dev.rebuytracker.ui.components.ScreenTitle
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.appBarColors
import dev.rebuytracker.ui.components.moneyColor
import dev.rebuytracker.ui.components.tabular
import dev.rebuytracker.util.formatDay
import dev.rebuytracker.util.formatSignedEuros
import dev.rebuytracker.util.parseCents
import dev.rebuytracker.util.sanitizeMoney

private data class ManualRow(val name: String, val won: Boolean = true, val amount: String = "") {
    val cents: Long get() = (parseCents(amount) ?: 0L).let { if (won) it else -it }
}

/** Registrar una partida jugada sin la app: solo quién ganó o perdió cuánto. */
@Composable
fun ManualGameScreen(
    profiles: List<Profile>,
    onBack: () -> Unit,
    onSave: (playedAt: Long, results: List<Pair<String, Long>>) -> Unit,
) {
    val rows = remember { mutableStateListOf<ManualRow>() }
    var newName by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val playedAt = dateState.selectedDateMillis ?: System.currentTimeMillis()

    val sum = rows.sumOf { it.cents }
    val filled = rows.isNotEmpty() && rows.all { parseCents(it.amount) != null }
    val canSave = rows.size >= 2 && filled && sum == 0L

    fun addName(name: String) {
        val n = name.trim()
        if (n.isNotEmpty() && rows.none { it.name.equals(n, ignoreCase = true) }) rows.add(ManualRow(n))
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Partida pasada", "Solo resultados, para la clasificación") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                colors = appBarColors(),
            )
        },
        bottomBar = {
            BottomActionBar {
                PrimaryAction("Guardar partida", enabled = canSave, onClick = {
                    onSave(playedAt, rows.map { it.name to it.cents })
                })
                Hint(
                    when {
                        rows.size < 2 -> "Añade al menos 2 jugadores"
                        !filled -> "Pon el resultado de cada jugador (0 si quedó igual)"
                        sum != 0L -> "Las ganancias y pérdidas deben sumar 0 € (ahora: ${formatSignedEuros(sum)})"
                        else -> "Todo cuadra"
                    },
                    color = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fecha: ${formatDay(playedAt)}")
                }
            }

            item { SectionLabel("Jugadores") }
            item {
                Panel {
                    if (profiles.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            profiles.sortedBy { it.name.lowercase() }.forEach { p ->
                                val selected = rows.any { it.name.equals(p.name, ignoreCase = true) }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (selected) rows.removeAll { it.name.equals(p.name, ignoreCase = true) } else addName(p.name)
                                    },
                                    label = { Text(p.name) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(24) },
                            label = { Text("Otro jugador") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addName(newName); newName = "" }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { addName(newName); newName = "" }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Añadir")
                        }
                    }
                }
            }

            if (rows.isNotEmpty()) {
                item { SectionLabel("Resultado de cada uno") }
                item {
                    Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                        rows.forEachIndexed { index, row ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text(
                                        formatSignedEuros(row.cents),
                                        style = MaterialTheme.typography.titleSmall.tabular(),
                                        color = moneyColor(row.cents),
                                    )
                                    IconButton(onClick = { rows.removeAt(index) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                                        SegmentedButton(
                                            selected = row.won,
                                            onClick = { rows[index] = row.copy(won = true) },
                                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                                        ) { Text("Ganó") }
                                        SegmentedButton(
                                            selected = !row.won,
                                            onClick = { rows[index] = row.copy(won = false) },
                                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                                        ) { Text("Perdió") }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    OutlinedTextField(
                                        value = row.amount,
                                        onValueChange = { rows[index] = row.copy(amount = sanitizeMoney(it)) },
                                        placeholder = { Text("0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                                        suffix = { Text("€") },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.titleMedium.tabular().copy(textAlign = TextAlign.End),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                        modifier = Modifier.width(120.dp),
                                    )
                                }
                            }
                            if (index < rows.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { showDate = false }) { Text("Aceptar") } },
        ) {
            DatePicker(state = dateState)
        }
    }
}
