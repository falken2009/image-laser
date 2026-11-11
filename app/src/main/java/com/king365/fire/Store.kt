package com.king365.fire

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

// ---------- Réglages enregistrés ----------

object Prefs {
    private lateinit var sp: SharedPreferences

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        sp = context.getSharedPreferences("king365", Context.MODE_PRIVATE)
        sort = CatalogSort.entries.firstOrNull { it.name == sp.getString("sort", null) } ?: CatalogSort.AZ
        groupSagas = sp.getBoolean("groupSagas", true)
        userAgentName = sp.getString("userAgent", "Falken IPTV")?.takeIf { it in Http.agents } ?: "Falken IPTV"
        theme = KingThemeChoice.entries.firstOrNull { it.name == sp.getString("theme", null) } ?: KingThemeChoice.RED
        profiles = loadProfiles()
        currentProfile = sp.getString("currentProfile", "default")?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id
        subscriptions = loadSubscriptions()
        lockedCategories = sp.getStringSet("lockedCategories", emptySet())?.toSet() ?: emptySet()
        hasPin = !sp.getString("pin", null).isNullOrEmpty()
        tmdbKey = sp.getString("tmdbKey", "") ?: ""
        loadProfileData()
    }

    /// « xtream » ou « m3u »
    var mode: String
        get() = sp.getString("mode", "xtream") ?: "xtream"
        set(v) = sp.edit().putString("mode", v).apply()

    var m3uUrl: String
        get() = sp.getString("m3uUrl", "") ?: ""
        set(v) = sp.edit().putString("m3uUrl", v).apply()

    var userAgentName by mutableStateOf("Falken IPTV")
        private set

    /// Clé gratuite de themoviedb.org : sagas et jaquettes manquantes
    var tmdbKey by mutableStateOf("")
        private set

    fun updateTmdbKey(key: String) {
        tmdbKey = key.trim()
        sp.edit().putString("tmdbKey", tmdbKey).apply()
    }

    fun updateUserAgent(name: String) {
        userAgentName = name
        sp.edit().putString("userAgent", name).apply()
    }

    var server: String
        get() = sp.getString("server", "") ?: ""
        set(v) = sp.edit().putString("server", v).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    var password: String
        get() = sp.getString("password", "") ?: ""
        set(v) = sp.edit().putString("password", v).apply()

    fun clearLogin() {
        sp.edit().remove("password").remove("m3uUrl").apply()
    }

    // ---------- Thème ----------

    var theme by mutableStateOf(KingThemeChoice.RED)
        private set

    fun updateTheme(value: KingThemeChoice) {
        theme = value
        appContext?.let { AppIcon.apply(it, value) }
        sp.edit().putString("theme", value.name).apply()
    }

    // ---------- Profils ----------

    data class Profile(val id: String, val name: String, val color: Int)

    var profiles by mutableStateOf(listOf<Profile>())
        private set
    var currentProfile by mutableStateOf("default")
        private set

    val profile: Profile get() = profiles.firstOrNull { it.id == currentProfile } ?: profiles.first()

    private fun loadProfiles(): List<Profile> {
        val list = runCatching {
            val a = JSONArray(sp.getString("profiles", "[]") ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                Profile(o.optString("id"), o.optString("name"), o.optInt("color"))
            }
        }.getOrDefault(emptyList())
        return list.ifEmpty { listOf(Profile("default", "Moi", 0)) }
    }

    private fun saveProfiles() {
        val a = JSONArray()
        profiles.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        sp.edit().putString("profiles", a.toString()).apply()
    }

    fun selectProfile(id: String) {
        currentProfile = id
        sp.edit().putString("currentProfile", id).apply()
        loadProfileData()
    }

    fun addProfile(name: String) {
        val clean = name.trim().ifEmpty { "Profil ${profiles.size + 1}" }
        profiles = profiles + Profile(java.util.UUID.randomUUID().toString(), clean, profiles.size % 8)
        saveProfiles()
    }

    fun deleteProfile(id: String) {
        if (profiles.size <= 1) return
        profiles = profiles.filter { it.id != id }
        saveProfiles()
        if (currentProfile == id) selectProfile(profiles.first().id)
    }

    /// Clé propre au profil actif (le profil d'origine garde les anciennes clés)
    private fun pkey(base: String) = if (currentProfile == "default") base else "$base.$currentProfile"

    private fun loadProfileData() {
        favorites = sp.getStringSet(pkey("favorites"), emptySet())?.toSet() ?: emptySet()
        watchlist = loadWatchlist()
        progressTick++
    }

    // ---------- Favoris (identifiants de chaînes) ----------

    var favorites by mutableStateOf(setOf<String>())
        private set

    fun isFavorite(id: String) = id in favorites

    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        sp.edit().putStringSet(pkey("favorites"), favorites).apply()
    }

    // ---------- Tri et regroupement des films ----------

    var sort by mutableStateOf(CatalogSort.AZ)
        private set

    fun updateSort(value: CatalogSort) {
        sort = value
        sp.edit().putString("sort", value.name).apply()
    }

    var groupSagas by mutableStateOf(true)
        private set

    fun updateGroupSagas(value: Boolean) {
        groupSagas = value
        sp.edit().putBoolean("groupSagas", value).apply()
    }

    // ---------- Reprise de lecture : position et durée en millisecondes ----------

    data class Progress(
        val key: String,
        val position: Long,
        val duration: Long,
        val title: String?,
        val image: String?,
        val url: String?
    ) {
        val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    /// Change à chaque sauvegarde : les écrans qui affichent la reprise se mettent à jour
    var progressTick by mutableStateOf(0)
        private set

    private fun progressJson(): JSONObject =
        runCatching { JSONObject(sp.getString(pkey("progress"), "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun toProgress(key: String, o: JSONObject) = Progress(
        key, o.optLong("p"), o.optLong("d"),
        o.str("n"), o.str("i"), o.str("u")
    )

    fun progress(key: String): Progress? {
        val o = progressJson().optJSONObject(key) ?: return null
        val p = toProgress(key, o)
        return if (p.position > 30_000 && p.fraction < 0.95f) p else null
    }

    /// Films et épisodes commencés, du plus récent au plus ancien
    fun inProgress(limit: Int = 20): List<Progress> {
        val all = progressJson()
        return all.keys().asSequence().toList()
            .mapNotNull { k -> all.optJSONObject(k)?.let { k to it } }
            .sortedByDescending { it.second.optLong("t") }
            .map { toProgress(it.first, it.second) }
            .filter { it.url != null && it.position > 30_000 && it.fraction < 0.95f }
            .take(limit)
    }

    fun isWatched(key: String) = sp.getStringSet(pkey("watched"), emptySet())?.contains(key) == true

    fun saveProgress(key: String, position: Long, duration: Long, item: PlayItem? = null) {
        if (duration < 60_000 || position < 0) return
        val all = progressJson()
        if (position.toFloat() / duration >= 0.95f) {
            all.remove(key)
            val watched = (sp.getStringSet(pkey("watched"), emptySet()) ?: emptySet()) + key
            sp.edit().putStringSet(pkey("watched"), watched).apply()
        } else if (position > 30_000) {
            val old = all.optJSONObject(key)
            all.put(key, JSONObject()
                .put("p", position).put("d", duration).put("t", System.currentTimeMillis())
                .put("n", item?.title ?: old?.str("n"))
                .put("i", item?.image ?: old?.str("i"))
                .put("u", item?.url ?: old?.str("u")))
        }
        // Garde les 300 plus récents
        if (all.length() > 300) {
            val keys = all.keys().asSequence().toList()
                .sortedBy { all.optJSONObject(it)?.optLong("t") ?: 0L }
            keys.take(all.length() - 300).forEach { all.remove(it) }
        }
        sp.edit().putString(pkey("progress"), all.toString()).apply()
        progressTick++
    }

    fun clearProgress(key: String) {
        val all = progressJson()
        all.remove(key)
        sp.edit().putString(pkey("progress"), all.toString()).apply()
        progressTick++
    }

    // ---------- À voir plus tard ----------

    data class WatchEntry(
        val kind: String,            // « movie » ou « series »
        val itemId: String,
        val name: String,
        val image: String?,
        val rating: Double,
        val ext: String?,
        val categoryId: String?,
        val year: Int?
    ) {
        val id get() = "$kind-$itemId"
        fun movie() = Movie(itemId, name, image, ext ?: "mp4", rating, categoryId, year, null)
        fun series() = SeriesItem(itemId, name, image, rating, categoryId)
    }

    var watchlist by mutableStateOf(listOf<WatchEntry>())
        private set

    private fun loadWatchlist(): List<WatchEntry> = runCatching {
        val a = JSONArray(sp.getString(pkey("watchlist"), "[]") ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            WatchEntry(
                o.optString("k"), o.optString("id"), o.optString("n"), o.str("i"),
                o.optDouble("r", 0.0), o.str("e"), o.str("c"), o.str("y")?.toIntOrNull()
            )
        }
    }.getOrDefault(emptyList())

    fun inWatchlist(id: String) = watchlist.any { it.id == id }

    fun toggleWatchlist(entry: WatchEntry) {
        watchlist = if (inWatchlist(entry.id)) watchlist.filter { it.id != entry.id } else listOf(entry) + watchlist
        val a = JSONArray()
        watchlist.forEach {
            a.put(JSONObject().put("k", it.kind).put("id", it.itemId).put("n", it.name).put("i", it.image)
                .put("r", it.rating).put("e", it.ext).put("c", it.categoryId).put("y", it.year?.toString()))
        }
        sp.edit().putString(pkey("watchlist"), a.toString()).apply()
    }

    // ---------- Plusieurs abonnements ----------

    data class Subscription(
        val id: String,
        val name: String,
        val mode: String,
        val server: String,
        val username: String,
        val password: String,
        val m3uUrl: String
    )

    var subscriptions by mutableStateOf(listOf<Subscription>())
        private set

    private fun loadSubscriptions(): List<Subscription> = runCatching {
        val a = JSONArray(sp.getString("subscriptions", "[]") ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Subscription(o.optString("id"), o.optString("name"), o.optString("mode"), o.optString("server"),
                o.optString("user"), o.optString("pass"), o.optString("m3u"))
        }
    }.getOrDefault(emptyList())

    private fun saveSubscriptions() {
        val a = JSONArray()
        subscriptions.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("mode", it.mode).put("server", it.server)
                .put("user", it.username).put("pass", it.password).put("m3u", it.m3uUrl))
        }
        sp.edit().putString("subscriptions", a.toString()).apply()
    }

    fun isCurrent(sub: Subscription) = sub.mode == mode &&
        (if (mode == "m3u") sub.m3uUrl == m3uUrl else sub.server == server && sub.username == username)

    /// Mémorise l'abonnement qui vient de se connecter avec succès
    fun rememberCurrentSubscription() {
        val existing = subscriptions.firstOrNull { isCurrent(it) }
        if (existing != null) {
            if (mode == "xtream" && existing.password != password) {
                subscriptions = subscriptions.map { if (it.id == existing.id) it.copy(password = password) else it }
                saveSubscriptions()
            }
            return
        }
        val host = runCatching {
            java.net.URL(if (mode == "m3u") m3uUrl else if (server.startsWith("http")) server else "http://$server").host
        }.getOrNull() ?: "Abonnement"
        val name = if (mode == "m3u") "Playlist $host" else "$host · $username"
        subscriptions = subscriptions + Subscription(
            java.util.UUID.randomUUID().toString(), name, mode, server, username,
            if (mode == "xtream") password else "", m3uUrl
        )
        saveSubscriptions()
    }

    fun applySubscription(sub: Subscription) {
        mode = sub.mode
        server = sub.server
        username = sub.username
        password = sub.password
        m3uUrl = sub.m3uUrl
    }

    fun deleteSubscription(id: String) {
        subscriptions = subscriptions.filter { it.id != id }
        saveSubscriptions()
    }

    // ---------- Contrôle parental ----------

    var hasPin by mutableStateOf(false)
        private set
    /// Déverrouillé jusqu'à ce que l'appli passe en arrière-plan
    var unlocked by mutableStateOf(false)
    var lockedCategories by mutableStateOf(setOf<String>())
        private set

    fun checkPin(pin: String) = pin == sp.getString("pin", null)

    fun setPin(pin: String?) {
        sp.edit().putString("pin", pin ?: "").apply()
        hasPin = !pin.isNullOrEmpty()
        unlocked = false
    }

    fun isProtected(category: String?) = hasPin && category != null && Catalog.fold(category) in lockedCategories

    fun isLocked(category: String?) = !unlocked && isProtected(category)

    fun toggleLock(category: String) {
        val key = Catalog.fold(category)
        lockedCategories = if (key in lockedCategories) lockedCategories - key else lockedCategories + key
        sp.edit().putStringSet("lockedCategories", lockedCategories).apply()
    }

    // ---------- Rappels ----------

    data class Reminder(val id: String, val channelName: String, val url: String, val title: String, val start: Long)

    fun reminders(): List<Reminder> = runCatching {
        val a = JSONArray(sp.getString("reminders", "[]") ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Reminder(o.optString("id"), o.optString("c"), o.optString("u"), o.optString("t"), o.optLong("s"))
        }.filter { it.start > System.currentTimeMillis() - 3_600_000 }
    }.getOrDefault(emptyList())

    fun saveReminders(list: List<Reminder>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("c", it.channelName).put("u", it.url).put("t", it.title).put("s", it.start)) }
        sp.edit().putString("reminders", a.toString()).apply()
        reminderTick++
    }

    var reminderTick by mutableStateOf(0)
        private set
}

