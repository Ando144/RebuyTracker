@file:OptIn(ExperimentalMaterial3Api::class)

package dev.rebuytracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rebuytracker.data.Profile
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PlayerAvatar
import dev.rebuytracker.ui.components.ScreenTitle
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.appBarColors

@Composable
fun RosterScreen(
    profiles: List<Profile>,
    gamesByProfile: Map<String, Int>,
    onBack: () -> Unit,
    onAdd: (String) -> Boolean,
    onRename: (id: String, name: String) -> Boolean,
    onDelete: (id: String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    val sorted = profiles.sortedBy { it.name.lowercase() }

    fun add() {
        if (newName.isBlank()) return
        if (onAdd(newName)) {
            newName = ""
            addError = null
        } else {
            addError = "Ya existe un jugador con ese nombre"
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Jugadores habituales", "${profiles.size} jugadores") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inicio") }
                },
                colors = appBarColors(),
            )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val err = addError
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(24); addError = null },
                            label = { Text("Nuevo jugador") },
                            singleLine = true,
                            isError = err != null,
                            supportingText = if (err != null) { { Text(err) } } else null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { add() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Añadir")
                        }
                    }
                }
            }

            item { SectionLabel("Lista") }
            if (sorted.isEmpty()) {
                item { Hint("Añade aquí a la gente con la que jugáis normalmente. Al crear una partida bastará con tocar sus nombres.") }
            } else {
                item {
                    Panel(contentPadding = PaddingValues(vertical = 4.dp)) {
                        sorted.forEachIndexed { index, profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = profile }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlayerAvatar(profile.name)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val games = gamesByProfile[profile.id] ?: 0
                                    Text(
                                        if (games == 1) "1 partida" else "$games partidas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                            if (index < sorted.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { profile ->
        EditProfileDialog(
            profile = profile,
            onDismiss = { editing = null },
            onRename = { name -> onRename(profile.id, name) },
            onDelete = { onDelete(profile.id); editing = null },
        )
    }
}

@Composable
private fun EditProfileDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onRename: (String) -> Boolean,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val err = error

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar a ${profile.name}?") },
            text = { Text("Desaparece de la lista de habituales. Sus partidas cerradas se mantienen y seguirá en la clasificación con su nombre.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                Text(
                    "El nuevo nombre se aplica también a la clasificación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = { confirmDelete = true }) { Text("Borrar jugador") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim() == profile.name || onRename(name)) onDismiss() else error = "Nombre vacío o repetido"
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
