package com.rsa.livemixer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var isEngineRunning = false

    companion object {
        init {
            System.loadLibrary("livemixer")
        }
    }

    private external fun startAudioEngine()
    private external fun stopAudioEngine()
    private external fun setMicGain(gain: Float)
    private external fun setLowEq(gainDb: Float)
    private external fun setMidEq(gainDb: Float)
    private external fun setHighEq(gainDb: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "RSA LIVE AUDIO MIXER"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        root.addView(title)

        val toggleBtn = Button(this).apply {
            text = "NYALAKAN MIXER (START)"
            setBackgroundColor(Color.parseColor("#1DB954"))
            setTextColor(Color.WHITE)
        }
        root.addView(toggleBtn)

        // Slider 1: Mic Gain (0% - 200%)
        root.addView(createSliderControl("Mic Gain / Volume", 0, 200, 100) { progress ->
            val gain = progress / 100f
            setMicGain(gain)
            "${progress}%"
        })

        // Slider 2: Low EQ 120Hz (-12dB s/d +12dB)
        root.addView(createSliderControl("LOW / BASS (120 Hz)", 0, 24, 12) { progress ->
            val db = (progress - 12).toFloat()
            setLowEq(db)
            "${if (db > 0) "+" else ""}${db.toInt()} dB"
        })

        // Slider 3: Mid EQ 2.5kHz (-12dB s/d +12dB)
        root.addView(createSliderControl("MID / VOKAL (2.5 kHz)", 0, 24, 12) { progress ->
            val db = (progress - 12).toFloat()
            setMidEq(db)
            "${if (db > 0) "+" else ""}${db.toInt()} dB"
        })

        // Slider 4: High EQ 10kHz (-12dB s/d +12dB)
        root.addView(createSliderControl("HIGH / CLARITY (10 kHz)", 0, 24, 12) { progress ->
            val db = (progress - 12).toFloat()
            setHighEq(db)
            "${if (db > 0) "+" else ""}${db.toInt()} dB"
        })

        setContentView(root)

        toggleBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                return@setOnClickListener
            }

            if (!isEngineRunning) {
                startAudioEngine()
                isEngineRunning = true
                toggleBtn.text = "MATIKAN MIXER (STOP)"
                toggleBtn.setBackgroundColor(Color.parseColor("#E53935"))
                Toast.makeText(this, "Audio Engine Aktif (Pasang Headset/OTG)", Toast.LENGTH_SHORT).show()
            } else {
                stopAudioEngine()
                isEngineRunning = false
                toggleBtn.text = "NYALAKAN MIXER (START)"
                toggleBtn.setBackgroundColor(Color.parseColor("#1DB954"))
            }
        }
    }

    private fun createSliderControl(label: String, min: Int, max: Int, defaultVal: Int, onUpdate: (Int) -> String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 36, 0, 12)
        }

        val infoText = TextView(this).apply {
            text = "$label : ${onUpdate(defaultVal)}"
            setTextColor(Color.LTGRAY)
            textSize = 15f
        }

        val seekBar = SeekBar(this).apply {
            this.max = max
            this.progress = defaultVal
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    infoText.text = "$label : ${onUpdate(progress)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        box.addView(infoText)
        box.addView(seekBar)
        return box
    }

    override fun onDestroy() {
        stopAudioEngine()
        super.onDestroy()
    }
}