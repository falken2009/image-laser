package com.king365.fire

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Modèles ----------

data class Category(val id: String, val name: String)

data class LiveChannel(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String?,
    val hasArchive: Boolean,
    val archiveDays: Int,
    /// Lien direct (playlists M3U) ; null avec Xtream
    val url: String? = null
)

data class Movie(
    val id: String,
    val name: String,
    val icon: String?,
    val ext: String,
    val rating: Double,
    val categoryId: String?,
    val year: Int?,
    /// Genre, quand le serveur l'indique dans la liste
    val genre: String?,
    /// Date d'ajout chez le fournisseur (millisecondes)
    val added: Long? = null
)

data class SeriesItem(
    val id: String,
    val name: String,
    val cover: String?,
    val rating: Double,
    val categoryId: String?,
    /// Dernière mise à jour chez le fournisseur (millisecondes)
    val added: Long? = null
)

data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val ext: String,
    val plot: String?,
    val duration: String?,
    val image: String?
)

data class SeriesInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val year: String?,
    val cover: String?,
    val seasons: Map<Int, List<Episode>>,
    val backdrop: String? = null
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val duration: String?,
    val year: String?,
    val cover: String?,
    val backdrop: String? = null
)

data class Account(
    val expiry: Long?,          // millisecondes
    val status: String?,
    val username: String?,
    val maxConnections: String?,
    val activeConnections: String?
)

data class Programme(
    val title: String,
    val desc: String?,
    val start: Long,            // millisecondes
    val stop: Long,
    val serverStart: String,    // « 2026-11-07 18:00:00 », heure du serveur
    val hasArchive: Boolean
) {
    fun isOn(now: Long = System.currentTimeMillis()) = now in start until stop
    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/// Ce que le lecteur doit ouvrir
data class PlayItem(
    val title: String,
    val url: String,
    val resumeKey: String? = null,
    val startAt: Long? = null,   // millisecondes
    val live: Boolean = false,
    /// Affiche (rangée « Continuer à regarder »)
    val image: String? = null,
    /// Chaînes voisines (zapping) ou épisodes de la saison (lecture enchaînée)
    val siblings: List<PlayItem> = emptyList(),
    val siblingIndex: Int = 0,
    val autoNext: Boolean = false,
    /// Chaîne en direct : identifiant (programme en cours) et logo
    val streamId: String? = null,
    val logo: String? = null,
    /// Programme en replay : haut/bas sautent 2 minutes (pour passer les pubs)
    val replay: Boolean = false
)

// ---------- Lecture tolérante du JSON Xtream ----------

fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = opt(key)?.toString()?.trim() ?: return null
    return if (v.isEmpty() || v == "null") null else v
}

fun JSONObject.num(key: String): Double? = str(key)?.toDoubleOrNull()

/// Image de fond : "backdrop_path" est un tableau de liens ou un simple lien selon les serveurs
fun backdrop(info: JSONObject?): String? {
    if (info == null) return null
    info.optJSONArray("backdrop_path")?.let { list ->
        for (i in 0 until list.length()) {
            val url = list.optString(i).trim()
            if (url.startsWith("http")) return url
        }
        return null
    }
    return info.str("backdrop_path")?.takeIf { it.startsWith("http") }
}

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun decode64(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return try {
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8).ifBlank { value }
    } catch (e: Exception) {
        value
    }
}

// ---------- Réseau : redirections, nouvelles tentatives, identifiant de l'appli ----------

class HttpException(val code: Int) : IOException("HTTP $code")

object Http {
    /// Certains fournisseurs n'acceptent que des applis connues : on peut se présenter sous un autre nom
    val agents = linkedMapOf(
        "Falken IPTV" to "FalkenIPTV/1.0",
        "IPTV Smarters" to "IPTVSmartersPro",
        "VLC" to "VLC/3.0.20 LibVLC/3.0.20",
        "TiviMate" to "TiviMate/4.7.0"
    )

    val userAgent: String get() = agents[Prefs.userAgentName] ?: "FalkenIPTV/1.0"

