package com.aura.launcher

import org.json.JSONArray
import org.json.JSONObject

/*
 * --- Dossiers --- un dossier regroupe plusieurs apps sous un nom, sur l'accueil.
 * Il prend place dans la liste des favoris sous la clé « folder:<id> », ce qui permet de le
 * réordonner avec les apps. Une app rangée dans un dossier n'est plus un favori à part.
 */

data class Folder(val id: String, val name: String, val apps: List<String>) {
    /** Clé du dossier dans la liste des favoris. */
    val favoriteKey: String get() = FOLDER_PREFIX + id
}

const val FOLDER_PREFIX = "folder:"

/** Un élément de l'accueil : une app épinglée, ou un dossier et ses apps encore installées. */
sealed interface HomeItem {
    val key: String

    data class App(val app: AppEntry) : HomeItem {
        override val key: String get() = app.key
    }

    data class Group(val folder: Folder, val apps: List<AppEntry>) : HomeItem {
        override val key: String get() = folder.favoriteKey
    }
}

fun foldersToJson(folders: List<Folder>): String = JSONArray().apply {
    folders.forEach { f ->
        put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)))
    }
}.toString()

fun foldersFromJson(json: String?): List<Folder> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val apps = o.getJSONArray("apps")
            Folder(o.getString("id"), o.getString("name"), (0 until apps.length()).map { apps.getString(it) })
        }
    } catch (e: Exception) {
        emptyList()
    }
}
