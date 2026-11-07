package com.aetherkeys.organ

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.soundstage.mixer.R // ← adapte au namespace de ton module (R de l'app)

/** Dimensions de référence de la maquette HTML (891 × 411). L'écran est mis à l'échelle à partir de ces valeurs. */
internal object OrganDesign {
    const val W = 891f
    const val H = 411f
    const val PAD = 8f
    const val GAP = 6f
    const val COL1 = 218.75f      // 25 %
    const val COL3 = 245f         // 28 %
    const val COL2 = 399.25f      // 1fr
    const val ROW1 = 82f
    const val ROW2 = 104f
    const val ROW3 = 112f
    const val ROW4 = 79f
    val X1 = PAD
    val X2 = PAD + COL1 + GAP
    val X3 = X2 + COL2 + GAP
    val Y1 = PAD
    val Y2 = Y1 + ROW1 + GAP
    val Y3 = Y2 + ROW2 + GAP
    val Y4 = Y3 + ROW3 + GAP
}

internal object OrganColors {
    val BgMainTop = Color(0xFF141B27)
    val BgMainBottom = Color(0xFF0F141D)
    val Panel = Color(0xFF1E2433)
    val PanelDark = Color(0xFF151A24)
    val Border = Color(0x0DFFFFFF)            // rgba(255,255,255,.05)
    val TextMain = Color(0xFFF4F6FB)
    val TextSub = Color(0xFF7B859A)
    val Accent = Color(0xFF8FB2E8)
    val AccentSoft = Color(0xFFA8C5F0)
    val AccentGlow = Color(0x668FB2E8)        // rgba(143,178,232,.4)
    val GroupText = Color(0xFFA0AABF)
    val Red = Color(0xFFEF4444)
    val LedRed = Color(0xFFFF4A4A)
    val LedGreen = Color(0xFF4ADE80)
    val BtnTop = Color(0xFF363E50)
    val BtnBottom = Color(0xFF252C3A)
    val Dark1 = Color(0xFF1A1F2B)
    val Dark2 = Color(0xFF2A3141)
    val Black = Color.Black
}

internal object OrganFonts {
    /** Le HTML utilisait IBM Plex Mono ; le dépôt a déjà sa police mono (share_tech_mono),
     *  celle qu'AeFonts.mono emploie. On s'y range plutôt que d'ajouter trois fichiers. */
    val PlexMono = FontFamily(Font(R.font.share_tech_mono))

    /** Police d'interface (rôle de 'Inter' / system-ui dans le HTML). Remplace par ta police globale si besoin. */
    var Ui: FontFamily = FontFamily.Default

    /** `font-family: monospace` du HTML (valeurs EQ / Drive). */
    val Mono: FontFamily = FontFamily.Monospace
}
