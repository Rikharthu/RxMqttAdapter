package com.example.mqttdemo

import android.content.Context
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import timber.log.Timber
import java.nio.charset.Charset

class MqttAdapter(context: Context, val serverUri: String) : MqttCallback, IMqttMessageListener {

    private val messagesSubject: PublishSubject<Message> = PublishSubject.create()

    private val topicSubscribtions = mutableMapOf<String, Int>()

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        // TODO create observable from here and filter it, as done with event bus
        Timber.d("messageArrived: $topic $message")
        messagesSubject.onNext(Message(topic!!, String(message!!.payload)))
    }

    override fun connectionLost(cause: Throwable?) {
        Timber.d("connectionLost ${cause.toString()}")
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        Timber.d("deliveryComplete")
    }

    // TODO better use filter operator
    private val topics: MutableMap<String, PublishSubject<String>> = mutableMapOf()

    val clientId = MqttClient.generateClientId()

    private lateinit var client: MqttAndroidClient
    private val appContext: Context = context.applicationContext

    init {
        client = MqttAndroidClient(appContext, serverUri, clientId)
    }

    fun connect(onSuccess: ((IMqttToken) -> Unit)? = null, onFailure: ((IMqttToken, Throwable) -> Unit)?) {
        val token = client.connect()
        token.actionCallback = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                // We are connected
                onConnected()
                if (onSuccess != null) {
                    onSuccess(asyncActionToken)
                }
            }

            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                // Something went wrong e.g. connection timeout or firewall problems
                if (onFailure != null) {
                    onFailure(asyncActionToken, exception)
                }
            }
        }
    }

    private fun onConnected() {
        client.setCallback(this)
    }

    fun subscribe(topic: String, qos: Int = 0): Observable<Message> {
        if (topicSubscribtions.containsKey(topic)) {
            val subCount = topicSubscribtions[topic]!!
            topicSubscribtions[topic] = subCount + 1
        } else {
            topicSubscribtions[topic] = 1
        }
        if (client.isConnected) {
            client.subscribe(topic, qos)
        }
        return messagesSubject.filter { it.topic == topic }
                .doOnDispose {
                    var subCount = topicSubscribtions[topic]!!
                    subCount--
                    Timber.d("$subCount subscribers left for topic $topic")
                    if (subCount == 0) {
                        Timber.d("No more subscribers left for $topic, unsubscribing from mqtt")
                        client.unsubscribe(topic)
                    }
                    topicSubscribtions[topic] = subCount
                }
    }

    fun post(topic: String, message: String) {
        client.publish(topic, message.toByteArray(Charset.forName("utf-8")), 0, false)
    }
}