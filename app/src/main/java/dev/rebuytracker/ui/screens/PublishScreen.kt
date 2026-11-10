@file:OptIn(ExperimentalMaterial3Api::class)

package dev.rebuytracker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.rebuytracker.data.PublishConfig
import dev.rebuytracker.ui.PublishStatus
import dev.rebuytracker.ui.components.BottomActionBar
import dev.rebuytracker.ui.components.Hint
import dev.rebuytracker.ui.components.Panel
import dev.rebuytracker.ui.components.PrimaryAction
import dev.rebuytracker.ui.components.ScreenTitle
import dev.rebuytracker.ui.components.SectionLabel
import dev.rebuytracker.ui.components.appBarColors
import dev.rebuytracker.ui.theme.Gain
import dev.rebuytracker.ui.theme.Loss
import dev.rebuytracker.util.formatDate

private const val TOKEN_URL = "https://github.com/settings/personal-access-tokens/new"

@Composable
fun PublishScreen(
    config: PublishConfig,
    status: PublishStatus,
    gamesCount: Int,
    onBack: () -> Unit,
    onSave: (PublishConfig) -> Unit,
    onPublish: () -> Unit,
) {
    val context = LocalContext.current
    var owner by remember { mutableStateOf(config.owner) }
    var repo by remember { mutableStateOf(config.repo) }
    var token by remember { mutableStateOf(config.token) }
    var auto by remember { mutableStateOf(config.auto) }
    var showToken by remember { mutableStateOf(false) }

    val draft = config.copy(owner = owner.trim(), repo = repo.trim(), token = token.trim(), auto = auto)
    val dirty = draft != config
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { ScreenTitle("Web pública", "Clasificación en GitHub Pages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inicio") }
                },
                colors = appBarColors(),
            )
        },
        bottomBar = {
            BottomActionBar {
                PrimaryAction(
                    text = if (dirty) "Guardar y publicar" else "Publicar ahora",
                    enabled = draft.isComplete && status != PublishStatus.Running,
                    onClick = {
                        if (dirty) onSave(draft)
                        onPublish()
                    },
                )
                if (draft.isComplete) {
                    TextButton(onClick = { open(draft.siteUrl) }) { Text("Abrir la web") }
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
            item { StatusPanel(config, status, gamesCount) }

            item { SectionLabel("Repositorio") }
            item {
                Panel {
                    OutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it.trim() },
                        label = { Text("Usuario de GitHub") },
                        placeholder = { Text("Ando144") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it.trim() },
                        label = { Text("Repositorio") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        label = { Text("Token de acceso") },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Ocultar" else "Ver") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Publicar automáticamente", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Al cerrar una partida, añadir una pasada o borrar del historial.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = auto, onCheckedChange = { auto = it })
                    }
                    if (draft.owner.isNotBlank() && draft.repo.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "La web estará en ${draft.siteUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item { SectionLabel("Cómo crear el token") }
            item {
                Panel {
                    listOf(
                        "Abre la página de tokens de GitHub (botón de abajo).",
                        "Pon un nombre, p. ej. \"RebuyTracker app\", y la caducidad máxima.",
                        "En Repository access elige \"Only select repositories\" y marca solo ${draft.repo.ifBlank { "RebuyTracker" }}.",
                        "En Permissions › Repository permissions, pon Contents en \"Read and write\".",
                        "Genera el token, cópialo y pégalo arriba.",
                    ).forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { open(TOKEN_URL) }) { Text("Abrir GitHub › Tokens") }
                }
            }
            item {
                Hint("El token solo da permiso sobre este repositorio y se guarda únicamente en este móvil. La web es pública: cualquiera con el enlace verá nombres y cantidades.")
            }
        }
    }
}

@Composable
private fun StatusPanel(config: PublishConfig, status: PublishStatus, gamesCount: Int) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (status) {
                PublishStatus.Running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                is PublishStatus.Failed -> Icon(Icons.Filled.Warning, contentDescription = null, tint = Loss, modifier = Modifier.size(20.dp))
                else -> if (config.lastPublishedAt != null) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Gain, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when (status) {
                        PublishStatus.Running -> "Publicando…"
                        is PublishStatus.Failed -> "No se ha podido publicar"
                        else -> config.lastPublishedAt?.let { "Publicado · ${formatDate(it)}" } ?: "Aún no se ha publicado"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (status is PublishStatus.Failed) status.message
                    else "$gamesCount partidas en el historial. La web tarda unos minutos en reflejar los cambios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