    /// Télécharge un texte (JSON, M3U) avec jusqu'à 2 nouvelles tentatives
    suspend fun text(url: String, timeoutMs: Int = 90_000): String = withContext(Dispatchers.IO) {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                return@withContext once(url, timeoutMs)
            } catch (e: Exception) {
                val retry = when (e) {
                    is HttpException -> e.code >= 500 || e.code == 429
                    is UnknownHostException -> false
                    is IOException -> true
                    else -> false
                }
                if (!retry || attempt == 2) throw e
                last = e
                delay(1_500L * (attempt + 1))
            }
        }
        throw last ?: IOException("Échec de connexion")
    }

    /// Temps de réponse en millisecondes, en un seul essai (test de connexion)
    suspend fun ping(start: String): Long = withContext(Dispatchers.IO) {
        val begin = System.currentTimeMillis()
        var url = URL(start)
        repeat(6) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Range", "bytes=0-1023")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw HttpException(code)
                    url = URL(url, location)
                    return@repeat
                }
                if (code !in 200..299) throw HttpException(code)
                runCatching { conn.inputStream.use { it.read(ByteArray(1024)) } }
                return@withContext System.currentTimeMillis() - begin
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Trop de redirections.")
    }

    /// Suit les redirections, y compris de http vers https (ce que HttpURLConnection ne fait pas seul)
    private fun once(start: String, timeoutMs: Int): String {
        var url = URL(start)
        repeat(6) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "*/*")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw HttpException(code)
                    url = URL(url, location)
                    return@repeat
                }
                if (code !in 200..299) throw HttpException(code)
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Trop de redirections.")
    }
}

/// Message en français, compréhensible, pour n'importe quelle erreur
fun friendlyMessage(e: Throwable): String = when (e) {
    is UnknownHostException -> "Serveur introuvable : vérifie l'adresse du serveur (sans faute de frappe)."
    is SocketTimeoutException -> "Le serveur ne répond pas. Vérifie ta connexion ou réessaie plus tard."
    is ConnectException -> "Connexion refusée : vérifie l'adresse et le port du serveur (par exemple :8080)."
    is SSLException -> "Connexion sécurisée impossible : essaie l'adresse en http:// au lieu de https://."
    is HttpException -> when (e.code) {
        401, 403 -> "Accès refusé : identifiants incorrects, abonnement expiré ou trop d'écrans connectés."
        404 -> "Adresse introuvable sur le serveur : vérifie le lien."
        429 -> "Le serveur reçoit trop de demandes. Réessaie dans un instant."
        in 500..599 -> "Le serveur rencontre un problème (erreur ${e.code}). Réessaie plus tard."
        else -> "Le serveur a répondu avec l'erreur ${e.code}."
    }
    is JSONException -> "Réponse inattendue : ce serveur n'est peut-être pas un serveur Xtream Codes."
    is java.net.MalformedURLException -> "Adresse invalide. Vérifie le lien ou le serveur."
    else -> e.message?.takeIf { it.isNotBlank() } ?: "Erreur de connexion."
}

// ---------- Client Xtream Codes ----------

class XtreamApi(server: String, val username: String, val password: String) {

    val host: String = server.trim().trimEnd('/').let {
        if (it.startsWith("http", ignoreCase = true)) it else "http://$it"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /// Adresse de vérification du compte (test de connexion)
    val accountUrl: String get() = "$host/player_api.php?username=${enc(username)}&password=${enc(password)}"

    private suspend fun get(params: Map<String, String>): String {
        val all = linkedMapOf("username" to username, "password" to password) + params
        val query = all.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return Http.text("$host/player_api.php?$query")
    }

    private suspend fun array(action: String, extra: Map<String, String> = emptyMap()): List<JSONObject> {
        val text = get(mapOf("action" to action) + extra).trim()
        return if (text.startsWith("[")) JSONArray(text).objects() else emptyList()
    }

    private suspend fun obj(action: String?, extra: Map<String, String> = emptyMap()): JSONObject {
        val params = if (action != null) mapOf("action" to action) + extra else extra
        val text = get(params).trim()
        return if (text.startsWith("{")) JSONObject(text) else JSONObject()
    }

    private fun categories(list: List<JSONObject>) = list.mapNotNull { o ->
        val id = o.str("category_id") ?: return@mapNotNull null
        Category(id, o.str("category_name") ?: "Sans nom")
    }

    // Compte

    suspend fun account(): Account {
        val text = get(emptyMap()).trim()
        if (!text.startsWith("{")) throw JSONException("not json")
        val root = JSONObject(text)
        val u = root.optJSONObject("user_info")
            ?: throw IOException("Identifiants refusés : vérifie le nom d'utilisateur et le mot de passe.")
        if (u.str("auth") == "0") throw IOException("Identifiants refusés : vérifie le nom d'utilisateur et le mot de passe.")
        return Account(
            expiry = u.num("exp_date")?.let { (it * 1000).toLong() },
            status = u.str("status"),
            username = u.str("username"),
            maxConnections = u.str("max_connections"),
            activeConnections = u.str("active_cons")
        )
    }

    // Direct

    suspend fun liveCategories() = categories(array("get_live_categories"))

    suspend fun liveStreams(): List<LiveChannel> = array("get_live_streams").mapNotNull { o ->
        val id = o.str("stream_id") ?: return@mapNotNull null
        LiveChannel(
            id = id,
            name = o.str("name") ?: "Chaîne",
            icon = o.str("stream_icon"),
            categoryId = o.str("category_id"),
            hasArchive = o.str("tv_archive") == "1",
            archiveDays = o.str("tv_archive_duration")?.toIntOrNull() ?: 0
        )
    }

    fun liveUrl(channel: LiveChannel) = "$host/live/${enc(username)}/${enc(password)}/${channel.id}.ts"

    private fun programmes(root: JSONObject): List<Programme> =
        (root.optJSONArray("epg_listings")?.objects() ?: emptyList()).map { p ->
            Programme(
                title = decode64(p.str("title")) ?: "Programme",
                desc = decode64(p.str("description")),
                start = ((p.num("start_timestamp") ?: 0.0) * 1000).toLong(),
                stop = ((p.num("stop_timestamp") ?: 0.0) * 1000).toLong(),
                serverStart = p.str("start") ?: "",
                hasArchive = p.str("has_archive") == "1"
            )
        }.sortedBy { it.start }

    suspend fun shortEpg(streamId: String) =
        programmes(obj("get_short_epg", mapOf("stream_id" to streamId, "limit" to "12")))

    suspend fun archive(streamId: String) =
        programmes(obj("get_simple_data_table", mapOf("stream_id" to streamId)))

    /// /timeshift/utilisateur/motdepasse/durée/AAAA-MM-JJ:HH-MM/id.ts
    fun replayUrl(channel: LiveChannel, p: Programme): String {
        val minutes = maxOf(((p.stop - p.start) / 60_000).toInt(), 1)
        val start = if (p.serverStart.length >= 16) {
            p.serverStart.substring(0, 10) + ":" + p.serverStart.substring(11, 16).replace(":", "-")
        } else {
            SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(p.start))
        }
        return "$host/timeshift/${enc(username)}/${enc(password)}/$minutes/$start/${channel.id}.ts"
    }

