package com.king365.fire

import androidx.compose.ui.focus.onFocusChanged

import kotlinx.coroutines.launch

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Listes de catégories (films et séries) ----------

@Composable
private fun CategoryScreen(
    title: String,
    load: suspend () -> List<Category>,
    cached: List<Category>,
    store: (List<Category>) -> Unit,
    onSelect: (Category) -> Unit
) {
    var categories by remember { mutableStateOf(cached) }
    var loading by remember { mutableStateOf(cached.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var askPin by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            runCatching { load() }
                .onSuccess { categories = it; store(it) }
                .onFailure { error = friendlyMessage(it) }
            loading = false
        }
    }
    LaunchedEffect(categories.size) { if (categories.isNotEmpty()) runCatching { first.requestFocus() } }

    KingBackground(dimmed = true) {
        when {
            loading -> Loading()
            error != null -> Message("Impossible de charger les catégories", error)
            categories.isEmpty() -> Message("Aucune catégorie")
            else -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                ScreenTitle(title)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(listOf(Catalog.RECENT) + categories) { c ->
                        Box(if (c == Catalog.RECENT) Modifier.focusRequester(first) else Modifier) {
                            ListRow(
                                (if (Prefs.isProtected(c.name)) "🔒 " else "") + c.name,
                                onMenu = if (Prefs.hasPin && (Prefs.unlocked || !Prefs.isProtected(c.name))) {
                                    { Prefs.toggleLock(c.name) }
                                } else null
                            ) {
                                if (Prefs.isLocked(c.name)) askPin = true else onSelect(c)
                            }
                        }
                    }
                }
            }
        }
        if (askPin) UnlockOverlay { askPin = false }
    }
}

/// Écran affiché quand on est connecté par playlist M3U
@Composable
fun XtreamOnly(feature: String) {
    KingBackground(dimmed = true) {
        Message(
            "$feature avec Xtream Codes",
            "Ta connexion actuelle est une playlist M3U, qui ne contient que les chaînes. " +
                "Pour $feature, connecte-toi avec tes identifiants Xtream dans Réglages → Se déconnecter."
        )
    }
}

@Composable
fun MovieCategoriesScreen(nav: Navigator) = if (Session.api == null) XtreamOnly("les films") else CategoryScreen(
    title = "FILMS",
    load = { Session.api!!.vodCategories() },
    cached = Session.vodCategories,
    store = { Session.vodCategories = it },
    onSelect = { nav.go(Screen.MovieGrid(it)) }
)

@Composable
fun SeriesCategoriesScreen(nav: Navigator) = if (Session.api == null) XtreamOnly("les séries") else CategoryScreen(
    title = "SÉRIES",
    load = { Session.api!!.seriesCategories() },
    cached = Session.seriesCategories,
    store = { Session.seriesCategories = it },
    onSelect = { nav.go(Screen.SeriesGrid(it)) }
)

/// Dans « Derniers ajouts », masque les titres des catégories verrouillées
private fun lockedIds(categories: List<Category>): Set<String> =
    categories.filter { Prefs.isLocked(it.name) }.map { it.id }.toSet()

// ---------- Barre de tri ----------

@Composable
private fun SortBar(showSagas: Boolean, series: Boolean = false) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
        items(CatalogSort.entries.toList()) { s ->
            Chip(s.label, selected = Prefs.sort == s) { Prefs.updateSort(s) }
        }
        if (showSagas) {
            item {
                val label = when {
                    series && Prefs.groupSagas -> "Une copie par série"
                    series -> "Toutes les copies"
                    Prefs.groupSagas -> "Sagas regroupées"
                    else -> "Sagas séparées"
                }
                Chip(label, selected = Prefs.groupSagas) {
                    Prefs.updateGroupSagas(!Prefs.groupSagas)
                }
            }
        }
    }
}

// ---------- Films ----------

