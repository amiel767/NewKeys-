// TrackSettingsDialog.kt
// Fenêtre « Track Settings » + page MIDI (device + canal), Jetpack Compose, sans image ni dépendance d'icônes.

package com.soundstage.mixer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.cos
import kotlin.math.sin

// ───────────────────────── Palette (relevée sur l'écran principal) ─────────────────────────

private object Ss {
    val Bg = Color(0xFF151924)
    val PanelBorder = Color(0xFF262C3C)
    val CardTop = Color(0xFF2D3243)
    val CardBottom = Color(0xFF262B3A)
    val CardBorder = Color(0xFF353C50)
    val KeyTop = Color(0xFF323849)
    val KeyBottom = Color(0xFF292E3D)
    val KeyBorder = Color(0xFF3A4156)
    val TextPrimary = Color(0xFFE8EBF3)
    val TextSoft = Color(0xFFA8B0C4)
    val TextMuted = Color(0xFF7C818E)
    val BlueA = Color(0xFFA7C6F0)   // bouton « Default » / état sélectionné
    val BlueB = Color(0xFFA2B9E5)
    val BlueBorder = Color(0xFFBCD2F4)
    val OnBlue = Color(0xFF252C3F)
    val OnBlueSoft = Color(0xFF46526B)
    val Connected = Color(0xFF3FB88A)
    val Disconnected = Color(0xFF555B6B)

    val CardBrush get() = Brush.verticalGradient(listOf(CardTop, CardBottom))
    val KeyBrush get() = Brush.verticalGradient(listOf(KeyTop, KeyBottom))
    val SelectedBrush get() = Brush.linearGradient(listOf(BlueA, BlueB))
}

// ───────────────────────── Modèles ─────────────────────────

data class MidiDevice(
    val id: String,
    val name: String,
    val connected: Boolean,
    val subtitle: String = "USB",
)

private enum class Page { Main, Midi, Velocity }

// ───────────────────────── API publique ─────────────────────────

/**
 * @param trackColor        couleur d'identité de la piste (ex. orange 0xFFD1812C)
 * @param selectedDeviceId  null = « All devices »
 * @param midiChannel       0 = Omni, 1..16 = canal précis
 * @param velocityCurve     f(0..1) -> 0..1, utilisée pour l'aperçu de courbe
 */
@Composable
fun TrackSettingsDialog(
    trackNumber: Int,
    soundName: String,
    trackColor: Color,
    devices: List<MidiDevice>,
    selectedDeviceId: String?,
    midiChannel: Int,
    onSelectDevice: (String?) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onSetVelocityCurve: (Float) -> Unit = {},
    velocityConfig: VelocityConfig = VelocityConfig(),
    onVelocityConfigChange: (VelocityConfig) -> Unit = {},
    onOpenVelocity: () -> Unit = {},
    onDismiss: () -> Unit,
    velocityCurve: (Float) -> Float = { it },
) {
    var page by rememberSaveable { mutableStateOf(Page.Main) }
    val panelShape = RoundedCornerShape(18.dp)

    val targetWidth = when (page) {
        Page.Velocity -> 750.dp
        else -> 520.dp
    }
    val targetHeightFraction = when (page) {
        Page.Velocity -> 0.94f
        else -> 0.85f
    }
    val animatedWidth by animateDpAsState(targetValue = targetWidth, animationSpec = tween(220), label = "dialogWidth")
    val animatedHeight by animateFloatAsState(targetValue = targetHeightFraction, animationSpec = tween(220), label = "dialogHeight")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .width(animatedWidth)
                .widthIn(max = 780.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(animatedHeight)
                .clip(panelShape)
                .background(Brush.verticalGradient(listOf(Ss.Bg, lerp(Ss.Bg, trackColor, 0.10f))))
                .border(1.dp, Ss.PanelBorder, panelShape)
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "trackSettingsPage",
            ) { current ->
                when (current) {
                    Page.Main -> MainPage(
                        trackNumber = trackNumber,
                        soundName = soundName,
                        trackColor = trackColor,
                        midiSummary = midiSummary(devices, selectedDeviceId, midiChannel),
                        velocityCurve = velocityCurve,
                        onOpenVelocity = {
                            page = Page.Velocity
                        },
                        onOpenMidi = { page = Page.Midi },
                        onClose = onDismiss,
                    )

                    Page.Midi -> MidiPage(
                        trackNumber = trackNumber,
                        trackColor = trackColor,
                        devices = devices,
                        selectedDeviceId = selectedDeviceId,
                        midiChannel = midiChannel,
                        onSelectDevice = onSelectDevice,
                        onSelectChannel = onSelectChannel,
                        onBack = { page = Page.Main },
                        onClose = onDismiss,
                    )

                    Page.Velocity -> VelocitySettingsPage(
                        trackNumber = trackNumber,
                        soundName = soundName,
                        trackColor = trackColor,
                        config = velocityConfig,
                        onConfigChange = onVelocityConfigChange,
                        onBack = { page = Page.Main },
                        onClose = onDismiss,
                        onDone = onDismiss,
                    )
                }
            }
        }
    }
}

