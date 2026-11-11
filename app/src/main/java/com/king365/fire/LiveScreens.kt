package com.king365.fire

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val hourFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
private fun hour(ms: Long) = hourFormat.format(Date(ms))

/// Charge la liste des chaînes une seule fois
@Composable
private fun rememberLiveLoaded(): Pair<Boolean, String?> {
    var loading by remember { mutableStateOf(Session.liveChannels.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (Session.liveChannels.isEmpty()) {
            runCatching { Session.ensureLive() }.onFailure { error = friendlyMessage(it) }
        }
        loading = false
    }
    return loading to error
}


// ---------- TV en direct et Guide ----------

@Composable
fun LiveScreen(nav: Navigator, guideMode: Boolean) {
    val (loading, error) = rememberLiveLoaded()
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf<LiveChannel?>(null) }
    val firstCategory = remember { FocusRequester() }

    var askPin by remember { mutableStateOf(false) }
    val categories = Session.liveCategories
    val current = selectedCategory ?: categories.firstOrNull { !Prefs.isLocked(it.name) }?.id
    val currentName = categories.firstOrNull { it.id == current }?.name
    val channels = remember(current, loading, Prefs.unlocked, Prefs.lockedCategories) {
        if (Prefs.isLocked(currentName)) emptyList() else Session.liveChannels.filter { it.categoryId == current }
    }

    LaunchedEffect(loading) {
        if (!loading) runCatching { firstCategory.requestFocus() }
    }

    KingBackground(dimmed = true) {
        when {
            loading -> Loading()
            error != null -> Message("Impossible de charger les chaînes", error)
            else -> Row(Modifier.fillMaxSize().padding(24.dp)) {
                Column(Modifier.width(250.dp).fillMaxHeight()) {
                    ScreenTitle(if (guideMode) "GUIDE" else "TV EN DIRECT")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                        items(categories, key = { it.id }) { c ->
                            val count = remember(c.id, loading) { Session.liveChannels.count { it.categoryId == c.id } }
                            Box(if (c.id == current) Modifier.focusRequester(firstCategory) else Modifier) {
                                ListRow(
                                    (if (Prefs.isProtected(c.name)) "🔒 " else "") + c.name,
                                    selected = c.id == current,
                                    trailing = "$count",
                                    onMenu = if (Prefs.hasPin && (Prefs.unlocked || !Prefs.isProtected(c.name))) {
                                        { Prefs.toggleLock(c.name) }
                                    } else null
                                ) {
                                    if (Prefs.isLocked(c.name)) askPin = true else selectedCategory = c.id
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(4.dp, 44.dp, 4.dp, 4.dp)
                ) {
                    items(channels, key = { it.id }) { ch ->
                        ChannelRow(
                            channel = ch,
                            showEpg = guideMode,
                            onFocus = { focused = ch },
                            onClick = { nav.go(Screen.Play(liveItemFor(ch, channels))) }
                        )
                    }
                }
                if (!guideMode) {
                    Spacer(Modifier.width(16.dp))
                    ChannelInfoPanel(focused, Modifier.width(300.dp).fillMaxHeight().padding(top = 44.dp))
                }
            }
        }
        if (askPin) UnlockOverlay { askPin = false }
    }
}

@Composable
fun ChannelRow(channel: LiveChannel, showEpg: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    var programmes by remember { mutableStateOf<List<Programme>>(emptyList()) }
    if (showEpg) {
        LaunchedEffect(channel.id) { programmes = Epg.short(channel.id) }
    }
    val (now, next) = Epg.nowAndNext(programmes)
    val favorite = Prefs.isFavorite(channel.url ?: channel.id)

    FocusCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(if (showEpg) 74.dp else 54.dp),
        focusedScale = 1.03f,
        onFocus = onFocus,
        onMenu = { Prefs.toggleFavorite(channel.url ?: channel.id) }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.icon, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 56.dp, height = 36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (showEpg) {
                    if (now != null) {
                        Text("${hour(now.start)}  ${now.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        LinearProgressIndicator(
                            progress = { now.progress() },
                            color = KingRed, trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.width(180.dp).padding(vertical = 2.dp)
                        )
                    }
                    if (next != null) {
                        Text("Ensuite ${hour(next.start)} · ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Muted)
                    }
                }
            }
            if (channel.hasArchive) Text("REPLAY", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(end = 8.dp))
            if (favorite) Icon(Icons.Filled.Star, contentDescription = "Favori", tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
        }
    }
}

/// Panneau de droite : programme en cours et suivant de la chaîne sélectionnée
@Composable
private fun ChannelInfoPanel(channel: LiveChannel?, modifier: Modifier) {
    var programmes by remember { mutableStateOf<List<Programme>>(emptyList()) }
    LaunchedEffect(channel?.id) {
        programmes = emptyList()
        val id = channel?.id ?: return@LaunchedEffect
        delay(300)
        programmes = Epg.short(id)
    }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(RowBg).padding(16.dp)) {
        if (channel == null) {
            Text("Choisis une chaîne", color = Muted)
            Text("OK pour regarder · ≡ pour l'ajouter aux favoris", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            return@Column
        }
        AsyncImage(model = channel.icon, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(80.dp))
        Text(channel.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        val (now, next) = Epg.nowAndNext(programmes)
        if (now != null) {
            Text("EN DIRECT", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(KingRed).padding(horizontal = 8.dp, vertical = 2.dp))
            Text("${hour(now.start)} – ${hour(now.stop)}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text(now.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { now.progress() }, color = KingRed, trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            now.desc?.let { Text(it, fontSize = 13.sp, color = Muted, maxLines = 5, overflow = TextOverflow.Ellipsis) }
        } else {
            Text("Pas d'information de programme", color = Muted, fontSize = 13.sp)
        }
        if (next != null) {
            Text("Ensuite · ${hour(next.start)}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            Text(next.title, fontSize = 15.sp)
        }
    }
}

// ---------- Favoris ----------

@Composable
fun FavoritesScreen(nav: Navigator) {
    val (loading, error) = rememberLiveLoaded()
    val lockedIds = Session.liveCategories.filter { Prefs.isLocked(it.name) }.map { it.id }.toSet()
    val favorites = Session.liveChannels.filter { Prefs.isFavorite(it.url ?: it.id) && it.categoryId !in lockedIds }
    val first = remember { FocusRequester() }
    LaunchedEffect(loading, favorites.size) { if (favorites.isNotEmpty()) runCatching { first.requestFocus() } }

    KingBackground(dimmed = true) {
        when {
            loading -> Loading()
            error != null -> Message("Impossible de charger les chaînes", error)
            favorites.isEmpty() -> Message("Pas encore de favoris", "Dans TV en direct, appuie sur le bouton ≡ (menu) de la télécommande sur une chaîne pour l'ajouter ici.")
            else -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                ScreenTitle("FAVORIS")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                    items(favorites, key = { it.id }) { ch ->
                        Box(if (ch == favorites.first()) Modifier.focusRequester(first) else Modifier) {
                            ChannelRow(ch, showEpg = true, onFocus = {}, onClick = { nav.go(Screen.Play(liveItemFor(ch, favorites))) })
                        }
                    }
                }
            }
        }
    }
}

// ---------- Replay ----------

@Composable
fun ReplayScreen(nav: Navigator) {
    if (Session.api == null) {
        XtreamOnly("le replay")
        return
    }
    val (loading, error) = rememberLiveLoaded()
    val lockedIds = Session.liveCategories.filter { Prefs.isLocked(it.name) }.map { it.id }.toSet()
    val channels = remember(loading, Prefs.unlocked) { Session.liveChannels.filter { it.hasArchive && it.categoryId !in lockedIds } }
    var selected by remember { mutableStateOf<LiveChannel?>(null) }
    var programmes by remember { mutableStateOf<List<Programme>>(emptyList()) }
    var loadingProgrammes by remember { mutableStateOf(false) }
    var dayOffset by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }

    LaunchedEffect(loading) { if (!loading && channels.isNotEmpty()) runCatching { first.requestFocus() } }
    LaunchedEffect(selected?.id) {
        val ch = selected ?: return@LaunchedEffect
        loadingProgrammes = true
        programmes = runCatching { Session.api?.archive(ch.id) ?: emptyList() }.getOrDefault(emptyList())
        loadingProgrammes = false
    }

    KingBackground(dimmed = true) {
        when {
            loading -> Loading()
            error != null -> Message("Impossible de charger les chaînes", error)
            channels.isEmpty() -> Message("Pas de replay", "Aucune chaîne de ton abonnement ne propose le replay.")
            else -> Row(Modifier.fillMaxSize().padding(24.dp)) {
                Column(Modifier.width(280.dp).fillMaxHeight()) {
                    ScreenTitle("REPLAY")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                        items(channels, key = { it.id }) { ch ->
                            Box(if (ch == channels.first()) Modifier.focusRequester(first) else Modifier) {
                                ListRow(ch.name, selected = ch.id == selected?.id, trailing = "${ch.archiveDays} j") {
                                    selected = ch
                                    dayOffset = 0
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val ch = selected
                    if (ch == null) {
                        Message("Choisis une chaîne", "Sélectionne une chaîne à gauche pour revoir ses programmes.")
                        return@Column
                    }
                    ScreenTitle(ch.name)
                    val days = (0 until ch.archiveDays.coerceIn(1, 14)).toList()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                        items(days) { d -> Chip(dayLabel(d), selected = d == dayOffset) { dayOffset = d } }
                    }
                    Spacer(Modifier.height(10.dp))
                    val (from, to) = dayRange(dayOffset)
                    val now = System.currentTimeMillis()
                    val shown = programmes.filter { it.start in from until to && it.stop < now && it.hasArchive }
                        .sortedByDescending { it.start }
                    when {
                        loadingProgrammes -> Loading()
                        shown.isEmpty() -> Message("Rien à revoir ce jour-là")
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                            items(shown) { p ->
                                FocusCard(
                                    onClick = {
                                        val url = Session.api?.replayUrl(ch, p) ?: return@FocusCard
                                        nav.go(Screen.Play(PlayItem(title = "${ch.name} · ${p.title}", url = url, replay = true)))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    focusedScale = 1.02f
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(hour(p.start), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(60.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            p.desc?.let { Text(it, fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                        }
                                        Text(formatDuration(p.stop - p.start), fontSize = 12.sp, color = Muted)
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

private fun dayRange(offset: Int): Pair<Long, Long> {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.DAY_OF_YEAR, -offset)
    val from = c.timeInMillis
    c.add(Calendar.DAY_OF_YEAR, 1)
    return from to c.timeInMillis
}

private fun dayLabel(offset: Int): String = when (offset) {
    0 -> "Aujourd'hui"
    1 -> "Hier"
    else -> {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -offset)
        SimpleDateFormat("EEE d", Locale.FRANCE).format(c.time).replaceFirstChar { it.uppercase() }
    }
}
