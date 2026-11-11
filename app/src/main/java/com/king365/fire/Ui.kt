package com.king365.fire

import androidx.compose.ui.input.key.onPreviewKeyEvent

import kotlinx.coroutines.launch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/// Thème de couleurs choisi dans les Réglages
enum class KingThemeChoice(val label: String, val main: Color, val dark: Color) {
    RED("Rouge", Color(0xFFE50914), Color(0xFF6B0005)),
    BLUE("Bleu", Color(0xFF0D6BF2), Color(0xFF051F66)),
    GREEN("Vert", Color(0xFF0DAD4D), Color(0xFF00421A)),
    GOLD("Or", Color(0xFFD99E1A), Color(0xFF664500))
}

val KingRed: Color get() = Prefs.theme.main
val KingDarkRed: Color get() = Prefs.theme.dark
val RowBg = Color(0x73000000)
val Muted = Color(0xFFB8B8B8)

val RedTileBrush: Brush get() = Brush.verticalGradient(listOf(KingRed, KingDarkRed))

@Composable
fun KingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KingRed,
            onPrimary = Color.White,
            background = Color.Black,
            surface = Color(0xFF141414),
            onSurface = Color.White,
            onBackground = Color.White
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

/// Fond noir et rouge avec reflets en diagonale
@Composable
fun KingBackground(dimmed: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color.Black)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Black, KingDarkRed, KingRed.copy(alpha = 0.9f), KingDarkRed, Color.Black),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    alpha = if (dimmed) 0.42f else 1f
                )
                val stripe = size.maxDimension * 0.14f
                for (i in 0 until 4) {
                    val x = size.width * (i * 0.32f - 0.1f)
                    rotate(35f, pivot = Offset(x, size.height / 2)) {
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = if (dimmed) 0.035f else 0.09f),
                                    Color.Transparent
                                ),
                                startX = x,
                                endX = x + stripe
                            ),
                            topLeft = Offset(x, -size.height),
                            size = Size(stripe, size.height * 3)
                        )
                    }
                }
            },
        content = content
    )
}

/// Élément sélectionnable à la télécommande : grossit et s'entoure de blanc quand il a le focus.
/// Le bouton « menu » (≡) de la télécommande, ou un appui long, déclenche onMenu.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    background: Brush? = null,
    focusedScale: Float = 1.05f,
    selected: Boolean = false,
    onMenu: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focus")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .onKeyEvent { e ->
                if (onMenu != null && e.type == KeyEventType.KeyUp && e.key == Key.Menu) {
                    onMenu()
                    true
                } else false
            }
            .clip(shape)
            .background(
                background ?: Brush.linearGradient(
                    listOf(
                        if (selected) KingRed.copy(alpha = 0.85f) else RowBg,
                        if (selected) KingRed.copy(alpha = 0.85f) else RowBg
                    )
                ),
                shape
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.22f),
                shape = shape
            )
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
    ) {
        content(focused)
    }
}

/// Silhouette de faucon en vol, emblème de Falken IPTV
@Composable
fun FalconMark(size: Dp, color: Color = KingRed) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.00f, h * 0.30f)
            lineTo(w * 0.40f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.40f)
            lineTo(w * 0.60f, h * 0.52f)
            lineTo(w * 1.00f, h * 0.30f)
            lineTo(w * 0.62f, h * 0.70f)
            lineTo(w * 0.50f, h * 0.92f)
            lineTo(w * 0.38f, h * 0.70f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun Crown(size: Dp, color: Color = KingRed) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.08f, h * 0.85f)
            lineTo(w * 0.05f, h * 0.25f)
            lineTo(w * 0.32f, h * 0.52f)
            lineTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.68f, h * 0.52f)
            lineTo(w * 0.95f, h * 0.25f)
            lineTo(w * 0.92f, h * 0.85f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun KingLogo(modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FalconMark(size = (fontSize.value * 1.3f).dp)
        Text("Falken", fontSize = fontSize, fontWeight = FontWeight.Black, color = Color.White)
        Text("IPTV", fontSize = fontSize, fontWeight = FontWeight.Black, color = KingRed)
    }
}

// ---------- Icônes des grandes tuiles ----------

