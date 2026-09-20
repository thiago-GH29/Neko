package com.thiago.ancexperimental

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

/**
 * Experimental phone-based ANC.
 *
 * If a Bluetooth headset exposes an HFP/HSP microphone, the app tries to route
 * both input and output through Bluetooth SCO. This is NOT real ANC: the
 * acoustic path and Bluetooth latency are not compensated, and the inverted
 * microphone signal is only a simple experiment.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var route: TextView
    private lateinit var level: TextView
    private lateinit var gain: SeekBar
    private var engine: AncEngine? = null

    companion object {
        private const val REQ_RECORD = 100
        private const val REQ_BLUETOOTH = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        route = findViewById(R.id.route)
        level = findViewById(R.id.level)
        gain = findViewById(R.id.gain)

        findViewById<Button>(R.id.start).setOnClickListener {
            requestNeededPermissionsAndStart()
        }

        findViewById<Button>(R.id.stop).setOnClickListener {
            stopAnc()
        }

        updateBluetoothRouteInfo()
    }

    private fun requestNeededPermissionsAndStart() {
        val missing = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            missing += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQ_RECORD)
        } else {
            startAnc()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD &&
            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startAnc()
        } else {
            status.text = "Permiso de micrófono/Bluetooth rechazado"
        }
    }

    private fun startAnc() {
        stopAnc()

        val amount = gain.progress / 100f
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        engine = AncEngine(audioManager) { rms, inputName ->
            runOnUiThread {
                level.text = "Nivel: %.3f".format(rms)
                route.text = "Entrada: $inputName"
            }
        }

        try {
            val usedBluetooth = engine!!.start(amount)
            status.text = if (usedBluetooth) {
                "Activo — micrófono Bluetooth + salida Bluetooth"
            } else {
                "Activo — no se encontró micrófono Bluetooth; usando entrada disponible"
            }
        } catch (e: Exception) {
            engine?.stop()
            engine = null
            status.text = "Error de audio: ${e.message ?: "desconocido"}"
        }
    }

    private fun stopAnc() {
        engine?.stop()
        engine = null
        status.text = "Detenido"
        route.text = "Entrada: —"
        level.text = "Nivel: —"
    }

    private fun updateBluetoothRouteInfo() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUT)
        val bt = inputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        route.text = if (bt != null) "Micrófono Bluetooth detectado: ${bt.productName}" else "Micrófono Bluetooth: no detectado"
    }

    override fun onDestroy() {
        stopAnc()
        super.onDestroy()
    }

    private class AncEngine(
        private val audioManager: AudioManager,
        private val onLevel: (Float, String) -> Unit
    ) {
        private var running = false
        private var record: AudioRecord? = null
        private var track: AudioTrack? = null
        private var thread: Thread? = null
        private var usingSco = false

        // Bluetooth HFP/SCO is commonly 8 or 16 kHz. 16 kHz is a practical
        // compromise for this experiment and keeps latency/buffer size lower.
        private val sampleRate = 16000
        private val frameCount = 160 // 10 ms
        private val bufferBytes = frameCount * 2

        fun start(antiGain: Float): Boolean {
            if (running) return usingSco

            val bluetoothInput = findBluetoothInput()
            usingSco = bluetoothInput != null

            if (usingSco) {
                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                    // Give Android a moment to establish the SCO route.
                    Thread.sleep(250)
                } catch (_: Exception) {
                    usingSco = false
                }
            }

            val source = if (usingSco) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

            val minIn = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val minOut = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minIn <= 0 || minOut <= 0) throw IllegalStateException("Audio no disponible")

            record = AudioRecord(
                source,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minIn, bufferBytes * 4)
            )

            track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(if (usingSco) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                maxOf(minOut, bufferBytes * 4),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            // On Android 12+, explicitly prefer the Bluetooth input/output device.
            if (Build.VERSION.SDK_INT >= 31) {
                bluetoothInput?.let { record?.setPreferredDevice(it) }
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val bluetoothOutput = outputs.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                bluetoothOutput?.let { track?.preferredDevice = it }
            }

            running = true
            thread = Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val input = ShortArray(frameCount)
                val output = ShortArray(frameCount)
                val inputName = record?.routedDevice?.productName?.toString() ?: "entrada del teléfono"

                try {
                    record!!.startRecording()
                    track!!.play()

                    while (running) {
                        val n = record!!.read(input, 0, input.size, AudioRecord.READ_BLOCKING)
                        if (n > 0) {
                            var sum = 0.0
                            for (i in 0 until n) {
                                val x = input[i].toDouble()
                                sum += x * x

                                // Simple phase inversion. This is deliberately
                                // NOT an adaptive ANC filter.
                                var y = -x * antiGain
                                if (y > 32767.0) y = 32767.0
                                if (y < -32768.0) y = -32768.0
                                output[i] = y.toInt().toShort()
                            }

                            val rms = sqrt(sum / n) / 32768.0
                            onLevel(rms.toFloat(), inputName)
                            track!!.write(output, 0, n, AudioTrack.WRITE_BLOCKING)
                        }
                    }
                } catch (_: Exception) {
                    // Stop/release handles the resources below.
                } finally {
                    try { record?.stop() } catch (_: Exception) {}
                    try { track?.stop() } catch (_: Exception) {}
                }
            }.also { it.start() }

            return usingSco
        }

        private fun findBluetoothInput(): AudioDeviceInfo? {
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUT)
            return inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                ?: if (Build.VERSION.SDK_INT >= 31) {
                    inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                } else null
        }

        fun stop() {
            running = false
            try { thread?.join(500) } catch (_: Exception) {}
            try { record?.release() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            record = null
            track = null
            thread = null

            if (usingSco) {
                try {
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = false
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                    audioManager.mode = AudioManager.MODE_NORMAL
                } catch (_: Exception) {}
            }
            usingSco = false
        }
    }
}
