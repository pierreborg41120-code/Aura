package com.aura.launcher.ui

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.min
import kotlin.math.pow

/** Durée de la transition « liquide » entre deux pochettes. */
const val LiquidTransitionMs = 720

/**
 * La transition liquide a besoin des shaders AGSL (Android 13+). En dessous, ou si le shader ne
 * compile pas sur ce téléphone (testé une seule fois, sans jamais planter) : révélation en disque.
 */
val liquidTransitionSupported: Boolean by lazy {
    Build.VERSION.SDK_INT >= 33 && try {
        RuntimeShader(LIQUID_AGSL)
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Courbe de la transition : départ lent (l'ancienne pochette commence à fondre), front rapide,
 * puis longue fin douce où les dernières traînées de l'ancienne image s'effacent.
 */
fun liquidEase(t: Float): Float {
    val s = t.coerceIn(0f, 1f).pow(1.5f)
    return 1f - (1f - s).pow(2.6f)
}

/**
 * Shader de la transition. Principe :
 * - un champ de bruit (fbm) mélangé à un balayage depuis le côté du saut décide quand chaque pixel
 *   bascule vers la nouvelle image ; le bruit crée des gouttes en avance du front et des îlots
 *   de l'ancienne image qui traînent derrière ;
 * - un champ d'écoulement tourbillonnant (domain warping) déforme l'ancienne image près du front,
 *   comme une encre qui coule, et floute la nouvelle dans le sens du flux avant qu'elle se fige.
 * La même math a été prototypée hors ligne et comparée image par image à la référence.
 */
private const val LIQUID_AGSL = """
uniform shader oldImg;
uniform shader newImg;
uniform float2 size;
uniform float4 oldRect;
uniform float4 newRect;
uniform float progress;
uniform float dir;

const float SCALE = 1.8;
const float SWEEP = 0.7;
const float NOISEW = 0.85;
const float W = 0.035;
const float AMP = 0.22;
const float BANDW = 0.22;

float hash(float2 q) {
    float3 p3 = fract(float3(q.x, q.y, q.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(float2 x) {
    float2 i = floor(x);
    float2 f = x - i;
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return a + (b - a) * u.x + (c - a) * u.y + (a - b - c + d) * u.x * u.y;
}

float fbm(float2 x) {
    float v = 0.0;
    float amp = 0.5;
    for (int k = 0; k < 4; k++) {
        v += amp * vnoise(x);
        x = x * 2.03 + float2(1.7, 9.2);
        amp *= 0.5;
    }
    return v;
}

half4 sampleOld(float2 uv) {
    uv = clamp(uv, 0.0, 1.0);
    return oldImg.eval(oldRect.xy + uv * oldRect.zw);
}

half4 sampleNew(float2 uv) {
    uv = clamp(uv, 0.0, 1.0);
    return newImg.eval(newRect.xy + uv * newRect.zw);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / size;
    float p = progress;

    // Balayage : depuis la droite (suivant), la gauche (précédent) ou le centre (changement externe).
    float sweep;
    if (dir > 0.5) {
        sweep = 1.0 - uv.x;
    } else if (dir < -0.5) {
        sweep = uv.x;
    } else {
        sweep = length(uv - 0.5) * 1.4142;
    }
    float n = fbm(uv * SCALE + float2(3.1, p * 0.8));
    float field = sweep * SWEEP + n * NOISEW;
    float span = SWEEP + NOISEW;
    float front = p * (span + 2.0 * W) - W;
    float reveal = 1.0 - smoothstep(front - W, front + W, field);

    // Champ d'écoulement tourbillonnant.
    float t = p * 1.3;
    float qx = fbm(uv * 3.0 + float2(t, 0.0)) - 0.5;
    float qy = fbm(uv * 3.0 + float2(5.2, 1.3 - t)) - 0.5;
    float2 q4 = float2(qx, qy) * 4.0;
    float fx = qx * 2.0 + fbm(uv * 3.0 + q4) - 0.5 - dir * 0.35;
    float fy = qy * 2.0 + fbm(uv * 3.0 + q4 + float2(7.7, 2.1)) - 0.5;
    float2 flow = float2(fx, fy);

    float e = (field - front) / BANDW;
    float band = exp(-e * e);
    float so = AMP * (band + 0.25 * p);
    float sn = AMP * (band + 0.25 * (1.0 - p));

    half4 oldC = sampleOld(uv + flow * so);
    // Quelques échantillons le long du flux : flou de mouvement sur la nouvelle image.
    half4 newC = (sampleNew(uv - flow * sn * 0.125) + sampleNew(uv - flow * sn * 0.375)
        + sampleNew(uv - flow * sn * 0.625) + sampleNew(uv - flow * sn * 0.875)) * 0.25;
    return mix(oldC, newC, half(reveal));
}
"""

/** Zone carrée centrale de l'image (équivalent de ContentScale.Crop pour un cadre carré). */
private fun cropRect(image: ImageBitmap): FloatArray {
    val w = image.width.toFloat()
    val h = image.height.toFloat()
    val s = min(w, h)
    return floatArrayOf((w - s) / 2f, (h - s) / 2f, s, s)
}

/**
 * Dessine la transition liquide de [from] vers [to]. [progress] est lu au moment du dessin :
 * l'animation redessine sans recomposer. [direction] : 1 = suivant, -1 = précédent, 0 = externe.
 */
@RequiresApi(33)
@Composable
fun LiquidArtTransition(
    from: ImageBitmap,
    to: ImageBitmap,
    direction: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shader = remember(from, to) {
        RuntimeShader(LIQUID_AGSL).apply {
            setInputShader("oldImg", BitmapShader(from.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            setInputShader("newImg", BitmapShader(to.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            val o = cropRect(from)
            val n = cropRect(to)
            setFloatUniform("oldRect", o[0], o[1], o[2], o[3])
            setFloatUniform("newRect", n[0], n[1], n[2], n[3])
        }
    }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("progress", liquidEase(progress()))
        shader.setFloatUniform("dir", direction.toFloat())
        // Nouveau pinceau à chaque image : les uniformes mis à jour sont bien pris en compte.
        drawRect(ShaderBrush(shader))
    }
}
