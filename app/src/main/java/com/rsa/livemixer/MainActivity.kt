package com.rsa.livemixer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private var isEqEnabled = true
    private var isDucking = false
    private var ytSizeMode = 0 // 0 = Normal Widescreen, 1 = Diperbesar, 2 = Dilipat
    private var normalMusicVolume = 85

    private lateinit var ytWebView: WebView
    private lateinit var eqView: ParametricEqView
    private lateinit var vuMeter: StereoVuMeterView
    private lateinit var seekMusicVol: SeekBar
    private lateinit var btnDucking: Button
    private lateinit var btnEqToggle: Button

    private lateinit var soundPool: SoundPool
    private val soundMap = HashMap<String, Int>()
    private val activeStreams = ArrayList<Int>()

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var fallbackEqualizer: Equalizer? = null
    private var visualizer: Visualizer? = null

    private val defaultFreqs = floatArrayOf(65f, 150f, 380f, 2200f, 5500f, 10000f)
    private val currentGains = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupAudioDspSession0()
        setupSoundboard()

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0E14"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
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
            text = "KARAOKE LYRICS & MASTERING DECK"
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
            layoutParams = LinearLayout.LayoutParams(150, 68).apply { marginEnd = 10 }
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
            layoutParams = LinearLayout.LayoutParams(130, 68)
            setOnClickListener {
                saveCustomPreset()
                Toast.makeText(context, "Preset EQ tersimpan ke CUSTOM!", Toast.LENGTH_SHORT).show()
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
            setPadding(6, 6, 6, 12)
        }
        vuBarRow.addView(TextView(this).apply {
            text = "STEREO LED VU"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 12, 0)
        })
        vuMeter = StereoVuMeterView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 26, 1f)
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
        }
        ytCard.addView(ytWebView)

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

        // === 4. MUSIC VOL FADER & DUCKING -50% ===
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
                if (isDucking) {
                    val ducked = (normalMusicVolume * 0.45f).toInt().coerceAtLeast(10)
                    seekMusicVol.progress = ducked
                    setSystemMusicVolume(ducked)
                    background = makeRoundedBg("#D97706", "#FBBF24", 12f)
                } else {
                    seekMusicVol.progress = normalMusicVolume
                    setSystemMusicVolume(normalMusicVolume)
                    background = makeRoundedBg("#1E293B", "#475569", 12f)
                }
            }
        }

        volRow.addView(seekMusicVol)
        volRow.addView(btnDucking)
        volCard.addView(volRow)
        root.addView(volCard)
        root.addView(spacer(10))

        // === 5. 6-BAND MUSIC PARAMETRIC EQ ===
        val eqCard = createCardPanel().apply { setPadding(14, 12, 14, 12) }
        eqCard.addView(TextView(this).apply {
            text = "6-BAND MUSIC PARAMETRIC EQ (GESER TITIK KURVA)"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(4, 0, 0, 8)
        })

        eqView = ParametricEqView(this, defaultFreqs, currentGains) { bandIdx, gainDb ->
            currentGains[bandIdx] = gainDb
            applyBandGain(bandIdx, gainDb)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 240)
        }
        eqCard.addView(eqView)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
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
                    val target = if (label == "CUSTOM") loadCustomPreset() else values
                    for (i in 0..5) {
                        currentGains[i] = target[i]
                        applyBandGain(i, target[i])
                    }
                    eqView.invalidate()
                    Toast.makeText(context, "EQ Mode: $label", Toast.LENGTH_SHORT).show()
                }
            }
            presetRow.addView(btn)
        }
        eqCard.addView(presetRow)
        root.addView(eqCard)
        root.addView(spacer(10))

        // === 6. INTERACTIVE SOUNDBOARD (8 PAD) ===
        val sbdCard = createCardPanel().apply { setPadding(12, 12, 12, 12) }
        sbdCard.addView(TextView(this).apply {
            text = "INTERACTIVE LIVE SOUNDBOARD"
            textSize = 9.5f
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
        for (i in 0..5) {
            currentGains[i] = initPreset[i]
            applyBandGain(i, initPreset[i])
        }
        eqView.invalidate()
        setupVisualizer()
    }

    private fun setupAudioDspSession0() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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

    private fun applyBandGain(bandIdx: Int, gainDb: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                for (ch in 0..1) {
                    val band = dynamicsProcessing!!.getPreEqBandByChannelIndex(ch, bandIdx)
                    band.cutoffFrequency = defaultFreqs[bandIdx]
                    band.gain = gainDb
                    dynamicsProcessing!!.setPreEqBandByChannelIndex(ch, bandIdx, band)
                }
            } else if (fallbackEqualizer != null) {
                val numBands = fallbackEqualizer!!.numberOfBands.toInt()
                if (bandIdx < numBands) {
                    fallbackEqualizer!!.setBandLevel(bandIdx.toShort(), (gainDb * 100).toInt().toShort())
                }
            }
        } catch (_: Exception) {}
    }

    private fun setupVisualizer() {
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                        if (waveform == null || waveform.isEmpty()) return
                        var maxAmp = 0
                        for (b in waveform) {
                            val amp = abs((b.toInt() and 0xFF) - 128)
                            if (amp > maxAmp) maxAmp = amp
                        }
                        vuMeter.setLevel(maxAmp / 128f)
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (_: Exception) {}
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
        for ((key, file) in files) {
            try {
                val fd = assets.openFd(file)
                soundMap[key] = soundPool.load(fd, 1)
            } catch (_: IOException) {}
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

        override fun onTouchEvent(event: MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            val padX = 52f
            val usableW = width - padX * 2
            val stepX = usableW / 5f
            val midY = height * 0.48f
            val maxAmpY = height * 0.36f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    var bestDist = 90f
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

            // Garis grid vertikal & horizontal
            for (i in 0..5) {
                val x = padX + i * stepX
                canvas.drawLine(x, 16f, x, height - 34f, gridPaint)
            }
            canvas.drawLine(padX, midY, width - padX, midY, zeroLinePaint)

            // Hitung koordinat titik & gambar kurva halus
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
            canvas.drawPath(path, curvePaint)

            // Gambar node lingkaran & label frekuensi
            val freqLabels = arrayOf("65Hz", "150Hz", "380Hz", "2.2kHz", "5.5kHz", "10kHz")
            for (i in 0..5) {
                val r = if (i == selectedBand) 18f else 13f
                canvas.drawCircle(pts[i].x, pts[i].y, r, nodeFillPaint)
                canvas.drawCircle(pts[i].x, pts[i].y, r, nodeStrokePaint)
                canvas.drawText(freqLabels[i], pts[i].x, height - 8f, textPaint)
            }
        }
    }

    // === STEREO LED VU METER ===
    class StereoVuMeterView(context: Context) : View(context) {
        private var level = 0.25f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun setLevel(l: Float) {
            level = (l * 1.4f).coerceIn(0.05f, 1f)
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val segments = 26
            val gap = 4f
            val segW = (width - gap * (segments - 1)) / segments
            val activeCount = (level * segments).toInt()
            val rowH = (height - 4f) / 2f

            for (row in 0..1) {
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