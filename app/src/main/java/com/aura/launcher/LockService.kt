package com.aura.launcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Service d'accessibilité d'Aura. Il ne lit pas le contenu de l'écran.
 * 1. Verrouille l'écran quand on touche deux fois l'accueil.
 * 2. --- Limiteur --- note quelle app est au premier plan (son nom de paquet seulement) pour
 *    compter le temps passé dans les apps limitées, et revient à l'accueil quand il est écoulé.
 */
class LockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val timeUpCheck = Runnable { checkLimit() }
    /** Dernière app vue au premier plan (pour reprendre la session après l'écran éteint). */
    private var lastPkg: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Écran éteint : le temps ne compte plus.
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(timeUpCheck)
                    ScreenTime.endSession()
                }
                // Déverrouillé : on reprend sur la dernière app vue (un changement d'app corrigera sinon).
                Intent.ACTION_USER_PRESENT -> lastPkg?.let { onForeground(it) }
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        ScreenTime.init(this)
        androidx.core.content.ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        if (instance === this) instance = null
        handler.removeCallbacks(timeUpCheck)
        ScreenTime.endSession()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // déjà désinscrit
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Volet des notifications, clavier : l'app d'en dessous reste au premier plan.
        if (pkg == "com.android.systemui" || isKeyboard(pkg)) return
        lastPkg = pkg
        onForeground(pkg)
    }

    private fun onForeground(pkg: String) {
        handler.removeCallbacks(timeUpCheck)
        val limit = ScreenTime.limits(this)[pkg]
        if (limit == null || limit <= 0) {
            ScreenTime.endSession()
            return
        }
        ScreenTime.startSession(pkg)
        checkLimit()
    }

    /** Temps écoulé : retour à l'accueil (Aura), qui affiche « Temps écoulé ». Sinon, on revérifie à l'échéance. */
    private fun checkLimit() {
        val pkg = ScreenTime.currentSession ?: return
        val limit = ScreenTime.limits(this)[pkg] ?: return
        val left = ScreenTime.remaining(pkg, limit)
        if (left <= 0) {
            ScreenTime.endSession()
            ScreenTime.signalTimeUp(pkg)
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            handler.postDelayed(timeUpCheck, left + 500)
        }
    }

    private fun isKeyboard(pkg: String): Boolean = try {
        getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == pkg }
    } catch (e: Exception) {
        false
    }

    override fun onInterrupt() = Unit

    companion object {
        private var instance: LockService? = null

        /** Vrai si le service est activé (verrouillage et limiteur en durée possibles). */
        val isRunning: Boolean get() = instance != null

        /** Verrouille l'écran. Renvoie false si le service n'est pas activé. */
        fun lockScreen(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}
