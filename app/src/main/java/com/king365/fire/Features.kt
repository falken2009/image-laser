package com.king365.fire

import androidx.compose.foundation.text.KeyboardOptions

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val profileColors = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFFD81B60), Color(0xFF00897B), Color(0xFFFDD835)
)

private val hhmm = SimpleDateFormat("HH:mm", Locale.FRANCE)
private fun hm(ms: Long) = hhmm.format(Date(ms))

// ---------- Profils ----------

@Composable
fun ProfileAvatar(profile: Prefs.Profile, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(profileColors[profile.color % profileColors.size])
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(profile.name.take(1).uppercase(), fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold)
    }
}

/// « Qui regarde ? » — ≡ (menu) sur un profil pour le supprimer
@Composable
fun ProfilesScreen(onDone: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    LaunchedEffect(adding) { runCatching { if (adding) field.requestFocus() else first.requestFocus() } }

    KingBackground {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Qui regarde ?", fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Prefs.profiles.forEachIndexed { i, p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FocusCard(
                            onClick = {
                                Prefs.selectProfile(p.id)
                                onDone()
                            },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            shape = CircleShape,
                            focusedScale = 1.12f,
                            onMenu = { Prefs.deleteProfile(p.id) }
                        ) {
                            ProfileAvatar(p, 110.dp)
                        }
                        Text(p.name, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FocusCard(onClick = { adding = true }, shape = CircleShape, focusedScale = 1.12f) {
                        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 48.sp)
                        }
                    }
                    Text("Ajouter", fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (adding) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                        value = name, onValueChange = { name = it },
                        label = { Text("Prénom") }, singleLine = true,
                        colors = whiteFieldColors(),
                        modifier = Modifier.okOpensKeyboard().width(320.dp).focusRequester(field)
                    )
                    ButtonTile("Ajouter") {
                        if (Prefs.profiles.size >= 1 && !Premium.require("Plusieurs profils")) return@ButtonTile
                        Prefs.addProfile(name)
                        name = ""
                        adding = false
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("≡ (menu) sur un profil pour le supprimer", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun whiteFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedBorderColor = KingRed,
    unfocusedBorderColor = Color.Transparent,
    focusedLabelColor = KingRed,
    unfocusedLabelColor = Color.DarkGray,
    cursorColor = KingRed
)

// ---------- Continuer à regarder ----------

@Composable
fun ContinueRow(nav: Navigator) {
    val tick = Prefs.progressTick
    val started = remember(tick, Prefs.currentProfile) { Prefs.inProgress() }
    if (started.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("Continuer à regarder", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
            items(started, key = { it.key }) { p ->
                FocusCard(
                    onClick = {
                        val url = p.url ?: return@FocusCard
                        nav.go(Screen.Play(PlayItem(p.title ?: "Reprise", url, resumeKey = p.key, startAt = p.position, image = p.image)))
                    },
                    modifier = Modifier.width(260.dp).height(96.dp),
                    focusedScale = 1.06f,
                    onMenu = { Prefs.clearProgress(p.key) }
                ) {
                    Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(p.image, Modifier.height(80.dp))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(p.title ?: "", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(
                                progress = { p.fraction }, color = KingRed, trackColor = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            )
                            Text("Reste ${formatDuration(p.duration - p.position)}", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

// ---------- À voir plus tard ----------

@Composable
fun WatchlistScreen(nav: Navigator) {
    val list = Prefs.watchlist
    val first = remember { FocusRequester() }
    LaunchedEffect(list.size) { if (list.isNotEmpty()) runCatching { first.requestFocus() } }
    KingBackground(dimmed = true) {
        if (list.isEmpty()) {
            Message("Ta liste est vide", "Sur la fiche d'un film ou d'une série, choisis « + Ma liste » pour l'ajouter ici.")
            return@KingBackground
        }
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            ScreenTitle("MA LISTE")
            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(list, key = { it.id }) { e ->
                    Box(if (e == list.first()) Modifier.focusRequester(first) else Modifier) {
                        PosterCard(e.name, e.image, badge = if (e.kind == "series") "Série" else null) {
                            if (e.kind == "series") nav.go(Screen.SeriesDetail(e.series())) else nav.go(Screen.MovieDetail(e.movie()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchlistButton(entry: Prefs.WatchEntry) {
    val added = Prefs.inWatchlist(entry.id)
    ButtonTile(if (added) "✓ Dans ma liste" else "+ Ma liste", primary = false) { Prefs.toggleWatchlist(entry) }
}

fun Movie.watchEntry() = Prefs.WatchEntry("movie", id, name, icon, rating, ext, categoryId, year)
fun SeriesItem.watchEntry() = Prefs.WatchEntry("series", id, name, cover, rating, null, categoryId, null)

// ---------- Code parental ----------

/// Clavier numérique à la télécommande
@Composable
fun PinPad(title: String, onCancel: () -> Unit, onComplete: (String) -> Boolean) {
    var digits by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xEE000000)), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(260.dp)) {
                Text("🔒", fontSize = 40.sp)
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) { i ->
                        Box(
                            Modifier.size(18.dp).clip(CircleShape)
                                .background(if (i < digits.length) KingRed else Color.Transparent)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }
                }
                if (wrong) Text("Code incorrect", color = KingRed, modifier = Modifier.padding(top = 10.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("✕", "0", "⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            FocusCard(
                                onClick = {
                                    when (key) {
                                        "✕" -> onCancel()
                                        "⌫" -> digits = digits.dropLast(1)
                                        else -> if (digits.length < 4) {
                                            digits += key
                                            wrong = false
                                            if (digits.length == 4) {
                                                if (!onComplete(digits)) {
                                                    wrong = true
                                                    digits = ""
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(width = 72.dp, height = 54.dp)
                                    .then(if (key == "1") Modifier.focusRequester(first) else Modifier),
                                focusedScale = 1.1f
                            ) {
                                Text(key, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
    }
}

/// Saisie du code avant d'ouvrir une catégorie verrouillée
@Composable
fun UnlockOverlay(onDone: () -> Unit) {
    PinPad("Catégorie verrouillée", onCancel = onDone) { pin ->
        if (Prefs.checkPin(pin)) {
            Prefs.unlocked = true
            onDone()
            true
        } else false
    }
}

// ---------- Rappels de programmes ----------

object Reminders {
    private const val CHANNEL = "rappels"

    fun has(id: String) = Prefs.reminders().any { it.id == id }

    fun id(channel: LiveChannel, p: Programme) = "${channel.id}|${p.start}"

    /// Programme une notification 5 minutes avant le début (ou l'annule si elle existe)
    fun toggle(context: Context, channel: LiveChannel, url: String, p: Programme): Boolean {
        val id = id(channel, p)
        val list = Prefs.reminders()
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, id, channel.name, url, p.title)
        if (list.any { it.id == id }) {
            alarm.cancel(pending)
            Prefs.saveReminders(list.filter { it.id != id })
            return false
        }
        val at = maxOf(p.start - 5 * 60_000, System.currentTimeMillis() + 5_000)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (canExact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarm.setExact(AlarmManager.RTC_WAKEUP, at, pending)
        }
        Prefs.saveReminders(list + Prefs.Reminder(id, channel.name, url, p.title, p.start))
        return true
    }

    private fun pendingIntent(context: Context, id: String, channelName: String, url: String, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("id", id).putExtra("channel", channelName).putExtra("url", url).putExtra("title", title)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }

    fun notify(context: Context, channelName: String, url: String, title: String, id: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Rappels de programmes", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("playUrl", url).putExtra("playTitle", channelName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val content = PendingIntent.getActivity(context, id.hashCode(), open, flags)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL)
        else Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH)
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Bientôt sur $channelName")
            .setContentText(title)
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id.hashCode(), notification) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.notify(
            context,
            intent.getStringExtra("channel") ?: "Chaîne",
            intent.getStringExtra("url") ?: return,
            intent.getStringExtra("title") ?: "",
            intent.getStringExtra("id") ?: ""
        )
    }
}

// ---------- Grille du guide : chaînes en lignes, heures en colonnes ----------

@Composable
fun GuideGridScreen(nav: Navigator) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(Session.liveChannels.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var windowStart by remember { mutableLongStateOf(roundedNow()) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (Session.liveChannels.isEmpty()) {
            runCatching { Session.ensureLive() }.onFailure { error = friendlyMessage(it) }
        }
        loading = false
    }

    val categories = Session.liveCategories.filter { !Prefs.isLocked(it.name) }
    val current = category ?: categories.firstOrNull()?.id
    val channels = remember(current, loading) { Session.liveChannels.filter { it.categoryId == current } }
    LaunchedEffect(loading) { if (!loading) runCatching { first.requestFocus() } }

    val hours = 3
    val windowEnd = windowStart + hours * 3_600_000L

    KingBackground(dimmed = true) {
        when {
            loading -> Loading()
            error != null -> Message("Impossible de charger les chaînes", error)
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GUIDE", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Chip("◀ 1 h", selected = false) { windowStart = maxOf(windowStart - 3_600_000L, roundedNow() - 3_600_000L) }
                    Chip("Maintenant", selected = windowStart == roundedNow()) { windowStart = roundedNow() }
                    Chip("1 h ▶", selected = false) { windowStart = minOf(windowStart + 3_600_000L, roundedNow() + 24 * 3_600_000L) }
                    Text(SimpleDateFormat("EEE d · HH:mm", Locale.FRANCE).format(Date(windowStart)), color = Muted, fontSize = 13.sp)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(categories, key = { it.id }) { c ->
                        Box(if (c.id == current) Modifier.focusRequester(first) else Modifier) {
                            Chip(c.name, selected = c.id == current) { category = c.id }
                        }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val channelWidth = 160.dp
                    val gridWidth = maxWidth - channelWidth - 8.dp
                    val perMinute = gridWidth.value / (hours * 60f)
                    Column {
                        // Règle des heures
                        Row {
                            Spacer(Modifier.width(channelWidth + 8.dp))
                            Box(Modifier.width(gridWidth).height(20.dp)) {
                                repeat(hours * 2) { i ->
                                    Text(
                                        hm(windowStart + i * 1_800_000L), fontSize = 11.sp, color = Muted,
                                        modifier = Modifier.offset(x = (i * 30 * perMinute).dp)
                                    )
                                }
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(channels, key = { it.id }) { ch ->
                                GuideRow(ch, windowStart, windowEnd, perMinute, channelWidth, gridWidth,
                                    onPlay = { nav.go(Screen.Play(liveItemFor(ch, channels))) },
                                    onProgramme = { p ->
                                        if (p.start > System.currentTimeMillis()) {
                                            val url = ch.url ?: Session.api?.liveUrl(ch) ?: return@GuideRow
                                            val added = Reminders.toggle(context, ch, url, p)
                                            Toast.makeText(
                                                context,
                                                if (added) "Rappel programmé 5 min avant « ${p.title} »" else "Rappel annulé",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            nav.go(Screen.Play(liveItemFor(ch, channels)))
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun roundedNow(): Long {
    val half = 30 * 60_000L
    return System.currentTimeMillis() / half * half
}

@Composable
private fun GuideRow(
    channel: LiveChannel,
    windowStart: Long,
    windowEnd: Long,
    perMinute: Float,
    channelWidth: Dp,
    gridWidth: Dp,
    onPlay: () -> Unit,
    onProgramme: (Programme) -> Unit
) {
    var programmes by remember { mutableStateOf<List<Programme>>(emptyList()) }
    LaunchedEffect(channel.id) { programmes = Epg.short(channel.id) }
    val tick = Prefs.reminderTick
    val reminders = remember(tick) { Prefs.reminders().map { it.id }.toSet() }
    val now = System.currentTimeMillis()

    Row(verticalAlignment = Alignment.CenterVertically) {
        FocusCard(onClick = onPlay, modifier = Modifier.width(channelWidth).height(50.dp), focusedScale = 1.03f) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = channel.icon, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 40.dp, height = 28.dp))
                Text(channel.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(gridWidth).height(50.dp)) {
            val visible = programmes.filter { it.stop > windowStart && it.start < windowEnd }
            if (visible.isEmpty()) {
                Text("Pas d'information de programme", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterStart))
            }
            visible.forEach { p ->
                val start = maxOf(p.start, windowStart)
                val end = minOf(p.stop, windowEnd)
                val x = (start - windowStart) / 60_000f * perMinute
                val w = ((end - start) / 60_000f * perMinute - 3f).coerceAtLeast(6f)
                val onAir = p.isOn(now)
                FocusCard(
                    onClick = { onProgramme(p) },
                    modifier = Modifier.offset(x = x.dp).width(w.dp).fillMaxHeight(),
                    background = if (onAir) RedTileBrush else null,
                    shape = RoundedCornerShape(6.dp),
                    focusedScale = 1.02f
                ) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(
                            (if (Reminders.id(channel, p) in reminders) "🔔 " else "") + p.title,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(hm(p.start), fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
        }
    }
}

/// Chaîne avec ses voisines, pour zapper dans le lecteur
fun liveItemFor(channel: LiveChannel, list: List<LiveChannel>): PlayItem {
    val api = Session.api
    fun url(c: LiveChannel) = c.url ?: api?.liveUrl(c) ?: ""
    val siblings = list.map { PlayItem(title = it.name, url = url(it), live = true, streamId = it.id, logo = it.icon) }
    return PlayItem(
        title = channel.name, url = url(channel), live = true, streamId = channel.id, logo = channel.icon,
        siblings = siblings, siblingIndex = list.indexOf(channel).coerceAtLeast(0)
    )
}

// ---------- Multi-écran ----------

@Composable
fun MultiScreen(nav: Navigator) {
    var layout by remember { mutableIntStateOf(2) }
    val slots = remember { mutableStateListOf<LiveChannel?>(null, null, null, null) }
    var audio by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<Int?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { Session.ensureLive() }
        runCatching { first.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MULTI-ÉCRAN", fontSize = 18.sp, fontWeight = FontWeight.Black)
                Chip("2 écrans", selected = layout == 2) { layout = 2; if (audio > 1) audio = 0 }
                Chip("4 écrans", selected = layout == 4) { layout = 4 }
                Text("OK : écouter · ≡ : changer de chaîne · OK deux fois : plein écran", color = Muted, fontSize = 12.sp)
            }
            if (layout == 4) {
                Text("4 vidéos à la fois peuvent saccader sur une Fire TV Stick.", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            val rows = if (layout == 4) listOf(listOf(0, 1), listOf(2, 3)) else listOf(listOf(0, 1))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        row.forEach { index ->
                            MultiSlot(
                                channel = slots[index],
                                audible = audio == index,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                                onSelect = {
                                    val ch = slots[index]
                                    if (ch == null) picking = index
                                    else if (audio == index) nav.go(Screen.Play(liveItemFor(ch, Session.liveChannels)))
                                    else audio = index
                                },
                                onMenu = { picking = index }
                            )
                        }
                    }
                }
            }
        }
        picking?.let { index ->
            ChannelPicker(
                onPick = { ch ->
                    slots[index] = ch
                    audio = index
                    picking = null
                },
                onCancel = { picking = null }
            )
        }
    }
}

@Composable
private fun MultiSlot(channel: LiveChannel?, audible: Boolean, modifier: Modifier, onSelect: () -> Unit, onMenu: () -> Unit) {
    FocusCard(
        onClick = onSelect,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        background = if (channel == null) RedTileBrush else null,
        focusedScale = 1.0f,
        onMenu = onMenu
    ) { focused ->
        if (channel == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", fontSize = 40.sp)
                Text("Ajouter une chaîne", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            SlotVideo(channel, audible)
            Text(
                (if (audible) "🔊 " else "🔇 ") + channel.name,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            if (audible) {
                Box(Modifier.fillMaxSize().border(3.dp, KingRed, RoundedCornerShape(8.dp)))
            }
        }
    }
}

@Composable
private fun SlotVideo(channel: LiveChannel, audible: Boolean) {
    val context = LocalContext.current
    val url = channel.url ?: Session.api?.liveUrl(channel) ?: return
    val player = remember(url) {
        buildExoPlayer(context).apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(audible, player) { player.volume = if (audible) 1f else 0f }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                isFocusable = false
                keepScreenOn = true
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize()
    )
}

/// Choix d'une chaîne pour une case du multi-écran
@Composable
private fun ChannelPicker(onPick: (LiveChannel) -> Unit, onCancel: () -> Unit) {
    var category by remember { mutableStateOf<String?>(null) }
    val categories = Session.liveCategories.filter { !Prefs.isLocked(it.name) }
    val current = category ?: categories.firstOrNull()?.id
    val channels = Session.liveChannels.filter { it.categoryId == current }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    androidx.activity.compose.BackHandler { onCancel() }

    Box(Modifier.fillMaxSize().background(Color(0xF2000000))) {
        Row(Modifier.fillMaxSize().padding(24.dp)) {
            Column(Modifier.width(250.dp)) {
                ScreenTitle("Choisis une chaîne")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories, key = { it.id }) { c ->
                        Box(if (c.id == current) Modifier.focusRequester(first) else Modifier) {
                            ListRow(c.name, selected = c.id == current) { category = c.id }
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 44.dp)) {
                items(channels, key = { it.id }) { ch ->
                    ListRow(ch.name) { onPick(ch) }
                }
            }
        }
    }
}

// ---------- Réglages supplémentaires ----------

@Composable
fun ThemeChooser() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Thème :", fontSize = 14.sp)
        KingThemeChoice.entries.forEach { t ->
            Chip(t.label, selected = Prefs.theme == t) {
                if (t != KingThemeChoice.entries.first() && !Premium.require("Les thèmes de couleur")) return@Chip
                Prefs.updateTheme(t)
            }
        }
    }
}

@Composable
fun SubscriptionsChooser(onSwitch: (Prefs.Subscription) -> Unit) {
    val subs = Prefs.subscriptions
    if (subs.isEmpty()) return
    Text("Abonnements (OK pour changer · ≡ pour supprimer) :", fontSize = 14.sp)
    subs.forEach { sub ->
        ListRow(
            (if (sub.mode == "m3u") "📋 " else "🖥 ") + sub.name,
            selected = Prefs.isCurrent(sub),
            onMenu = { if (!Prefs.isCurrent(sub)) Prefs.deleteSubscription(sub.id) }
        ) { if (!Prefs.isCurrent(sub) && Premium.require("Plusieurs abonnements")) onSwitch(sub) }
    }
}

/// Étape en cours de la saisie du code parental (affichée par-dessus l'écran des réglages)
class ParentalFlow {
    var step by mutableStateOf<String?>(null)
    var firstPin = ""
}

/// Activer, changer ou désactiver le code parental
@Composable
fun ParentalSettings(flow: ParentalFlow) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Contrôle parental :", fontSize = 14.sp)
        if (Prefs.hasPin) {
            Chip("Changer le code", selected = false) { flow.step = "check-change" }
            Chip("Désactiver", selected = false) { flow.step = "check-disable" }
        } else {
            Chip("Activer", selected = false) { flow.step = "create" }
        }
    }
    if (Prefs.hasPin) {
        Text("≡ (menu) sur une catégorie (direct, films, séries) pour la verrouiller ou la déverrouiller.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun ParentalPinOverlay(flow: ParentalFlow) {
    when (flow.step) {
        "create" -> PinPad("Choisis un code", onCancel = { flow.step = null }) { pin ->
            flow.firstPin = pin
            flow.step = "confirm"
            true
        }
        "confirm" -> PinPad("Confirme le code", onCancel = { flow.step = null }) { pin ->
            if (pin == flow.firstPin) {
                Prefs.setPin(pin)
                flow.step = null
                true
            } else false
        }
        "check-change" -> PinPad("Code actuel", onCancel = { flow.step = null }) { pin ->
            if (Prefs.checkPin(pin)) { flow.step = "create"; true } else false
        }
        "check-disable" -> PinPad("Code actuel", onCancel = { flow.step = null }) { pin ->
            if (Prefs.checkPin(pin)) { Prefs.setPin(null); flow.step = null; true } else false
        }
    }
}
