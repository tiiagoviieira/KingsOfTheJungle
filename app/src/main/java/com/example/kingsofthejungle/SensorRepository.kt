package com.example.kingsofthejungle

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SensorRepository(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val _headingFlow = MutableStateFlow(0f)
    val headingFlow = _headingFlow.asStateFlow()

    private val _heartRateFlow = MutableStateFlow<Int?>(null)
    val heartRateFlow = _heartRateFlow.asStateFlow()

    fun startListening() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        
        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("SensorRepo", "Heart rate sensor registered")
        } else {
            Log.w("SensorRepo", "Heart rate sensor not found on this device")
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                
                val orientationAngles = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                
                // azimuth is orientationAngles[0], in radians. Convert to degrees.
                val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                
                // Normalize to 0-360
                val normalizedHeading = (azimuthDegrees + 360) % 360
                _headingFlow.value = normalizedHeading
            }
            Sensor.TYPE_HEART_RATE -> {
                val heartRate = event.values[0].toInt()
                _heartRateFlow.value = heartRate
                Log.d("SensorRepo", "New heart rate: $heartRate")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}
