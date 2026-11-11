package com.king365.fire

import android.content.Context
import android.net.Uri
import android.view.KeyEvent
import android.widget.FrameLayout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

// ---------- Lecteur VLC (secours) ----------

/// Deuxième lecteur, utilisé quand celui de la Fire TV n'arrive pas à lire une chaîne
/// (écran noir, pause, format non pris en charge) : VLC lit pratiquement tous les flux.
class VlcEngine(context: Context) {
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--network-caching=1500", "--live-caching=1500", "--http-reconnect", "--no-stats")
    )
    val player = MediaPlayer(libVlc)

    fun play(url: String, startAtMs: Long? = null) {
        player.stop()
        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":http-user-agent=${Http.userAgent}")
        media.addOption(":http-reconnect")
        startAtMs?.let { media.addOption(":start-time=${it / 1000}") }
        player.media = media
        media.release()
        player.play()
    }

    fun release() {
        runCatching {
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
        }
    }
}

/// Mémorise les chaînes que le lecteur de la Fire TV n'arrive pas à lire : elles démarrent
/// directement avec VLC la fois suivante
object VlcMemory {
    private val set = java.util.Collections.synchronizedSet(HashSet<String>())
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("vlcChannels", Context.MODE_PRIVATE)
        set.addAll(prefs?.getStringSet("urls", emptySet()) ?: emptySet())
    }

    private fun id(url: String) = url.substringBefore('?').substringAfter("://").substringBeforeLast('.')

    fun contains(url: String) = id(url) in set

    /// Extensions que le lecteur de la Fire TV ne sait pas lire
    private val vlcOnly = setOf("avi", "wmv", "flv", "divx", "xvid", "mpg", "mpeg", "vob", "rmvb", "rm", "3gp", "m2ts", "ogv", "asf")

    fun needsVlc(url: String) = url.substringBefore('?').substringAfterLast('.', "").lowercase() in vlcOnly

    fun remember(url: String) {
        if (set.add(id(url))) prefs?.edit()?.putStringSet("urls", set.toList().takeLast(500).toSet())?.apply()
    }
}

/// Écran VLC qui reçoit les touches de la télécommande
class VlcView(context: Context) : FrameLayout(context) {
    val layout = VLCVideoLayout(context)
    var onKey: ((Int) -> Boolean)? = null

    init {
        addView(layout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        keepScreenOn = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && onKey?.invoke(event.keyCode) == true) return true
        // Les relâchements des touches gérées ne doivent pas aller plus loin
        if (event.action == KeyEvent.ACTION_UP && event.keyCode in handledKeys) return true
        return super.dispatchKeyEvent(event)
    }

    private val handledKeys = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND
    )
}