private fun midiSummary(devices: List<MidiDevice>, selectedId: String?, channel: Int): String {
    val device = devices.firstOrNull { it.id == selectedId }?.name ?: "All devices"
    val ch = if (channel == 0) "Omni" else "Ch $channel"
    return "$device · $ch"
}

// ───────────────────────── Pages ─────────────────────────

@Composable
private fun MainPage(
    trackNumber: Int,
    soundName: String,
    trackColor: Color,
    midiSummary: String,
    velocityCurve: (Float) -> Float,
    onOpenVelocity: () -> Unit,
    onOpenMidi: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header(
            title = "Track $trackNumber Settings",
            subtitle = "Sound: $soundName",
            onClose = onClose,
            leading = {
                IconBadge(trackColor, 36.dp) { SsIcon(IconKind.Sliders, Color.White, 19.dp) }
            },
        )
        Spacer(Modifier.height(4.dp))
        SettingCard(
            trackColor = trackColor,
            icon = IconKind.Gauge,
            title = "Velocity & Dynamic Response",
            subtitle = "Strike sensitivity and touch curve",
            onClick = onOpenVelocity,
            trailing = { CurvePreview(trackColor, velocityCurve) },
        )
        SettingCard(
            trackColor = trackColor,
            icon = IconKind.Midi,
            title = "MIDI",
            subtitle = midiSummary,
            onClick = onOpenMidi,
        )
    }
}

@Composable
private fun MidiPage(
    trackNumber: Int,
    trackColor: Color,
    devices: List<MidiDevice>,
    selectedDeviceId: String?,
    midiChannel: Int,
    onSelectDevice: (String?) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Header(
            title = "MIDI",
            subtitle = "Track $trackNumber listens to",
            onClose = onClose,
            leading = { KeyButton(IconKind.Back, onClick = onBack) },
        )

        SectionLabel("DEVICE")
        DeviceRow(
            name = "All devices",
            subtitle = "Listen to everything connected",
            icon = IconKind.Plugs,
            connected = null,
            selected = selectedDeviceId == null,
            trackColor = trackColor,
            onClick = { onSelectDevice(null) },
        )
        devices.forEach { d ->
            DeviceRow(
                name = d.name,
                subtitle = if (d.connected) "${d.subtitle} · connected" else "${d.subtitle} · not connected",
                icon = IconKind.Usb,
                connected = d.connected,
                selected = selectedDeviceId == d.id,
                trackColor = trackColor,
                onClick = { onSelectDevice(d.id) },
            )
        }

        SectionLabel("CHANNEL")
        ChannelChip("Omni", midiChannel == 0, Modifier.fillMaxWidth(), 40.dp, trackColor) { onSelectChannel(0) }
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 1..8) {
                    val ch = row * 8 + c
                    ChannelChip("$ch", midiChannel == ch, Modifier.weight(1f), 38.dp, trackColor) { onSelectChannel(ch) }
                }
            }
        }
    }
}

@Composable
private fun VelocityPage(
    trackNumber: Int,
    trackColor: Color,
    velocityCurve: (Float) -> Float,
    onSetVelocityCurve: (Float) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    var curveValue by remember { mutableFloatStateOf(0.5f) }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header(
            title = "Velocity & Dynamic Response",
            subtitle = "Track $trackNumber touch curve",
            onClose = onClose,
            leading = { KeyButton(IconKind.Back, onClick = onBack) },
        )

        SectionLabel("DYNAMIC VELOCITY CURVE")
        Box(
            Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ss.CardTop)
                .border(1.dp, Ss.CardBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            CurvePreview(trackColor, velocityCurve)
        }

        SectionLabel("CURVE RESPONSE SLIDER")
        Slider(
            value = curveValue,
            onValueChange = {
                curveValue = it
                onSetVelocityCurve(it)
            },
            valueRange = 0.0f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = Ss.KeyBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("PRESETS")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Soft Touch" to 0.15f, "Linear (1:1)" to 0.50f, "Hard Touch" to 0.85f).forEach { (lbl, valVal) ->
                val isSelected = abs(curveValue - valVal) < 0.08f
                ChannelChip(lbl, isSelected, Modifier.weight(1f), 38.dp) {
                    curveValue = valVal
                    onSetVelocityCurve(valVal)
                }
            }
        }
    }
}

