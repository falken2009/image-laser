package com.king365.fire

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Tmdb.init(this)
        LiveFormat.init(this)
        VlcMemory.init(this)
        Premium.init(this)
        handleReminder(intent)
        setContent {
            KingTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReminder(intent)
    }

    override fun onStop() {
        super.onStop()
        // Le contrôle parental se reverrouille quand on quitte l'appli
        Prefs.unlocked = false
    }

    /// Un rappel de programme a été touché : on ouvre la chaîne
    private fun handleReminder(intent: Intent?) {
        if (intent == null) return
        val url = intent.getStringExtra("playUrl") ?: return
        val title = intent.getStringExtra("playTitle") ?: "Chaîne"
        Session.pendingPlay = PlayItem(title = title, url = url, live = true)
        intent.removeExtra("playUrl")
    }
}

// ---------- Navigation entre les écrans ----------

sealed interface Screen {
    data object Home : Screen
    data object Live : Screen
    data object Guide : Screen
    data object Favorites : Screen
    data object Replay : Screen
    data object Movies : Screen
    data object Series : Screen
    data object Search : Screen
    data object Account : Screen
    data object Settings : Screen
    data object Profiles : Screen
    data object Watchlist : Screen
    data object Multi : Screen
    data class MovieGrid(val category: Category) : Screen
    data class Saga(val name: String, val movies: List<Movie>) : Screen
    data class MovieDetail(val movie: Movie) : Screen
    data class SeriesGrid(val category: Category) : Screen
    data class SeriesDetail(val series: SeriesItem) : Screen
    data class Play(val item: PlayItem) : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    /// Numéro unique de chaque écran ouvert : un écran rouvert repart tout en haut,
    /// mais on retrouve sa position quand on revient en arrière (depuis une fiche, par exemple)
    private val ids = mutableStateListOf(0)
    private var nextId = 1
    val current: Screen get() = stack.last()
    val currentId: Int get() = ids.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun go(screen: Screen) {
        // Fonctions Premium (version boutique) : la fenêtre Premium s'ouvre à la place
        val premiumReason = when (screen) {
            Screen.Guide -> "Le guide en grille"
            Screen.Replay -> "Le replay"
            Screen.Multi -> "Le multi-écran"
            else -> null
        }
        if (premiumReason != null && !Premium.require(premiumReason)) return
        stack.add(screen)
        ids.add(nextId++)
    }

    fun back() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            ids.removeAt(ids.lastIndex)
        }
    }

    fun home() {
        while (stack.size > 1) back()
    }
}

@Composable
fun AppRoot() {
    var showSplash by remember { mutableStateOf(true) }
    var loggedIn by remember { mutableStateOf(Session.restore()) }
    var profileChosen by remember { mutableStateOf(Prefs.profiles.size <= 1) }
    val nav = remember { Navigator() }
    val saveable = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!loggedIn) {
            LoginScreen(onLoggedIn = {
                nav.home()
                loggedIn = true
            })
        } else if (!profileChosen) {
            ProfilesScreen(onDone = { profileChosen = true })
        } else {
            LaunchedEffect(Session.pendingPlay) {
                Session.pendingPlay?.let {
                    Session.pendingPlay = null
                    nav.go(Screen.Play(it))
                }
            }
            BackHandler(enabled = nav.canGoBack) { nav.back() }
            val screen = nav.current
            // Garde la position dans les listes quand on revient en arrière
            saveable.SaveableStateProvider("${nav.currentId}-${screen::class.simpleName}") {
                when (screen) {
                    Screen.Home -> HomeScreen(nav)
                    Screen.Live -> LiveScreen(nav, guideMode = false)
                    Screen.Guide -> GuideGridScreen(nav)
                    Screen.Favorites -> FavoritesScreen(nav)
                    Screen.Replay -> ReplayScreen(nav)
                    // Rangées façon Netflix en Premium, liste de catégories sinon
                    Screen.Movies -> if (Premium.active) MovieRowsScreen(nav) else MovieCategoriesScreen(nav)
                    Screen.Series -> if (Premium.active) SeriesRowsScreen(nav) else SeriesCategoriesScreen(nav)
                    Screen.Search -> SearchScreen(nav)
                    Screen.Account -> AccountScreen(onBack = { nav.back() })
                    Screen.Profiles -> ProfilesScreen(onDone = { nav.home() })
                    Screen.Watchlist -> WatchlistScreen(nav)
                    Screen.Multi -> MultiScreen(nav)
                    Screen.Settings -> SettingsScreen(
                        onLogout = {
                            Session.logout()
                            loggedIn = false
                        },
                        onSwitch = { sub ->
                            Prefs.applySubscription(sub)
                            Session.clearCaches()
                            if (Session.restore()) nav.home() else loggedIn = false
                        },
                        onSwitchProfile = { nav.go(Screen.Profiles) }
                    )
                    is Screen.MovieGrid -> MovieGridScreen(nav, screen.category)
                    is Screen.Saga -> SagaScreen(nav, screen.name, screen.movies)
                    is Screen.MovieDetail -> MovieDetailScreen(nav, screen.movie)
                    is Screen.SeriesGrid -> SeriesGridScreen(nav, screen.category)
                    is Screen.SeriesDetail -> SeriesDetailScreen(nav, screen.series)
                    is Screen.Play -> PlayerScreen(screen.item, onExit = { nav.back() })
                }
            }
        }

        if (Premium.showPaywall) PremiumScreen(onClose = { Premium.showPaywall = false })

        // Nouvelle version publiée sur GitHub ? On propose de l'installer (après l'intro)
        // (la version boutique est mise à jour par l'Amazon Appstore)
        if (!showSplash && loggedIn && !Premium.enabled) {
            UpdatePrompt(trigger = Updater.requests, onNothingNew = {
                if (Updater.requests > 0) Updater.lastResult = "Ta version est à jour ✓"
            })
        }

        if (showSplash) {
            SplashScreen(onFinished = { showSplash = false })
        }
    }
}