    // Films

    suspend fun vodCategories() = categories(array("get_vod_categories"))

    private fun movie(o: JSONObject): Movie? {
        val id = o.str("stream_id") ?: return null
        val name = o.str("name") ?: "Sans titre"
        val year = o.str("year")?.take(4)?.toIntOrNull()?.takeIf { it > 1900 }
            ?: Regex("(19|20)\\d{2}").find(name)?.value?.toIntOrNull()
        return Movie(
            id = id,
            name = name,
            icon = o.str("stream_icon"),
            ext = o.str("container_extension") ?: "mp4",
            rating = o.num("rating") ?: 0.0,
            categoryId = o.str("category_id"),
            year = year,
            genre = o.str("genre"),
            added = o.num("added")?.let { (it * 1000).toLong() }
        )
    }

    suspend fun movies(categoryId: String) =
        array("get_vod_streams", mapOf("category_id" to categoryId)).mapNotNull { movie(it) }

    suspend fun allMovies() = array("get_vod_streams").mapNotNull { movie(it) }

    suspend fun movieInfo(id: String): MovieInfo {
        val info = obj("get_vod_info", mapOf("vod_id" to id)).optJSONObject("info")
            ?: return MovieInfo(null, null, null, null, null, null, null)
        return MovieInfo(
            plot = info.str("plot") ?: info.str("description"),
            genre = info.str("genre"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            duration = info.str("duration"),
            year = (info.str("releasedate") ?: info.str("release_date"))?.take(4),
            cover = info.str("cover_big") ?: info.str("movie_image"),
            backdrop = backdrop(info)
        )
    }

    fun movieUrl(m: Movie) = "$host/movie/${enc(username)}/${enc(password)}/${m.id}.${m.ext}"

    // Séries

    suspend fun seriesCategories() = categories(array("get_series_categories"))

    private fun series(o: JSONObject): SeriesItem? {
        val id = o.str("series_id") ?: return null
        return SeriesItem(
            id, o.str("name") ?: "Sans titre", o.str("cover"), o.num("rating") ?: 0.0, o.str("category_id"),
            added = o.num("last_modified")?.let { (it * 1000).toLong() }
        )
    }

    suspend fun series(categoryId: String) =
        array("get_series", mapOf("category_id" to categoryId)).mapNotNull { series(it) }

    suspend fun allSeries() = array("get_series").mapNotNull { series(it) }

    suspend fun seriesInfo(id: String): SeriesInfo {
        val root = obj("get_series_info", mapOf("series_id" to id))
        val info = root.optJSONObject("info")
        val seasons = sortedMapOf<Int, MutableList<Episode>>()

        fun episode(e: JSONObject): Episode? {
            val eid = e.str("id") ?: return null
            val i = e.optJSONObject("info")
            return Episode(
                id = eid,
                number = e.str("episode_num")?.toDoubleOrNull()?.toInt() ?: 0,
                title = e.str("title") ?: "Épisode",
                ext = e.str("container_extension") ?: "mp4",
                plot = i?.str("plot"),
                duration = i?.str("duration"),
                image = i?.str("movie_image")
            )
        }

        // Les épisodes arrivent soit par saison {"1": [...]}, soit en tableau de tableaux
        root.optJSONObject("episodes")?.let { byseason ->
            for (key in byseason.keys()) {
                val list = byseason.optJSONArray(key)?.objects()?.mapNotNull { episode(it) } ?: continue
                seasons.getOrPut(key.toIntOrNull() ?: 0) { mutableListOf() }.addAll(list)
            }
        } ?: root.optJSONArray("episodes")?.let { arrays ->
            for (i in 0 until arrays.length()) {
                val inner = arrays.optJSONArray(i) ?: continue
                for (e in inner.objects()) {
                    val season = e.str("season")?.toDoubleOrNull()?.toInt() ?: 1
                    episode(e)?.let { seasons.getOrPut(season) { mutableListOf() }.add(it) }
                }
            }
        }

        return SeriesInfo(
            plot = info?.str("plot"),
            genre = info?.str("genre"),
            cast = info?.str("cast"),
            year = (info?.str("releaseDate") ?: info?.str("release_date"))?.take(4),
            cover = info?.str("cover"),
            seasons = seasons.mapValues { (_, v) -> v.sortedBy { it.number } },
            backdrop = backdrop(info)
        )
    }

    fun episodeUrl(e: Episode) = "$host/series/${enc(username)}/${enc(password)}/${e.id}.${e.ext}"
}

// ---------- Playlists M3U ----------

object M3U {
    data class Playlist(val categories: List<Category>, val channels: List<LiveChannel>)