// ---------- Session en cours et caches ----------

object Session {
    var api: XtreamApi? = null
    /// Chaîne à ouvrir après avoir touché un rappel
    var pendingPlay by mutableStateOf<PlayItem?>(null)
    var account: Account? = null

    var liveCategories: List<Category> = emptyList()
    var liveChannels: List<LiveChannel> = emptyList()
    var vodCategories: List<Category> = emptyList()
    var seriesCategories: List<Category> = emptyList()
    val moviesByCategory = HashMap<String, List<Movie>>()
    val seriesByCategory = HashMap<String, List<SeriesItem>>()
    var allMovies: List<Movie>? = null
    var allSeries: List<SeriesItem>? = null

    /// Connexion par playlist M3U : pas de films, séries, replay ni compte
    val isM3U: Boolean get() = Prefs.mode == "m3u"

    fun restore(): Boolean {
        if (isM3U) {
            api = null
            return Prefs.m3uUrl.isNotBlank()
        }
        if (Prefs.server.isBlank() || Prefs.username.isBlank() || Prefs.password.isBlank()) return false
        api = XtreamApi(Prefs.server, Prefs.username, Prefs.password)
        return true
    }

    fun logout() {
        Prefs.clearLogin()
        api = null
        clearCaches()
    }

    fun clearCaches() {
        account = null
        liveCategories = emptyList()
        liveChannels = emptyList()
        vodCategories = emptyList()
        seriesCategories = emptyList()
        moviesByCategory.clear()
        seriesByCategory.clear()
        allMovies = null
        allSeries = null
        Epg.clear()
    }

