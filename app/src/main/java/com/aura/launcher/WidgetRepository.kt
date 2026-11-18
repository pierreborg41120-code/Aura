package com.aura.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Un widget proposé par une app (ex. « Tesla — commandes »). */
data class WidgetProvider(
    val info: AppWidgetProviderInfo,
    val label: String,
) {
    /** Clé unique (le même widget peut exister dans le profil pro). */
    val key: String get() = "${info.provider.flattenToShortString()}#${info.profile.hashCode()}"

    /** Taille indicative en cases (« 4 × 2 »), connue seulement à partir d'Android 12. */
    val cells: String?
        get() = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            "${info.targetCellWidth} × ${info.targetCellHeight}"
        } else {
            null
        }
}

/**
 * Hôte de widgets d'Aura : un seul AppWidgetHost (id constant) pour toutes les cartes d'app.
 * Aucune permission n'est nécessaire pour héberger des widgets ; l'autorisation de les « lier »
 * passe par la fenêtre système ACTION_APPWIDGET_BIND (voir MainActivity).
 */
class WidgetRepository(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context.applicationContext, HOST_ID)

    fun startListening() {
        try {
            host.startListening()
        } catch (e: Exception) {
            // Certains systèmes lèvent une exception si un fournisseur est en cours de mise à jour.
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (e: Exception) {
        }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
        }
    }

    fun info(id: Int): AppWidgetProviderInfo? = try {
        manager.getAppWidgetInfo(id)
    } catch (e: Exception) {
        null
    }

    /** Widgets d'écran d'accueil proposés par le paquet de l'app. */
    fun providersFor(app: AppEntry): List<WidgetProvider> = try {
        manager.getInstalledProvidersForPackage(app.packageName, app.user).orEmpty()
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .map { WidgetProvider(it, it.loadLabel(context.packageManager)?.trim().orEmpty().ifEmpty { app.label }) }
            .sortedBy { it.label.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    /** Aperçu du widget (ou, à défaut, son icône), réduit à [maxPx] de côté. À appeler hors du thread principal. */
    fun loadPreview(provider: WidgetProvider, maxPx: Int): ImageBitmap? = try {
        val density = context.resources.displayMetrics.densityDpi
        val drawable = provider.info.loadPreviewImage(context, density)
            ?: provider.info.loadIcon(context, density)
        drawable?.let {
            val w = it.intrinsicWidth.takeIf { v -> v > 0 } ?: maxPx
            val h = it.intrinsicHeight.takeIf { v -> v > 0 } ?: maxPx
            val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
            it.toBitmap(
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            ).asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }

    /** Lie l'id au widget sans fenêtre si Android le permet déjà. */
    fun bindIfAllowed(id: Int, provider: WidgetProvider): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, provider.info.profile, provider.info.provider, bindOptions())
    } catch (e: Exception) {
        false
    }

    /** Vue du widget, à placer dans la carte. */
    fun createView(viewContext: Context, id: Int): View? {
        val info = info(id) ?: return null
        return try {
            host.createView(viewContext, id, info).apply {
                // Android ajoute une marge intérieure par défaut aux anciens widgets (~8 dp de chaque
                // côté) : dans la carte, elle rognait leur contenu. C'est la carte qui encadre le widget.
                setPadding(0, 0, 0, 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Garde les ids encore valides, supprime les autres (app désinstallée, widget retiré par une
     * mise à jour) ainsi que les ids orphelins restés dans l'hôte après une interruption.
     */
    fun cleanUp(saved: Map<String, Int>): Map<String, Int> {
        val valid = saved.filterValues { info(it) != null }
        (saved.values - valid.values.toSet()).forEach(::deleteId)
        try {
            host.appWidgetIds.filter { it !in valid.values }.forEach(::deleteId)
        } catch (e: Exception) {
        }
        return valid
    }

    companion object {
        /** Identifiant constant de l'hôte de widgets d'Aura (« AW »). */
        const val HOST_ID = 0x4157

        fun bindOptions(): Bundle = Bundle().apply {
            putInt(
                AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            )
        }

        /** Hauteur d'une case d'écran d'accueil sur un launcher moderne (dp). */
        private const val CELL_HEIGHT_DP = 110

        /** Bornes de secours quand le widget ne déclare pas ses limites de redimensionnement (dp). */
        const val FALLBACK_MIN_DP = 80
        const val FALLBACK_MAX_DP = 480

        /**
         * Hauteurs permises (dp) pour le réglage par widget : de minResizeHeight à maxResizeHeight
         * (Android 12+) quand le widget les déclare, sinon 80–480 dp.
         * Attention : AppWidgetProviderInfo donne ces valeurs en pixels, d'où la division par [density].
         */
        fun heightRangeDp(info: AppWidgetProviderInfo?, density: Float): IntRange {
            if (info == null) return FALLBACK_MIN_DP..FALLBACK_MAX_DP
            val minPx = info.minResizeHeight.takeIf { it > 0 }
            val low = minPx?.let { (it / density).roundToInt() }?.coerceIn(40, FALLBACK_MAX_DP) ?: FALLBACK_MIN_DP
            val maxPx = if (Build.VERSION.SDK_INT >= 31) info.maxResizeHeight else 0
            val high = if (maxPx > 0) (maxPx / density).roundToInt().coerceIn(low, 720) else FALLBACK_MAX_DP
            return low..maxOf(low, high)
        }

        /**
         * Hauteur par défaut (dp) du widget sur toute la largeur de la carte : le nombre de cases visé
         * (Android 12+, sinon déduit de minHeight avec la formule d'Android « 70 × n − 30 ») multiplié
         * par une case de [CELL_HEIGHT_DP] dp, jamais moins que minHeight, et dans [heightRangeDp].
         */
        fun preferredHeightDp(info: AppWidgetProviderInfo?, density: Float): Int {
            if (info == null) return 2 * CELL_HEIGHT_DP
            val minDp = info.minHeight / density
            val cells = if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) {
                info.targetCellHeight
            } else {
                ceil((minDp + 30f) / 70f).toInt().coerceAtLeast(1)
            }
            val wanted = maxOf(minDp.roundToInt(), cells * CELL_HEIGHT_DP)
            val range = heightRangeDp(info, density)
            return wanted.coerceIn(range.first, range.last)
        }

        /**
         * Indique au widget la taille réelle dont il dispose, **en dp** (c'est ce qu'attendent
         * updateAppWidgetSize et les options OPTION_APPWIDGET_MIN/MAX_WIDTH/HEIGHT), pour qu'il
         * choisisse sa mise en page. Min = max : la carte a une taille exacte.
         */
        @Suppress("DEPRECATION")
        fun applySize(view: View, widthDp: Int, heightDp: Int) {
            val hostView = view as? AppWidgetHostView ?: return
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    // Remplit aussi OPTION_APPWIDGET_SIZES et les MIN/MAX d'après cette taille.
                    hostView.updateAppWidgetSize(
                        bindOptions(),
                        listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
                    )
                } else {
                    hostView.updateAppWidgetSize(bindOptions(), widthDp, heightDp, widthDp, heightDp)
                }
            } catch (e: Exception) {
            }
        }
    }
}
