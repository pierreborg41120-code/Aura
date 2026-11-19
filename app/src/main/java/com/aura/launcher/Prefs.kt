package com.aura.launcher

import android.content.Context

/** Préférences persistantes. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("aura", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString(KEY_FAVORITES, "").orEmpty().split(SEP).filter { it.isNotBlank() }
        set(value) = sp.edit().putString(KEY_FAVORITES, value.joinToString(SEP)).apply()

    var hidden: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
        set(value) = sp.edit().putStringSet(KEY_HIDDEN, value).apply()

    var showIcons: Boolean
        get() = sp.getBoolean(KEY_ICONS, true)
        set(value) = sp.edit().putBoolean(KEY_ICONS, value).apply()

    var autoLaunch: Boolean
        get() = sp.getBoolean(KEY_AUTO_LAUNCH, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    var showNotificationPreviews: Boolean
        get() = sp.getBoolean(KEY_NOTIF_PREVIEWS, true)
        set(value) = sp.edit().putBoolean(KEY_NOTIF_PREVIEWS, value).apply()

    var textScale: Float
        get() = sp.getFloat(KEY_TEXT_SCALE, 1f)
        set(value) = sp.edit().putFloat(KEY_TEXT_SCALE, value).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /** Noms personnalisés : clé de l'app → nom. */
    var customLabels: Map<String, String>
        get() = sp.all
            .filterKeys { it.startsWith(LABEL_PREFIX) }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(LABEL_PREFIX) to it } }
            .toMap()
        set(value) {
            val editor = sp.edit()
            sp.all.keys.filter { it.startsWith(LABEL_PREFIX) }.forEach(editor::remove)
            value.forEach { (k, v) -> editor.putString(LABEL_PREFIX + k, v) }
            editor.apply()
        }

    // --- Sous l'heure : batterie + agenda ---
    var showBattery: Boolean
        get() = sp.getBoolean("status_battery", true)
        set(value) = sp.edit().putBoolean("status_battery", value).apply()

    var showNextEvent: Boolean
        get() = sp.getBoolean("status_next_event", false)
        set(value) = sp.edit().putBoolean("status_next_event", value).apply()

    // --- Widget par app ---
    /** Widget attaché à chaque app : clé de l'app → appWidgetId. */
    var widgetIds: Map<String, Int>
        get() = sp.all
            .filterKeys { it.startsWith(WIDGET_PREFIX) }
            .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(WIDGET_PREFIX) to it } }
            .toMap()
        set(value) {
            val editor = sp.edit()
            sp.all.keys.filter { it.startsWith(WIDGET_PREFIX) }.forEach(editor::remove)
            value.forEach { (k, v) -> editor.putInt(WIDGET_PREFIX + k, v) }
            editor.apply()
        }

    /**
     * Hauteur choisie (en dp) pour le widget de chaque app : clé de l'app → hauteur.
     * Absente = hauteur calculée d'après le widget.
     */
    var widgetHeights: Map<String, Int>
        get() = sp.all
            .filterKeys { it.startsWith(WIDGET_HEIGHT_PREFIX) }
            .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(WIDGET_HEIGHT_PREFIX) to it } }
            .toMap()
        set(value) {
            val editor = sp.edit()
            sp.all.keys.filter { it.startsWith(WIDGET_HEIGHT_PREFIX) }.forEach(editor::remove)
            value.forEach { (k, v) -> editor.putInt(WIDGET_HEIGHT_PREFIX + k, v) }
            editor.apply()
        }

    // --- Réponses aux notifications ---
    var replyFromHome: Boolean
        get() = sp.getBoolean("reply_from_home", true)
        set(value) = sp.edit().putBoolean("reply_from_home", value).apply()

    // --- Météo ---
    /** Désactivée par défaut : rien n'est envoyé sur Internet tant que l'utilisateur ne l'a pas activée. */
    var weatherEnabled: Boolean
        get() = sp.getBoolean("weather_enabled", false)
        set(value) = sp.edit().putBoolean("weather_enabled", value).apply()

    /** Vrai = ville saisie à la main ; faux = position du téléphone. */
    var weatherUseCity: Boolean
        get() = sp.getBoolean("weather_use_city", false)
        set(value) = sp.edit().putBoolean("weather_use_city", value).apply()

    var weatherFahrenheit: Boolean
        get() = sp.getBoolean("weather_fahrenheit", false)
        set(value) = sp.edit().putBoolean("weather_fahrenheit", value).apply()

    /** Ville choisie (coordonnées déjà arrondies à 2 décimales). */
    var weatherCity: WeatherPlace?
        get() {
            val name = sp.getString("weather_city_name", null) ?: return null
            val lat = sp.getString("weather_city_lat", null)?.toDoubleOrNull() ?: return null
            val lon = sp.getString("weather_city_lon", null)?.toDoubleOrNull() ?: return null
            return WeatherPlace(name, sp.getString("weather_city_detail", "").orEmpty(), lat, lon)
        }
        set(value) {
            val editor = sp.edit()
            if (value == null) {
                listOf("weather_city_name", "weather_city_detail", "weather_city_lat", "weather_city_lon")
                    .forEach { editor.remove(it) }
            } else {
                editor.putString("weather_city_name", value.name)
                    .putString("weather_city_detail", value.detail)
                    .putString("weather_city_lat", value.lat.toString())
                    .putString("weather_city_lon", value.lon.toString())
            }
            editor.apply()
        }

    /** Dernière réponse brute d'Open-Meteo et son heure de réception (cache hors ligne). */
    var weatherCache: String?
        get() = sp.getString("weather_cache", null)
        set(value) = sp.edit().putString("weather_cache", value).apply()

    var weatherFetchedAt: Long
        get() = sp.getLong("weather_fetched_at", 0L)
        set(value) = sp.edit().putLong("weather_fetched_at", value).apply()

    // --- Tiroir et recherche ---
    var searchContacts: Boolean
        get() = sp.getBoolean("drawer_search_contacts", false)
        set(value) = sp.edit().putBoolean("drawer_search_contacts", value).apply()

    var sortByUsage: Boolean
        get() = sp.getBoolean("drawer_sort_by_usage", true)
        set(value) = sp.edit().putBoolean("drawer_sort_by_usage", value).apply()

    var showSuggestions: Boolean
        get() = sp.getBoolean("drawer_suggestions", true)
        set(value) = sp.edit().putBoolean("drawer_suggestions", value).apply()

    // --- Accueil ---
    /** Widget libre de l'accueil (null = aucun). */
    var homeWidgetId: Int?
        get() = sp.getInt("home_widget_id", -1).takeIf { it >= 0 }
        set(value) = sp.edit().apply { if (value == null) remove("home_widget_id") else putInt("home_widget_id", value) }.apply()

    /** Position du widget d'accueil : [HOME_WIDGET_UNDER_CLOCK] ou [HOME_WIDGET_ABOVE_FAVORITES]. */
    var homeWidgetPosition: String
        get() = sp.getString("home_widget_position", HOME_WIDGET_ABOVE_FAVORITES) ?: HOME_WIDGET_ABOVE_FAVORITES
        set(value) = sp.edit().putString("home_widget_position", value).apply()

    /** Hauteur choisie (dp) pour le widget d'accueil ; null = hauteur calculée. */
    var homeWidgetHeight: Int?
        get() = sp.getInt("home_widget_height", -1).takeIf { it > 0 }
        set(value) = sp.edit().apply { if (value == null) remove("home_widget_height") else putInt("home_widget_height", value) }.apply()

    var showHeadphoneApps: Boolean
        get() = sp.getBoolean("headphone_apps", true)
        set(value) = sp.edit().putBoolean("headphone_apps", value).apply()

    /** Paquets des dernières apps qui ont joué de la musique (le plus récent d'abord). */
    var recentMusicPackages: List<String>
        get() = sp.getString("recent_music", "").orEmpty().split(SEP).filter { it.isNotBlank() }
        set(value) = sp.edit().putString("recent_music", value.joinToString(SEP)).apply()

    var allowRotation: Boolean
        get() = sp.getBoolean("allow_rotation", false)
        set(value) = sp.edit().putBoolean("allow_rotation", value).apply()

    var hapticsEnabled: Boolean
        get() = sp.getBoolean("haptics", true)
        set(value) = sp.edit().putBoolean("haptics", value).apply()

    // --- Récap des notifications ---
    var notifRecap: Boolean
        get() = sp.getBoolean("notif_recap", true)
        set(value) = sp.edit().putBoolean("notif_recap", value).apply()

    // --- Limiteur ---
    /** Apps pour lesquelles une pause s'affiche avant l'ouverture. */
    var limitedApps: Set<String>
        get() = sp.getStringSet("limited_apps", emptySet()).orEmpty().toSet()
        set(value) = sp.edit().putStringSet("limited_apps", value).apply()

    // --- Gestes ---
    var gestureActions: Map<Gesture, String>
        get() = Gesture.entries.associateWith { sp.getString("gesture_" + it.name, null) ?: GestureAction.default(it) }
        set(value) {
            val editor = sp.edit()
            value.forEach { (g, a) -> editor.putString("gesture_" + g.name, a) }
            editor.apply()
        }

    // --- Dossiers ---
    var folders: List<Folder>
        get() = foldersFromJson(sp.getString("folders", null))
        set(value) = sp.edit().putString("folders", foldersToJson(value)).apply()

    // --- Sauvegarde ---
    /**
     * Tous les réglages sous forme de JSON, sauf ce qui est propre à ce téléphone (widgets, liés à
     * l'hôte de widgets local), le cache météo et l'assistant (à refaire sur un nouveau téléphone).
     */
    fun exportJson(): String {
        val values = org.json.JSONObject()
        sp.all.filterKeys(::isExportable).forEach { (k, v) ->
            val entry = when (v) {
                is String -> org.json.JSONObject().put("t", "s").put("v", v)
                is Boolean -> org.json.JSONObject().put("t", "b").put("v", v)
                is Int -> org.json.JSONObject().put("t", "i").put("v", v)
                is Long -> org.json.JSONObject().put("t", "l").put("v", v)
                is Float -> org.json.JSONObject().put("t", "f").put("v", v.toDouble())
                is Set<*> -> org.json.JSONObject().put("t", "set").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> null
            }
            entry?.let { values.put(k, it) }
        }
        return org.json.JSONObject()
            .put("app", BACKUP_APP)
            .put("version", BACKUP_VERSION)
            .put("prefs", values)
            .toString(2)
    }

    /** Remplace les réglages par ceux de la sauvegarde. Faux si le fichier n'est pas une sauvegarde d'Aura. */
    fun importJson(json: String): Boolean {
        val root = try {
            org.json.JSONObject(json)
        } catch (e: Exception) {
            return false
        }
        if (root.optString("app") != BACKUP_APP) return false
        val values = root.optJSONObject("prefs") ?: return false
        val editor = sp.edit()
        sp.all.keys.filter(::isExportable).forEach(editor::remove)
        values.keys().forEach { k ->
            if (!isExportable(k)) return@forEach
            val e = values.optJSONObject(k) ?: return@forEach
            when (e.optString("t")) {
                "s" -> editor.putString(k, e.optString("v"))
                "b" -> editor.putBoolean(k, e.optBoolean("v"))
                "i" -> editor.putInt(k, e.optInt("v"))
                "l" -> editor.putLong(k, e.optLong("v"))
                "f" -> editor.putFloat(k, e.optDouble("v").toFloat())
                "set" -> {
                    val a = e.optJSONArray("v") ?: org.json.JSONArray()
                    editor.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        // Écriture immédiate : l'activité redémarre juste après.
        return editor.commit()
    }

    private fun isExportable(key: String): Boolean =
        !key.startsWith(WIDGET_PREFIX) && !key.startsWith(WIDGET_HEIGHT_PREFIX) &&
            !key.startsWith("home_widget_") && key != "weather_cache" && key != "weather_fetched_at" &&
            key != KEY_ONBOARDING_DONE

    // --- Apparence ---
    var appearance: Appearance
        get() {
            val d = Appearance()
            return Appearance(
                theme = enumOr(sp.getString("look_theme", null), d.theme),
                clock = enumOr(sp.getString("look_clock", null), d.clock),
                font = enumOr(sp.getString("look_font", null), d.font),
                accent = sp.getInt("look_accent", d.accent).takeIf { it == ACCENT_AUTO || it in 0 until ACCENT_PALETTE_SIZE } ?: d.accent,
                veil = enumOr(sp.getString("look_veil", null), d.veil),
                blurDrawer = sp.getBoolean("look_blur_drawer", d.blurDrawer),
                alignment = enumOr(sp.getString("look_alignment", null), d.alignment),
                iconStyle = enumOr(sp.getString("look_icon_style", null), d.iconStyle),
                iconPack = sp.getString("look_icon_pack", d.iconPack) ?: d.iconPack,
            )
        }
        set(value) = sp.edit()
            .putString("look_theme", value.theme.name)
            .putString("look_clock", value.clock.name)
            .putString("look_font", value.font.name)
            .putInt("look_accent", value.accent)
            .putString("look_veil", value.veil.name)
            .putBoolean("look_blur_drawer", value.blurDrawer)
            .putString("look_alignment", value.alignment.name)
            .putString("look_icon_style", value.iconStyle.name)
            .putString("look_icon_pack", value.iconPack)
            .apply()

    /** Valeur enregistrée si elle existe encore dans l'enum, sinon [default]. */
    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val WIDGET_PREFIX = "widget:"
        // « widget_h: » ne commence pas par « widget: » : les deux familles de clés ne se mélangent pas.
        const val WIDGET_HEIGHT_PREFIX = "widget_h:"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_ICONS = "show_icons"
        const val KEY_AUTO_LAUNCH = "auto_launch"
        const val KEY_NOTIF_PREVIEWS = "notif_previews"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val LABEL_PREFIX = "label:"
        const val SEP = "\n"
        const val BACKUP_APP = "aura"
        const val BACKUP_VERSION = 1
    }
}

// --- Accueil ---
const val HOME_WIDGET_UNDER_CLOCK = "clock"
const val HOME_WIDGET_ABOVE_FAVORITES = "favorites"
