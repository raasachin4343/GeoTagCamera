package com.example.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

object MapTileHelper {
    /**
     * Calculates the tile coordinates for OpenStreetMap for a given latitude, longitude, and zoom level.
     * Returns an OSM tile URL: https://tile.openstreetmap.org/{zoom}/{x}/{y}.png
     */
    fun getTileUrl(latitude: Double, longitude: Double, zoom: Int = 16): String {
        val lat = latitude.coerceIn(-85.0511, 85.0511)
        val lon = longitude.coerceIn(-180.0, 180.0)

        val n = 1 shl zoom
        val x = ((lon + 180.0) / 360.0 * n).toInt()
        
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()

        return "https://tile.openstreetmap.org/$zoom/$x/$y.png"
    }
}