    suspend fun ensureLive() {
        if (liveChannels.isNotEmpty()) return
        if (isM3U) {
            val playlist = M3U.parse(Http.text(Prefs.m3uUrl, timeoutMs = 120_000))
            if (playlist.channels.isEmpty()) throw java.io.IOException("Aucune chaîne trouvée dans la playlist.")
            liveCategories = playlist.categories
            liveChannels = playlist.channels
            return
        }
        val api = api ?: return
        liveCategories = api.liveCategories()
        liveChannels = api.liveStreams()
    }
}

/// Programmes en cours et suivants, gardés 10 minutes
object Epg {
    private val cache = HashMap<String, Pair<Long, List<Programme>>>()

    fun clear() = cache.clear()

    suspend fun short(streamId: String): List<Programme> {
        val now = System.currentTimeMillis()
        cache[streamId]?.let { if (now - it.first < 10 * 60_000) return it.second }
        val list = runCatching { Session.api?.shortEpg(streamId) ?: emptyList() }.getOrDefault(emptyList())
        cache[streamId] = now to list
        return list
    }

    fun nowAndNext(list: List<Programme>, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val current = list.firstOrNull { it.isOn(now) }
        val next = list.firstOrNull { it.start >= (current?.stop ?: now) }
        return current to next
    }
}

