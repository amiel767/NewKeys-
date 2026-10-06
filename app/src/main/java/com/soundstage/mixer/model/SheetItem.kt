package com.soundstage.mixer.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class SheetItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val key: String = "C",
    val tempo: Int = 120,
    val timeSignature: String = "4/4",
    val swing: Int = 54,
    val content: String = "",
    val colorIndex: Int = 0,
    val fontSize: Int = 18,
    val textColorHex: String = "#FFFFFF",
    val notationSystem: String = "NASHVILLE",
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getKeyColor(): Color {
        val palette = listOf(
            Color(0xFFF59E0B), // Amber
            Color(0xFF10B981), // Emerald
            Color(0xFF8B5CF6), // Purple
            Color(0xFFF43F5E), // Rose
            Color(0xFF06B6D4), // Cyan
            Color(0xFFEC4899), // Pink
            Color(0xFFEAB308), // Yellow
            Color(0xFF3B82F6), // Blue
            Color(0xFF14B8A6), // Teal
            Color(0xFFA855F7), // Violet
            Color(0xFFF97316), // Orange
            Color(0xFF6366F1)  // Indigo
        )
        return palette[Math.abs(colorIndex % palette.size)]
    }

    fun getTextColor(): Color {
        return try {
            Color(android.graphics.Color.parseColor(textColorHex))
        } catch (_: Exception) {
            Color.White
        }
    }

    fun getPageColor(): Color {
        val pagePalette = listOf(
            Color(0xFF1E2638), // Deep Slate Navy
            Color(0xFF28231D), // Warm Amber Charcoal
            Color(0xFF1B2925), // Forest Sage
            Color(0xFF271D2A), // Muted Plum / Wine
            Color(0xFF1A262C), // Nordic Teal
            Color(0xFF2A1C20), // Velvet Rose
            Color(0xFF2B281B), // Kraft Warm Olive
            Color(0xFF1B2332), // Midnight Blue
            Color(0xFF192928), // Dark Emerald
            Color(0xFF231B2E), // Royal Violet
            Color(0xFF2D211B), // Warm Clay
            Color(0xFF1E2030)  // Deep Indigo
        )
        return pagePalette[Math.abs(colorIndex % pagePalette.size)]
    }
}