@Composable
private fun Badge(icon: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(KingRed)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun LiveTvIcon(size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Tv, contentDescription = null, modifier = Modifier.size(size))
        Text("▶", fontSize = (size.value * 0.22f).sp, modifier = Modifier.padding(bottom = size * 0.1f))
        Badge(Icons.Filled.Wifi, size * 0.36f, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
fun GuideIcon(size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Tv, contentDescription = null, modifier = Modifier.size(size))
        Icon(Icons.Filled.Info, contentDescription = null,
            modifier = Modifier.size(size * 0.34f).padding(bottom = size * 0.04f))
    }
}

@Composable
fun VodIcon(size: Dp) {
    Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(size))
}

/// Grande tuile rouge à bordure blanche de l'accueil
@Composable
fun BigTile(title: String, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    FocusCard(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.1f),
        background = RedTileBrush,
        focusedScale = 1.08f
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = (size.value * 0.11f).sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
fun SmallTile(title: String, icon: ImageVector, size: Dp, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size * 0.12f),
        background = RedTileBrush,
        focusedScale = 1.1f
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.42f))
            Text(title, fontSize = (size.value * 0.13f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ---------- Éléments communs ----------

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = modifier.padding(bottom = 10.dp))
}

@Composable
fun ListRow(
    text: String,
    selected: Boolean = false,
    trailing: String? = null,
    onFocus: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        selected = selected,
        focusedScale = 1.03f,
        onFocus = onFocus,
        onMenu = onMenu
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontSize = 15.sp)
            if (trailing != null) Text(trailing, color = if (selected) Color.White else Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun Poster(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))) {
        if (url != null) {
            // Jaquette TMDB en dessous, image du fournisseur par-dessus : si celle du fournisseur
            // est cassée, vide, transparente ou trop lente, c'est la jaquette TMDB qui se voit
            val backup = Tmdb.fallback(url)
            if (backup != null) {
                AsyncImage(model = backup, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    image: String?,
    badge: String? = null,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column {
        FocusCard(onClick = onClick, shape = RoundedCornerShape(8.dp), focusedScale = 1.08f, onFocus = onFocus) {
            Poster(image)
            if (badge != null) {
                Text(
                    badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(KingRed)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Text(title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick, shape = RoundedCornerShape(50), selected = selected, focusedScale = 1.08f) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
fun ButtonTile(text: String, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        background = if (primary) RedTileBrush else null,
        focusedScale = 1.06f
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp))
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = KingRed)
    }
}

@Composable
fun Message(title: String, detail: String? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (detail != null) Text(detail, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp).width(420.dp))
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "$h h ${"%02d".format(m)}" else "$m min"
}

/// Fond avec l'image d'un film ou d'une série, assombri pour garder le texte lisible.
/// Change en fondu quand on passe d'une affiche à l'autre.
@Composable
fun BackdropBackground(image: String?, content: @Composable BoxScope.() -> Unit) {
    KingBackground(dimmed = true) {
        androidx.compose.animation.Crossfade(targetState = image, label = "fond") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.8f))
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        content()
    }
}

/// Retour dans une longue liste : remonte d'abord tout en haut (et y remet le focus) ;
/// un second appui sur Retour quitte l'écran
@Composable
fun BackToTop(atTop: Boolean, scrollToTop: suspend () -> Unit, focusTop: androidx.compose.ui.focus.FocusRequester) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.activity.compose.BackHandler(enabled = !atTop) {
        scope.launch {
            scrollToTop()
            // L'élément du haut doit d'abord réapparaître à l'écran
            repeat(10) {
                kotlinx.coroutines.delay(40)
                if (runCatching { focusTop.requestFocus() }.isSuccess) return@launch
            }
        }
    }
}

/// Case à remplir : le clavier ne s'ouvre plus tout seul quand on arrive dessus,
/// seulement quand on appuie sur OK
@Composable
fun Modifier.okOpensKeyboard(): Modifier {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    return this.onPreviewKeyEvent { e ->
        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp &&
            (e.key == androidx.compose.ui.input.key.Key.DirectionCenter || e.key == androidx.compose.ui.input.key.Key.Enter ||
                e.key == androidx.compose.ui.input.key.Key.NumPadEnter)
        ) keyboard?.show()
        false
    }
}