// ---------- Tri, sagas et recherche ----------

enum class CatalogSort(val label: String) {
    AZ("A → Z"), ZA("Z → A"), RECENT("Derniers ajouts"), RATING("Mieux notés"), ORIGINAL("Fournisseur")
}

object Catalog {
    private val bracketPrefix = Regex("""^\s*[\[(|][A-Za-z0-9 ]{2,8}[\])|]\s*""")
    private val dashPrefix = Regex("""^\s*[A-Z0-9]{2,6}\s*[-:|]\s*""")
    private val accents = Regex("\\p{Mn}+")

    /// Enlève « FR - », « |FR| », « [VOSTFR] », « 4K - »… en gardant les majuscules
    fun stripPrefix(name: String): String {
        var s = bracketPrefix.replaceFirst(name, "")
        s = dashPrefix.replaceFirst(s, "")
        return s.dropWhile { !it.isLetterOrDigit() }
    }

    fun fold(s: String): String =
        accents.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().trim()

    fun sortKey(name: String) = fold(stripPrefix(name))

    /// Ordre naturel : « Film 2 » avant « Film 10 »
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(sj, j).trimStart('0').ifEmpty { "0" }
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                if (ca != cb) return ca - cb
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    /// Catégorie spéciale « Derniers ajouts », en tête des films et des séries
    val RECENT = Category("__recent", "🆕 Derniers ajouts")

