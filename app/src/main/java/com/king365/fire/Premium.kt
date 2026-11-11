package com.king365.fire

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Falken IPTV Premium ----------

/// Paiement : Amazon Appstore dans la version « store », rien dans la version perso (tout est débloqué)
interface BillingBackend {
    /// `onPremium` reçoit l'état de l'abonnement Premium ; `onPrices` les prix affichés par la boutique
    fun start(context: Context, onPremium: (Boolean) -> Unit, onPrices: (Map<String, String>) -> Unit)
    fun purchase(sku: String)
    fun restore()
}

object Premium {
    /// Abonnements proposés (références à créer à l'identique dans la console Amazon)
    const val SKU_MONTH = "falken.premium.mensuel"
    const val SKU_YEAR = "falken.premium.annuel"
    private const val TRIAL_DAYS = 7

    /// false dans la version perso : tout reste débloqué, aucune fenêtre Premium
    val enabled: Boolean get() = BuildConfig.PREMIUM

    var purchased by mutableStateOf(false)
        private set
    var prices by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /// Fenêtre Premium affichée par-dessus l'écran en cours
    var showPaywall by mutableStateOf(false)
    var paywallReason by mutableStateOf<String?>(null)
        private set

    private var firstLaunch = System.currentTimeMillis()
    private var sp: android.content.SharedPreferences? = null
    private val billing: BillingBackend = BillingProvider.create()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("premium", Context.MODE_PRIVATE)
        sp = prefs
        firstLaunch = prefs.getLong("firstLaunch", 0L).takeIf { it > 0 }
            ?: System.currentTimeMillis().also { prefs.edit().putLong("firstLaunch", it).apply() }
        // Dernier état connu (l'appli marche aussi hors ligne), confirmé ensuite par la boutique
        purchased = prefs.getBoolean("purchased", false)
        if (enabled) {
            billing.start(context, onPremium = { active ->
                purchased = active
                prefs.edit().putBoolean("purchased", active).apply()
                if (active) showPaywall = false
            }, onPrices = { prices = it })
        }
    }

    val trialDaysLeft: Int
        get() {
            val used = ((System.currentTimeMillis() - firstLaunch) / 86_400_000L).toInt()
            return (TRIAL_DAYS - used).coerceAtLeast(0)
        }

    /// Toutes les fonctions sont-elles accessibles ? (version perso, essai en cours ou Premium acheté)
    val active: Boolean get() = !enabled || purchased || trialDaysLeft > 0

    /// À appeler avant une fonction Premium : true si elle est accessible, sinon ouvre la fenêtre Premium
    fun require(reason: String): Boolean {
        if (active) return true
        paywallReason = reason
        showPaywall = true
        return false
    }

    /// Ouvre la fenêtre Premium depuis les Réglages
    fun open() {
        paywallReason = null
        showPaywall = true
    }

    fun buy(sku: String) = billing.purchase(sku)
    fun restore() = billing.restore()

    val statusText: String
        get() = when {
            !enabled -> "Version perso : tout est débloqué"
            purchased -> "Premium actif ✓"
            trialDaysLeft > 0 -> "Essai gratuit : encore $trialDaysLeft jour${if (trialDaysLeft > 1) "s" else ""}"
            else -> "Version gratuite"
        }
}

/// Fenêtre « Falken IPTV Premium » : avantages, deux offres, restauration des achats
@Composable
fun PremiumScreen(onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    var status by remember { mutableStateOf<String?>(null) }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val perks = listOf(
        "Profils illimités pour toute la famille",
        "Plusieurs abonnements, changement en un geste",
        "Guide en grille et rappels avant les programmes",
        "Replay et multi-écran (2 ou 4 chaînes)",
        "Films et séries en rangées, derniers ajouts",
        "Épisode suivant automatique et minuteur de veille",
        "Thèmes de couleur et icône assortie"
    )

    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(760.dp)
                .background(Color(0xFF111111), RoundedCornerShape(18.dp))
                .border(2.dp, KingRed, RoundedCornerShape(18.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FalconMark(56.dp)
                Column {
                    Text("Falken IPTV Premium", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(Premium.paywallReason?.let { "$it fait partie de Premium" } ?: Premium.statusText,
                        color = Muted, fontSize = 14.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                perks.forEach { Text("✓  $it", fontSize = 16.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                val month = Premium.prices[Premium.SKU_MONTH] ?: "1,99 €"
                val year = Premium.prices[Premium.SKU_YEAR] ?: "14,99 €"
                ButtonTile("Mensuel · $month / mois", Modifier.width(330.dp).focusRequester(first), primary = false) {
                    status = "Ouverture du paiement Amazon…"
                    Premium.buy(Premium.SKU_MONTH)
                }
                ButtonTile("Annuel · $year / an (le plus choisi)", Modifier.width(330.dp)) {
                    status = "Ouverture du paiement Amazon…"
                    Premium.buy(Premium.SKU_YEAR)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonTile("Restaurer mes achats", Modifier.width(330.dp), primary = false) {
                    status = "Recherche de tes achats…"
                    Premium.restore()
                }
                ButtonTile("Plus tard", Modifier.width(330.dp), primary = false) { onClose() }
            }
            status?.let { Text(it, color = Muted, fontSize = 13.sp) }
            Text(
                "Paiement et résiliation gérés par Amazon. L'appli ne fournit aucune chaîne : " +
                    "elle lit le contenu de ton propre abonnement.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/// Ligne « Falken IPTV Premium » dans les Réglages
@Composable
fun PremiumSettings() {
    if (!Premium.enabled) return
    Text("Falken IPTV Premium", fontSize = 14.sp)
    ButtonTile("👑  ${Premium.statusText}", Modifier.fillMaxWidth(), primary = false) {
        Premium.open()
    }
}
