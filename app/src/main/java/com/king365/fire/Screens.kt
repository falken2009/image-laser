package com.king365.fire

import android.media.MediaPlayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Intro animée ----------

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val letters = "FALKEN IPTV"
    var visible by remember { mutableIntStateOf(0) }
    var crown by remember { mutableStateOf(false) }
    var glow by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }

    val crownScale by animateFloatAsState(if (crown) 1f else 0.2f, tween(600), label = "crown")
    val logoScale by animateFloatAsState(if (zoom) 8f else 1f, tween(550), label = "zoom")
    val logoAlpha by animateFloatAsState(if (zoom) 0f else 1f, tween(550), label = "fade")

    LaunchedEffect(Unit) {
        runCatching {
            MediaPlayer.create(context, R.raw.intro)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
        crown = true
        delay(350)
        for (i in 1..letters.length) {
            visible = i
            delay(110)
        }
        glow = true
        delay(800)
        zoom = true
        delay(560)
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = logoScale
                scaleY = logoScale
                alpha = logoAlpha
            }
        ) {
            Box(Modifier.graphicsLayer {
                scaleX = crownScale
                scaleY = crownScale
                alpha = if (crown) 1f else 0f
            }) {
                FalconMark(size = 90.dp)
            }
            Row {
                letters.forEachIndexed { i, c ->
                    val shown = i < visible
                    val a by animateFloatAsState(if (shown) 1f else 0f, tween(250), label = "a$i")
                    val s by animateFloatAsState(if (shown) 1f else 1.9f, tween(250), label = "s$i")
                    Text(
                        c.toString(),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = if (i < 6) KingRed else Color.White,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = KingRed.copy(alpha = if (glow) 0.9f else 0.3f),
                                blurRadius = if (glow) 40f else 12f
                            )
                        ),
                        modifier = Modifier.graphicsLayer {
                            alpha = a
                            scaleX = s
                            scaleY = s
                        }
                    )
                }
            }
        }
    }
}