    fun <T> sort(
        items: List<T>,
        sort: CatalogSort,
        name: (T) -> String,
        rating: (T) -> Double,
        added: (T) -> Long? = { null }
    ): List<T> =
        when (sort) {
            CatalogSort.ORIGINAL -> items
            CatalogSort.RECENT -> items.sortedByDescending { added(it) ?: 0L }
            CatalogSort.RATING -> items.sortedByDescending(rating)
            CatalogSort.AZ, CatalogSort.ZA -> {
                val keyed = items.map { sortKey(name(it)) to it }
                val sorted = keyed.sortedWith { x, y -> naturalCompare(x.first, y.first) }.map { it.second }
                if (sort == CatalogSort.ZA) sorted.reversed() else sorted
            }
        }

    // Sagas

    sealed interface MovieEntry {
        data class Single(val movie: Movie) : MovieEntry
        data class Saga(val name: String, val movies: List<Movie>) : MovieEntry
    }

    private val stopwords = setOf(
        "le", "la", "les", "l", "un", "une", "des", "de", "du", "d", "et", "a", "au", "aux", "en",
        "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses", "notre", "votre", "leur",
        "the", "an", "of", "and", "in", "on", "to", "my", "his", "her"
    )
    private val yearInParens = Regex("""\s*[(\[]\s*(19|20)\d{2}\s*[)\]]""")
    private val yearAtEnd = Regex("""\s+(19|20)\d{2}\s*$""")
    private val subtitle = Regex("""\s*(:|\s[-–—]\s)""")
    private val sequel = Regex(
        """\s+((partie|part|chapitre|chapter|episode|épisode|volume|vol\.?|tome)\s*)?(\d{1,2}|[ivx]{1,4})\s*$""",
        RegexOption.IGNORE_CASE
    )

