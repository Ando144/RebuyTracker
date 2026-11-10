@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.rebuytracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.SheetState
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
import dev.rebuytracker.data.Movement
import dev.rebuytracker.data.MovementType
import dev.rebuytracker.data.Player
import dev.rebuytracker.data.Profile
import dev.rebuytracker.data.PurchaseSplit
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
import dev.rebuytracker.util.chipsPerEuro
import dev.rebuytracker.util.formatChipValue
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
    profiles: List<Profile>,
    onBack: () -> Unit,
    onRebuy: (playerId: Int, cents: Long, sellerId: Int?) -> Unit,
    onRemoveRebuy: (Long) -> Unit,
    onAddPlayer: (name: String, sellerId: Int?) -> Boolean,
    onRenamePlayer: (playerId: Int, name: String) -> Boolean,
    onRemovePlayer: (playerId: Int) -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    var rebuyFor by remember { mutableStateOf<Player?>(null) }
    var editing by remember { mutableStateOf<Player?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAddPlayer by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var rebuyToRemove by remember { mutableStateOf<Movement?>(null) }

    // Las entradas iniciales no se listan como movimientos; las tardías sí.
    val log = game.movements.filter { it.type == MovementType.REBUY || it.timestamp != game.createdAt }.asReversed()

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
                    game.players.forEach { player ->
                        PlayerRow(game, player, onRebuy = { rebuyFor = player }, onEdit = { editing = player })
                        HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddPlayer = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp).padding(8.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Añadir jugador", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Hint("Toca un jugador para cambiarle el nombre o quitarlo de la partida.") }

            if (log.isNotEmpty()) {
                item { SectionLabel("Movimientos · ${log.size}") }
                item {
                    Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                        log.forEachIndexed { index, movement ->
                            MovementRow(game, movement, onRemove = { rebuyToRemove = movement })
                            if (index < log.lastIndex) {
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
        AddPlayerSheet(game, profiles, onDismiss = { showAddPlayer = false }, onAdd = onAddPlayer)
    }

    editing?.let { player ->
        EditPlayerDialog(
            game = game,
            player = player,
            onDismiss = { editing = null },
            onRename = { name -> onRenamePlayer(player.id, name) },
            onRemove = { onRemovePlayer(player.id); editing = null },
        )
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
    val perEuro = chipsPerEuro(game.centsPerChip)
    Panel {
        Row(Modifier.fillMaxWidth()) {
            Stat(
                "En la mesa",
                formatEuros(game.tableValueCents),
                Modifier.weight(1.15f),
                sub = "${formatChips(game.chipsDistributed)} fichas",
            )
            Stat(
                "Banca",
                formatChips(bank),
                Modifier.weight(1f),
                sub = formatEuros(game.centsForChips(bank).roundToLong()),
            )
            Stat(
                "Valor ficha",
                formatChipValue(game.centsPerChip),
                Modifier.weight(1f),
                sub = perEuro?.let { "1 € = ${formatChips(it)} fichas" },
                valueColor = MaterialTheme.colorScheme.secondary,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Entrada ${formatEuros(game.buyInCents)} = ${formatChips(game.chipsPerBuyIn)} fichas · ${game.rebuys.size} rebuys (${formatEuros(game.rebuys.sumOf { it.cents })})",
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerRow(game: Game, player: Player, onRebuy: () -> Unit, onEdit: () -> Unit) {
    val rebuys = game.rebuysOf(player.id).size
    val sold = game.soldCents(player.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
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
private fun MovementRow(game: Game, movement: Movement, onRemove: () -> Unit) {
    val buyer = game.player(movement.playerId)?.name ?: "?"
    val seller = game.player(movement.sellerId)?.name ?: "?"
    val kind = if (movement.type == MovementType.ENTRY) "entrada" else "rebuy"
    val origin = when {
        movement.chipsFromSeller == 0L -> "Banca · ${formatChips(movement.chipsFromBank)} fichas"
        movement.chipsFromBank == 0L -> "$seller · ${formatChips(movement.chipsFromSeller)} fichas"
        else -> "Banca ${formatChips(movement.chipsFromBank)} + $seller ${formatChips(movement.chipsFromSeller)} fichas"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = if (movement.type == MovementType.ENTRY) 6.dp else 0.dp)) {
            Text("$buyer · $kind ${formatEuros(movement.cents)}", style = MaterialTheme.typography.bodyLarge.tabular())
            Text(
                "${formatTime(movement.timestamp)} · $origin",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (movement.type == MovementType.REBUY) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Desglose de una compra de fichas y selección del jugador que cede fichas si la banca no llega. */
@Composable
private fun ColumnScope.PurchaseSource(
    game: Game,
    split: PurchaseSplit?,
    buyerName: String,
    buyerId: Int?,
    sellerId: Int?,
    onSellerChange: (Int) -> Unit,
) {
    if (split == null || split.chips <= 0) return
    val bank = game.bankRemaining.coerceAtLeast(0)
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
    if (split.fromSeller > 0) {
        Spacer(Modifier.height(16.dp))
        Text(
            if (bank == 0L) "La banca está vacía. ¿Quién le pasa las fichas?"
            else "La banca no llega. ¿Quién pone las fichas que faltan?",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            game.players.filter { it.id != buyerId }.forEach { other ->
                FilterChip(
                    selected = sellerId == other.id,
                    onClick = { onSellerChange(other.id) },
                    label = { Text(other.name) },
                )
            }
        }
        val sellerName = game.player(sellerId)?.name
        if (sellerName != null) {
            Hint("${buyerName.ifBlank { "El jugador" }} le debe ${formatEuros(split.centsToSeller)} a $sellerName. Se compensa en los bizums del final.")
        }
    }
}

@Composable
private fun PurchaseSheetFrame(sheetState: SheetState, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            content = content,
        )
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
    val lastAmount = game.rebuys.lastOrNull()?.cents ?: game.buyInCents
    var amountText by remember { mutableStateOf(centsToInput(lastAmount)) }
    var sellerId by remember { mutableStateOf<Int?>(null) }

    val cents = parseCents(amountText)?.takeIf { it > 0 }
    val split = cents?.let { game.splitPurchase(it) }
    val needsSeller = split != null && split.fromSeller > 0
    val canConfirm = split != null && split.chips > 0 && (!needsSeller || sellerId != null)
    val bank = game.bankRemaining.coerceAtLeast(0)

    PurchaseSheetFrame(sheetState, onDismiss) {
        Text("Rebuy de ${player.name}", style = MaterialTheme.typography.titleLarge)
        Text(
            "Banca: ${formatChips(bank)} fichas (${formatEuros(game.centsForChips(bank).roundToLong())}) · 1 ficha = ${formatChipValue(game.centsPerChip)}",
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

        PurchaseSource(game, split, player.name, player.id, sellerId, onSellerChange = { sellerId = it })

        Spacer(Modifier.height(20.dp))
        PrimaryAction(
            text = cents?.let { "Confirmar rebuy · ${formatEuros(it)}" } ?: "Confirmar rebuy",
            enabled = canConfirm,
            onClick = {
                onConfirm(cents!!, if (needsSeller) sellerId else null)
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            },
        )
    }
}

@Composable
private fun AddPlayerSheet(
    game: Game,
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    onAdd: (name: String, sellerId: Int?) -> Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var sellerId by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val split = game.splitPurchase(game.buyInCents)
    val needsSeller = split.fromSeller > 0
    val inGame = game.players.map { it.name.lowercase() }.toSet()
    val available = profiles.filter { it.name.lowercase() !in inGame }.sortedBy { it.name.lowercase() }
    val err = error

    PurchaseSheetFrame(sheetState, onDismiss) {
        Text("Añadir jugador", style = MaterialTheme.typography.titleLarge)
        Text(
            "Entra con una entrada de ${formatEuros(game.buyInCents)} (${formatChips(game.chipsPerBuyIn)} fichas).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (available.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Habituales", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { p ->
                    FilterChip(
                        selected = name.trim().equals(p.name, ignoreCase = true),
                        onClick = { name = p.name; error = null },
                        label = { Text(p.name) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24); error = null },
            label = { Text("Nombre") },
            singleLine = true,
            isError = err != null,
            supportingText = if (err != null) { { Text(err) } } else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        PurchaseSource(game, split, name.trim(), buyerId = null, sellerId = sellerId, onSellerChange = { sellerId = it })

        Spacer(Modifier.height(20.dp))
        PrimaryAction(
            text = "Añadir y entregar fichas",
            enabled = name.isNotBlank() && (!needsSeller || sellerId != null),
            onClick = {
                val n = name.trim()
                if (game.players.any { it.name.equals(n, ignoreCase = true) }) {
                    error = "Ya hay un jugador con ese nombre"
                } else if (onAdd(n, if (needsSeller) sellerId else null)) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                } else {
                    error = "No se ha podido añadir"
                }
            },
        )
    }
}

@Composable
private fun EditPlayerDialog(
    game: Game,
    player: Player,
    onDismiss: () -> Unit,
    onRename: (String) -> Boolean,
    onRemove: () -> Unit,
) {
    var name by remember { mutableStateOf(player.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val err = error
    val canRemove = game.canRemove(player.id) && game.players.size > 2

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("¿Quitar a ${player.name}?") },
            text = { Text("Se elimina de la partida junto con su entrada. Sus fichas vuelven a la banca.") },
            confirmButton = { TextButton(onClick = onRemove) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        title = { Text("Editar jugador") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24); error = null },
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = err != null,
                    supportingText = if (err != null) { { Text(err) } } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmRemove = true }, enabled = canRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quitar de la partida")
                }
                if (!canRemove) {
                    Text(
                        if (game.players.size <= 2) "Una partida necesita al menos 2 jugadores."
                        else "No se puede quitar: tiene rebuys o ha cedido fichas. Elimina antes esos movimientos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim() == player.name) onDismiss()
                else if (onRename(name)) onDismiss()
                else error = "Nombre vacío o repetido"
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
