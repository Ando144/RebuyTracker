@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.rebuytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rebuytracker.data.Game
import dev.rebuytracker.data.Player
import dev.rebuytracker.data.Rebuy
import dev.rebuytracker.ui.components.BottomActionBar
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.KeyValueRow
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PlayerAvatar
import dev.rebuytracker.ui.components.PrimaryAction
import dev.rebuytracker.ui.components.ScreenTitle
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.Stat
import dev.rebuytracker.ui.components.appBarColors
import dev.rebuytracker.ui.components.tabular
import dev.rebuytracker.util.centsToInput
import dev.rebuytracker.util.formatChips
import dev.rebuytracker.util.formatDate
import dev.rebuytracker.util.formatEuros
import dev.rebuytracker.util.formatTime
import dev.rebuytracker.util.parseCents
import dev.rebuytracker.util.sanitizeMoney
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@Composable
fun GameScreen(
    game: Game,
    onBack: () -> Unit,
    onRebuy: (playerId: Int, cents: Long, sellerId: Int?) -> Unit,
    onRemoveRebuy: (Long) -> Unit,
    onAddPlayer: (String) -> Boolean,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    var rebuyFor by remember { mutableStateOf<Player?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAddPlayer by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var rebuyToRemove by remember { mutableStateOf<Rebuy?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Partida", formatDate(game.createdAt)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inicio") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Añadir jugador") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = { menuOpen = false; showAddPlayer = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Descartar partida") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; confirmDiscard = true },
                            )
                        }
                    }
                },
                colors = appBarColors(),
            )
        },
        bottomBar = {
            BottomActionBar { PrimaryAction("Terminar y contar fichas", onClick = onFinish) }
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
            item { SummaryPanel(game) }

            item { SectionLabel("Jugadores · ${game.players.size}") }
            item {
                Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                    game.players.forEachIndexed { index, player ->
                        PlayerRow(game, player, onRebuy = { rebuyFor = player })
                        if (index < game.players.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            if (game.rebuys.isNotEmpty()) {
                item { SectionLabel("Movimientos") }
                item {
                    Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                        val list = game.rebuys.asReversed()
                        list.forEachIndexed { index, rebuy ->
                            MovementRow(game, rebuy, onRemove = { rebuyToRemove = rebuy })
                            if (index < list.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    rebuyFor?.let { player ->
        RebuySheet(
            game = game,
            player = player,
            onDismiss = { rebuyFor = null },
            onConfirm = { cents, sellerId -> onRebuy(player.id, cents, sellerId) },
        )
    }

    if (showAddPlayer) {
        AddPlayerDialog(game, onDismiss = { showAddPlayer = false }, onAdd = onAddPlayer)
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar la partida?") },
            text = { Text("Se perderán todos los movimientos registrados.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } },
        )
    }

    rebuyToRemove?.let { rebuy ->
        AlertDialog(
            onDismissRequest = { rebuyToRemove = null },
            title = { Text("¿Eliminar este rebuy?") },
            text = {
                Text("${game.player(rebuy.playerId)?.name ?: "?"} · ${formatEuros(rebuy.cents)}. Las fichas vuelven a contarse como si no se hubieran entregado.")
            },
            confirmButton = { TextButton(onClick = { rebuyToRemove = null; onRemoveRebuy(rebuy.id) }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { rebuyToRemove = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SummaryPanel(game: Game) {
    val bank = game.bankRemaining.coerceAtLeast(0)
    Panel {
        Row(Modifier.fillMaxWidth()) {
            Stat(
                "En la mesa",
                formatEuros(game.tableValueCents),
                Modifier.weight(1.2f),
                sub = "${formatChips(game.chipsDistributed)} fichas",
            )
            Stat(
                "Banca",
                formatChips(bank),
                Modifier.weight(1f),
                sub = formatEuros(game.centsForChips(bank).roundToLong()),
            )
            Stat(
                "Rebuys",
                game.rebuys.size.toString(),
                Modifier.weight(0.8f),
                sub = formatEuros(game.rebuys.sumOf { it.cents }),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Entrada ${formatEuros(game.buyInCents)} = ${formatChips(game.chipsPerBuyIn)} fichas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerRow(game: Game, player: Player, onRebuy: () -> Unit) {
    val rebuys = game.rebuysOf(player.id).size
    val sold = game.soldCents(player.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(player.name)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append("Ha puesto ")
                    append(formatEuros(game.investedCents(player.id)))
                    if (rebuys > 0) append(" · $rebuys rebuy${if (rebuys > 1) "s" else ""}")
                },
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sold > 0) {
                Text(
                    "Ha cedido fichas por ${formatEuros(sold)}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        FilledTonalButton(onClick = onRebuy, contentPadding = PaddingValues(horizontal = 18.dp)) { Text("Rebuy") }
    }
}

@Composable
private fun MovementRow(game: Game, rebuy: Rebuy, onRemove: () -> Unit) {
    val buyer = game.player(rebuy.playerId)?.name ?: "?"
    val seller = game.player(rebuy.sellerId)?.name ?: "?"
    val origin = when {
        rebuy.chipsFromSeller == 0L -> "Banca · ${formatChips(rebuy.chipsFromBank)} fichas"
        rebuy.chipsFromBank == 0L -> "$seller · ${formatChips(rebuy.chipsFromSeller)} fichas"
        else -> "Banca ${formatChips(rebuy.chipsFromBank)} + $seller ${formatChips(rebuy.chipsFromSeller)} fichas"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$buyer · rebuy ${formatEuros(rebuy.cents)}", style = MaterialTheme.typography.bodyLarge.tabular())
            Text(
                "${formatTime(rebuy.timestamp)} · $origin",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RebuySheet(
    game: Game,
    player: Player,
    onDismiss: () -> Unit,
    onConfirm: (cents: Long, sellerId: Int?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val lastAmount = game.rebuys.lastOrNull()?.cents ?: 1000L
    var amountText by remember { mutableStateOf(centsToInput(lastAmount)) }
    var sellerId by remember { mutableStateOf<Int?>(null) }

    val cents = parseCents(amountText)?.takeIf { it > 0 }
    val split = cents?.let { game.splitRebuy(it) }
    val needsSeller = split != null && split.fromSeller > 0
    val canConfirm = split != null && split.chips > 0 && (!needsSeller || sellerId != null)
    val bank = game.bankRemaining.coerceAtLeast(0)

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text("Rebuy de ${player.name}", style = MaterialTheme.typography.titleLarge)
            Text(
                "En la banca quedan ${formatChips(bank)} fichas (${formatEuros(game.centsForChips(bank).roundToLong())})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500L, 1000L, 2000L).forEach { c ->
                    FilterChip(
                        selected = cents == c,
                        onClick = { amountText = centsToInput(c) },
                        label = { Text(formatEuros(c)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = sanitizeMoney(it) },
                label = { Text("Importe") },
                suffix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            if (split != null && split.chips > 0) {
                Spacer(Modifier.height(16.dp))
                Panel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    KeyValueRow("Fichas a entregar", formatChips(split.chips))
                    if (split.fromBank > 0) {
                        KeyValueRow("De la banca", "${formatChips(split.fromBank)} · ${formatEuros(split.centsToBank)}")
                    }
                    if (split.fromSeller > 0) {
                        KeyValueRow(
                            "De otro jugador",
                            "${formatChips(split.fromSeller)} · ${formatEuros(split.centsToSeller)}",
                            valueColor = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            if (needsSeller) {
                Spacer(Modifier.height(16.dp))
                Text(
                    if (bank == 0L) "La banca está vacía. ¿Quién le pasa las fichas?"
                    else "La banca no llega. ¿Quién pone las fichas que faltan?",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    game.players.filter { it.id != player.id }.forEach { other ->
                        FilterChip(
                            selected = sellerId == other.id,
                            onClick = { sellerId = other.id },
                            label = { Text(other.name) },
                        )
                    }
                }
                val sellerName = game.player(sellerId)?.name
                if (sellerName != null && split != null) {
                    Hint("${player.name} le debe ${formatEuros(split.centsToSeller)} a $sellerName. Se compensa en los bizums del final.")
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryAction(
                text = cents?.let { "Confirmar rebuy · ${formatEuros(it)}" } ?: "Confirmar rebuy",
                enabled = canConfirm,
                onClick = {
                    onConfirm(cents!!, if (needsSeller) sellerId else null)
                    close()
                },
            )
        }
    }
}

@Composable
private fun AddPlayerDialog(game: Game, onDismiss: () -> Unit, onAdd: (String) -> Boolean) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val err = error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir jugador") },
        text = {
            Column {
                Text(
                    "Entra con una entrada de ${formatEuros(game.buyInCents)} (${formatChips(game.chipsPerBuyIn)} fichas de la banca).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24); error = null },
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = err != null,
                    supportingText = if (err != null) { { Text(err) } } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                val problem = when {
                    n.isEmpty() -> "Escribe un nombre"
                    game.players.any { it.name.equals(n, ignoreCase = true) } -> "Ya hay un jugador con ese nombre"
                    game.bankRemaining < game.chipsPerBuyIn -> "La banca no tiene fichas para otra entrada"
                    else -> null
                }
                error = problem
                if (problem == null && onAdd(n)) onDismiss()
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
