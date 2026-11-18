package com.aura.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Accès à l'API publique Open-Meteo (gratuite, sans compte ni clé).
 * Seules des coordonnées arrondies à 2 décimales (~1 km) sont envoyées.
 */
object WeatherClient {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"

    /** Arrondi à 2 décimales : assez pour la météo, trop flou pour situer une adresse. */
    fun round2(value: Double): Double = (value * 100).roundToLong() / 100.0

    /** Réponse brute des prévisions, ou null (hors ligne, serveur en panne…). */
    suspend fun fetchForecast(lat: Double, lon: Double): String? {
        val url = String.format(
            Locale.US,
            "%s?latitude=%.2f&longitude=%.2f" +
                "&current=temperature_2m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=7",
            FORECAST, round2(lat), round2(lon),
        )
        return get(url)
    }

    /**
     * Villes correspondant à [name]. Liste vide si aucune ; null si la recherche a échoué
     * (pas de connexion…).
     */
    suspend fun searchCity(name: String): List<WeatherPlace>? {
        val query = URLEncoder.encode(name.trim(), "UTF-8")
        val raw = get("$GEOCODING?name=$query&count=5&language=fr&format=json") ?: return null
        return try {
            val results = JSONObject(raw).optJSONArray("results") ?: return emptyList()
            (0 until results.length()).mapNotNull { i ->
                val o = results.optJSONObject(i) ?: return@mapNotNull null
                if (!o.has("latitude") || !o.has("longitude")) return@mapNotNull null
                val detail = listOf(o.optString("admin1"), o.optString("country"))
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                WeatherPlace(
                    name = o.optString("name"),
                    detail = detail,
                    lat = round2(o.getDouble("latitude")),
                    lon = round2(o.getDouble("longitude")),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
