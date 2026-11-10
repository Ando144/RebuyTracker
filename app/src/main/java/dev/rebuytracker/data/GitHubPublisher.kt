package dev.rebuytracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

@Serializable
data class PublishConfig(
    val owner: String = "",
    val repo: String = "RebuyTracker",
    val token: String = "",
    val auto: Boolean = true,
    val lastPublishedAt: Long? = null,
) {
    val isComplete: Boolean get() = owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()

    /** Dirección de GitHub Pages del repositorio. */
    val siteUrl: String get() = "https://${owner.trim().lowercase()}.github.io/${repo.trim()}/"
}

class PublishException(message: String) : Exception(message)

/**
 * Sube el JSON público a la rama [BRANCH] del repositorio mediante la API de GitHub.
 * Si la rama no existe, la crea a partir de la rama principal.
 */
class GitHubPublisher {

    suspend fun publish(config: PublishConfig, content: String) = withContext(Dispatchers.IO) {
        val base = "https://api.github.com/repos/${config.owner.trim()}/${config.repo.trim()}"
        ensureBranch(config, base)
        val body = { sha: String? ->
            buildJsonObject {
                put("message", "Update leaderboard data")
                put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
                put("branch", BRANCH)
                if (sha != null) put("sha", sha)
            }.toString()
        }
        try {
            request(config, "PUT", "$base/contents/$FILE", body(currentSha(config, base)))
        } catch (e: ConflictException) {
            // Alguien actualizó el archivo entre medias: se reintenta una vez con el sha nuevo.
            request(config, "PUT", "$base/contents/$FILE", body(currentSha(config, base)))
        }
        Unit
    }

    private fun currentSha(config: PublishConfig, base: String): String? =
        request(config, "GET", "$base/contents/$FILE?ref=$BRANCH", allow404 = true)
            ?.let { obj(it)["sha"]?.jsonPrimitive?.content }

    private fun ensureBranch(config: PublishConfig, base: String) {
        if (request(config, "GET", "$base/git/ref/heads/$BRANCH", allow404 = true) != null) return
        val repoInfo = request(config, "GET", base, notFoundMessage = REPO_NOT_FOUND)!!
        val defaultBranch = obj(repoInfo)["default_branch"]?.jsonPrimitive?.content ?: "main"
        val ref = request(config, "GET", "$base/git/ref/heads/$defaultBranch")!!
        val sha = obj(ref)["object"]!!.jsonObject["sha"]!!.jsonPrimitive.content
        val body = buildJsonObject {
            put("ref", "refs/heads/$BRANCH")
            put("sha", sha)
        }.toString()
        request(config, "POST", "$base/git/refs", body)
    }

    private class ConflictException : Exception()

    private fun request(
        config: PublishConfig,
        method: String,
        url: String,
        body: String? = null,
        allow404: Boolean = false,
        notFoundMessage: String = REPO_NOT_FOUND,
    ): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer ${config.token.trim()}")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "RebuyTracker-Android")
        }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = try {
                conn.responseCode
            } catch (e: java.io.IOException) {
                throw PublishException("Sin conexión con GitHub. Comprueba que tienes internet.")
            }
            if (code == 404 && allow404) return null
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code in 200..299 -> return text
                code == 409 -> throw ConflictException()
                code == 401 -> throw PublishException("El token no es válido o ha caducado.")
                code == 403 -> throw PublishException("El token no tiene permiso de escritura (Contents: Read and write).")
                code == 404 -> throw PublishException(notFoundMessage)
                else -> throw PublishException("GitHub ha respondido con un error ($code).")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    companion object {
        const val BRANCH = "data"
        const val FILE = "games.json"
        private const val REPO_NOT_FOUND =
            "No se encuentra el repositorio o el token no tiene acceso a él. Revisa el usuario, el nombre del repo y los permisos del token."
    }
}