    private data class Base(val display: String, val key: String, val pair: Pair<String, String>?)

    /// Mentions de qualité ou de langue : « Avatar 4K », « Avatar [MULTI] »
    private val qualityTags = Regex(
        """(?i)[\[(|]?\b(4k|uhd|fhd|hd|sd|hdr10|hdr|multi|vostfr|vff|vf|1080p|720p|2160p|hevc|h265|x265|x264)\b[\])|]?"""
    )

    private fun base(name: String): Base {
        var s = stripPrefix(name)
        s = qualityTags.replace(s, " ").replace(Regex("\\s{2,}"), " ").trim()
        s = yearInParens.replace(s, "")
        s = yearAtEnd.replace(s, "")
        subtitle.find(s)?.let { s = s.substring(0, it.range.first) }
        s = sequel.replace(s, "").trim()
        if (s.isEmpty()) s = name
        val words = s.split(" ").filter { it.isNotEmpty() }
        var pair: Pair<String, String>? = null
        if (words.size > 2) {
            val first = fold(words[0]).filter { it.isLetter() }
            val second = fold(words[1]).filter { it.isLetter() }
            if (first.length > 1 && second.length > 1 && first !in stopwords && second !in stopwords) {
                val display = words[0] + " " + words[1]
                pair = display to fold(display)
            }
        }
        return Base(s, fold(s), pair)
    }

    fun groupSagas(list: List<Movie>): List<MovieEntry> {
        // Un même film en plusieurs qualités : on ne garde que la meilleure copie
        val movies = Duplicates.keepBest(list, { it.name }, { it.year })
        val bases = movies.map { base(it.name) }
        val pairCount = bases.mapNotNull { it.pair?.second }.groupingBy { it }.eachCount()
        val heuristic = bases.map { b ->
            val p = b.pair
            if (p != null && (pairCount[p.second] ?: 0) >= 2) p.second to p.first else b.key to b.display
        }
        // Saga trouvée sur Internet (TMDB) en priorité ; un film non trouvé rejoint quand même
        // la saga TMDB qui porte son nom (« Deadpool » → « Deadpool »)
        val online = movies.map { Tmdb.collection(it) }
        val onlineByName = online.filterNotNull().associate { fold(it.second) to it }
        val firstKeys = heuristic.mapIndexed { i, h -> online[i] ?: onlineByName[h.first] ?: h }
        // « Deadpool & Wolverine », « Alien vs Predator » : un titre qui commence par le nom d'un autre film
        // (au moins 5 lettres) rejoint sa saga
        val known = firstKeys.associate { it.first to it.second }
        val keys = firstKeys.map { k ->
            val parent = known.keys
                .filter { it != k.first && it.count { c -> c.isLetter() } >= 5 && k.first.startsWith("$it ") }
                .minByOrNull { it.length }
            if (parent != null) parent to (known[parent] ?: k.second) else k
        }
        val buckets = LinkedHashMap<String, MutableList<Movie>>()
        val names = HashMap<String, String>()
        movies.zip(keys).forEach { (m, k) ->
            buckets.getOrPut(k.first) { mutableListOf() }.add(m)
            names.putIfAbsent(k.first, k.second)
        }
        val emitted = HashSet<String>()
        val entries = ArrayList<MovieEntry>()
        movies.zip(keys).forEach { (m, k) ->
            val members = buckets[k.first] ?: emptyList()
            if (members.size >= 2) {
                if (emitted.add(k.first)) {
                    val ordered = members.sortedWith { a, b ->
                        val ya = a.year
                        val yb = b.year
                        if (ya != null && yb != null && ya != yb) ya - yb
                        else naturalCompare(sortKey(a.name), sortKey(b.name))
                    }
                    entries.add(MovieEntry.Saga(names[k.first] ?: m.name, ordered))
                }
            } else {
                entries.add(MovieEntry.Single(m))
            }
        }
        return entries
    }

