package com.soundstage.mixer.utils

import com.soundstage.mixer.model.AppLanguage

object LanguageManager {

    private val translations = mapOf(
        AppLanguage.ENGLISH to mapOf(
            "settings" to "Settings",
            "language" to "Language",
            "select_language" to "Select Application Language",
            "notes_title" to "NOTES & LYRICS",
            "new_note" to "New Note",
            "convert_degrees" to "Convert Degrees",
            "save_scene" to "Save Scene",
            "audio_browser" to "Audio Browser",
            "chord_display" to "Chord Display",
            "save" to "Save",
            "close" to "Close",
            "delete" to "Delete",
            "copy" to "Copy",
            "create" to "Create",
            "cancel" to "Cancel",
            "key_root" to "Key / Scale",
            "recordings" to "Recordings",
            "loops" to "Loops",
            "drum_pad" to "Drum Pad",
            "presets" to "Presets",
            "snapshots" to "Snapshots",
            "insert_chord" to "Insert Played Chord"
        ),
        AppLanguage.FRENCH to mapOf(
            "settings" to "Paramètres",
            "language" to "Langue",
            "select_language" to "Sélectionner la langue de l'application",
            "notes_title" to "MES NOTES & PAROLES",
            "new_note" to "Nouvelle Note",
            "convert_degrees" to "Convertir Degrés",
            "save_scene" to "Sauvegarder Scène",
            "audio_browser" to "Explorateur Audio",
            "chord_display" to "Afficheur d'Accords",
            "save" to "Sauvegarder",
            "close" to "Fermer",
            "delete" to "Supprimer",
            "copy" to "Copier",
            "create" to "Créer",
            "cancel" to "Annuler",
            "key_root" to "Tonalité / Gamme",
            "recordings" to "Enregistrements",
            "loops" to "Boucles / Loops",
            "drum_pad" to "Pads de Batterie",
            "presets" to "Préréglages",
            "snapshots" to "Sub-Scènes",
            "insert_chord" to "Insérer Accord Joué"
        ),
        AppLanguage.SPANISH to mapOf(
            "settings" to "Ajustes",
            "language" to "Idioma",
            "select_language" to "Seleccionar idioma de la aplicación",
            "notes_title" to "MIS NOTAS Y LETRAS",
            "new_note" to "Nueva Nota",
            "convert_degrees" to "Convertir Grados",
            "save_scene" to "Guardar Escena",
            "audio_browser" to "Explorador de Audio",
            "chord_display" to "Visualizador de Acordes",
            "save" to "Guardar",
            "close" to "Cerrar",
            "delete" to "Eliminar",
            "copy" to "Copiar",
            "create" to "Crear",
            "cancel" to "Cancelar",
            "key_root" to "Tonalidad / Escala",
            "recordings" to "Grabaciones",
            "loops" to "Bucles / Loops",
            "drum_pad" to "Pads de Batería",
            "presets" to "Ajustes Predefinidos",
            "snapshots" to "Sub-Escenas",
            "insert_chord" to "Insertar Acorde Tocado"
        )
    )

    fun getString(key: String, language: AppLanguage = AppLanguage.ENGLISH): String {
        return translations[language]?.get(key)
            ?: translations[AppLanguage.ENGLISH]?.get(key)
            ?: key
    }
}
