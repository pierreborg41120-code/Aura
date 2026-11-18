package com.aura.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** État de la batterie affiché sous l'heure. */
data class BatteryInfo(val percent: Int, val charging: Boolean, val full: Boolean)

/** Prochain rendez-vous de l'agenda (évènement à heure fixe, jamais « toute la journée »). */
data class NextEvent(val eventId: Long, val title: String, val begin: Long, val end: Long)

/**
 * Écoute la batterie et le changement de minute tant que l'accueil est visible.
 * Aucune permission n'est nécessaire : ACTION_BATTERY_CHANGED est un broadcast « collant »,
 * ACTION_TIME_TICK est envoyé chaque minute par le système.
 */
class DeviceInfoWatcher(
    private val context: Context,
    private val onBattery: (BatteryInfo) -> Unit,
    private val onTick: () -> Unit,
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> parseBattery(intent)?.let(onBattery)
                else -> onTick()
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        // Broadcasts système : reçus même avec RECEIVER_NOT_EXPORTED. Le retour contient
        // la dernière valeur collante de la batterie, ce qui donne un affichage immédiat.
        val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        sticky?.let { parseBattery(it) }?.let(onBattery)
    }

    fun stop() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Déjà désinscrit : rien à faire.
        }
        registered = false
    }

    private fun parseBattery(intent: Intent): BatteryInfo? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || (plugged && !full)
        return BatteryInfo(percent = (level * 100f / scale).toInt().coerceIn(0, 100), charging = charging, full = full && plugged)
    }
}

/** Lecture de l'agenda via CalendarContract.Instances (permission READ_CALENDAR). */
object CalendarReader {
    /** Fenêtre de recherche : les prochaines 24 heures. */
    private const val WINDOW_MS = 24L * 60 * 60 * 1000

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Premier évènement à heure fixe qui commence dans les 24 h à venir.
     * Les évènements « toute la journée » sont ignorés : les agendas d'anniversaires ou de jours
     * fériés occuperaient la ligne en permanence. Les évènements annulés ou refusés aussi.
     */
    suspend fun nextEvent(context: Context, now: Long = System.currentTimeMillis()): NextEvent? =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext null
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, now)
                ContentUris.appendId(it, now + WINDOW_MS)
            }.build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
            )
            val selection = "${CalendarContract.Instances.ALL_DAY} = 0" +
                " AND ${CalendarContract.Instances.BEGIN} >= ?" +
                " AND ${CalendarContract.Instances.VISIBLE} = 1" +
                " AND (${CalendarContract.Instances.STATUS} IS NULL" +
                " OR ${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})" +
                " AND (${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL" +
                " OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != " +
                "${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})"
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    arrayOf(now.toString()),
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    NextEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.trim().orEmpty().ifEmpty { "Rendez-vous" },
                        begin = c.getLong(2),
                        end = c.getLong(3),
                    )
                }
            } catch (e: Exception) {
                // Permission retirée entre-temps ou fournisseur d'agenda absent.
                null
            }
        }

    /** Intent qui ouvre l'évènement dans l'app d'agenda. */
    fun viewIntent(event: NextEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)

    /** Prévient quand l'agenda change (ajout, modification, synchro). Renvoie de quoi arrêter l'écoute. */
    fun observe(context: Context, listener: () -> Unit): (() -> Unit)? {
        if (!hasPermission(context)) return null
        // Sur les Android récents, toutes les variantes de onChange finissent par appeler celle-ci.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                listener()
            }
        }
        return try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            val stop: () -> Unit = { context.contentResolver.unregisterContentObserver(observer) }
            stop
        } catch (e: Exception) {
            null
        }
    }
}
