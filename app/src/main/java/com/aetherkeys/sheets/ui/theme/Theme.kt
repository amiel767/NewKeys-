package com.aetherkeys.sheets.ui.theme

// Tokens Sheets : source unique des couleurs, rayons, espacements, polices.
// IMPORTANT : adapte l'import de R (ressources de ton app).
// Fichiers à placer dans res/font (noms exacts) :
//   plus_jakarta_sans.ttf (variable), architects_daughter.ttf, caveat.ttf (variable),
//   kalam.ttf, patrick_hand.ttf, fira_code.ttf (variable), jetbrains_mono.ttf (variable)

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.aetherkeys.sheets.model.FontIds
import com.aetherkeys.sheets.model.PalettePresets
import com.aetherkeys.sheets.model.SectionKind

object SheetsColors {
    val Background = Color(0xFF0B0F1C)
    val Ribbon = Color(0xFF0B0F1C)
    val Surface = Color(0xFF161C30)
    val SurfaceRaised = Color(0xFF1C2340)
    val SurfaceChip = Color(0xFF232B45)
    val Chip = Color(0xFF1A2036)
    val Outline = Color(0xFF4A5A8A)
    val TextPrimary = Color(0xFFCFD8F5)
    val TextMuted = Color(0xFF8793B5)
    val Accent = Color(0xFF9DB8F2)
    val AccentText = Color(0xFF0B0F1C)
    val Warning = Color(0xFFF0A23C)
    val InkDark = Color(0xFF2B2A33)
    val Scrim = Color(0x99000000)
}

object SheetPalette {
    val Paper = Color(PalettePresets.Paper)
    val Presets: List<Color> = PalettePresets.Presets.map { Color(it) }
}

fun Long.toColor(): Color = Color(this)
fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** Lisible sur n'importe quel fond : texte sombre sur fond clair, blanc sur fond sombre. */
fun onColorFor(background: Color): Color = if (background.luminance() > 0.5f) SheetsColors.InkDark else Color.White

fun SectionKind.color(): Color = when (this) {
    SectionKind.Intro -> Color(0xFF7A5CFF)
    SectionKind.Verse -> Color(0xFF26A58A)
    SectionKind.PreChorus -> Color(0xFFD97706)
    SectionKind.Chorus -> Color(0xFFE0508F)
    SectionKind.Bridge -> Color(0xFF3F7BE8)
    SectionKind.Solo -> Color(0xFFF08A3C)
    SectionKind.Outro -> Color(0xFF0F9E94)
    SectionKind.Tag -> Color(0xFF8B5CF6)
    SectionKind.Interlude -> Color(0xFF0891B2)
    SectionKind.Custom -> Color(0xFF64748B)
}

object SheetsRadius {
    val Chip = 99.dp
    val Button = 12.dp
    val Drawer = 12.dp
    val Card = 16.dp
    val Floating = 26.dp
    val Chord = 8.dp
}

object SheetsSpacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val Page = 34.dp
}

object SheetsLayout {
    const val RibbonWeight = 0.40f
    const val PageWeight = 0.60f
    val FloatingWidth = 372.dp
    val SectionTagWidth = 54.dp
    const val ChordMinSizeSp = 18f
    const val ChordMaxSizeSp = 56f
}

data class FontOption(val id: String, val label: String, val family: FontFamily)

object SheetsFonts {
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun variable(res: Int, w: FontWeight) =
        Font(res, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))

    val PlusJakartaSans = FontFamily(
        variable(R.font.plus_jakarta_sans, FontWeight.Normal),
        variable(R.font.plus_jakarta_sans, FontWeight.Medium),
        variable(R.font.plus_jakarta_sans, FontWeight.Bold),
        variable(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
    )
    val ArchitectsDaughter = FontFamily(Font(R.font.architects_daughter))
    val Caveat = FontFamily(
        variable(R.font.caveat, FontWeight.Normal),
        variable(R.font.caveat, FontWeight.Medium),
    )
    val Kalam = FontFamily(Font(R.font.kalam))
    val PatrickHand = FontFamily(Font(R.font.patrick_hand))
    val FiraCode = FontFamily(
        variable(R.font.fira_code, FontWeight.Normal),
        variable(R.font.fira_code, FontWeight.Medium),
    )
    val JetBrainsMono = FontFamily(
        variable(R.font.jetbrains_mono, FontWeight.Normal),
        variable(R.font.jetbrains_mono, FontWeight.Medium),
    )

    val Options: List<FontOption> = listOf(
        FontOption(FontIds.ArchitectsDaughter, "Architects Daughter", ArchitectsDaughter),
        FontOption(FontIds.Caveat, "Caveat", Caveat),
        FontOption(FontIds.Kalam, "Kalam", Kalam),
        FontOption(FontIds.PatrickHand, "Patrick Hand", PatrickHand),
        FontOption(FontIds.PlusJakartaSans, "Plus Jakarta Sans", PlusJakartaSans),
        FontOption(FontIds.FiraCode, "Fira Code", FiraCode),
        FontOption(FontIds.JetBrainsMono, "JetBrains Mono", JetBrainsMono),
    )

    /** Repli : police manquante -> Architects Daughter. */
    fun family(id: String?): FontFamily = Options.firstOrNull { it.id == id }?.family ?: ArchitectsDaughter

    /** Les polices manuscrites n'ont pas de gras : on évite le faux gras. */
    fun lyricsWeight(id: String?): FontWeight = if (id == FontIds.PlusJakartaSans) FontWeight.ExtraBold else FontWeight.Normal
}

object SheetsType {
    val Title = TextStyle(fontFamily = SheetsFonts.PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    val Label = TextStyle(fontFamily = SheetsFonts.PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    val Body = TextStyle(fontFamily = SheetsFonts.PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val Caption = TextStyle(fontFamily = SheetsFonts.PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    val Overline = TextStyle(fontFamily = SheetsFonts.PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
    val SectionTag = TextStyle(fontFamily = SheetsFonts.PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.sp)

    /** Notes : police de la partition (défaut Architects Daughter). */
    fun chord(family: FontFamily = SheetsFonts.ArchitectsDaughter, sizeSp: Float = 32f) =
        TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = sizeSp.sp)
}
