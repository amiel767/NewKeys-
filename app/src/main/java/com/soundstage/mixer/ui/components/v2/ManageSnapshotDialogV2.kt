package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ManageSnapshotDialogV2(
    slotKey: String,
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var recallMode by remember { mutableStateOf("crossfade") }
    val tM = Color(0xFFF1F4FA)
    val tG = Color(0xFF7B859A)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(
                    Color(0xFF232C3E), Color(0xFF1A2230))))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Manage Snapshot",
                        color = tM,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color(0x0FFFFFFF))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "×",
                            color = Color(0xFF9AA5BA),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (slotKey == "slot_default") "Default snapshot"
                    else "Snapshot · used in this session",
                    color = tG,
                    fontSize = 11.5.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "NAME",
                    color = tG,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = tM,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0xFF0F141D), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x668FB2E8), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "RECALL MODE",
                    color = tG,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F141D))
                        .padding(3.dp)
                ) {
                    listOf("immediate", "crossfade").forEach { mode ->
                        val sel = recallMode == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (sel) Color(0xFF38445E) else Color.Transparent)
                                .clickable { recallMode = mode },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mode.replaceFirstChar { it.uppercase() },
                                color = if (sel) Color(0xFF8FB2E8) else tG,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0AFFFFFF))
                            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Cancel",
                            color = Color(0xFFC7CFDD),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(listOf(
                                Color(0xFF9DBDEB), Color(0xFF7FA3DD))))
                            .clickable {
                                if (name.isNotBlank()) onSave(name.trim(), recallMode)
                            }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Save",
                            color = Color(0xFF0F1726),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
