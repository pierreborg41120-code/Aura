package com.aura.launcher.search

import android.content.Intent
import android.provider.Settings
import com.aura.launcher.AppEntry

/**
 * Raccourci vers une page des réglages d'Android.
 * [intentActions] : actions à essayer dans l'ordre (certains constructeurs n'ont pas toutes les pages).
 * [keywords] : mots-clés sans accents ni majuscules.
 */
data class SettingsShortcut(
    val id: String,
    val label: String,
    val intentActions: List<String>,
    val keywords: List<String>,
)

object SettingsShortcuts {
    private val all = listOf(
        SettingsShortcut("wifi", "Wi-Fi", listOf(Settings.ACTION_WIFI_SETTINGS), listOf("wifi", "wi-fi", "internet")),
        SettingsShortcut(
            "bluetooth", "Bluetooth",
            listOf(Settings.ACTION_BLUETOOTH_SETTINGS),
            listOf("bluetooth", "casque", "ecouteurs", "appairer"),
        ),
        SettingsShortcut(
            "network", "Réseau et internet",
            listOf(Settings.ACTION_WIRELESS_SETTINGS),
            listOf("reseau", "donnees mobiles", "4g", "5g", "sim", "partage de connexion"),
        ),
        SettingsShortcut(
            "airplane", "Mode avion",
            listOf(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            listOf("mode avion", "avion"),
        ),
        SettingsShortcut(
            "battery", "Batterie",
            listOf(Intent.ACTION_POWER_USAGE_SUMMARY, Settings.ACTION_BATTERY_SAVER_SETTINGS),
            listOf("batterie", "economiseur", "autonomie", "energie"),
        ),
        SettingsShortcut(
            "display", "Affichage",
            listOf(Settings.ACTION_DISPLAY_SETTINGS),
            listOf("affichage", "ecran", "luminosite", "mode sombre", "theme sombre"),
        ),
        SettingsShortcut(
            "sound", "Son et vibreur",
            listOf(Settings.ACTION_SOUND_SETTINGS),
            listOf("son", "volume", "sonnerie", "vibreur", "vibration"),
        ),
        SettingsShortcut(
            "apps", "Applications",
            listOf(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, Settings.ACTION_APPLICATION_SETTINGS),
            listOf("applications", "applis", "apps"),
        ),
        SettingsShortcut(
            "storage", "Stockage",
            listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            listOf("stockage", "memoire", "espace"),
        ),
        SettingsShortcut(
            "location", "Localisation",
            listOf(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            listOf("localisation", "position", "gps"),
        ),
        SettingsShortcut(
            "security", "Sécurité",
            listOf(Settings.ACTION_SECURITY_SETTINGS),
            listOf("securite", "verrouillage", "empreinte", "code"),
        ),
        SettingsShortcut(
            "accessibility", "Accessibilité",
            listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            listOf("accessibilite"),
        ),
        SettingsShortcut("date", "Date et heure", listOf(Settings.ACTION_DATE_SETTINGS), listOf("date", "heure", "fuseau")),
        SettingsShortcut("language", "Langues", listOf(Settings.ACTION_LOCALE_SETTINGS), listOf("langue", "langues")),
        SettingsShortcut("nfc", "NFC", listOf(Settings.ACTION_NFC_SETTINGS), listOf("nfc", "sans contact", "paiement")),
        SettingsShortcut(
            "about", "À propos du téléphone",
            listOf(Settings.ACTION_DEVICE_INFO_SETTINGS),
            listOf("a propos", "telephone", "version", "numero de build"),
        ),
        SettingsShortcut(
            "developer", "Options pour les développeurs",
            listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            listOf("developpeur", "options pour les developpeurs", "debogage"),
        ),
        SettingsShortcut("all", "Tous les paramètres", listOf(Settings.ACTION_SETTINGS), listOf("parametres", "reglages")),
    )

    /**
     * Raccourcis dont un mot commence par la saisie (au moins 3 lettres, pour ne pas encombrer
     * les résultats pendant qu'on tape le début d'un nom d'app).
     */
    fun match(query: String, max: Int = 3): List<SettingsShortcut> {
        val q = AppEntry.normalize(query)
        if (q.length < 3) return emptyList()
        return all.filter { shortcut ->
            (shortcut.keywords + AppEntry.normalize(shortcut.label)).any { key ->
                key.startsWith(q) || key.split(' ', '-').any { it.startsWith(q) }
            }
        }.take(max)
    }
}
