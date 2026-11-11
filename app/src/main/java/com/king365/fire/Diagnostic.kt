package com.king365.fire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- État de la connexion (Réglages) ----------

private val Ok = Color(0xFF4CAF50)
private val Warn = Color(0xFFFFA726)
private val Bad = Color(0xFFEF5350)

private data class CheckLine(val color: Color, val title: String, val detail: String)

private class CheckResult(val lines: List<CheckLine>, val advice: String)

/// Vérifie Internet, le serveur de l'abonnement, son temps de réponse et l'état du compte
private suspend fun runCheck(): CheckResult {
    val lines = mutableListOf<CheckLine>()

    // 1. Internet
    try {
        val ms = Http.ping("http://connectivitycheck.gstatic.com/generate_204")
        lines += CheckLine(Ok, "Internet", "Connecté · $ms ms")
    } catch (e: Exception) {
        lines += CheckLine(Bad, "Internet", friendlyMessage(e))
        return CheckResult(lines, "La Fire TV n'a pas accès à Internet : vérifie le Wi-Fi ou le câble réseau.")
    }

    // 2. Serveur de l'abonnement
    val api = Session.api
    if (api == null) {
        val url = Prefs.m3uUrl.trim()
        if (url.isEmpty()) return CheckResult(lines + CheckLine(Warn, "Abonnement", "Aucun abonnement configuré"),
            "Connecte-toi avec un lien M3U ou tes identifiants Xtream.")
        return try {
            val ms = Http.ping(url)
            lines += CheckLine(if (ms > 3000) Warn else Ok, "Playlist", "Répond en $ms ms" + if (ms > 3000) " (lent)" else "")
            CheckResult(lines, "Tout fonctionne. Avec un lien M3U, la date d'expiration n'est pas connue : demande-la à ton fournisseur.")
        } catch (e: Exception) {
            lines += CheckLine(Bad, "Playlist", friendlyMessage(e))
            CheckResult(lines, "Internet fonctionne mais le lien de ta playlist ne répond pas : vérifie-le auprès de ton fournisseur.")
        }
    }

    try {
        val ms = Http.ping(api.accountUrl)
        lines += CheckLine(if (ms > 3000) Warn else Ok, "Serveur", "Répond en $ms ms" + if (ms > 3000) " (lent)" else "")
    } catch (e: Exception) {
        lines += CheckLine(Bad, "Serveur", friendlyMessage(e))
        return CheckResult(lines, "Internet fonctionne mais le serveur de ton abonnement ne répond pas. " +
            "Il est peut-être en panne ou son adresse a changé : contacte ton fournisseur.")
    }

    val account = try {
        api.account()
    } catch (e: Exception) {
        lines += CheckLine(Bad, "Compte", friendlyMessage(e))
        return CheckResult(lines, "Le serveur répond mais refuse ton compte : vérifie ton nom d'utilisateur et ton mot de passe.")
    }

    var advice: String? = null
    val now = System.currentTimeMillis()
    val status = account.status?.lowercase() ?: ""
    val expired = account.expiry?.let { it < now } ?: false
    when {
        expired || status == "expired" -> {
            lines += CheckLine(Bad, "Compte", "Expiré")
            advice = "Ton abonnement est expiré : renouvelle-le auprès de ton fournisseur."
        }
        status.isEmpty() || status == "active" -> lines += CheckLine(Ok, "Compte", "Actif")
        else -> {
            lines += CheckLine(Warn, "Compte", "État : ${account.status}")
            advice = "Le serveur indique que ton compte n'est pas actif (« ${account.status} »)."
        }
    }

    val expiry = account.expiry
    if (expiry != null) {
        val days = ((expiry - now) / 86_400_000L).coerceAtLeast(0)
        val date = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(expiry))
        val color = if (expired) Bad else if (days <= 7) Warn else Ok
        lines += CheckLine(color, "Expiration", if (expired) "Le $date" else "Le $date (dans $days jour${if (days > 1) "s" else ""})")
    } else {
        lines += CheckLine(Ok, "Expiration", "Illimité")
    }

    val max = account.maxConnections?.toIntOrNull() ?: 0
    if (max > 0) {
        val active = account.activeConnections?.toIntOrNull() ?: 0
        val full = active >= max
        lines += CheckLine(if (full) Warn else Ok, "Écrans", "$active utilisé${if (active > 1) "s" else ""} sur $max")
        if (full && advice == null) {
            advice = "Tous tes écrans sont utilisés : arrête la lecture sur un autre appareil si une chaîne refuse de démarrer."
        }
    }
    return CheckResult(lines, advice ?: "Tout fonctionne.")
}

@Composable
fun ConnectionCheckPanel() {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    var checkedAt by remember { mutableStateOf<Long?>(null) }

    Text("État de la connexion", fontSize = 14.sp)
    ButtonTile(if (running) "Test en cours…" else "🩺  Tester la connexion", Modifier.fillMaxWidth(), primary = false) {
        if (running) return@ButtonTile
        running = true
        scope.launch {
            result = runCheck()
            checkedAt = System.currentTimeMillis()
            running = false
        }
    }
    result?.let { r ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            r.lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth().background(RowBg).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("●", color = line.color, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                    Text(line.title, modifier = Modifier.weight(1f), fontSize = 15.sp)
                    Text(line.detail, color = Muted, fontSize = 15.sp)
                }
            }
        }
        Text(r.advice, fontSize = 13.sp)
        checkedAt?.let {
            Text("Vérifié à " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(it)), color = Muted, fontSize = 12.sp)
        }
    }
}