// ───────────────────────── Composants ─────────────────────────

@Composable
private fun Header(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = Ss.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Ss.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        KeyButton(IconKind.Close, onClick = onClose)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Ss.TextMuted,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 0.dp),
    )
}

/** Carré d'icône entièrement coloré avec la couleur de piste et icône en blanc. */
@Composable
private fun IconBadge(trackColor: Color, size: Dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(trackColor),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Petit bouton carré, même matière que S / M. */
@Composable
private fun KeyButton(icon: IconKind, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .ssClick(onClick)
            .ssSurface(RoundedCornerShape(9.dp), Ss.KeyBrush, Ss.KeyBorder),
        contentAlignment = Alignment.Center,
    ) { SsIcon(icon, Color(0xFFC9CFDD), 16.dp) }
}

@Composable
private fun SettingCard(
    trackColor: Color,
    icon: IconKind,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cardBrush = Brush.verticalGradient(
        listOf(
            lerp(Ss.CardTop, trackColor, 0.08f),
            lerp(Ss.CardBottom, trackColor, 0.04f),
        )
    )
    val cardBorder = Color(0x18FFFFFF)
    Row(
        Modifier
            .fillMaxWidth()
            .ssClick(onClick)
            .ssSurface(RoundedCornerShape(11.dp), cardBrush, cardBorder)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(trackColor, 36.dp) { SsIcon(icon, Color.White, 19.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ss.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Ss.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        SsIcon(IconKind.Chevron, Ss.TextMuted, 16.dp)
    }
}

@Composable
private fun CurvePreview(color: Color, curve: (Float) -> Float) {
    val bright = brighten(color)
    Canvas(Modifier.size(44.dp, 30.dp)) {
        val pad = 3.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val path = Path()
        val n = 24
        for (i in 0..n) {
            val t = i / n.toFloat()
            val v = curve(t).coerceIn(0f, 1f)
            val x = pad + t * w
            val y = pad + h * (1f - v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, bright.copy(alpha = 0.30f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, bright, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** connected = null -> pas de pastille (ligne « All devices »). */
@Composable
private fun DeviceRow(
    name: String,
    subtitle: String,
    icon: IconKind,
    connected: Boolean?,
    selected: Boolean,
    trackColor: Color = Ss.BlueA,
    onClick: () -> Unit,
) {
    val brush = if (selected) {
        Brush.verticalGradient(
            listOf(
                lerp(Ss.CardTop, trackColor, 0.28f),
                lerp(Ss.CardBottom, trackColor, 0.16f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                lerp(Ss.CardTop, trackColor, 0.10f),
                lerp(Ss.CardBottom, trackColor, 0.05f),
            )
        )
    }
    val border = if (selected) trackColor.copy(alpha = 0.65f) else trackColor.copy(alpha = 0.22f)
    val titleColor = if (selected) Color.White else Ss.TextPrimary
    val subColor = if (selected) Color.White.copy(alpha = 0.75f) else Ss.TextMuted
    val iconColor = if (selected) brighten(trackColor) else Ss.TextSoft

    Row(
        Modifier
            .fillMaxWidth()
            .ssClick(onClick)
            .ssSurface(RoundedCornerShape(11.dp), brush, border)
            .alpha(if (connected == false && !selected) 0.6f else 1f)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SsIcon(icon, iconColor, 20.dp)
        Column(Modifier.weight(1f)) {
            Text(name, color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = subColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (connected != null) {
            val dot = if (connected) Ss.Connected else Ss.Disconnected
            Box(
                Modifier
                    .size(7.dp)
                    .drawBehind {
                        if (connected) {
                            val r = 9.dp.toPx()
                            drawCircle(
                                Brush.radialGradient(listOf(dot.copy(alpha = 0.55f), Color.Transparent), center = center, radius = r),
                                radius = r,
                                center = center,
                            )
                        }
                    }
                    .clip(CircleShape)
                    .background(dot)
            )
        }
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) Ss.OnBlue else Color.Transparent)
                .border(1.5.dp, if (selected) Ss.OnBlue else Color(0xFF4A526A), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) SsIcon(IconKind.Check, Ss.BlueA, 12.dp) }
    }
}

@Composable
private fun ChannelChip(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    height: Dp,
    trackColor: Color = Ss.BlueA,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val selBrush = Brush.verticalGradient(
        listOf(
            lerp(Ss.CardTop, trackColor, 0.38f),
            lerp(Ss.CardBottom, trackColor, 0.20f),
        )
    )
    val selBorder = trackColor.copy(alpha = 0.70f)
    Box(
        modifier
            .height(height)
            .ssClick(onClick)
            .ssSurface(
                shape,
                if (selected) selBrush else Ss.KeyBrush,
                if (selected) selBorder else Ss.KeyBorder,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFFC9CFDD),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// ───────────────────────── Modifiers utilitaires ─────────────────────────

/** Surface avec dégradé, bordure fine et reflet en haut (même matière que S / M). */
private fun Modifier.ssSurface(shape: Shape, brush: Brush, border: Color): Modifier =
    this
        .clip(shape)
        .background(brush)
        .border(1.dp, border, shape)
        .drawWithContent {
            drawContent()
            drawLine(
                Color.White.copy(alpha = 0.07f),
                Offset(10.dp.toPx(), 1.dp.toPx()),
                Offset(size.width - 10.dp.toPx(), 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }

/** Clic avec léger effet d'enfoncement, sans ripple. */
@Composable
private fun Modifier.ssClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

private fun brighten(c: Color): Color = lerp(c, Color.White, 0.15f)

// ───────────────────────── Icônes (Canvas, aucune ressource) ─────────────────────────

private enum class IconKind { Close, Back, Check, Chevron, Sliders, Gauge, Midi, Usb, Plugs }

@Composable
private fun SsIcon(kind: IconKind, tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val sw = 2f * s
        val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun poly(vararg pts: Pair<Float, Float>) {
            val path = Path()
            pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * s, y * s) else path.lineTo(x * s, y * s) }
            drawPath(path, tint, style = stroke)
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        fun dot(x: Float, y: Float, r: Float) = drawCircle(tint, r * s, p(x, y))

        when (kind) {
            IconKind.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            IconKind.Back -> { line(19f, 12f, 5f, 12f); poly(11f to 6f, 5f to 12f, 11f to 18f) }
            IconKind.Check -> poly(5f to 13f, 10f to 18f, 19f to 7f)
            IconKind.Chevron -> poly(9f to 6f, 15f to 12f, 9f to 18f)
            IconKind.Sliders -> {
                line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f)
                dot(9f, 6f, 2.6f); dot(15f, 12f, 2.6f); dot(8f, 18f, 2.6f)
            }
            IconKind.Gauge -> {
                drawArc(
                    tint, 180f, 180f, false,
                    topLeft = p(3f, 4f), size = Size(18f * s, 18f * s), style = stroke,
                )
                line(12f, 13f, 16f, 8f)
                dot(12f, 13f, 1.6f)
            }
            IconKind.Midi -> {
                drawCircle(tint, 9f * s, p(12f, 12f), style = stroke)
                for (deg in intArrayOf(0, 45, 90, 135, 180)) {
                    val a = Math.toRadians(deg.toDouble())
                    dot(12f + 5.2f * cos(a).toFloat(), 12.5f - 5.2f * sin(a).toFloat(), 1.2f)
                }
            }
            IconKind.Usb -> {
                line(12f, 19f, 12f, 5f)
                poly(9.5f to 7.5f, 12f to 4.5f, 14.5f to 7.5f)
                dot(12f, 19.5f, 2f)
                poly(12f to 14f, 7f to 11f); dot(7f, 10.5f, 1.6f)
                poly(12f to 12f, 17f to 9f)
                drawRect(tint, p(15.6f, 5.8f), Size(3.4f * s, 3.4f * s))
            }
            IconKind.Plugs -> {
                drawCircle(tint, 5f * s, p(9f, 12f), style = stroke)
                drawCircle(tint, 5f * s, p(15f, 12f), style = stroke)
            }
        }
    }
}
