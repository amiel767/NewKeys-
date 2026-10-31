package com.aetherkeys.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Le chrome de la fenêtre : l'en-tête et les cartes de la rangée.
 *
 * Toutes les cotes sont des UNITÉS DE MAQUETTE. `FxStage` a déjà mis la densité
 * à l'échelle, donc écrire `209.dp` ici donne exactement 208,8 unités de
 * maquette sur l'écran — la cote mesurée sur la référence.
 */

/** En-tête : titre, compteur, boutons. */
@Composable
fun FxHeader(
    title: String,
    big: Boolean,
    onBack: (() -> Unit)?,
    counter: String? = null,
    onClose: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(FxGeo.HEAD.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            FxIconButton(AeFxIcons.BACK, onClick = onBack)
        } else {
            // Le trait vertical qui marque le titre du rack.
            Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF7F9BC7)))
        }
        Spacer(Modifier.width(10.dp))
        if (big) {
            Text(
                title,
                color = FxC.ink,
                fontSize = 27.sp,
                fontFamily = FxFonts.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.graphicsLayerRotate(),
            )
        } else {
            Text(title, color = FxC.ink, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (counter != null) {
            Text(
                counter.uppercase(),
                color = FxC.mute, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp,
            )
        }
        trailing()
        FxIconButton(AeFxIcons.CLOSE, onClick = onClose)
    }
}

/**
 * Le titre d'une page d'effet est en Cinzel et VOLONTAIREMENT posé de travers —
 * 1,2° de rotation et 3 unités de descente. Bien aligné il se lisait comme un
 * libellé d'interface ; décalé il se lit comme une enseigne.
 */
private fun Modifier.graphicsLayerRotate(): Modifier =
    graphicsLayer(rotationZ = -1.2f, translationY = 3f)

/** Bouton carré de l'en-tête : fond sobre, glyphe centré. */
@Composable
fun FxIconButton(icon: String, onClick: () -> Unit, size: Int = 28, glyph: Int = 14) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .fxSvg(AeFxSvg.ghost(10f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FxIcon(icon, glyph.dp, FxC.dim) }
}

/** Emplacement libre : le cadre en pointillé et le `+`. */
@Composable
fun FxEmptySlot(label: String, onAdd: () -> Unit) {
    Box(
        Modifier.width(FxGeo.CARD_W.dp).fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAdd)
            .fxSvg(AeFxSvg.emptySlot()),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            label.uppercase(),
            color = FxC.mute, fontSize = 8.5.sp, fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
}

/** Rangée des quatre emplacements, à la largeur de la maquette. */
@Composable
fun FxSlotRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().fillMaxHeight()) {
        content()
    }
}

/** L'espacement entre deux cartes. */
@Composable
fun FxGap() = Spacer(Modifier.width(FxGeo.GAP.dp))

/** Colonne qui empile un libellé et sa valeur, comme dans la maquette. */
@Composable
fun FxLabelValue(label: String, value: String, valueColor: Color = FxC.ink) {
    Column {
        Text(label.uppercase(), color = FxC.mute, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.1.sp, maxLines = 1)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
