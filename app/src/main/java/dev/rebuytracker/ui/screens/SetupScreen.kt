@file:OptIn(ExperimentalMaterial3Api::class)

package dev.rebuytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.rebuytracker.ui.components.BottomActionBar
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PlayerAvatar
import dev.rebuytracker.ui.components.PrimaryAction
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.appBarColors
import dev.rebuytracker.util.centsToInput
import dev.rebuytracker.util.formatChips
import dev.rebuytracker.util.formatEuros
import dev.rebuytracker.util.parseCents
import dev.rebuytracker.util.parseChips
import dev.rebuytracker.util.sanitizeChips
import dev.rebuytracker.util.sanitizeMoney

private val NamesSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

@Composable
fun SetupScreen(
    onBack: () -> Unit,
    onStart: (buyInCents: Long, chipsPerBuyIn: Long, bankTotalChips: Long, names: List<String>) -> Unit,
) {
    var buyInText by rememberSaveable { mutableStateOf("10") }
    var chipsText by rememberSaveable { mutableStateOf("1000") }
    var bankText by rememberSaveable { mutableStateOf("") }
    var newName by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    val players = rememberSaveable(saver = NamesSaver) { mutableStateListOf<String>() }

    val buyIn = parseCents(buyInText)?.takeIf { it > 0 }
    val chips = parseChips(chipsText)?.takeIf { it > 0 }
    val bank = parseChips(bankText)
    val needed = (chips ?: 0L) * players.size
    val bankError = bank != null && chips != null && bank < needed

    val blocker = when {
        buyIn == null -> "Indica el precio de la entrada"
        chips == null -> "Indica cuántas fichas da cada entrada"
        bank == null -> "Indica las fichas totales de la banca"
        players.size < 2 -> "Añade al menos 2 jugadores"
        bankError -> "La banca no tiene fichas suficientes para repartir"
        else -> null
    }

    fun addPlayer() {
        val name = newName.trim()
        nameError = when {
            name.isEmpty() -> null
            players.any { it.equals(name, ignoreCase = true) } -> "Ya está en la mesa"
            else -> {
                players.add(name)
                newName = ""
                null
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Nueva partida") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                colors = appBarColors(),
            )
        },
        bottomBar = {
            BottomActionBar {
                PrimaryAction(
                    "Empezar partida",
                    enabled = blocker == null,
                    onClick = { onStart(buyIn!!, chips!!, bank!!, players.toList()) },
                )
                if (blocker != null) Hint(blocker)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item { SectionLabel("Entrada y fichas") }
            item {
                Panel {
                    OutlinedTextField(
                        value = buyInText,
                        onValueChange = { buyInText = sanitizeMoney(it) },
                        label = { Text("Precio de la entrada") },
                        suffix = { Text("€") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500L, 1000L, 2000L).forEach { c ->
                            FilterChip(
                                selected = buyIn == c,
                                onClick = { buyInText = centsToInput(c) },
                                label = { Text(formatEuros(c)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = chipsText,
                        onValueChange = { chipsText = sanitizeChips(it) },
                        label = { Text("Fichas por entrada") },
                        suffix = { Text("fichas") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bankText,
                        onValueChange = { bankText = sanitizeChips(it) },
                        label = { Text("Fichas totales de la banca") },
                        suffix = { Text("fichas") },
                        singleLine = true,
                        isError = bankError,
                        supportingText = {
                            Text(
                                if (bankError) "Hacen falta al menos ${formatChips(needed)} fichas para las entradas"
                                else "Todas las fichas del maletín, incluidas las que se reparten ahora"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (buyIn != null && chips != null) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        val bankLine = if (bank != null && !bankError && players.isNotEmpty()) {
                            " · quedan ${formatChips(bank - needed)} en la banca"
                        } else ""
                        Text(
                            "${formatEuros(buyIn)} = ${formatChips(chips)} fichas$bankLine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionLabel("Jugadores · ${players.size}") }
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(24); nameError = null },
                            label = { Text("Nombre") },
                            singleLine = true,
                            isError = nameError != null,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { addPlayer() }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { addPlayer() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Añadir jugador")
                        }
                    }
                    nameError?.let { Hint(it, color = MaterialTheme.colorScheme.error) }
                    if (players.isEmpty()) {
                        Hint("Escribe un nombre y pulsa + (o Intro en el teclado).")
                    }
                    players.forEachIndexed { index, name ->
                        if (index == 0) Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            PlayerAvatar(name, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { players.removeAt(index) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Quitar a $name",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
