package io.github.yuushilabs.sokutei.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.yuushilabs.sokutei.core.VerticalDisplacementEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.asin

/**
 * センサ（回転ベクトル＋線形加速度）を core の変位推定器につなぐ橋渡し。
 *
 * 線形加速度は端末座標で得られるため、回転行列で世界座標に変換し、
 * 鉛直成分（世界 Z）だけを推定器へ渡す。カメラの向き（仰角）は
 * カメラ光軸（端末 -Z）の世界 Z 成分から求める。
 */
class MotionTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val estimator = VerticalDisplacementEstimator()
    private val rotationMatrix = FloatArray(9)
    @Volatile private var hasRotation = false
    @Volatile private var measuring = false
    private var lastPublishMillis = 0L

    private val _displacementMeters = MutableStateFlow(0.0)
    val displacementMeters: StateFlow<Double> = _displacementMeters

    private val _elevationDeg = MutableStateFlow(0.0)
    /** カメラ光軸の仰角（水平=0、上向き正）。 */
    val elevationDeg: StateFlow<Double> = _elevationDeg

    private val _still = MutableStateFlow(true)
    val still: StateFlow<Boolean> = _still

    val hasSensors: Boolean get() = rotationSensor != null && accelSensor != null
    val isMeasuring: Boolean get() = measuring

    fun startListening() {
        rotationSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        measuring = false
    }

    fun startMeasurement() {
        estimator.reset()
        _displacementMeters.value = 0.0
        measuring = true
    }

    fun stopMeasurement(): Double {
        measuring = false
        _displacementMeters.value = estimator.displacementMeters()
        return _displacementMeters.value
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotation = true
                // 端末 -Z（カメラ光軸）の世界成分は (-R[2], -R[5], -R[8])。仰角は Z 成分から。
                val sinElev = (-rotationMatrix[8]).coerceIn(-1f, 1f)
                _elevationDeg.value = Math.toDegrees(asin(sinElev).toDouble())
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (!hasRotation || !measuring) return
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                // 世界 Z 成分 = R の3行目と加速度の内積
                val worldZ = rotationMatrix[6] * ax + rotationMatrix[7] * ay + rotationMatrix[8] * az
                val tMillis = event.timestamp / 1_000_000
                estimator.add(tMillis, worldZ.toDouble())
                // 推定は全サンプルの再計算になるため 100ms ごとに間引く
                if (tMillis - lastPublishMillis >= 100) {
                    lastPublishMillis = tMillis
                    _displacementMeters.value = estimator.displacementMeters()
                    _still.value = estimator.isStill()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
