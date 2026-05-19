package com.example.kingsofthejungle

import android.location.Location
import kotlin.math.cos
import kotlin.math.sin

object MapMathHelper {
    /**
     * Converts a target location into 0.0 to 1.0 coordinates relative to a center location.
     * The center location is mapped to (0.5, 0.5).
     * @param maxRadiusMeters The real-world distance that represents the edge of the canvas.
     */
    fun convertToCanvasCoordinates(
        center: Location,
        target: Location,
        maxRadiusMeters: Float = 100f
    ): Pair<Float, Float> {
        val distance = center.distanceTo(target)
        val bearingDegrees = center.bearingTo(target)
        val bearingRadians = Math.toRadians(bearingDegrees.toDouble())

        // Calculate offset in terms of "canvas units" (where 0.5 is the radius from center to edge)
        val normalizedDistance = (distance / maxRadiusMeters) * 0.5f

        // X = sin(bearing) * distance
        // Y = -cos(bearing) * distance (because Y increases downwards in Android Canvas)
        val offsetX = sin(bearingRadians).toFloat() * normalizedDistance
        val offsetY = -cos(bearingRadians).toFloat() * normalizedDistance

        val x = (0.5f + offsetX).coerceIn(0.0f, 1.0f)
        val y = (0.5f + offsetY).coerceIn(0.0f, 1.0f)

        return Pair(x, y)
    }
}
