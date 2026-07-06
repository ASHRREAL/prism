package com.prism.muse.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Tilts the composable in 3D by sampling the accelerometer, giving album art
 * and floating cards the subtle "looking into glass" depth called for by the
 * reference UI. Falls back to a static, untilted layer on devices/emulators
 * without a usable sensor.
 */
@Composable
fun Modifier.gyroTilt(maxDegrees: Float = 6f, perspective: Float = 0.0016f): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rotationX = remember { Animatable(0f) }
    val rotationY = remember { Animatable(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0].coerceIn(-9.8f, 9.8f) / 9.8f
                val y = event.values[1].coerceIn(-9.8f, 9.8f) / 9.8f
                scope.launch {
                    rotationX.animateTo(-y * maxDegrees, spring(stiffness = 120f))
                }
                scope.launch {
                    rotationY.animateTo(x * maxDegrees, spring(stiffness = 120f))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return this.graphicsLayer {
        this.cameraDistance = 12f / perspective
        this.rotationX = rotationX.value
        this.rotationY = rotationY.value
    }
}
