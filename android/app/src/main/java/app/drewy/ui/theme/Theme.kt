package app.drewy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.drewy.R

/** Primitives — mirror the Figma "Primitives" collection. */
object DrewyPalette {
    val navy900 = Color(0xFF2B2D6E)
    val navy700 = Color(0xFF4A4C78)
    val navy500 = Color(0xFF6B6D9A)
    val navy400 = Color(0xFF8A8CB3)
    val navy100 = Color(0xFFE4E6F2)
    val navy50 = Color(0xFFEEEFF7)
    val ink950 = Color(0xFF17182B)
    val blue600 = Color(0xFF2F6BF0)
    val blue500 = Color(0xFF3D7BFF)
    val blue400 = Color(0xFF5B93FF)
    val blue100 = Color(0xFFDCE6FF)
    val lavender100 = Color(0xFFE9EDFF)
    val blush100 = Color(0xFFFBE9F6)
    val yellow400 = Color(0xFFFFC21A)
    val yellow300 = Color(0xFFF2D24B)
    val yellow100 = Color(0xFFFFF1A8)
    val yellow50 = Color(0xFFFFFBE6)
    val pink500 = Color(0xFFFF5C8A)
    val pink300 = Color(0xFFFF8FBF)
    val pink100 = Color(0xFFFFD6E8)
    val mint700 = Color(0xFF1C7A63)
    val mint500 = Color(0xFF2EC4A0)
    val mint100 = Color(0xFFCFF5E7)
    val paper200 = Color(0xFFECE8DA)
    val paper50 = Color(0xFFFFFDF7)
    val white = Color(0xFFFFFFFF)
}

/** Semantic tokens — mirror the Figma "Color" collection (code syntax DrewyTheme.colors.*). */
@Immutable
class DrewyColors {
    private val p = DrewyPalette
    val textPrimary = p.navy900
    val textSecondary = p.navy700
    val textSubtle = p.navy500
    val textMuted = p.navy400
    val textOnBrand = p.white
    val textLink = p.blue500
    val bgApp = p.navy50
    val bgSurface = p.white
    val bgPaper = p.paper50
    val bgSubtle = p.navy100
    val bgBrand = p.blue500
    val bgBrandStrong = p.blue600
    val bgBrandSoft = p.blue400
    val bgInverse = p.navy900
    val bgCamera = p.ink950
    val bgGradientTop = p.lavender100
    val bgGradientBottom = p.blush100
    val accentYellow = p.yellow100
    val accentPink = p.pink100
    val accentMint = p.mint100
    val accentSky = p.blue100
    val accentNote = p.yellow50
    val popPink = p.pink500
    val popSun = p.yellow400
    val popGreen = p.mint500
    val popGreenInk = p.mint700
    val popBlush = p.pink300
    val borderStrong = p.navy900
    val borderBrand = p.blue500
    val borderSubtle = p.navy100
    val borderNote = p.yellow300
    val borderGrid = p.paper200

    val appGradient: Brush get() = Brush.verticalGradient(listOf(bgGradientTop, bgGradientBottom))
    val brandGradient: Brush get() = Brush.verticalGradient(listOf(p.blue400, p.blue600))

    /** Ink colours Drewy (and the user) can draw with. */
    fun ink(name: String?): Color = when (name) {
        "blue" -> bgBrand
        "pink" -> popPink
        "sun" -> popSun
        "green" -> popGreen
        else -> textPrimary
    }
}

object DrewySpacing { val s4 = 4.dp; val s8 = 8.dp; val s12 = 12.dp; val s16 = 16.dp; val s20 = 20.dp; val s24 = 24.dp; val s32 = 32.dp }
object DrewyRadius { val sm = 12.dp; val md = 16.dp; val lg = 22.dp; val xl = 28.dp; val xxl = 40.dp }

val Fredoka = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)
val Caveat = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_bold, FontWeight.Bold),
)

/** Text styles — mirror the Figma text styles. */
object DrewyType {
    val display = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 1.2.em)
    val title = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 1.25.em)
    val heading = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 1.25.em)
    val button = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 1.2.em)
    val body = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 1.45.em)
    val bodyRegular = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 1.5.em)
    val label = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 1.3.em)
    val caption = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 1.3.em)
    val overline = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 1.2.em, letterSpacing = 0.06.em)
    val handXL = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 1.1.em)
    val handL = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 1.15.em)
    val handM = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 1.15.em)
}

object DrewyTheme {
    val colors = DrewyColors()
    val spacing = DrewySpacing
    val radius = DrewyRadius
    val type = DrewyType
}

@Composable
fun DrewyThemeProvider(content: @Composable () -> Unit) = content()
