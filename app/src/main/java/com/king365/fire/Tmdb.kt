package com.king365.fire

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

// ---------- The Movie Database (TMDB) : sagas et jaquettes manquantes ----------

/// Cherche chaque film sur TMDB (base de films gratuite) pour savoir à quelle saga il appartient
/// et récupérer une jaquette quand le fournisseur n'en donne pas. Nécessite une clé TMDB gratuite
/// (Réglages). Les réponses sont gardées sur l'appareil : chaque titre n'est cherché qu'une fois.
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p/w342"
    /// Titres traités au maximum par écran (les catalogues peuvent contenir des dizaines de milliers de films)
    private const val MAX_PER_LIST = 600

    private data class Info(val poster: String?, val collectionId: Int?, val collectionName: String?)

    private val cache = ConcurrentHashMap<String, Info>()
    private val byMovieId = ConcurrentHashMap<String, Info>()
    /// Jaquette TMDB de secours pour chaque image du fournisseur (souvent des liens cassés)
    private val fallbacks = ConcurrentHashMap<String, String>()

    /// Jaquette à afficher si l'image du fournisseur ne se charge pas
    fun fallback(url: String?): String? {
        version // relit l'état : l'affiche se met à jour dès qu'une jaquette est trouvée
        return url?.let { fallbacks[it] }
    }
    private var file: File? = null

    /// Change à chaque nouvelle information : les écrans regroupent à nouveau les sagas
    var version by mutableIntStateOf(0)
        private set

    val enabled: Boolean get() = BuildConfig.TMDB_ALLOWED && Prefs.tmdbKey.isNotBlank()

    fun init(context: Context) {
        val f = File(context.filesDir, "tmdb.json")
        file = f
        runCatching {
            if (!f.exists()) return
            val root = JSONObject(f.readText())
            for (key in root.keys()) {
                val o = root.getJSONObject(key)
                cache[key] = Info(
                    poster = o.optString("p").ifEmpty { null },
                    collectionId = if (o.has("c")) o.getInt("c") else null,
                    collectionName = o.optString("n").ifEmpty { null }
                )
            }
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val root = JSONObject()
            for ((key, info) in cache) {
                val o = JSONObject()
                info.poster?.let { o.put("p", it) }
                info.collectionId?.let { o.put("c", it) }
                info.collectionName?.let { o.put("n", it) }
                root.put(key, o)
            }
            f.writeText(root.toString())
        }
    }

    /// Saga d'un film d'après TMDB : (clé de regroupement, nom affiché)
    fun collection(movie: Movie): Pair<String, String>? {
        val info = byMovieId[movie.id] ?: cache[movieKey(movie)]?.also { byMovieId[movie.id] = it }
        val id = info?.collectionId ?: return null
        return "tmdb:$id" to (info?.collectionName ?: movie.name)
    }

    private fun movieKey(m: Movie) = "m|" + Duplicates.key(m.name) + "|" + (m.year ?: "")
    private fun seriesKey(s: SeriesItem) = "s|" + Duplicates.key(s.name)

    /// « Deadpool - Saga », « Harry Potter : La Collection » → « Deadpool », « Harry Potter »
    private fun cleanCollection(name: String): String =
        name.replace(Regex("""(?i)\s*[-–:(]?\s*(la\s+)?(saga|collection|trilogie|trilogy)\)?\s*$"""), "")
            .replace(Regex("""(?i)^(saga|collection)\s+"""), "")
            .trim()
            .ifEmpty { name }

    private suspend fun get(path: String, params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val key = Prefs.tmdbKey.trim()
        // Clé « API » (32 caractères) ou jeton d'accès en lecture (long, commence par « ey »)
        val bearer = key.length > 40
        val query = (params + if (bearer) emptyMap() else mapOf("api_key" to key))
            .entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val conn = URL("$API$path?$query").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        if (bearer) conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpException(code)
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun image(path: String?): String? =
        path?.takeIf { it.isNotBlank() && it != "null" }?.let { IMAGES + it }

    private suspend fun lookupMovie(m: Movie): Info {
        val key = movieKey(m)
        cache[key]?.let { return it }
        val params = mutableMapOf("query" to Duplicates.key(m.name), "language" to "fr-FR", "include_adult" to "false")
        m.year?.let { params["year"] = it.toString() }
        var results = get("/search/movie", params).optJSONArray("results")
        if ((results == null || results.length() == 0) && m.year != null) {
            // L'année du fournisseur est parfois fausse : nouvel essai sans elle
            params.remove("year")
            results = get("/search/movie", params).optJSONArray("results")
        }
        val first = results?.optJSONObject(0)
        val info = if (first == null) {
            Info(null, null, null)
        } else {
            val details = get("/movie/${first.getInt("id")}", mapOf("language" to "fr-FR"))
            val coll = details.optJSONObject("belongs_to_collection")
            Info(
                poster = image(first.optString("poster_path")),
                collectionId = coll?.optInt("id")?.takeIf { it > 0 },
                collectionName = coll?.optString("name")?.let(::cleanCollection)
            )
        }
        cache[key] = info
        return info
    }

    private suspend fun lookupSeries(s: SeriesItem): Info {
        val key = seriesKey(s)
        cache[key]?.let { return it }
        val results = get("/search/tv", mapOf("query" to Duplicates.key(s.name), "language" to "fr-FR"))
            .optJSONArray("results")
        val info = Info(image(results?.optJSONObject(0)?.optString("poster_path")), null, null)
        cache[key] = info
        return info
    }

    /// Complète une liste de films (sagas + jaquettes manquantes) ; `update` reçoit la liste
    /// au fur et à mesure (jaquettes ajoutées)
    suspend fun enrichMovies(list: List<Movie>, update: (List<Movie>) -> Unit) {
        if (!enabled || list.isEmpty()) return
        val todo = list.take(MAX_PER_LIST)
        val gate = Semaphore(4)
        var changed = false
        runCatching {
            coroutineScope {
                todo.chunked(24).forEach { chunk ->
                    chunk.map { m ->
                        async { gate.withPermit { runCatching { m to lookupMovie(m) }.getOrNull() } }
                    }.awaitAll().filterNotNull().forEach { (m, info) ->
                        byMovieId[m.id] = info
                        val icon = m.icon
                        if (!icon.isNullOrBlank() && info.poster != null) fallbacks[icon] = info.poster
                        changed = true
                    }
                    withContext(Dispatchers.Main) {
                        update(list.map { m ->
                            if (m.icon.isNullOrBlank()) byMovieId[m.id]?.poster?.let { m.copy(icon = it) } ?: m else m
                        })
                        version++
                    }
                }
            }
        }
        if (changed) withContext(Dispatchers.IO) { save() }
    }

    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    /// Prépare en arrière-plan les jaquettes de secours (images cassées du fournisseur)
    fun prefetchMovies(list: List<Movie>) {
        if (!enabled || list.isEmpty()) return
        background.launch { enrichMovies(list) { } }
    }

    fun prefetchSeries(list: List<SeriesItem>) {
        if (!enabled || list.isEmpty()) return
        background.launch { enrichSeries(list) { } }
    }

    /// Complète les jaquettes manquantes d'une liste de séries
    suspend fun enrichSeries(list: List<SeriesItem>, update: (List<SeriesItem>) -> Unit) {
        if (!enabled || list.isEmpty()) return
        val todo = list.take(MAX_PER_LIST)
        val gate = Semaphore(4)
        val posters = HashMap<String, String>()
        runCatching {
            coroutineScope {
                todo.chunked(24).forEach { chunk ->
                    chunk.map { s ->
                        async { gate.withPermit { runCatching { s to lookupSeries(s) }.getOrNull() } }
                    }.awaitAll().filterNotNull().forEach { (s, info) ->
                        val poster = info.poster ?: return@forEach
                        val cover = s.cover
                        if (cover.isNullOrBlank()) posters[s.id] = poster else fallbacks[cover] = poster
                    }
                    withContext(Dispatchers.Main) {
                        update(list.map { s -> posters[s.id]?.let { s.copy(cover = it) } ?: s })
                        version++
                    }
                }
            }
        }
        withContext(Dispatchers.IO) { save() }
    }

    /// Vérifie la clé saisie dans Réglages
    suspend fun check(): String = runCatching {
        get("/configuration", emptyMap())
        "Clé TMDB valide ✓"
    }.getOrElse { e ->
        if (e is HttpException && e.code == 401) "Clé TMDB refusée : vérifie-la sur themoviedb.org"
        else friendlyMessage(e)
    }

    fun clear() {
        cache.clear()
        byMovieId.clear()
        fallbacks.clear()
        file?.delete()
        version++
    }
}
