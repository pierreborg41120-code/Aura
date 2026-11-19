package com.aura.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/*
 * --- Icônes --- packs d'icônes installés (format standard des packs « compatibles Nova / ADW ») :
 * le pack déclare une activité avec l'une des actions ci-dessous et un fichier appfilter.xml qui
 * associe chaque activité d'app (ComponentInfo{paquet/classe}) au nom d'une image du pack.
 */

data class IconPackInfo(val packageName: String, val label: String)

class IconPacks(private val context: Context) {

    private val pm = context.packageManager

    /** Pack déjà lu : ressources + table activité → nom d'image. */
    private class Loaded(val res: Resources, val map: Map<String, String>)

    private var loaded: Pair<String, Loaded?>? = null

    /** Packs installés, triés par nom. À appeler hors du thread principal. */
    fun installed(): List<IconPackInfo> =
        PACK_ACTIONS.flatMap { action ->
            try {
                pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                emptyList()
            }
        }
            .map { it.activityInfo.packageName to it }
            .distinctBy { it.first }
            .map { (pkg, info) -> IconPackInfo(pkg, info.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }

    /** Image du pack pour cette activité, ou null si le pack ne la prévoit pas. À appeler hors du thread principal. */
    @Synchronized
    fun iconFor(pack: String, component: ComponentName): Drawable? {
        val data = load(pack) ?: return null
        val name = data.map[component.flattenToString()] ?: return null
        return try {
            @Suppress("DiscouragedApi") // seul moyen de trouver une image d'un autre paquet par son nom
            val id = data.res.getIdentifier(name, "drawable", pack)
            if (id == 0) null
            else data.res.getDrawableForDensity(id, context.resources.displayMetrics.densityDpi, null)
        } catch (e: Exception) {
            null
        }
    }

    /** Oublie le pack lu (il a été mis à jour ou désinstallé). */
    @Synchronized
    fun forget(pack: String) {
        if (loaded?.first == pack) loaded = null
    }

    private fun load(pack: String): Loaded? {
        loaded?.let { (p, l) -> if (p == pack) return l }
        val result = try {
            val res = pm.getResourcesForApplication(pack)
            Loaded(res, parseAppFilter(res, pack))
        } catch (e: Exception) {
            null
        }
        loaded = pack to result
        return result
    }

    /** Lit appfilter.xml (dans res/xml, sinon dans assets). */
    private fun parseAppFilter(res: Resources, pack: String): Map<String, String> {
        @Suppress("DiscouragedApi")
        val xmlId = res.getIdentifier("appfilter", "xml", pack)
        val parser: XmlPullParser = if (xmlId != 0) {
            res.getXml(xmlId)
        } else {
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), "UTF-8")
            }
        }
        val map = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (comp != null && drawable != null) {
                    // « ComponentInfo{paquet/classe} » → forme normalisée « paquet/classe ».
                    val inner = comp.substringAfter("{", "").substringBefore("}", "")
                    ComponentName.unflattenFromString(inner)?.let { map.putIfAbsent(it.flattenToString(), drawable) }
                }
            }
            event = parser.next()
        }
        return map
    }

    private companion object {
        val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
        )
    }
}
