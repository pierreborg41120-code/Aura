package com.aura.launcher.ui

import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Point de passage unique des vibrations d'Aura : respecte le réglage « Retour haptique »
 * (Réglages → Accueil). [enabled] est mis à jour par MainActivity.
 */
object AuraHaptics {
    @Volatile
    var enabled: Boolean = true
}

/** Vibration Compose, seulement si le retour haptique est activé. */
fun HapticFeedback.auraHaptic(type: HapticFeedbackType) {
    if (AuraHaptics.enabled) performHapticFeedback(type)
}

/** Vibration d'une vue Android (constantes HapticFeedbackConstants), seulement si activée. */
fun View.auraHaptic(constant: Int) {
    if (AuraHaptics.enabled) performHapticFeedback(constant)
}