    private fun attribute(key: String, line: String): String? {
        val start = line.indexOf("$key=\"")
        if (start < 0) return null
        val from = start + key.length + 2
        val end = line.indexOf('"', from)
        return if (end < 0) null else line.substring(from, end)
    }

    /// Le nom de la chaîne est après la première virgule située hors guillemets
    private fun name(line: String): String {
        var inQuotes = false
        line.forEachIndexed { i, c ->
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return line.substring(i + 1).trim().ifEmpty { "Sans nom" }
        }
        return "Sans nom"
    }

    fun parse(text: String): Playlist {
        val channels = ArrayList<LiveChannel>()
        val groups = LinkedHashSet<String>()
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup = "Sans catégorie"

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF") -> {
                    pendingName = name(line)
                    pendingLogo = attribute("tvg-logo", line)?.ifBlank { null }
                    pendingGroup = attribute("group-title", line)?.ifBlank { null } ?: "Sans catégorie"
                }
                line.startsWith("#EXTGRP:") -> pendingGroup = line.removePrefix("#EXTGRP:").trim().ifEmpty { pendingGroup }
                line.startsWith("#") -> Unit
                pendingName != null -> {
                    groups.add(pendingGroup)
                    channels.add(
                        LiveChannel(
                            id = "m3u-${channels.size}",
                            name = pendingName,
                            icon = pendingLogo,
                            categoryId = pendingGroup,
                            hasArchive = false,
                            archiveDays = 0,
                            url = line
                        )
                    )
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = "Sans catégorie"
                }
            }
        }
        return Playlist(groups.map { Category(it, it) }, channels)
    }
}

/// Format des chaînes Xtream : HLS (.m3u8) ou TS (.ts). Beaucoup de chaînes ne marchent que dans l'un des deux ;
/// l'appli essaie l'autre en cas d'échec et s'en souvient.
object LiveFormat {
    private val remembered = java.util.Collections.synchronizedSet(HashSet<String>())
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("liveFormat", android.content.Context.MODE_PRIVATE)
        remembered.addAll(prefs?.getStringSet("alt", emptySet()) ?: emptySet())
    }

    private fun id(url: String) = url.substringBefore('?').substringAfter("://")

    fun alternate(url: String): String? {
        val base = url.substringBefore('?')
        val rest = url.substring(base.length)
        val last = base.substringAfterLast('/')
        return when {
            base.endsWith(".m3u8", ignoreCase = true) -> base.dropLast(5) + ".ts" + rest
            base.endsWith(".ts", ignoreCase = true) -> base.dropLast(3) + ".m3u8" + rest
            last.isNotEmpty() && last.all { it.isDigit() } -> "$base.ts$rest"
            else -> null
        }
    }

    fun prefersAlternate(url: String) = id(url) in remembered

    fun remember(url: String) {
        if (remembered.add(id(url))) save()
    }

    fun forget(url: String) {
        if (remembered.remove(id(url))) save()
    }

    private fun save() {
        prefs?.edit()?.putStringSet("alt", remembered.toList().takeLast(500).toSet())?.apply()
    }
}
