package com.example.mqttdemo

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var adapter: MqttAdapter

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var orientationObserver: Disposable? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = MqttAdapter(this, "tcp://broker.hivemq.com:1883")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        publishBtn.setOnClickListener {
            adapter.post("test/messages/hello", "Hello, World! ${Random().nextInt()}")
            adapter.post("test/messages/bye", "Goodbye, World! ${Random().nextInt()}")
        }

        unsubBtn.setOnClickListener {
            Timber.d("Disposing bye sub1")
            byeSub1?.dispose()
        }

        unsubBtn2.setOnClickListener {
            Timber.d("Disposing bye sub2")
            byeSub2?.dispose()
        }

        pushOrientationBtn.text = "Start publishing"
        pushOrientationBtn.setOnClickListener {
            if (orientationObserver == null) {
                orientationObserver = Observable.interval(1, TimeUnit.SECONDS)
                        .subscribe {
                            updateOrientationAngles()
                            val message = "${Math.toDegrees(orientationAngles[0].toDouble())}/" +
                                    "${Math.toDegrees(orientationAngles[1].toDouble())}/" +
                                    "${Math.toDegrees(orientationAngles[2].toDouble())}"
                            adapter.post("device/orientation", message)
                        }
                pushOrientationBtn.text = "Stop publishing"
            } else {
                orientationObserver!!.dispose()
                orientationObserver = null
                pushOrientationBtn.text = "Start publishing"
            }
        }
    }

    private var helloSub: Disposable? = null

    private var byeSub1: Disposable? = null
    private var byeSub2: Disposable? = null

    override fun onResume() {
        super.onResume()

        adapter.connect(
                onSuccess = { token ->
                    onMqttConnected()
                },
                onFailure = { token, throwable ->
                    Timber.d("onFailure")
                    Timber.e(throwable)
                })

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager.registerListener(this, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magneticField,
                SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Timber.d("Sensor accuracy changed to $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading,
                    0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.size)
        }
    }

    // Compute the three orientation angles based on the most recent readings from
    // the device's accelerometer and magnetometer.
    fun updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading)
        // "mRotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        // "mOrientationAngles" now has up-to-date information.
    }

    private var orientationSub: Disposable? = null

    private fun onMqttConnected() {
        Timber.d("onSuccess")
        helloSub = adapter.subscribe("test/messages/hello").subscribe {
            Timber.d("hello:" + it.toString())
        }
        byeSub1 = adapter.subscribe("test/messages/bye").subscribe {
            Timber.d("bye1:" + it.toString())
        }
        byeSub2 = adapter.subscribe("test/messages/bye").subscribe {
            Timber.d("bye2:" + it.toString())
        }

        orientationSub = adapter.subscribe("device/orientation").subscribe {
            Timber.d("orientation:" + it.toString())
            val degrees = it.message.split("/")
            // Azimuth Pitch Roll
            val dataText = "Azimuth: %.2f\nPitch: %.2f\nRoll: %.2f"
                    .format(degrees[0].toFloat(), degrees[1].toFloat(), degrees[2].toFloat())
            sensorDataTextView.text = dataText
        }
    }

    fun Double.format(digits: Int) = java.lang.String.format("%.${digits}f", this)
}
