package com.rsa.livemixer

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private var isEqEnabled = true
    private var isToneLocked = false
    private var isDucking = false
    private var ytSizeMode = 0
    private var normalMusicVolume = 85

    private lateinit var ytWebView: WebView
    private lateinit var eqView: ParametricEqView
    private lateinit var vuMeter: StereoVuMeterView
    private lateinit var seekMusicVol: SeekBar
    private lateinit var btnDucking: Button
    private lateinit var btnEqToggle: Button
    private lateinit var btnLockTone: Button

    private lateinit var knobBass: RotaryKnobView
    private lateinit var knobMid: RotaryKnobView
    private lateinit var knobTreble: RotaryKnobView

    private lateinit var soundPool: SoundPool
    private val soundMap = HashMap<String, Int>()
    private val activeStreams = ArrayList<Int>()
    private var pendingPadKeyForPicker: String? = null

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var fallbackEqualizer: Equalizer? = null
    private var visualizer: Visualizer? = null

    private val defaultFreqs = floatArrayOf(65f, 150f, 380f, 2200f, 5500f, 10000f)
    private val currentGains = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)

    private var toneBass = 0f
    private var toneMid = 0f
    private var toneTreble = 0f

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val key = pendingPadKeyForPicker
        if (uri != null && key != null) {
            importCustomSoundToPad(key, uri)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Jaga layar HP tetap menyala selama live karaoke
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupAudioDspWithLimiter()
        setupSoundboard()

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0E14"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 28)
        }

        // === 1. TOP HEADER BAR ===
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 12)
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(TextView(this).apply {
            text = "OHO AUDIO MIXER"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F8FAFC"))
            letterSpacing = 0.05f
        })
        titleBox.addView(TextView(this).apply {
            text = "PRO KARAOKE & MASTERING DECK"
            textSize = 8.5f
            setTextColor(Color.parseColor("#38BDF8"))
            letterSpacing = 0.08f
        })

        btnEqToggle = Button(this).apply {
            text = "EQ ON"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = makeRoundedBg("#10B981", "#34D399", 16f)
            layoutParams = LinearLayout.LayoutParams(145, 68).apply { marginEnd = 10 }
            setOnClickListener {
                isEqEnabled = !isEqEnabled
                applyDspEnabled(isEqEnabled)
                text = if (isEqEnabled) "EQ ON" else "BYPASS"
                background = if (isEqEnabled) makeRoundedBg("#10B981", "#34D399", 16f)
                else makeRoundedBg("#334155", "#64748B", 16f)
            }
        }

        val btnSave = Button(this).apply {
            text = "SAVE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            background = makeRoundedBg("#1E293B", "#475569", 16f)
            layoutParams = LinearLayout.LayoutParams(125, 68)
            setOnClickListener {
                saveCustomPreset()
                Toast.makeText(context, "Preset EQ & Tone tersimpan ke CUSTOM!", Toast.LENGTH_SHORT).show()
            }
        }

        header.addView(titleBox)
        header.addView(btnEqToggle)
        header.addView(btnSave)
        root.addView(header)

        // === 2. STEREO LED VU METER ===
        val vuBarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 4, 6, 12)
        }
        vuBarRow.addView(TextView(this).apply {
            text = "STEREO VU"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 12, 0)
        })
        vuMeter = StereoVuMeterView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 28, 1f)
        }
        vuBarRow.addView(vuMeter)
        root.addView(vuBarRow)

        // === 3. WIDESCREEN YOUTUBE KARAOKE PLAYER ===
        val ytCard = createCardPanel().apply { setPadding(10, 10, 10, 10) }
        val ytBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 8)
        }

        ytBar.addView(TextView(this).apply {
            text = "▶ YOUTUBE KARAOKE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F87171"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val btnSearchYt = Button(this).apply {
            text = "🔍 CARI LAGU"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = makeRoundedBg("#DC2626", "#F87171", 12f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 64).apply {
                marginEnd = 8
            }
            setOnClickListener {
                ytWebView.loadUrl("https://m.youtube.com/results?search_query=karaoke+campursari+dangdut+lirik")
            }
        }

        val btnResizeYt = Button(this).apply {
            text = "PERBESAR / LIPAT"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            background = makeRoundedBg("#1E293B", "#475569", 12f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 64)
        }

        ytBar.addView(btnSearchYt)
        ytBar.addView(btnResizeYt)
        ytCard.addView(ytBar)

        ytWebView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 520)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("https://m.youtube.com")

            setOnTouchListener { v, _ ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        ytCard.addView(ytWebView)

        // Kotak Catatan Request Lagu Penonton
        val requestNoteInput = EditText(this).apply {
            hint = "📝 Catatan Request Lagu Penonton (ketik di sini)..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 11f
            background = makeRoundedBg("#0F172A", "#1E293B", 10f)
            setPadding(18, 12, 18, 12)
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        ytCard.addView(requestNoteInput)

        btnResizeYt.setOnClickListener {
            ytSizeMode = (ytSizeMode + 1) % 3
            val lp = ytWebView.layoutParams
            when (ytSizeMode) {
                0 -> {
                    ytWebView.visibility = View.VISIBLE
                    lp.height = 520
                    btnResizeYt.text = "PERBESAR / LIPAT"
                }
                1 -> {
                    ytWebView.visibility = View.VISIBLE
                    lp.height = 760
                    btnResizeYt.text = "LIPAT VIDEO"
                }
                2 -> {
                    ytWebView.visibility = View.GONE
                    btnResizeYt.text = "BUKA VIDEO"
                }
            }
            ytWebView.layoutParams = lp
        }

        root.addView(ytCard)
        root.addView(spacer(10))

        // === 4. MUSIC VOL FADER & SMOOTH DUCKING ===
        val volCard = createCardPanel().apply { setPadding(16, 12, 16, 12) }
        val volRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        volRow.addView(TextView(this).apply {
            text = "MUSIC VOL"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#CBD5E1"))
        })

        seekMusicVol = SeekBar(this).apply {
            max = 100
            progress = normalMusicVolume
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
                marginEnd = 12
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser && !isDucking) normalMusicVolume = p
                    setSystemMusicVolume(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        btnDucking = Button(this).apply {
            text = "DUCKING -50%"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = makeRoundedBg("#1E293B", "#475569", 12f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 64)
            setOnClickListener {
                isDucking = !isDucking
                val startVol = seekMusicVol.progress
                val targetVol = if (isDucking) (normalMusicVolume * 0.45f).toInt().coerceAtLeast(10) else normalMusicVolume
                animateVolumeFade(startVol, targetVol)
                background = if (isDucking) makeRoundedBg("#D97706", "#FBBF24", 12f)
                else makeRoundedBg("#1E293B", "#475569", 12f)
            }
        }

        volRow.addView(seekMusicVol)
        volRow.addView(btnDucking)
        volCard.addView(volRow)
        root.addView(volCard)
        root.addView(spacer(10))

        // === 5. 6-BAND MUSIC PARAMETRIC EQ + TONE LOCK ===
        val eqCard = createCardPanel().apply { setPadding(14, 12, 14, 12) }

        val eqHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 8)
        }

        eqHeaderRow.addView(TextView(this).apply {
            text = "6-BAND PARAMETRIC EQ & TONE"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // TOMBOL PENGUNCI EQ, Q, BASS, MID, TREBLE AGAR TIDAK KESENGGOL SAAT SCROLL
        btnLockTone = Button(this).apply {
            text = "🔓 UNLOCKED"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = makeRoundedBg("#1E293B", "#38BDF8", 12f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 58)
            setOnClickListener {
                isToneLocked = !isToneLocked
                eqView.isLocked = isToneLocked
                knobBass.isLocked = isToneLocked
                knobMid.isLocked = isToneLocked
                knobTreble.isLocked = isToneLocked

                if (isToneLocked) {
                    text = "🔒 TERKUNCI"
                    background = makeRoundedBg("#B91C1C", "#F87171", 12f)
                    Toast.makeText(context, "EQ & Knob dikunci aman saat scroll", Toast.LENGTH_SHORT).show()
                } else {
                    text = "🔓 UNLOCKED"
                    background = makeRoundedBg("#1E293B", "#38BDF8", 12f)
                }
            }
        }
        eqHeaderRow.addView(btnLockTone)
        eqCard.addView(eqHeaderRow)

        eqView = ParametricEqView(this, defaultFreqs, currentGains) { bandIdx, gainDb ->
            currentGains[bandIdx] = gainDb
            applySingleBandCalculated(bandIdx)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 240)
        }
        eqCard.addView(eqView)

        // Preset Cepat EQ
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        val presets = listOf(
            "DANGDUT" to floatArrayOf(6.5f, 4.0f, -1.5f, -3.0f, 3.5f, 5.0f),
            "KARAOKE" to floatArrayOf(4.0f, 2.5f, -2.0f, -4.5f, 3.0f, 4.0f),
            "CAMPURSARI" to floatArrayOf(5.0f, 3.5f, 0.0f, -2.5f, 4.0f, 4.5f),
            "CUSTOM" to loadCustomPreset()
        )
        presets.forEachIndexed { idx, (label, values) ->
            val btn = Button(this).apply {
                text = label
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#F1F5F9"))
                background = makeRoundedBg("#1E293B", "#38BDF8", 12f)
                layoutParams = LinearLayout.LayoutParams(0, 66, 1f).apply {
                    if (idx < presets.size - 1) marginEnd = 8
                }
                setOnClickListener {
                    if (isToneLocked) {
                        Toast.makeText(context, "Buka kunci (🔒 TERKUNCI) dulu untuk ganti preset", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val target = if (label == "CUSTOM") loadCustomPreset() else values
                    for (i in 0..5) currentGains[i] = target[i]
                    applyAllBands()
                    eqView.invalidate()
                    Toast.makeText(context, "EQ Mode: $label", Toast.LENGTH_SHORT).show()
                }
            }
            presetRow.addView(btn)
        }
        eqCard.addView(presetRow)

        // MASTER QUICK TONE KNOBS (BASS, MIDDLE, TREBLE)
        val toneContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedBg("#181F2F", "#1E293B", 14f)
            setPadding(12, 14, 12, 14)
        }

        toneContainer.addView(TextView(this).apply {
            text = "QUICK TONE TUNING (BASS • MIDDLE • TREBLE)"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        })

        val knobsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        knobBass = RotaryKnobView(this, "MASTER BASS", -12f, 12f, 0f, "dB", "#F59E0B") { value ->
            toneBass = value
            applyAllBands()
        }
        knobMid = RotaryKnobView(this, "MASTER MID", -12f, 12f, 0f, "dB", "#10B981") { value ->
            toneMid = value
            applyAllBands()
        }
        knobTreble = RotaryKnobView(this, "MASTER TREBLE", -12f, 12f, 0f, "dB", "#38BDF8") { value ->
            toneTreble = value
            applyAllBands()
        }

        val knobLayout = LinearLayout.LayoutParams(0, 186, 1f)
        knobsRow.addView(knobBass, knobLayout)
        knobsRow.addView(knobMid, knobLayout)
        knobsRow.addView(knobTreble, knobLayout)
        toneContainer.addView(knobsRow)

        eqCard.addView(toneContainer)
        root.addView(eqCard)
        root.addView(spacer(10))

        // === 6. INTERACTIVE SOUNDBOARD (8 PAD + LONG PRESS GANTI MP3) ===
        val sbdCard = createCardPanel().apply { setPadding(12, 12, 12, 12) }
        sbdCard.addView(TextView(this).apply {
            text = "SOUNDBOARD (TEKAN TAHAN PAD UNTUK GANTI FILE MP3)"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(4, 0, 0, 8)
        })

        val sbdGrid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
        }

        val pads = listOf(
            Triple("KETAWA", "ketawa", "#A855F7"),
            Triple("APPLAUSE", "applause", "#10B981"),
            Triple("LAWAN", "lawan", "#F59E0B"),
            Triple("GENDANG", "gendang", "#38BDF8"),
            Triple("JANGKRIK", "jangkrik", "#06B6D4"),
            Triple("AIRHORN", "airhorn", "#EF4444"),
            Triple("INTRO", "intro", "#EC4899"),
            Triple("STOP ALL", "STOP_ALL", "#DC2626")
        )

        pads.forEachIndexed { idx, (label, key, neonHex) ->
            val isStop = key == "STOP_ALL"
            val btn = Button(this).apply {
                text = label
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isStop) Color.parseColor("#FCA5A5") else Color.WHITE)
                background = makeRoundedBg(if (isStop) "#3B1219" else "#161E2E", neonHex, 14f)
                setOnClickListener {
                    if (isStop) stopAllSounds() else playSoundPad(key)
                }
                if (!isStop) {
                    setOnLongClickListener {
                        pendingPadKeyForPicker = key
                        Toast.makeText(context, "Pilih file MP3/WAV untuk tombol $label", Toast.LENGTH_SHORT).show()
                        pickAudioLauncher.launch("audio/*")
                        true
                    }
                }
            }
            val gp = GridLayout.LayoutParams().apply {
                width = 0
                height = 96
                columnSpec = GridLayout.spec(idx % 4, 1f)
                rowSpec = GridLayout.spec(idx / 4, 1f)
                setMargins(5, 5, 5, 5)
            }
            sbdGrid.addView(btn, gp)
        }
        sbdCard.addView(sbdGrid)
        root.addView(sbdCard)

        rootScroll.addView(root)
        setContentView(rootScroll)

        // Terapkan preset Dangdut sebagai bawaan awal
        val initPreset = presets[0].second
        for (i in 0..5) currentGains[i] = initPreset[i]
        applyAllBands()
        eqView.invalidate()

        // Minta izin runtime RECORD_AUDIO agar Stereo LED VU Meter aktif bergerak
        ensureVisualizerPermissionAndStart()
    }

    private fun ensureVisualizerPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            setupVisualizer()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 202)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 202 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupVisualizer()
        }
    }

    private fun setupAudioDspWithLimiter() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Aktifkan PreEQ 6-Band + Limiter Output agar suara ke soundcard tidak pecah
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, 6, false, 0, false, 0, true
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, 0, cfg).apply {
                    enabled = true
                    for (ch in 0..1) {
                        val preEq = getPreEqByChannelIndex(ch)
                        preEq.isEnabled = true
                        for (b in 0..5) {
                            val band = preEq.getBand(b)
                            band.isEnabled = true
                            band.cutoffFrequency = defaultFreqs[b]
                            band.gain = 0f
                            preEq.setBand(b, band)
                        }
                        val limiter = getLimiterByChannelIndex(ch)
                        limiter.isEnabled = true
                        limiter.threshold = -1.5f
                        limiter.ratio = 10f
                        limiter.attackTime = 2f
                        limiter.releaseTime = 60f
                        setLimiterByChannelIndex(ch, limiter)
                    }
                }
            } else {
                fallbackEqualizer = Equalizer(0, 0).apply { enabled = true }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyDspEnabled(enable: Boolean) {
        try {
            dynamicsProcessing?.enabled = enable
            fallbackEqualizer?.enabled = enable
        } catch (_: Exception) {}
    }

    private fun applySingleBandCalculated(bandIdx: Int) {
        val toneOffset = when (bandIdx) {
            0, 1 -> toneBass
            2, 3 -> toneMid
            else -> toneTreble
        }
        val calculatedGain = (currentGains[bandIdx] + toneOffset).coerceIn(-15f, 15f)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                for (ch in 0..1) {
                    val band = dynamicsProcessing!!.getPreEqBandByChannelIndex(ch, bandIdx)
                    band.cutoffFrequency = defaultFreqs[bandIdx]
                    band.gain = calculatedGain
                    dynamicsProcessing!!.setPreEqBandByChannelIndex(ch, bandIdx, band)
                }
            } else if (fallbackEqualizer != null) {
                val numBands = fallbackEqualizer!!.numberOfBands.toInt()
                if (bandIdx < numBands) {
                    fallbackEqualizer!!.setBandLevel(bandIdx.toShort(), (calculatedGain * 100).toInt().toShort())
                }
            }
        } catch (_: Exception) {}
    }

    private fun applyAllBands() {
        for (i in 0..5) applySingleBandCalculated(i)
    }

    private fun setupVisualizer() {
        try {
            visualizer?.release()
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                        if (waveform == null || waveform.isEmpty()) return
                        var sumSqL = 0.0
                        var sumSqR = 0.0
                        val half = waveform.size / 2
                        for (i in 0 until half) {
                            val sampleL = ((waveform[i].toInt() and 0xFF) - 128) / 128.0
                            sumSqL += sampleL * sampleL
                        }
                        for (i in half until waveform.size) {
                            val sampleR = ((waveform[i].toInt() and 0xFF) - 128) / 128.0
                            sumSqR += sampleR * sampleR
                        }
                        val rmsL = sqrt(sumSqL / max(1, half)).toFloat()
                        val rmsR = sqrt(sumSqR / max(1, waveform.size - half)).toFloat()
                        vuMeter.setStereoLevels(rmsL * 3.2f, rmsR * 3.2f)
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {}
                }, Visualizer.getMaxCaptureRate(), true, false)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateVolumeFade(fromVol: Int, toVol: Int) {
        ValueAnimator.ofInt(fromVol, toVol).apply {
            duration = 350
            addUpdateListener { anim ->
                val v = anim.animatedValue as Int
                seekMusicVol.progress = v
                setSystemMusicVolume(v)
            }
            start()
        }
    }

    private fun setSystemMusicVolume(percent: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    private fun setupSoundboard() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()

        val files = listOf(
            "ketawa" to "ketawa.mp3",
            "applause" to "applause.mp3",
            "lawan" to "lawan.mp3",
            "gendang" to "gendang.mp3",
            "jangkrik" to "jangkrik.mp3",
            "airhorn" to "airhorn.mp3",
            "intro" to "intro.mp3"
        )
        for ((key, defaultAsset) in files) {
            val customFile = File(filesDir, "custom_$key.audio")
            if (customFile.exists()) {
                soundMap[key] = soundPool.load(customFile.absolutePath, 1)
            } else {
                try {
                    val fd = assets.openFd(defaultAsset)
                    soundMap[key] = soundPool.load(fd, 1)
                } catch (_: IOException) {}
            }
        }
    }

    private fun importCustomSoundToPad(key: String, uri: Uri) {
        try {
            val destFile = File(filesDir, "custom_$key.audio")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            soundMap[key] = soundPool.load(destFile.absolutePath, 1)
            Toast.makeText(this, "Suara baru berhasil dipasang ke tombol!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memuat file audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playSoundPad(key: String) {
        val id = soundMap[key] ?: return
        val streamId = soundPool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        if (streamId != 0) activeStreams.add(streamId)
    }

    private fun stopAllSounds() {
        for (sid in activeStreams) soundPool.stop(sid)
        activeStreams.clear()
    }

    private fun saveCustomPreset() {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE).edit()
        for (i in 0..5) prefs.putFloat("band_$i", currentGains[i])
        prefs.apply()
    }

    private fun loadCustomPreset(): FloatArray {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
        return FloatArray(6) { i -> prefs.getFloat("band_$i", 0f) }
    }

    private fun createCardPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = makeRoundedBg("#121722", "#263248", 18f)
    }

    private fun makeRoundedBg(fillHex: String, strokeHex: String, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            setStroke(2, Color.parseColor(strokeHex))
            cornerRadius = radius
        }

    private fun spacer(h: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::ytWebView.isInitialized && ytWebView.canGoBack()) ytWebView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        try { visualizer?.release() } catch (_: Exception) {}
        try { dynamicsProcessing?.release() } catch (_: Exception) {}
        try { fallbackEqualizer?.release() } catch (_: Exception) {}
        soundPool.release()
        super.onDestroy()
    }

    // === GRAFIK 6-BAND PARAMETRIC EQ INTERAKTIF ===
    class ParametricEqView(
        context: Context,
        private val freqs: FloatArray,
        private val gains: FloatArray,
        private val onBandChanged: (Int, Float) -> Unit
    ) : View(context) {

        var isLocked = false
        private var selectedBand = -1

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B"); strokeWidth = 2f
        }
        private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155"); strokeWidth = 3f
        }
        private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.parseColor("#22D3EE")
        }
        private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.parseColor("#0F172A")
        }
        private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.parseColor("#38BDF8")
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8"); textSize = 21f; textAlign = Paint.Align.CENTER
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Jika dikunci, biarkan ScrollView luar menggulir layar dengan mulus!
            if (isLocked) return false

            parent?.requestDisallowInterceptTouchEvent(true)
            val padX = 52f
            val usableW = width - padX * 2
            val stepX = usableW / 5f
            val midY = height * 0.48f
            val maxAmpY = height * 0.36f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    var bestDist = 85f
                    selectedBand = -1
                    for (i in 0..5) {
                        val nx = padX + i * stepX
                        val dist = abs(event.x - nx)
                        if (dist < bestDist) { bestDist = dist; selectedBand = i }
                    }
                    return selectedBand != -1
                }
                MotionEvent.ACTION_MOVE -> {
                    if (selectedBand in 0..5) {
                        val normY = ((midY - event.y) / maxAmpY).coerceIn(-1f, 1f)
                        val db = (normY * 15f * 10f).roundToInt() / 10f
                        gains[selectedBand] = db
                        onBandChanged(selectedBand, db)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> { selectedBand = -1; invalidate(); return true }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val padX = 52f
            val usableW = width - padX * 2
            val stepX = usableW / 5f
            val midY = height * 0.48f
            val maxAmpY = height * 0.36f

            for (i in 0..5) {
                val x = padX + i * stepX
                canvas.drawLine(x, 16f, x, height - 34f, gridPaint)
            }
            canvas.drawLine(padX, midY, width - padX, midY, zeroLinePaint)

            val pts = Array(6) { i ->
                val x = padX + i * stepX
                val y = midY - (gains[i] / 15f) * maxAmpY
                PointF(x, y)
            }

            val path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 0 until 5) {
                val midX = (pts[i].x + pts[i + 1].x) / 2f
                path.cubicTo(midX, pts[i].y, midX, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
            }
            curvePaint.color = if (isLocked) Color.parseColor("#64748B") else Color.parseColor("#22D3EE")
            canvas.drawPath(path, curvePaint)

            val freqLabels = arrayOf("65Hz", "150Hz", "380Hz", "2.2kHz", "5.5kHz", "10kHz")
            for (i in 0..5) {
                val r = if (i == selectedBand) 18f else 13f
                nodeStrokePaint.color = if (isLocked) Color.parseColor("#94A3B8") else Color.parseColor("#38BDF8")
                canvas.drawCircle(pts[i].x, pts[i].y, r, nodeFillPaint)
                canvas.drawCircle(pts[i].x, pts[i].y, r, nodeStrokePaint)
                canvas.drawText(freqLabels[i], pts[i].x, height - 8f, textPaint)
            }
        }
    }

    // === KOMPONEN KUSTOM: ROTARY KNOB ===
    class RotaryKnobView(
        context: Context,
        private val label: String,
        private val minVal: Float,
        private val maxVal: Float,
        defaultVal: Float,
        private val unit: String,
        accentHex: String,
        private val onChange: (Float) -> Unit
    ) : View(context) {

        var isLocked = false
        private var currentVal = defaultVal
        private var lastY = 0f
        private val accentColor = Color.parseColor(accentHex)

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#242D3D")
        }
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
            color = accentColor
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.parseColor("#141A25")
        }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.parseColor("#3B4861")
        }
        private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
            color = accentColor
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8"); textSize = 16f
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 18f
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }

        fun setValue(v: Float) {
            currentVal = v.coerceIn(minVal, maxVal)
            onChange(currentVal)
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Jika dikunci, kembalikan false agar layar bisa di-scroll tanpa memutar knob
            if (isLocked) return false

            parent?.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastY = event.y; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = lastY - event.y
                    lastY = event.y
                    setValue(currentVal + (dy / 200f) * (maxVal - minVal))
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height * 0.38f
            val radius = min(width, height) * 0.22f
            val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

            canvas.drawArc(arcRect, 135f, 270f, false, trackPaint)
            val ratio = (currentVal - minVal) / (maxVal - minVal)
            activePaint.color = if (isLocked) Color.parseColor("#64748B") else accentColor
            pointerPaint.color = if (isLocked) Color.parseColor("#94A3B8") else accentColor
            canvas.drawArc(arcRect, 135f, 270f * ratio, false, activePaint)

            val innerR = radius * 0.72f
            canvas.drawCircle(cx, cy, innerR, bodyPaint)
            canvas.drawCircle(cx, cy, innerR, rimPaint)

            val angleRad = Math.toRadians((135.0 + 270.0 * ratio))
            val px = cx + cos(angleRad).toFloat() * (innerR * 0.75f)
            val py = cy + sin(angleRad).toFloat() * (innerR * 0.75f)
            canvas.drawLine(cx, cy, px, py, pointerPaint)

            val formatted = if (minVal < 0 && currentVal > 0.4f) "+${currentVal.toInt()} $unit"
            else "${currentVal.toInt()} $unit"
            canvas.drawText(formatted, cx, cy + radius + 25f, valPaint)
            canvas.drawText(label, cx, cy + radius + 46f, labelPaint)
        }
    }

    // === STEREO LED VU METER (L & R) ===
    class StereoVuMeterView(context: Context) : View(context) {
        private var levelL = 0.05f
        private var levelR = 0.05f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun setStereoLevels(l: Float, r: Float) {
            levelL = l.coerceIn(0.04f, 1f)
            levelR = r.coerceIn(0.04f, 1f)
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val segments = 26
            val gap = 4f
            val segW = (width - gap * (segments - 1)) / segments
            val rowH = (height - 4f) / 2f

            val levels = floatArrayOf(levelL, levelR)
            for (row in 0..1) {
                val activeCount = (levels[row] * segments).toInt()
                val top = row * (rowH + 4f)
                for (i in 0 until segments) {
                    val left = i * (segW + gap)
                    val isLit = i < activeCount
                    paint.color = when {
                        i >= 22 -> if (isLit) Color.parseColor("#EF4444") else Color.parseColor("#3B181E")
                        i >= 17 -> if (isLit) Color.parseColor("#F59E0B") else Color.parseColor("#3B2E16")
                        else -> if (isLit) Color.parseColor("#10B981") else Color.parseColor("#14332B")
                    }
                    canvas.drawRoundRect(left, top, left + segW, top + rowH, 3f, 3f, paint)
                }
            }
        }
    }
}