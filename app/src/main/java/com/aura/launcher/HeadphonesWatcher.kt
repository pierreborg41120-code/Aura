package com.aura.launcher

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

/**
 * Surveille le branchement d'un casque ou d'écouteurs (filaires, USB ou Bluetooth).
 * [onChange] reçoit vrai dès qu'au moins une sortie audio « écouteurs » est disponible.
 * Aucune permission n'est nécessaire pour lire la liste des sorties audio.
 */
class HeadphonesWatcher(context: Context, private val onChange: (Boolean) -> Unit) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var registered = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = notifyState()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = notifyState()
    }

    fun start() {
        if (registered || audio == null) return
        try {
            // Le rappel est appelé tout de suite avec les sorties déjà branchées.
            audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        } catch (e: Exception) {
        }
        notifyState()
    }

    fun stop() {
        if (!registered) return
        try {
            audio?.unregisterAudioDeviceCallback(callback)
        } catch (e: Exception) {
        }
        registered = false
    }

    private fun notifyState() {
        val connected = try {
            audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any { it.type in HEADPHONE_TYPES }
        } catch (e: Exception) {
            false
        }
        onChange(connected)
    }

    companion object {
        /** TYPE_BLE_HEADSET (26) n'existe qu'à partir d'Android 12 : sa valeur n'apparaît pas avant. */
        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            26, // AudioDeviceInfo.TYPE_BLE_HEADSET
        )

        /**
         * Paquets des apps de musique, dans l'ordre de préférence : d'abord celles qui ont joué de
         * la musique récemment ([recent]), puis celles qui se déclarent lecteur de musique.
         * À appeler hors du thread principal.
         */
        @Suppress("DEPRECATION")
        fun musicPackages(context: Context, recent: List<String>): List<String> {
            val found = LinkedHashSet(recent)
            val pm = context.packageManager
            val intents = listOf(
                Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
            )
            intents.forEach { intent ->
                try {
                    pm.queryIntentActivities(intent, 0).forEach { found += it.activityInfo.packageName }
                } catch (e: Exception) {
                }
            }
            return found.toList()
        }
    }
}