@Composable
fun MovieGridScreen(nav: Navigator, category: Category) {
    var movies by remember { mutableStateOf(Session.moviesByCategory[category.id]) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    // Retour : remonte en haut de la grille avant de quitter l'écran
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val gridScope = androidx.compose.runtime.rememberCoroutineScope()
    BackToTop(gridState.firstVisibleItemIndex == 0, { gridState.scrollToItem(0) }, first)
    // Image de fond : l'affiche sélectionnée
    var backdrop by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(category.id) {
        if (movies == null) {
            runCatching {
                val api = Session.api!!
                if (category == Catalog.RECENT)
                    Catalog.sort(api.allMovies(), CatalogSort.RECENT, { it.name }, { it.rating }, { it.added }).take(300)
                else api.movies(category.id)
            }
                .onSuccess { movies = it; Session.moviesByCategory[category.id] = it }
                .onFailure { error = friendlyMessage(it) }
        }
    }
    // Sagas et jaquettes manquantes trouvées sur Internet (TMDB), si une clé est réglée
    LaunchedEffect(category.id, movies != null, Prefs.tmdbKey) {
        val l = movies ?: return@LaunchedEffect
        Tmdb.enrichMovies(l) { updated -> movies = updated; Session.moviesByCategory[category.id] = updated }
    }

    val list = movies
    var decade by remember { mutableStateOf<Int?>(null) }
    var minRating by remember { mutableStateOf<Int?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    val genres = remember(list) { Catalog.genres(list ?: emptyList()) }
    val isRecent = category == Catalog.RECENT
    val hidden = if (isRecent) lockedIds(Session.vodCategories) else emptySet()
    val filteredList = remember(list, decade, minRating, genre, hidden) {
        (list ?: emptyList()).filter { Catalog.matches(it, decade, minRating, genre) && it.categoryId !in hidden }
    }
    val sorted = remember(filteredList, Prefs.sort) {
        Catalog.sort(filteredList, if (isRecent) CatalogSort.RECENT else Prefs.sort, { it.name }, { it.rating }, { it.added })
    }
    val entries = remember(sorted, Prefs.groupSagas, Tmdb.version) {
        if (Prefs.groupSagas && !isRecent) Catalog.groupSagas(sorted) else sorted.map { Catalog.MovieEntry.Single(it) }
    }
    LaunchedEffect(list != null) { if (!entries.isEmpty()) runCatching { first.requestFocus() } }

    BackdropBackground(backdrop) {
        when {
            error != null -> Message("Impossible de charger les films", error)
            list == null -> Loading()
            list.isEmpty() -> Message("Aucun film dans cette catégorie")
            else -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                ScreenTitle(category.name)
                if (!isRecent) SortBar(showSagas = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                    item {
                        Chip(
                            when (decade) { null -> "Années : toutes"; 1990 -> "Avant 2000"; else -> "Années $decade" },
                            selected = decade != null
                        ) {
                            decade = when (decade) { null -> 2020; 2020 -> 2010; 2010 -> 2000; 2000 -> 1990; else -> null }
                        }
                    }
                    item {
                        Chip(if (minRating == null) "Note : toutes" else "Note ≥ $minRating", selected = minRating != null) {
                            minRating = when (minRating) { null -> 6; 6 -> 7; 7 -> 8; else -> null }
                        }
                    }
                    if (genres.isNotEmpty()) {
                        item {
                            Chip(genre ?: "Genre : tous", selected = genre != null) {
                                val i = genres.indexOf(genre)
                                genre = if (i + 1 < genres.size) genres[i + 1] else null
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(128.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(entries) { entry ->
                        Box(
                            (if (entry == entries.first()) Modifier.focusRequester(first) else Modifier)
                                // La rangée sélectionnée remonte toute seule en haut de l'écran
                                .onFocusChanged { if (it.hasFocus) gridScope.launch { gridState.animateScrollToItem(entries.indexOf(entry)) } }
                        ) {
                            when (entry) {
                                is Catalog.MovieEntry.Single -> PosterCard(
                                    entry.movie.name, entry.movie.icon, onFocus = { backdrop = entry.movie.icon }
                                ) {
                                    nav.go(Screen.MovieDetail(entry.movie))
                                }
                                is Catalog.MovieEntry.Saga -> PosterCard(
                                    entry.name, entry.movies.first().icon, badge = "${entry.movies.size} films",
                                    onFocus = { backdrop = entry.movies.first().icon }
                                ) {
                                    nav.go(Screen.Saga(entry.name, entry.movies))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SagaScreen(nav: Navigator, name: String, movies: List<Movie>) {
    val first = remember { FocusRequester() }
    // Retour : remonte en haut de la grille avant de quitter l'écran
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val gridScope = androidx.compose.runtime.rememberCoroutineScope()
    BackToTop(gridState.firstVisibleItemIndex == 0, { gridState.scrollToItem(0) }, first)
    var backdrop by remember { mutableStateOf(movies.firstOrNull()?.icon) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackdropBackground(backdrop) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            ScreenTitle("$name · ${movies.size} films")
            LazyVerticalGrid(
                    state = gridState,
                columns = GridCells.Adaptive(128.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(movies) { m ->
                    Box(
                            (if (m == movies.first()) Modifier.focusRequester(first) else Modifier)
                                // La rangée sélectionnée remonte toute seule en haut de l'écran
                                .onFocusChanged { if (it.hasFocus) gridScope.launch { gridState.animateScrollToItem(movies.indexOf(m)) } }
                        ) {
                        PosterCard(m.name, m.icon, onFocus = { backdrop = m.icon }) { nav.go(Screen.MovieDetail(m)) }
                    }
                }
            }
        }
    }
}

@Composable
fun MovieDetailScreen(nav: Navigator, movie: Movie) {
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    val key = "movie-${movie.id}"
    // Relu à chaque retour sur l'écran (après la lecture)
    val resume = Prefs.progress(key)
    val first = remember { FocusRequester() }

    LaunchedEffect(movie.id) {
        runCatching { first.requestFocus() }
        info = runCatching { Session.api?.movieInfo(movie.id) }.getOrNull()
    }

    fun play(start: Long?) {
        val url = Session.api?.movieUrl(movie) ?: return
        nav.go(Screen.Play(PlayItem(movie.name, url, resumeKey = key, startAt = start, image = info?.cover ?: movie.icon)))
    }

    BackdropBackground(info?.backdrop ?: info?.cover ?: movie.icon) {
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Poster(info?.cover ?: movie.icon, Modifier.width(230.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(movie.name, fontSize = 26.sp, fontWeight = FontWeight.Black)
                val details = listOfNotNull(info?.year ?: movie.year?.toString(), info?.genre, info?.duration).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, color = Muted)
                if (movie.rating > 0) Text("★ ${"%.1f".format(movie.rating)}", color = Color(0xFFFFC107))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    if (resume != null) {
                        ButtonTile("▶  Reprendre à ${formatDuration(resume.position)}", Modifier.focusRequester(first)) { play(resume.position) }
                        ButtonTile("Depuis le début", primary = false) { play(null) }
                    } else {
                        ButtonTile(if (Prefs.isWatched(key)) "▶  Revoir" else "▶  Lecture", Modifier.focusRequester(first)) { play(null) }
                    }
                    WatchlistButton(movie.watchEntry())
                }
                if (resume != null) {
                    LinearProgressIndicator(progress = { resume.fraction }, color = KingRed,
                        trackColor = Color.White.copy(alpha = 0.2f), modifier = Modifier.width(360.dp))
                }
                info?.plot?.let { Text(it, fontSize = 15.sp, maxLines = 7, overflow = TextOverflow.Ellipsis) }
                info?.cast?.let { Text("Avec : $it", color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                info?.director?.let { Text("Réalisation : $it", color = Muted, fontSize = 13.sp) }
            }
        }
    }
}

// ---------- Séries ----------

@Composable
fun SeriesGridScreen(nav: Navigator, category: Category) {
    var loaded by remember { mutableStateOf(Session.seriesByCategory[category.id]) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    // Retour : remonte en haut de la grille avant de quitter l'écran
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val gridScope = androidx.compose.runtime.rememberCoroutineScope()
    BackToTop(gridState.firstVisibleItemIndex == 0, { gridState.scrollToItem(0) }, first)
    var backdrop by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(category.id) {
        if (loaded == null) {
            runCatching {
                val api = Session.api!!
                if (category == Catalog.RECENT)
                    Catalog.sort(api.allSeries(), CatalogSort.RECENT, { it.name }, { it.rating }, { it.added }).take(300)
                else api.series(category.id)
            }
                .onSuccess { loaded = it; Session.seriesByCategory[category.id] = it }
                .onFailure { error = friendlyMessage(it) }
        }
    }
    LaunchedEffect(category.id, loaded != null, Prefs.tmdbKey) {
        val l = loaded ?: return@LaunchedEffect
        Tmdb.enrichSeries(l) { updated -> loaded = updated; Session.seriesByCategory[category.id] = updated }
    }
    val list = loaded
    val isRecent = category == Catalog.RECENT
    val hidden = if (isRecent) lockedIds(Session.seriesCategories) else emptySet()
    val sorted = remember(list, Prefs.sort, hidden, Prefs.groupSagas) {
        val visible = (list ?: emptyList()).filter { it.categoryId !in hidden }
        // Une même série en plusieurs qualités : on ne garde que la meilleure copie
        val kept = if (Prefs.groupSagas) Duplicates.keepBest(visible, { it.name }) else visible
        Catalog.sort(kept, if (isRecent) CatalogSort.RECENT else Prefs.sort, { it.name }, { it.rating }, { it.added })
    }
    LaunchedEffect(list != null) { if (sorted.isNotEmpty()) runCatching { first.requestFocus() } }

    BackdropBackground(backdrop) {
        when {
            error != null -> Message("Impossible de charger les séries", error)
            list == null -> Loading()
            list.isEmpty() -> Message("Aucune série dans cette catégorie")
            else -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                ScreenTitle(category.name)
                if (!isRecent) SortBar(showSagas = true, series = true)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(128.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(sorted) { s ->
                        Box(
                            (if (s == sorted.first()) Modifier.focusRequester(first) else Modifier)
                                // La rangée sélectionnée remonte toute seule en haut de l'écran
                                .onFocusChanged { if (it.hasFocus) gridScope.launch { gridState.animateScrollToItem(sorted.indexOf(s)) } }
                        ) {
                            PosterCard(s.name, s.cover, onFocus = { backdrop = s.cover }) { nav.go(Screen.SeriesDetail(s)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(nav: Navigator, series: SeriesItem) {
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableIntStateOf(-1) }
    val first = remember { FocusRequester() }

    LaunchedEffect(series.id) {
        runCatching { Session.api!!.seriesInfo(series.id) }
            .onSuccess { i ->
                info = i
                if (season == -1) season = i.seasons.keys.firstOrNull { it > 0 } ?: i.seasons.keys.firstOrNull() ?: 1
            }
            .onFailure { error = friendlyMessage(it) }
    }
    LaunchedEffect(info != null) { if (info != null) runCatching { first.requestFocus() } }

    BackdropBackground(info?.backdrop ?: info?.cover ?: series.cover) {
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(200.dp)) {
                Poster(info?.cover ?: series.cover)
                Text(series.name, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
                val details = listOfNotNull(info?.year, info?.genre).joinToString(" · ")
                Spacer(Modifier.height(8.dp))
                WatchlistButton(series.watchEntry())
                if (details.isNotEmpty()) Text(details, color = Muted, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val i = info
                when {
                    error != null -> Message("Impossible de charger la série", error)
                    i == null -> Loading()
                    i.seasons.isEmpty() -> Message("Aucun épisode disponible")
                    else -> {
                        i.plot?.let { Text(it, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp)) }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                            items(i.seasons.keys.toList()) { n ->
                                Chip(if (n == 0) "Bonus" else "Saison $n", selected = n == season) { season = n }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val episodes = i.seasons[season] ?: emptyList()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                            items(episodes) { e ->
                                Box(if (e == episodes.first()) Modifier.focusRequester(first) else Modifier) {
                                    EpisodeRow(e) { _ ->
                                        val api = Session.api ?: return@EpisodeRow
                                        // L'épisode choisi suivi du reste de la saison (lecture enchaînée)
                                        val queue = episodes.map { ep ->
                                            val k = "episode-${ep.id}"
                                            PlayItem(
                                                "${series.name} · ${ep.title}", api.episodeUrl(ep), resumeKey = k,
                                                startAt = Prefs.progress(k)?.position, image = ep.image ?: i.cover ?: series.cover
                                            )
                                        }
                                        val index = episodes.indexOf(e)
                                        nav.go(Screen.Play(queue[index].copy(siblings = queue, siblingIndex = index, autoNext = true)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(e: Episode, onPlay: (Long?) -> Unit) {
    val key = "episode-${e.id}"
    val resume = Prefs.progress(key)
    val watched = Prefs.isWatched(key)
    FocusCard(onClick = { onPlay(resume?.position) }, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${e.number}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                e.duration?.let { Text(it, fontSize = 12.sp, color = Muted) }
                e.plot?.let { Text(it, fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (resume != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(progress = { resume.fraction }, color = KingRed,
                            trackColor = Color.White.copy(alpha = 0.2f), modifier = Modifier.width(160.dp))
                        Text("Reprendre à ${formatDuration(resume.position)}", fontSize = 11.sp, color = Muted)
                    }
                }
            }
            if (watched && resume == null) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Vu", tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = KingRed, modifier = Modifier.size(30.dp))
        }
    }
}

// ---------- Recherche (clavier ou dictée) ----------

@Composable
fun SearchScreen(nav: Navigator) {
    if (Session.api == null) {
        XtreamOnly("la recherche de films et séries")
        return
    }
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var movies by remember { mutableStateOf(Session.allMovies) }
    var series by remember { mutableStateOf(Session.allSeries) }
    val field = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { field.requestFocus() }
        if (Prefs.hasPin) {
            // Noms des catégories pour cacher celles qui sont verrouillées
            if (Session.vodCategories.isEmpty()) runCatching { Session.api?.vodCategories() }.getOrNull()?.let { Session.vodCategories = it }
            if (Session.seriesCategories.isEmpty()) runCatching { Session.api?.seriesCategories() }.getOrNull()?.let { Session.seriesCategories = it }
        }
        if (movies == null) {
            movies = runCatching { Session.api?.allMovies() }.getOrNull() ?: emptyList()
            Session.allMovies = movies
        }
        if (series == null) {
            series = runCatching { Session.api?.allSeries() }.getOrNull() ?: emptyList()
            Session.allSeries = series
        }
    }

    val cleaned = Catalog.cleanQuery(query)
    val lockedVod = Session.vodCategories.filter { Prefs.isLocked(it.name) }.map { it.id }.toSet()
    val lockedSeries = Session.seriesCategories.filter { Prefs.isLocked(it.name) }.map { it.id }.toSet()
    val movieResults = remember(cleaned, movies, lockedVod) {
        Catalog.rank((movies ?: emptyList()).filter { it.categoryId !in lockedVod }, cleaned, { it.name })
    }
    val seriesResults = remember(cleaned, series, lockedSeries) {
        Catalog.rank((series ?: emptyList()).filter { it.categoryId !in lockedSeries }, cleaned, { it.name })
    }

    KingBackground(dimmed = true) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Titre d'un film ou d'une série") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = KingRed,
                        unfocusedBorderColor = Color.Transparent,
                        focusedLabelColor = KingRed,
                        unfocusedLabelColor = Color.DarkGray,
                        cursorColor = KingRed
                    ),
                    modifier = Modifier.okOpensKeyboard().width(560.dp).focusRequester(field)
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

            when {
                movies == null || series == null -> Loading()
                cleaned.isEmpty() -> Message("Recherche dans tout le catalogue", "Films et séries de ton abonnement.")
                movieResults.isEmpty() && seriesResults.isEmpty() -> Message("Aucun résultat pour « $cleaned »")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(128.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    if (movieResults.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Films (${movieResults.size})", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        items(movieResults) { m -> PosterCard(m.name, m.icon) { nav.go(Screen.MovieDetail(m)) } }
                    }
                    if (seriesResults.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Séries (${seriesResults.size})", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        items(seriesResults) { s -> PosterCard(s.name, s.cover) { nav.go(Screen.SeriesDetail(s)) } }
                    }
                }
            }
        }
    }
}
