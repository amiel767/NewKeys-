package com.soundstage.mixer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.MidiActionCategory
import com.soundstage.mixer.model.MidiActionType
import com.soundstage.mixer.model.MidiControlMode
import com.soundstage.mixer.model.MidiControlType
import com.soundstage.mixer.model.MidiDeviceItem
import com.soundstage.mixer.model.MidiMappingItem

/**
 * MIDI mapping, as a page inside Settings — not a floating window.
 *
 * Three levels: the category list, the actions of one category, then the manual
 * editor for a single action. Every row is drawn with the Settings components
 * (`SectionLabel`, `SettingRow`, `SetSwitch`, `SetDivider`) so the page is
 * indistinguishable from the rest of the Settings screen.
 */
@Composable
fun MidiMappingPage(
    mappings: List<MidiMappingItem>,
    antiJitter: Boolean,
    jitterWindowMs: Int,
    midiDevices: List<MidiDeviceItem>,
    learningActionType: MidiActionType?,
    lastMidiSummary: String,
    onStartLearn: (MidiActionType) -> Unit,
    onCancelLearn: () -> Unit,
    onUnmap: (MidiActionType) -> Unit,
    onToggleEnabled: (MidiActionType) -> Unit,
    onUpdateManual: (MidiActionType, MidiControlType, Int, Int) -> Unit,
    onSetMode: (MidiActionType, MidiControlMode) -> Unit,
    onResetDefaults: () -> Unit,
    onClearAll: () -> Unit,
    onToggleDevice: (String) -> Unit,
    onAntiJitterChange: (Boolean) -> Unit,
    onJitterWindowChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var category by remember { mutableStateOf<MidiActionCategory?>(null) }
    var editing by remember { mutableStateOf<MidiActionType?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val byAction = remember(mappings) { mappings.associateBy { it.actionType } }
    fun itemOf(action: MidiActionType) = byAction[action]

    Column(modifier.fillMaxSize()) {
        PageHeader(
            title = editing?.title ?: category?.displayName ?: "MIDI Mapping",
            subtitle = editing?.subtitle ?: category?.subtitle ?: "Map controllers to app actions",
            onBack = {
                when {
                    editing != null -> editing = null
                    category != null -> category = null
                    else -> onBack()
                }
            }
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp)
        ) {
            when {
                editing != null -> manualEditor(
                    action = editing!!,
                    item = itemOf(editing!!),
                    learning = learningActionType == editing,
                    onStartLearn = { onStartLearn(editing!!) },
                    onCancelLearn = onCancelLearn,
                    onUnmap = { onUnmap(editing!!) },
                    onToggleEnabled = { onToggleEnabled(editing!!) },
                    onUpdateManual = { t, c, n -> onUpdateManual(editing!!, t, c, n) },
                    onSetMode = { m -> onSetMode(editing!!, m) }
                )

                category != null -> categoryActions(
                    category = category!!,
                    itemOf = ::itemOf,
                    learningActionType = learningActionType,
                    onOpen = { editing = it }
                )

                else -> {
                    item("input_label") { SectionLabel("Input") }
                    item("anti_jitter") {
                        SettingRow(
                            title = "Anti-jitter filter",
                            hint = "Ignores repeat signals from worn buttons and encoders."
                        ) {
                            SetSwitch(antiJitter, onAntiJitterChange)
                        }
                    }
                    item("jitter_div") { SetDivider() }
                    item("jitter_window") {
                        SliderRow(
                            title = "Filter sensitivity window",
                            hint = null,
                            value = jitterWindowMs.toFloat(),
                            min = SettingsRanges.JITTER_MIN.toFloat(),
                            max = SettingsRanges.JITTER_MAX.toFloat(),
                            step = 1f,
                            defaultValue = SettingsDefaults.JITTER_WINDOW_MS.toFloat(),
                            format = { "${it.toInt()} ms" },
                            onChange = { onJitterWindowChange(it.toInt()) },
                            enabled = antiJitter
                        )
                    }

                    item("devices_label") { SectionLabel("Connected devices") }
                    if (midiDevices.isEmpty()) {
                        item("devices_empty") {
                            SettingRow(
                                title = "No USB MIDI device",
                                hint = "Plug a controller to route notes, faders and pads."
                            )
                        }
                    } else {
                        items(midiDevices, key = { it.id }) { dev ->
                            SettingRow(
                                title = dev.name,
                                hint = if (dev.isConnected) "Connected" else "Disconnected"
                            ) {
                                SetSwitch(
                                    on = dev.isEnabled,
                                    onChange = { onToggleDevice(dev.id) },
                                    enabled = dev.isConnected
                                )
                            }
                            SetDivider()
                        }
                    }

                    item("cat_label") { SectionLabel("Mapping") }
                    val categories = listOf(
                        MidiActionCategory.SNAPSHOTS,
                        MidiActionCategory.PATCHES,
                        MidiActionCategory.TRACKS,
                        MidiActionCategory.DRUMPAD,
                        MidiActionCategory.GLOBAL
                    )
                    items(categories, key = { it.name }) { cat ->
                        val all = MidiActionType.values().filter { it.category == cat }
                        val done = all.count { itemOf(it)?.isAssigned == true }
                        SettingRow(
                            title = cat.displayName,
                            hint = cat.subtitle,
                            onClick = { category = cat }
                        ) {
                            Text("$done / ${all.size}", style = setText(12.sp, FontWeight.Bold, SetColors.Mute))
                            SetIcon(SetIconKind.Chevron, SetColors.Mute, Modifier.size(16.dp))
                        }
                        SetDivider()
                    }

                    item("reset") {
                        SettingRow(
                            title = "Reset to defaults",
                            hint = "Restore the factory mapping for every action.",
                            onClick = { confirmReset = true }
                        )
                    }
                    item("reset_div") { SetDivider() }
                    item("clear") {
                        SettingRow(
                            title = "Clear all mappings",
                            hint = "Remove every binding. Actions keep working from the UI.",
                            onClick = { confirmClear = true }
                        )
                    }
                }
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset mappings?",
            message = "Every action goes back to its factory binding. Your own assignments are lost.",
            confirmLabel = "Reset",
            danger = false,
            onConfirm = { onResetDefaults(); confirmReset = false },
            onDismiss = { confirmReset = false }
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all mappings?",
            message = "Every binding is removed. This cannot be undone.",
            confirmLabel = "Clear all",
            danger = true,
            onConfirm = { onClearAll(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(white(0.06f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { SetIcon(SetIconKind.Back, SetColors.Soft, Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = setText(17.sp, FontWeight.Bold, SetColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = setText(11.sp, color = SetColors.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categoryActions(
    category: MidiActionCategory,
    itemOf: (MidiActionType) -> MidiMappingItem?,
    learningActionType: MidiActionType?,
    onOpen: (MidiActionType) -> Unit
) {
    val actions = MidiActionType.values().filter { it.category == category }
    items(actions, key = { it.name }) { action ->
        val item = itemOf(action)
        val assigned = item?.isAssigned == true
        val binding = if (assigned) item!!.formatDescription() else "Unassigned"
        SettingRow(
            title = action.title,
            hint = binding,
            onClick = { onOpen(action) }
        ) {
            if (learningActionType == action) {
                Text("Learn…", style = setText(11.5.sp, FontWeight.Bold, SetColors.Accent))
            } else if (assigned) {
                Text("Mapped", style = setText(11.5.sp, FontWeight.Bold, SetColors.Ok))
            } else {
                SetIcon(SetIconKind.Chevron, SetColors.Mute, Modifier.size(16.dp))
            }
        }
        SetDivider()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.manualEditor(
    action: MidiActionType,
    item: MidiMappingItem?,
    learning: Boolean,
    onStartLearn: () -> Unit,
    onCancelLearn: () -> Unit,
    onUnmap: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUpdateManual: (MidiControlType, Int, Int) -> Unit,
    onSetMode: (MidiControlMode) -> Unit
) {
    val type = item?.midiType ?: action.defaultType
    val channel = item?.channel ?: -1
    val number = item?.controlNumber ?: -1
    val assigned = item?.isAssigned == true

    item("learn") {
        SettingRow(
            title = if (learning) "Waiting for a signal…" else "Learn",
            hint = if (learning) "Move the control on your device." else "Press a control to bind it.",
            onClick = { if (learning) onCancelLearn() else onStartLearn() }
        ) {
            Text(
                if (learning) "Cancel" else "Start",
                style = setText(12.sp, FontWeight.Bold, if (learning) SetColors.Warn else SetColors.Accent)
            )
        }
    }
    item("learn_div") { SetDivider() }

    item("enabled") {
        SettingRow(title = "Enabled", hint = "The action reacts to incoming MIDI when on.") {
            SetSwitch(item?.isEnabled ?: true, { onToggleEnabled() }, enabled = assigned)
        }
    }
    item("enabled_div") { SetDivider() }

    item("type_label") { SectionLabel("Signal") }
    item("type") {
        ChoiceRow(
            options = listOf(
                MidiControlType.CC to "CC",
                MidiControlType.NOTE to "Note",
                MidiControlType.PROGRAM_CHANGE to "PC"
            ),
            selected = type,
            onSelect = { onUpdateManual(it, channel, number) }
        )
    }

    item("channel_label") { SectionLabel("Channel") }
    item("channel") {
        SliderRow(
            title = "MIDI channel",
            hint = null,
            value = (channel + 1).toFloat(),
            min = 0f,
            max = 16f,
            step = 1f,
            defaultValue = 0f,
            format = { v -> if (v.toInt() == 0) "Omni (All)" else "Ch. ${v.toInt()}" },
            onChange = { onUpdateManual(type, it.toInt() - 1, number) }
        )
    }

    item("number_label") { SectionLabel(if (type == MidiControlType.PROGRAM_CHANGE) "Program number" else "Control number") }
    item("number") {
        SliderRow(
            title = if (type == MidiControlType.PROGRAM_CHANGE) "Program" else "Number",
            hint = null,
            value = number.coerceAtLeast(0).toFloat(),
            min = 0f,
            max = 127f,
            step = 1f,
            defaultValue = 0f,
            format = { "N° ${it.toInt()}" },
            onChange = { onUpdateManual(type, channel, it.toInt()) }
        )
    }

    item("mode_label") { SectionLabel("Behaviour") }
    item("mode") {
        ChoiceRow(
            options = MidiControlMode.values().map { it to it.displayName },
            selected = item?.mode ?: MidiControlMode.MOMENTARY,
            onSelect = onSetMode
        )
    }

    if (assigned) {
        item("unmap") {
            SettingRow(
                title = "Remove mapping",
                hint = "This action stops reacting to MIDI.",
                onClick = onUnmap
            ) {
                Text("Unmap", style = setText(12.sp, FontWeight.Bold, SetColors.Danger))
            }
        }
    }
}

/** Segmented choice drawn with the Settings palette. */
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) SetColors.Accent else SetColors.Well)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = setText(12.sp, FontWeight.Bold, if (on) SetColors.AccentText else SetColors.Soft),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(white(0.55f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SetColors.PanelTop)
                .clickable(enabled = false) {}
                .padding(18.dp)
        ) {
            Text(title, style = setText(15.sp, FontWeight.Bold, SetColors.Ink))
            Spacer(Modifier.height(6.dp))
            Text(message, style = setText(12.sp, color = SetColors.Mute))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(SetColors.Well).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", style = setText(12.5.sp, FontWeight.Bold, SetColors.Soft)) }
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (danger) SetColors.Danger else SetColors.AccentDeep)
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center
                ) { Text(confirmLabel, style = setText(12.5.sp, FontWeight.Bold, Color_White)) }
            }
        }
    }
}

private val Color_White = androidx.compose.ui.graphics.Color.White
