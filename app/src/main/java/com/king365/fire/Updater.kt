package com.king365.fire

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ---------- Mise à jour automatique depuis GitHub ----------

/// À chaque envoi sur GitHub, une nouvelle version est compilée et publiée (onglet Releases).
/// Au démarrage, l'appli regarde si une version plus récente existe et propose de l'installer.
object Updater {
    /// `notes` = les nouveautés de cette version (fichier NOUVEAUTES.txt du dépôt GitHub)
    data class Release(val number: Int, val apkUrl: String, val notes: String = "")

    /// Demandes de vérification depuis Réglages, et le résultat à afficher
    var requests by androidx.compose.runtime.mutableIntStateOf(0)
    var lastResult by mutableStateOf<String?>(null)

    /// Dépôt GitHub où l'appli est compilée (rempli automatiquement pendant la compilation)
    private val repo: String get() = BuildConfig.UPDATE_REPO

    @Suppress("DEPRECATION")
    fun installedNumber(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    }.getOrDefault(0)

    /// Dernière version publiée, ou null si rien de plus récent
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "FalkenIPTV-Updater")
            val json = try {
                if (conn.responseCode != 200) return@runCatching null
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
            val number = json.optString("tag_name").filter { it.isDigit() }.toIntOrNull() ?: return@runCatching null
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
            }
            val notes = json.optString("body").trim().takeIf { it != "null" } ?: ""
            if (apk != null && number > installedNumber(context)) Release(number, apk, notes) else null
        }.getOrNull()
    }

    /// Télécharge l'APK ; `progress` reçoit un pourcentage (ou -1 si la taille est inconnue)
    suspend fun download(context: Context, release: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Falken-IPTV-${release.number}.apk")
        var url = URL(release.apkUrl)
        // GitHub redirige vers son serveur de fichiers
        repeat(6) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "FalkenIPTV-Updater")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    url = URL(url, conn.getHeaderField("Location") ?: throw HttpException(code))
                    return@repeat
                }
                if (code != 200) throw HttpException(code)
                val total = conn.contentLengthLong
                var done = 0L
                var last = -2
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else -1
                            if (pct != last) {
                                last = pct
                                withContext(Dispatchers.Main) { progress(pct) }
                            }
                        }
                    }
                }
                return@withContext file
            } finally {
                conn.disconnect()
            }
        }
        throw java.io.IOException("Trop de redirections.")
    }

    /// L'appli a-t-elle le droit d'installer des applis ? (à autoriser une seule fois)
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /// Ouvre l'écran d'autorisation, quand l'appareil le propose
    fun openInstallPermission(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } else false
    }.getOrDefault(false)

    /// Lance l'installation (Android affiche « Installer » puis relance l'appli à jour)
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/// Fenêtre « Mise à jour disponible » : vérifie au démarrage (ou quand `manual` change dans Réglages)
@Composable
fun UpdatePrompt(trigger: Int = 0, onNothingNew: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(trigger) {
        val found = Updater.check(context)
        if (found == null) onNothingNew?.invoke() else { Updater.lastResult = null; release = found }
    }
    val r = release ?: return

    LaunchedEffect(r) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { release = null }

    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(560.dp)
                .background(Color(0xFF141414), RoundedCornerShape(16.dp))
                .border(2.dp, KingRed, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Mise à jour disponible", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Version 1.${r.number} (installée : 1.${Updater.installedNumber(context)}). " +
                    "Tes réglages, favoris et abonnements sont conservés.",
                color = Muted, fontSize = 14.sp
            )
            // Ce qui change dans cette version
            if (r.notes.isNotBlank()) {
                Text("Nouveautés :", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(r.notes, fontSize = 14.sp, maxLines = 12)
            }
            status?.let { Text(it, fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ButtonTile(if (busy) "Téléchargement…" else "Installer", Modifier.width(240.dp).focusRequester(focus)) {
                    if (busy) return@ButtonTile
                    if (!Updater.canInstall(context)) {
                        status = "Autorise d'abord Falken IPTV à installer des applis, puis appuie de nouveau sur Installer."
                        if (!Updater.openInstallPermission(context)) {
                            status = "Va dans Paramètres → Ma Fire TV → Options pour les développeurs → " +
                                "Installer des applis inconnues, active Falken IPTV, puis reviens appuyer sur Installer."
                        }
                        return@ButtonTile
                    }
                    busy = true
                    scope.launch {
                        runCatching {
                            Updater.download(context, r) { pct -> status = if (pct >= 0) "Téléchargement : $pct %" else "Téléchargement…" }
                        }.onSuccess { apk ->
                            status = "Confirme l'installation à l'écran."
                            Updater.install(context, apk)
                        }.onFailure { e ->
                            status = "Échec du téléchargement : " + friendlyMessage(e)
                        }
                        busy = false
                    }
                }
                ButtonTile("Plus tard", Modifier.width(240.dp), primary = false) { release = null }
            }
        }
    }
}