    // Filtres des films

    fun matches(m: Movie, decade: Int?, minRating: Int?, genre: String?): Boolean {
        if (decade != null) {
            val y = m.year ?: return false
            if (decade == 1990) { if (y >= 2000) return false } else if (y < decade || y >= decade + 10) return false
        }
        if (minRating != null && m.rating < minRating) return false
        if (genre != null && m.genre?.contains(genre, ignoreCase = true) != true) return false
        return true
    }

    fun genres(movies: List<Movie>): List<String> = movies.asSequence()
        .flatMap { (it.genre ?: "").split(',', '/', '|').asSequence() }
        .map { g -> g.trim().replaceFirstChar { it.uppercase() } }
        .filter { it.length > 1 }
        .distinct()
        .sorted()
        .toList()

    // Recherche (texte ou dictée)

    private val fillers = listOf(
        "je voudrais regarder", "je voudrais voir", "je veux regarder", "je veux voir",
        "est-ce que tu as", "tu as", "mets moi", "mets-moi", "mets", "lance", "joue",
        "regarder", "cherche", "trouve", "le film", "la serie", "les films", "les series",
        "un film", "une serie", "film", "serie", "s'il te plait", "stp"
    )
    private val numbers = mapOf(
        "un" to "1", "deux" to "2", "trois" to "3", "quatre" to "4", "cinq" to "5",
        "six" to "6", "sept" to "7", "huit" to "8", "neuf" to "9", "dix" to "10"
    )
    private val punctuation = Regex("""[^\p{L}\p{N}' -]""")

    fun cleanQuery(text: String): String {
        var s = punctuation.replace(fold(text), " ").trim().replace(Regex("\\s+"), " ")
        var changed = true
        while (changed) {
            changed = false
            for (f in fillers) {
                if (s == f || s.startsWith("$f ")) {
                    s = s.removePrefix(f).trim()
                    changed = true
                }
            }
        }
        return s.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { numbers[it] ?: it }
    }

    fun <T> rank(items: List<T>, query: String, name: (T) -> String, limit: Int = 40): List<T> {
        val words = query.split(" ").filter { it.length > 1 || it.toIntOrNull() != null }
        if (words.isEmpty()) return emptyList()
        return items.mapNotNull { item ->
            val key = sortKey(name(item))
            if (!words.all { key.contains(it) }) return@mapNotNull null
            var score = 0
            if (key == query) score += 300
            if (key.startsWith(query)) score += 150
            if (key.startsWith(words[0])) score += 50
            score -= key.length / 3
            score to item
        }.sortedByDescending { it.first }.take(limit).map { it.second }
    }
}

/// Icône et bannière de l'appli (écran d'accueil du Fire Stick) dans la couleur du thème
object AppIcon {
    private fun alias(theme: KingThemeChoice) =
        "com.king365.fire.Launcher" + theme.name.lowercase().replaceFirstChar { it.uppercase() }

    fun apply(context: Context, theme: KingThemeChoice) {
        runCatching {
            val pm = context.packageManager
            val enabled = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            val disabled = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val flags = android.content.pm.PackageManager.DONT_KILL_APP
            // D'abord activer la nouvelle icône, ensuite seulement désactiver les autres
            pm.setComponentEnabledSetting(android.content.ComponentName(context.packageName, alias(theme)), enabled, flags)
            KingThemeChoice.entries.filter { it != theme }.forEach { other ->
                pm.setComponentEnabledSetting(android.content.ComponentName(context.packageName, alias(other)), disabled, flags)
            }
        }
    }
}
