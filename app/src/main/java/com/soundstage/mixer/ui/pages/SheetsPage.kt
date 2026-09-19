package com.soundstage.mixer.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.theme.*

private val SheetsDarkBg = Color(0xFF0F121C)
private val CardBgDark = Color(0xFF161B26)
private val CardBorderDark = Color(0x33FFFFFF)

@Composable
fun SheetsPage(
    notesText: String,
    onNotesChange: (String) -> Unit,
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
    onBackToMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val commonChords = listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim", "C7", "G7", "F#m", "Bb", "Eb")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SheetsDarkBg)
            .padding(8.dp)
            .testTag("sheets_page_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBgDark)
                    .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBackToMixer,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x1FFFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Retour Mixeur",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Soundstage • Grilles & Notes",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.weight(1f))

                // Transposition
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Transp. :", fontSize = 11.sp, color = TextDim)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { onTransposeChange(transpose - 1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        text = if (transpose > 0) "+$transpose" else "$transpose",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { onTransposeChange(transpose + 1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Bouton Effacer
                IconButton(
                    onClick = { onNotesChange("") },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Effacer", tint = MuteRed, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Raccourcis d'accords cliquables
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(commonChords) { chord ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1FFFFFFF))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .clickable {
                                onNotesChange(if (notesText.isEmpty()) chord else "$notesText  $chord")
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(chord, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SoloAmber)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zone d'écriture plein écran
            OutlinedTextField(
                value = notesText,
                onValueChange = onNotesChange,
                placeholder = {
                    Text(
                        text = "Écrivez vos grilles d'accords, paroles ou structures de morceaux ici...\nExemple :\n[Intro]  C  •  Am  •  F  •  G\n[Couplet]  C  •  G  •  Am  •  F",
                        fontSize = 13.sp,
                        color = TextDim,
                        fontFamily = FontFamily.Monospace
                    )
                },
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBgDark,
                    unfocusedContainerColor = CardBgDark,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = CardBorderDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
