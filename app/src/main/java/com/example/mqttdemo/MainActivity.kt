package com.example.mqttdemo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var adapter: MqttAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = MqttAdapter(this, "tcp://broker.hivemq.com:1883")

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
    }

    private var helloSub: Disposable? = null

    private var byeSub1: Disposable? = null
    private var byeSub2: Disposable? = null

    override fun onResume() {
        super.onResume()

        adapter.connect(
                onSuccess = { token ->
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
                },
                onFailure = { token, throwable ->
                    Timber.d("onFailure")
                    Timber.e(throwable)
                })
    }
}