// ---------- Connexion ----------

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    var server by remember { mutableStateOf(Prefs.server) }
    var username by remember { mutableStateOf(Prefs.username) }
    var password by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf(Prefs.mode == "m3u") }
    var m3uUrl by remember { mutableStateOf(Prefs.m3uUrl) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }

    LaunchedEffect(m3u) { runCatching { first.requestFocus() } }

    val fieldColors = OutlinedTextFieldDefaults.colors(
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

    KingBackground {
        Column(
            Modifier.align(Alignment.Center).width(460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KingLogo(fontSize = 40.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Xtream Codes", selected = !m3u) { m3u = false; error = null }
                Chip("Lien M3U", selected = m3u) { m3u = true; error = null }
            }
            if (m3u) {
                OutlinedTextField(
                    value = m3uUrl, onValueChange = { m3uUrl = it },
                    label = { Text("Lien de la playlist M3U") },
                    singleLine = true, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, showKeyboardOnFocus = false),
                    modifier = Modifier.okOpensKeyboard().fillMaxWidth().focusRequester(first)
                )
            } else {
                OutlinedTextField(
                    value = server, onValueChange = { server = it },
                    label = { Text("Serveur (http://exemple.com:8080)") },
                    singleLine = true, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, showKeyboardOnFocus = false),
                    modifier = Modifier.okOpensKeyboard().fillMaxWidth().focusRequester(first)
                )
                OutlinedTextField(
                    keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                    value = username, onValueChange = { username = it },
                    label = { Text("Nom d'utilisateur") },
                    singleLine = true, colors = fieldColors,
                    modifier = Modifier.okOpensKeyboard().fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    singleLine = true, colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, showKeyboardOnFocus = false),
                    modifier = Modifier.okOpensKeyboard().fillMaxWidth()
                )
            }
            ButtonTile(if (loading) "Connexion…" else "S'IDENTIFIER", modifier = Modifier.fillMaxWidth()) {
                if (loading) return@ButtonTile
                if (m3u && m3uUrl.isBlank()) {
                    error = "Colle le lien de ta playlist M3U."
                    return@ButtonTile
                }
                if (!m3u && (server.isBlank() || username.isBlank() || password.isBlank())) {
                    error = "Remplis le serveur, le nom d'utilisateur et le mot de passe."
                    return@ButtonTile
                }
                loading = true
                error = null
                scope.launch {
                    if (m3u) {
                        // Vérifie que la playlist se télécharge et contient des chaînes
                        val url = m3uUrl.trim()
                        runCatching { M3U.parse(Http.text(url, timeoutMs = 120_000)) }
                            .onSuccess { playlist ->
                                if (playlist.channels.isEmpty()) {
                                    error = "Aucune chaîne trouvée dans cette playlist."
                                } else {
                                    Prefs.mode = "m3u"
                                    Prefs.m3uUrl = url
                                    Prefs.rememberCurrentSubscription()
                                    Session.clearCaches()
                                    Session.api = null
                                    Session.liveCategories = playlist.categories
                                    Session.liveChannels = playlist.channels
                                    onLoggedIn()
                                }
                            }
                            .onFailure { error = friendlyMessage(it) }
                    } else {
                        val api = XtreamApi(server, username.trim(), password)
                        runCatching { api.account() }
                            .onSuccess { account ->
                                if (account.status?.lowercase() == "expired") {
                                    val date = account.expiry?.let { SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(it)) }
                                    error = if (date != null) "Ton abonnement a expiré le $date." else "Ton abonnement a expiré."
                                } else {
                                    Prefs.mode = "xtream"
                                    Prefs.server = server.trim()
                                    Prefs.username = username.trim()
                                    Prefs.password = password
                                    Prefs.rememberCurrentSubscription()
                                    Session.clearCaches()
                                    Session.api = api
                                    Session.account = account
                                    onLoggedIn()
                                }
                            }
                            .onFailure { error = friendlyMessage(it) }
                    }
                    loading = false
                }
            }
            error?.let { Text(it, color = Color.White, fontSize = 14.sp, modifier = Modifier.background(Color(0x99000000)).padding(10.dp)) }
            Text("Falken IPTV est un lecteur : il ne fournit aucune chaîne.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

// ---------- Accueil ----------

@Composable
fun HomeScreen(nav: Navigator) {
    var account by remember { mutableStateOf(Session.account) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
        if (account == null) {
            account = runCatching { Session.api?.account() }.getOrNull()
            Session.account = account
        }
    }

    val hasContinue = remember(Prefs.progressTick, Prefs.currentProfile) { Prefs.inProgress().isNotEmpty() }
    val big = if (hasContinue) 150.dp else 190.dp
    val small = if (hasContinue) 70.dp else 84.dp

    KingBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth()) {
                FocusCard(
                    onClick = { nav.go(Screen.Profiles) },
                    modifier = Modifier.align(Alignment.CenterStart),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    focusedScale = 1.15f
                ) {
                    ProfileAvatar(Prefs.profile, 40.dp)
                }
                KingLogo(Modifier.align(Alignment.Center), fontSize = 30.sp)
                Row(Modifier.align(Alignment.CenterEnd)) {
                    FocusCard(onClick = { nav.go(Screen.Search) }, background = null, focusedScale = 1.15f) {
                        Icon(Icons.Outlined.Search, contentDescription = "Rechercher", modifier = Modifier.padding(8.dp).height(30.dp).width(30.dp))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                BigTile("TV EN DIRECT", big, Modifier.focusRequester(first), onClick = { nav.go(Screen.Live) }) {
                    LiveTvIcon(big * 0.55f)
                }
                BigTile("GUIDE", big, onClick = { nav.go(Screen.Guide) }) { GuideIcon(big * 0.55f) }
                BigTile("FILMS", big, onClick = { nav.go(Screen.Movies) }) { VodIcon(big * 0.55f) }
                BigTile("SÉRIES", big, onClick = { nav.go(Screen.Series) }) {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.height(big * 0.55f).width(big * 0.55f))
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SmallTile("FAVORIS", Icons.Outlined.StarOutline, small) { nav.go(Screen.Favorites) }
                SmallTile("MA LISTE", Icons.Outlined.BookmarkBorder, small) { nav.go(Screen.Watchlist) }
                SmallTile("MULTI", Icons.Outlined.GridView, small) { nav.go(Screen.Multi) }
                SmallTile("REPLAY", Icons.Outlined.History, small) { nav.go(Screen.Replay) }
                SmallTile("RECHERCHE", Icons.Outlined.Search, small) { nav.go(Screen.Search) }
                SmallTile("COMPTE", Icons.Outlined.Person, small) { nav.go(Screen.Account) }
                SmallTile("RÉGLAGES", Icons.Outlined.Settings, small) { nav.go(Screen.Settings) }
            }

            if (hasContinue) {
                Spacer(Modifier.height(12.dp))
                ContinueRow(nav)
            }

            Spacer(Modifier.weight(1f))

            val expiry = account?.expiry
            Text(
                when {
                    expiry != null -> "Expire : " + SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(expiry))
                    account != null -> "Abonnement sans date d'expiration"
                    else -> ""
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------- Compte ----------

@Composable
fun AccountScreen(onBack: () -> Unit) {
    var account by remember { mutableStateOf(Session.account) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
        runCatching { Session.api?.account() }
            .onSuccess { account = it; Session.account = it }
            .onFailure { error = friendlyMessage(it) }
    }
    KingBackground(dimmed = true) {
        Column(Modifier.align(Alignment.Center).width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenTitle("Compte")
            if (Session.isM3U) {
                InfoLine("Connexion", "Playlist M3U")
                InfoLine("Chaînes", "${Session.liveChannels.size}")
                Text("Les infos d'abonnement ne sont disponibles qu'avec Xtream Codes.", color = Muted, fontSize = 13.sp)
                ButtonTile("Retour", primary = false, modifier = Modifier.padding(top = 12.dp).focusRequester(first)) { onBack() }
                return@Column
            }
            val a = account
            val df = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
            InfoLine("Utilisateur", a?.username ?: Prefs.username)
            InfoLine("Statut", when (a?.status?.lowercase()) {
                "active" -> "Actif"
                "expired" -> "Expiré"
                "banned" -> "Suspendu"
                "disabled" -> "Désactivé"
                null -> "…"
                else -> a?.status ?: "Inconnu"
            })
            a?.expiry?.let {
                InfoLine("Expire le", df.format(Date(it)))
                val days = ((it - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                InfoLine("Jours restants", "$days")
            }
            a?.maxConnections?.let { InfoLine("Écrans simultanés", "${a?.activeConnections ?: "0"} / $it") }
            InfoLine("Serveur", Session.api?.host ?: "—")
            error?.let { Text(it, color = KingRed) }
            // Élément focusable pour que la télécommande reste active sur cet écran
            ButtonTile("Retour", primary = false, modifier = Modifier.padding(top = 12.dp).focusRequester(first)) { onBack() }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().background(RowBg).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Text(value, color = Muted, fontSize = 16.sp)
    }
}

// ---------- Réglages ----------

@Composable
fun SettingsScreen(onLogout: () -> Unit, onSwitch: (Prefs.Subscription) -> Unit, onSwitchProfile: () -> Unit = {}) {
    var cleared by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val parental = remember { ParentalFlow() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    KingBackground(dimmed = true) {
        Column(
            Modifier.align(Alignment.Center).width(640.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScreenTitle("Réglages")
            // Numéro de version : permet de vérifier que la dernière compilation est bien installée
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            InfoLine("Version de l'appli", version)
            ButtonTile("🔄  Rechercher une mise à jour", Modifier.fillMaxWidth(), primary = false) {
                Updater.lastResult = "Recherche…"
                Updater.requests++
            }
            Updater.lastResult?.let { Text(it, color = Muted, fontSize = 12.sp) }
            if (Session.isM3U) {
                InfoLine("Playlist M3U", Prefs.m3uUrl.take(60))
            } else {
                InfoLine("Serveur", Prefs.server)
                InfoLine("Utilisateur", Prefs.username)
            }
            Text("Se présenter au serveur comme :", fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Http.agents.keys.forEach { name ->
                    Chip(name, selected = Prefs.userAgentName == name) {
                        Prefs.updateUserAgent(name)
                        Session.clearCaches()
                    }
                }
            }
            Text(
                "Si ton fournisseur refuse la connexion ou la lecture, choisis « IPTV Smarters » ou « VLC ».",
                color = Muted, fontSize = 12.sp
            )
            ButtonTile(if (cleared) "Catalogue rechargé" else "Recharger le catalogue", primary = false,
                modifier = Modifier.fillMaxWidth().focusRequester(first)) {
                Session.clearCaches()
                cleared = true
            }
            PremiumSettings()
            ConnectionCheckPanel()
            if (BuildConfig.TMDB_ALLOWED) TmdbSettings()
            SubscriptionsChooser(onSwitch)
            ThemeChooser()
            ParentalSettings(parental)
            ButtonTile("Changer de profil", primary = false, modifier = Modifier.fillMaxWidth()) { onSwitchProfile() }
            ButtonTile("Se déconnecter", modifier = Modifier.fillMaxWidth()) { onLogout() }
            Text(
                "Astuce : sur une chaîne, appuie sur le bouton ≡ (menu) de la télécommande pour l'ajouter aux favoris.",
                color = Muted, fontSize = 13.sp
            )
            Text("Falken IPTV pour Fire TV · version 2.1", color = Muted, fontSize = 12.sp)
        }
        ParentalPinOverlay(parental)
    }
}

/// Clé TMDB : sagas détectées sur Internet et jaquettes manquantes
@Composable
private fun TmdbSettings() {
    var key by remember { mutableStateOf(Prefs.tmdbKey) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Text("Sagas et jaquettes depuis Internet (TMDB)", fontSize = 14.sp)
    OutlinedTextField(
        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
        value = key,
        onValueChange = { key = it },
        singleLine = true,
        label = { Text("Clé API TMDB (gratuite)") },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KingRed, cursorColor = KingRed),
        modifier = Modifier.okOpensKeyboard().fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ButtonTile("Enregistrer et vérifier", Modifier.width(300.dp)) {
            Prefs.updateTmdbKey(key)
            status = "Vérification…"
            scope.launch { status = if (key.isBlank()) "Désactivé" else Tmdb.check() }
        }
        ButtonTile("Effacer les infos TMDB", Modifier.width(300.dp), primary = false) {
            Tmdb.clear()
            status = "Infos effacées : elles seront cherchées à nouveau"
        }
    }
    Text(
        status ?: "Crée un compte gratuit sur themoviedb.org → Paramètres → API, puis colle ta clé ici. " +
            "L'appli regroupera alors les sagas d'après TMDB et complétera les jaquettes manquantes." +
            " Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.",
        color = Muted, fontSize = 12.sp
    )
}
