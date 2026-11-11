package com.king365.fire

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.widthIn
import org.videolan.libvlc.MediaPlayer
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/// Crée un lecteur ExoPlayer avec le bon identifiant d'appli et les redirections http ↔ https
@OptIn(UnstableApi::class)
fun buildExoPlayer(context: Context): ExoPlayer {
    val renderers = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Http.userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    // Flux TS des fournisseurs IPTV : sans ces options, certaines chaînes restent figées au démarrage
    val extractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        .setTsExtractorTimestampSearchBytes(1500 * 188)
    val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors)
    // Démarre dès 1 seconde de vidéo reçue (chaînes plus rapides à lancer),
    // puis garde une bonne réserve pour encaisser les ralentissements du réseau
    val buffer = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 50_000, 1_000, 2_500)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(sources)
        .setLoadControl(buffer)
        .build()
}

/// Lecteur qui transforme les flèches haut/bas en zapping et le bouton ≡ en minuteur de veille,
/// quand les commandes de lecture sont masquées
@OptIn(UnstableApi::class)
@SuppressLint("ViewConstructor")
class ZapPlayerView(context: Context) : PlayerView(context) {
    var onZap: ((Int) -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    var onBack: (() -> Unit)? = null
    /// Replay : saut en avant ou en arrière (millisecondes) avec haut/bas, pour passer les pubs
    var onSkip: ((Long) -> Unit)? = null
    /// Le menu de la vidéo s'affiche ou se cache (pour la barre d'infos de la chaîne)
    var onControllerVisible: ((Boolean) -> Unit)? = null

    /// Boutons ▲ ▼ à droite : ils apparaissent avec les commandes de lecture et se cachent avec elles
    private val sideBar = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun sideButton(label: String, description: String, action: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 24f
        setTextColor(android.graphics.Color.WHITE)
        gravity = Gravity.CENTER
        contentDescription = description
        isFocusable = true
        isClickable = true
        fun paint(focused: Boolean) {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (focused) KingRed.toArgb() else 0xB3000000.toInt())
                setStroke(dp(2), if (focused) android.graphics.Color.WHITE else 0x40FFFFFF)
            }
            scaleX = if (focused) 1.1f else 1f
            scaleY = scaleX
        }
        paint(false)
        setOnFocusChangeListener { _, focused -> paint(focused) }
        setOnClickListener { action() }
    }

    init {
        listOf(
            sideButton("▲", "Chaîne précédente") { onZap?.invoke(-1) },
            sideButton("▼", "Chaîne suivante") { onZap?.invoke(1) }
        ).forEach { button ->
            sideBar.addView(button, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(14) })
        }
        addView(
            sideBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply { marginEnd = dp(28) }
        )
        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            val show = visibility == View.VISIBLE && onZap != null
            sideBar.visibility = if (show) View.VISIBLE else View.GONE
            onControllerVisible?.invoke(visibility == View.VISIBLE)
            // Menu caché : le lecteur reprend la main, sinon les flèches ne zappent plus
            if (visibility != View.VISIBLE) keepFocus()
        })
    }

    /// Redonne le focus au lecteur (après un zapping ou quand le menu se cache)
    fun keepFocus(force: Boolean = false) {
        post {
            if (force || (!isControllerFullyVisible && (!hasFocus() || sideBar.hasFocus()))) {
                val before = descendantFocusability
                descendantFocusability = FOCUS_BLOCK_DESCENDANTS
                requestFocus()
                descendantFocusability = before
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Retour : quitte toujours la vidéo (le lecteur ne doit jamais garder cette touche)
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) onBack?.invoke()
            return true
        }
        // En direct, haut/bas zappent tout de suite, même avec le menu de la vidéo affiché
        // (sauf quand on se déplace dans les boutons ▲ ▼ de droite)
        val zap = onZap
        val zapKey = event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || event.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN
        if (zapKey && zap != null && !sideBar.hasFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                hideController()
                val up = event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP
                zap(if (up) -1 else 1)
            }
            return true // le relâchement aussi : sinon le menu de la vidéo réapparaîtrait
        }
        val skip = onSkip
        if (zapKey && skip != null && zap == null && !sideBar.hasFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val up = event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP
                skip(if (up) -SKIP_MS else SKIP_MS)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && !isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> onMenu?.let { it(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

/// Saut du replay pour passer les pubs : 2 minutes
const val SKIP_MS = 120_000L

/// Lecteur vu par Alexa et la télécommande : « Alexa, suivant / précédent » change de chaîne
@OptIn(UnstableApi::class)
private class ZapAwarePlayer(player: Player) : ForwardingPlayer(player) {
    var onZap: ((Int) -> Unit)? = null

    private val zapCommands = listOf(
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
    )

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        return if (onZap == null) base else base.buildUpon().addAll(*zapCommands.toIntArray()).build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        (onZap != null && command in zapCommands) || super.isCommandAvailable(command)

    override fun seekToNext() { onZap?.invoke(1) ?: super.seekToNext() }
    override fun seekToNextMediaItem() { onZap?.invoke(1) ?: super.seekToNextMediaItem() }
    override fun seekToPrevious() { onZap?.invoke(-1) ?: super.seekToPrevious() }
    override fun seekToPreviousMediaItem() { onZap?.invoke(-1) ?: super.seekToPreviousMediaItem() }
}

/// Lecteur plein écran : direct (TS, HLS), films et séries (MP4, MKV, AVI…), langues et sous-titres,
/// commandes Alexa, zapping, épisode suivant automatique et minuteur de veille
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(item: PlayItem, onExit: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(item) }
    var error by remember { mutableStateOf<String?>(null) }
    var upNext by remember { mutableStateOf<PlayItem?>(null) }
    var countdown by remember { mutableIntStateOf(10) }
    var sleepEnd by remember { mutableStateOf<Long?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    // Barre d'infos de la chaîne (en bas) : visible avec le menu de la vidéo
    var controlsVisible by remember { mutableStateOf(false) }
    var infoFlash by remember { mutableStateOf(false) }
    var infoTick by remember { mutableIntStateOf(0) }
    var programmes by remember { mutableStateOf<List<Programme>>(emptyList()) }
    var bannerTick by remember { mutableIntStateOf(0) }

    val player = remember { buildExoPlayer(context) }
    // Lecteur VLC de secours (créé seulement si besoin)
    var useVlc by remember { mutableStateOf(false) }
    var vlcPaused by remember { mutableStateOf(false) }
    var vlcRetries by remember { mutableIntStateOf(0) }
    val vlcHolder = remember { arrayOfNulls<VlcEngine>(1) }
    fun vlc(): VlcEngine = vlcHolder[0] ?: VlcEngine(context).also { vlcHolder[0] = it }
    // Permet à Alexa et aux touches lecture/pause de la télécommande de piloter la vidéo
    val remote = remember { ZapAwarePlayer(player) }
    val session = remember { runCatching { MediaSession.Builder(context, remote).build() }.getOrNull() }
    val latest by rememberUpdatedState(current)

    fun saveProgress() {
        val playing = latest
        val key = playing.resumeKey ?: return
        vlcHolder[0]?.let { v ->
            if (useVlc) {
                val length = v.player.length
                if (length > 0) Prefs.saveProgress(key, v.player.time, length, playing)
                return
            }
        }
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        Prefs.saveProgress(key, player.currentPosition, duration, playing)
    }

    fun sibling(index: Int): PlayItem = current.siblings[index].copy(
        siblings = current.siblings, siblingIndex = index, autoNext = current.autoNext
    )

    fun show(text: String) {
        banner = text
        bannerTick++
    }

    fun play(next: PlayItem) {
        saveProgress()
        upNext = null
        error = null
        current = next
    }

    var retries by remember { mutableIntStateOf(0) }

    /// Le lecteur de la Fire TV n'y arrive pas : on passe sur VLC (et on s'en souvient pour cette chaîne)
    fun switchToVlc() {
        if (useVlc) return
        VlcMemory.remember(latest.url)
        player.stop()
        error = null
        vlcRetries = 0
        useVlc = true
        show("Lecture avec VLC…")
    }

    // Lien en cours : celui de la chaîne, ou son autre format (.ts ⇄ .m3u8) si celui-là marche mieux
    var playingUrl by remember { mutableStateOf("") }

    fun reconnect() {
        retries++
        // Un essai sur deux dans l'autre format : beaucoup de chaînes ne marchent que dans l'un des deux
        val alt = LiveFormat.alternate(latest.url)
        val url = if (alt != null && playingUrl == latest.url) alt else latest.url
        if (alt != null) {
            if (url == alt) LiveFormat.remember(latest.url) else LiveFormat.forget(latest.url)
        }
        show(if (url != playingUrl && alt != null) "Essai dans un autre format…" else "Reconnexion…")
        playingUrl = url
        // Ferme d'abord l'ancienne connexion (abonnements limités à un écran)
        player.stop()
        player.setMediaItem(MediaItem.fromUri(url))
        player.playWhenReady = true
        player.prepare()
    }

    // (Re)charge la vidéo quand l'élément change (zapping, épisode suivant)
    LaunchedEffect(current) {
        retries = 0
        vlcRetries = 0
        vlcPaused = false
        // Formats que le lecteur de la Fire TV ne lit pas (AVI, WMV, DivX…) : VLC directement, sans attendre l'échec
        useVlc = VlcMemory.contains(current.url) || VlcMemory.needsVlc(current.url)
        if (useVlc) {
            player.stop()
            return@LaunchedEffect
        }
        // Chaîne qui a déjà eu besoin de l'autre format : on commence directement par celui-là
        val start = if (current.live && LiveFormat.prefersAlternate(current.url))
            LiveFormat.alternate(current.url) ?: current.url else current.url
        playingUrl = start
        // Reprise : on demande directement la bonne position (au lieu de charger le début puis de sauter)
        val resumeAt = current.startAt
        if (resumeAt != null) player.setMediaItem(MediaItem.fromUri(start), resumeAt)
        else player.setMediaItem(MediaItem.fromUri(start))
        player.playWhenReady = true
        player.prepare()
    }

    // VLC : lance la chaîne (au passage sur VLC ou en zappant), se reconnecte, ou affiche un message clair
    LaunchedEffect(current, useVlc) {
        if (!useVlc) {
            vlcHolder[0]?.player?.stop()
            return@LaunchedEffect
        }
        val engine = vlc()
        val failMessage = "Impossible de lire « ${current.title} ».\n" +
            "La chaîne est peut-être hors ligne, ou tous tes écrans sont utilisés " +
            "(vérifie dans Réglages → Tester la connexion)."
        var everPlayed = false
        var waited = 0

        fun retry() {
            val now = latest
            if (vlcRetries >= 3) {
                engine.player.stop()
                error = failMessage
                return
            }
            vlcRetries++
            // Un essai sur deux dans l'autre format de la chaîne (.ts ⇄ .m3u8)
            val alt = if (now.live) LiveFormat.alternate(now.url) else null
            show("Reconnexion…")
            waited = 0
            engine.play(if (alt != null && vlcRetries % 2 == 1) alt else now.url)
        }

        engine.player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> {
                    everPlayed = true
                    vlcRetries = 0
                    error = null
                }
                MediaPlayer.Event.EncounteredError -> retry()
                MediaPlayer.Event.EndReached -> {
                    val now = latest
                    when {
                        now.live -> retry() // un direct ne se termine pas : le flux a coupé
                        now.autoNext && Premium.active && now.siblingIndex + 1 < now.siblings.size -> upNext = now.siblings[now.siblingIndex + 1]
                            .copy(siblings = now.siblings, siblingIndex = now.siblingIndex + 1, autoNext = true)
                        else -> onExit()
                    }
                }
            }
        }
        engine.play(current.url, current.startAt)

        // Démarrage qui n'aboutit pas, ou direct figé : nouvel essai toutes les 15 secondes
        while (true) {
            delay(1_000)
            if (error != null || vlcPaused) { waited = 0; continue }
            if (engine.player.isPlaying) { waited = 0; continue }
            waited++
            if (waited >= 15) retry()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                // Format que la Fire TV ne sait pas décoder : VLC tout de suite
                if (e.errorCode in listOf(
                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                    )
                ) { switchToVlc(); return }
                // Direct : on retente tout seul, puis VLC prend le relais
                if (latest.live && retries >= 5) { switchToVlc(); return }
                if (latest.live && retries < 5) {
                    if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        player.seekToDefaultPosition()
                        player.prepare()
                    } else {
                        reconnect()
                    }
                    return
                }
                error = "Impossible de lire « ${latest.title} ».\n" +
                    when (e.errorCode) {
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Le serveur refuse le flux (abonnement ou nombre d'écrans)."
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Problème de connexion."
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Format non pris en charge par la Fire TV."
                        else -> "Le flux est peut-être hors ligne."
                    }
            }

            // Direct mis en pause par le système (son pris par une autre appli, Alexa…) : on relance
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && latest.live && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    player.playWhenReady = true
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.isPlaying) retries = 0
                // Un direct ne se termine pas : le flux a coupé, on se reconnecte
                if (state == Player.STATE_ENDED && latest.live) { reconnect(); return }
                if (state != Player.STATE_ENDED) return
                saveProgress()
                val now = latest
                if (now.autoNext && Premium.active && now.siblingIndex + 1 < now.siblings.size) {
                    upNext = now.siblings[now.siblingIndex + 1].copy(
                        siblings = now.siblings, siblingIndex = now.siblingIndex + 1, autoNext = true
                    )
                } else if (!now.live) {
                    onExit()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            session?.release()
            player.release()
            vlcHolder[0]?.release()
        }
    }

    // Direct figé (chargement sans fin ou pause non demandée) : on relance
    LaunchedEffect(player) {
        var stuck = 0
        var black = 0
        var everStarted = false
        var watched: PlayItem? = null
        while (true) {
            delay(2_000)
            if (watched !== latest) { watched = latest; everStarted = false; stuck = 0 }
            if (player.isPlaying) everStarted = true
            // Film ou épisode qui ne démarre pas au bout de 15 s : VLC prend le relais
            if (!latest.live && error == null && !useVlc) {
                if (player.isPlaying || !player.playWhenReady || everStarted) { stuck = 0; continue }
                stuck += 2
                if (stuck >= 15) { stuck = 0; switchToVlc() }
                continue
            }
            if (!latest.live || error != null || useVlc) { stuck = 0; black = 0; continue }
            if (player.isPlaying) {
                stuck = 0
                retries = 0
                // Le son passe mais pas l'image (écran noir) : VLC prend le relais
                black = if (player.videoSize.width == 0) black + 2 else 0
                if (black >= 8) { black = 0; switchToVlc() }
                continue
            }
            if (!player.playWhenReady) continue // pause demandée avec la télécommande
            stuck += 2
            if (stuck >= 12) {
                stuck = 0
                if (retries >= 2) {
                    // Toujours rien après 2 essais : VLC prend le relais
                    switchToVlc()
                } else {
                    reconnect()
                }
            }
        }
    }

    // Mémorise la position toutes les 5 secondes pour la reprise
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            saveProgress()
        }
    }

    // Compte à rebours avant l'épisode suivant
    LaunchedEffect(upNext) {
        val next = upNext ?: return@LaunchedEffect
        countdown = 10
        while (countdown > 0) {
            delay(1_000)
            countdown--
        }
        play(next)
    }

    // Minuteur de veille
    LaunchedEffect(sleepEnd) {
        val end = sleepEnd ?: return@LaunchedEffect
        delay((end - System.currentTimeMillis()).coerceAtLeast(0))
        player.pause()
        vlcHolder[0]?.player?.pause()
        onExit()
    }

    LaunchedEffect(bannerTick) {
        if (banner != null) {
            delay(2_500)
            banner = null
        }
    }

    val canZap = !current.autoNext && current.siblings.size > 1

    /// Replay : saute 2 minutes (bas = en avant pour passer la pub, haut = retour en arrière)
    fun skipBy(delta: Long) {
        val engine = vlcHolder[0]
        if (useVlc && engine != null) {
            engine.player.time = (engine.player.time + delta).coerceAtLeast(0)
        } else {
            val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            player.seekTo((player.currentPosition + delta).coerceIn(0, max))
        }
        show(if (delta > 0) "⏭ +2 min (passer la pub)" else "⏮ −2 min")
    }

    /// Montre la barre d'infos quelques secondes (après un changement de chaîne, ou OK avec VLC)
    fun flashInfo() {
        infoFlash = true
        infoTick++
    }

    fun zapBy(delta: Int) {
        val count = current.siblings.size
        if (count < 2) return
        val next = sibling((current.siblingIndex + delta + count) % count)
        play(next)
        flashInfo()
    }

    // Replay : rappel des touches pour passer les pubs
    LaunchedEffect(current) {
        if (current.replay) {
            delay(1_500)
            show("Bas : +2 min (passer la pub) · Haut : −2 min")
        }
    }

    // Programme en cours et suivant de la chaîne (mis à jour chaque minute)
    LaunchedEffect(current) {
        programmes = emptyList()
        val id = current.streamId ?: return@LaunchedEffect
        if (!current.live || Session.api == null) return@LaunchedEffect
        while (true) {
            programmes = Epg.short(id)
            delay(60_000)
        }
    }
    LaunchedEffect(infoTick) {
        delay(5_000)
        infoFlash = false
    }

    fun cycleSleep() {
        if (!Premium.require("Le minuteur de veille")) return
        // 30 min → 60 min → 90 min → désactivé
        val minutesLeft = sleepEnd?.let { ((it - System.currentTimeMillis()) / 60_000).toInt() + 1 }
        val next = when {
            minutesLeft == null -> 30
            minutesLeft <= 30 -> 60
            minutesLeft <= 60 -> 90
            else -> 0
        }
        if (next == 0) {
            sleepEnd = null
            show("Minuteur de veille désactivé")
        } else {
            sleepEnd = System.currentTimeMillis() + next * 60_000L
            show("Arrêt dans $next min")
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (useVlc) {
            AndroidView(
                factory = { ctx ->
                    VlcView(ctx).also { view ->
                        vlc().player.attachViews(view.layout, null, false, false)
                        view.post { view.requestFocus() }
                    }
                },
                update = { view ->
                    view.onKey = { code ->
                        val engine = vlcHolder[0]
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                                if (canZap) zapBy(-1) else if (current.replay) skipBy(-SKIP_MS); true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                if (canZap) zapBy(1) else if (current.replay) skipBy(SKIP_MS); true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                engine?.player?.let { p ->
                                    if (p.isPlaying) { p.pause(); vlcPaused = true; show("⏸ Pause") }
                                    else { p.play(); vlcPaused = false; show("▶ Lecture") }
                                    flashInfo()
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                if (!current.live) engine?.player?.let { it.time = (it.time - 10_000).coerceAtLeast(0); show("◀◀ 10 s") }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                if (!current.live) engine?.player?.let { it.time = it.time + 30_000; show("▶▶ 30 s") }
                                true
                            }
                            KeyEvent.KEYCODE_MENU -> { cycleSleep(); true }
                            KeyEvent.KEYCODE_BACK -> { upNext = null; onExit(); true }
                            else -> false
                        }
                    }
                },
                onRelease = { vlcHolder[0]?.player?.detachViews() },
                modifier = Modifier.fillMaxSize()
            )
        } else AndroidView(
            factory = { ctx ->
                ZapPlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
                    post { requestFocus() }
                }
            },
            update = { view ->
                view.onBack = {
                    upNext = null
                    onExit()
                }
                view.onControllerVisible = { controlsVisible = it }
                view.onSkip = if (current.replay) { delta -> skipBy(delta) } else null
                view.onZap = if (canZap) { delta ->
                    zapBy(delta)
                    view.keepFocus()
                } else null
                // ≡ : menu du lecteur (sur un film, directement le minuteur de veille)
                // ≡ : minuteur de veille (30 → 60 → 90 min → désactivé)
                view.onMenu = { cycleSleep() }
                remote.onZap = view.onZap
            },
            modifier = Modifier.fillMaxSize()
        )

        banner?.let {
            Text(
                it,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(KingRed)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }

        // Barre d'infos de la chaîne : logo, nom, programme en cours avec son résumé, programme suivant
        val infoVisible = current.live && (controlsVisible || infoFlash)
        androidx.compose.animation.AnimatedVisibility(
            visible = infoVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ChannelInfoBar(current, programmes, lifted = controlsVisible && !useVlc)
        }

        upNext?.let { next ->
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp)
                    .width(340.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6000000))
                    .border(2.dp, KingRed, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Épisode suivant dans $countdown s", color = Muted, fontSize = 13.sp)
                Text(next.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ButtonTile("▶ Lire maintenant") { play(next) }
                    ButtonTile("Annuler", primary = false) {
                        upNext = null
                        onExit()
                    }
                }
            }
        }

        error?.let {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(48.dp)
                    .widthIn(max = 900.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC000000))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(it, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (canZap) "Haut / bas pour changer de chaîne · Retour pour revenir." else "Appuie sur Retour pour revenir.",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/// Barre d'infos en bas du lecteur : chaîne, programme en cours (horaires, progression, résumé) et suivant
@Composable
private fun ChannelInfoBar(item: PlayItem, programmes: List<Programme>, lifted: Boolean) {
    val (now, next) = Epg.nowAndNext(programmes)
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
    Row(
        Modifier
            .fillMaxWidth()
            // Au-dessus de la barre de progression du menu de la vidéo quand il est affiché
            .padding(start = 32.dp, end = 32.dp, bottom = if (lifted) 96.dp else 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xD9000000))
            .border(1.dp, KingRed.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.logo != null) {
            coil.compose.AsyncImage(
                model = item.logo,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.width(96.dp).height(64.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (now != null) {
                Text(
                    "${time.format(java.util.Date(now.start))} – ${time.format(java.util.Date(now.stop))}   ${now.title}",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
                val total = (now.stop - now.start).coerceAtLeast(1)
                val done = ((System.currentTimeMillis() - now.start).toFloat() / total).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF))) {
                    Box(Modifier.fillMaxWidth(done).height(4.dp).background(KingRed))
                }
                now.desc?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 13.sp, color = Muted, maxLines = 2)
                }
            } else {
                Text("Pas de programme disponible pour cette chaîne", fontSize = 13.sp, color = Muted)
            }
            next?.let {
                Text("Ensuite à ${time.format(java.util.Date(it.start))} : ${it.title}", fontSize = 13.sp, color = Muted, maxLines = 1)
            }
        }
    }
}
