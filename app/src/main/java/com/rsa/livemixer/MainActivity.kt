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
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayInputStream
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
    private val currentGains = floatArrayOf(6.5f, 4.0f, -1.5f, -3.0f, 3.5f, 5.0f)

    private var toneBass = 0f
    private var toneMid = 0f
    private var toneTreble = 0f

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val key = pendingPadKeyForPicker
        if (uri != null && key != null) {
            importCustomSoundToPad(key, uri)
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun makeConsoleButton(
        label: String,
        fillHex: String,
        strokeHex: String,
        textColorHex: String = "#FFFFFF",
        textSp: Float = 11f
    ): Button = Button(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(textColorHex))
        background = makeRoundedBg(fillHex, strokeHex, dp(8).toFloat())
        gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0)
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        maxLines = 1
        isAllCaps = false
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        restoreLastSessionState()

        setupAudioDspWithLimiter()
        setupSoundboard()

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0E14"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(16))
        }

        // === 1. TOP HEADER BAR ===
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(8))
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(TextView(this).apply {
            text = "OHO AUDIO MIXER"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F8FAFC"))
            letterSpacing = 0.04f
        })
        titleBox.addView(TextView(this).apply {
            text = "PRO KARAOKE & MASTERING DECK"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(Color.parseColor("#38BDF8"))
            letterSpacing = 0.06f
        })

        btnEqToggle = makeConsoleButton("EQ ON", "#10B981", "#34D399", "#FFFFFF", 11f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(36)).apply { marginEnd = dp(6) }
            setOnClickListener {
                isEqEnabled = !isEqEnabled
                applyDspEnabled(isEqEnabled)
                text = if (isEqEnabled) "EQ ON" else "BYPASS"
                background = if (isEqEnabled) makeRoundedBg("#10B981", "#34D399", dp(8).toFloat())
                else makeRoundedBg("#334155", "#64748B", dp(8).toFloat())
            }
        }

        val btnSave = makeConsoleButton("SAVE", "#1E293B", "#475569", "#E2E8F0", 11f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(36))
            setOnClickListener {
                saveCustomPreset()
                saveLastSessionState()
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
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        vuBarRow.addView(TextView(this).apply {
            text = "STEREO VU"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, dp(8), 0)
        })
        vuMeter = StereoVuMeterView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(14), 1f)
        }
        vuBarRow.addView(vuMeter)
        root.addView(vuBarRow)

        // === 3. WIDESCREEN YOUTUBE KARAOKE PLAYER (AD-FREE SHIELD) ===
        val ytCard = createCardPanel().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val ytBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }

        ytBar.addView(TextView(this).apply {
            text = "▶ YOUTUBE (NO ADS)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F87171"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val btnSearchYt = makeConsoleButton("🔍 CARI LAGU", "#DC2626", "#F87171", "#FFFFFF", 10.5f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                ytWebView.loadUrl("https://m.youtube.com/results?search_query=karaoke+campursari+dangdut+lirik")
            }
        }

        val btnResizeYt = makeConsoleButton("PERBESAR / LIPAT", "#1E293B", "#475569", "#E2E8F0", 10f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
        }

        ytBar.addView(btnSearchYt)
        ytBar.addView(btnResizeYt)
        ytCard.addView(ytBar)

        ytWebView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(255))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            webViewClient = object : WebViewClient() {
                private val adHosts = listOf(
                    "doubleclick.net",
                    "googlesyndication.com",
                    "googleadservices.com",
                    "adservice.google",
                    "pagead2.googlesyndication.com",
                    "ads.youtube.com"
                )

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString()?.lowercase() ?: return super.shouldInterceptRequest(view, request)
                    val isAdUrl = adHosts.any { url.contains(it) } ||
                            url.contains("/pagead/") ||
                            url.contains("/api/stats/ads") ||
                            url.contains("/ptracking")
                    return if (isAdUrl) {
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    } else {
                        super.shouldInterceptRequest(view, request)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val adBlockJs = """
                        (function() {
                            if (window.__ohoAdShieldActive) return;
                            window.__ohoAdShieldActive = true;
                            let wasAd = false;
                            let prevMuted = false;
                            const selectors = [
                                'ytm-promoted-sparkles-web-renderer',
                                'ytm-companion-slot-renderer',
                                'ad-slot-renderer',
                                'ytm-statement-banner-renderer',
                                '.ad-container',
                                '.ytp-ad-overlay-container',
                                '.ytp-ad-message-container'
                            ];
                            setInterval(function() {
                                selectors.forEach(function(sel) {
                                    document.querySelectorAll(sel).forEach(function(el) {
                                        el.style.display = 'none';
                                    });
                                });
                                const player = document.querySelector('.html5-video-player');
                                const video = document.querySelector('video');
                                const isAd = player && (
                                    player.classList.contains('ad-showing') ||
                                    player.classList.contains('ad-interrupting')
                                );
                                if (isAd && video) {
                                    if (!wasAd) {
                                        prevMuted = video.muted;
                                        wasAd = true;
                                    }
                                    video.muted = true;
                                    if (isFinite(video.duration) && video.duration > 0) {
                                        video.currentTime = video.duration;
                                    }
                                    const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                                    if (skipBtn) skipBtn.click();
                                } else if (!isAd && wasAd && video) {
                                    video.muted = prevMuted;
                                    wasAd = false;
                                }
                            }, 300);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(adBlockJs, null)
                }
            }

            webChromeClient = WebChromeClient()
            loadUrl("https://m.youtube.com")

            setOnTouchListener { v, _ ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        ytCard.addView(ytWebView)

        btnResizeYt.setOnClickListener {
            ytSizeMode = (ytSizeMode + 1) % 3
            val lp = ytWebView.layoutParams
            when (ytSizeMode) {
                0 -> {
                    ytWebView.visibility = View.VISIBLE
                    lp.height = dp(255)
                    btnResizeYt.text = "PERBESAR / LIPAT"
                }
                1 -> {
                    ytWebView.visibility = View.VISIBLE
                    lp.height = dp(385)
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
        root.addView(spacer(dp(8)))

        // === 4. MUSIC VOL FADER & SMOOTH DUCKING ===
        val volCard = createCardPanel().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val volRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        volRow.addView(TextView(this).apply {
            text = "MUSIC VOL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#CBD5E1"))
        })

        seekMusicVol = SeekBar(this).apply {
            max = 100
            progress = normalMusicVolume
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
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

        btnDucking = makeConsoleButton("DUCKING -50%", "#1E293B", "#475569", "#FFFFFF", 10f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
            setOnClickListener {
                isDucking = !isDucking
                val startVol = seekMusicVol.progress
                val targetVol = if (isDucking) (normalMusicVolume * 0.45f).toInt().coerceAtLeast(10) else normalMusicVolume
                animateVolumeFade(startVol, targetVol)
                background = if (isDucking) makeRoundedBg("#D97706", "#FBBF24", dp(8).toFloat())
                else makeRoundedBg("#1E293B", "#475569", dp(8).toFloat())
            }
        }

        volRow.addView(seekMusicVol)
        volRow.addView(btnDucking)
        volCard.addView(volRow)
        root.addView(volCard)
        root.addView(spacer(dp(8)))

        // === 5. 6-BAND MUSIC PARAMETRIC EQ + TONE LOCK ===
        val eqCard = createCardPanel().apply { setPadding(dp(10), dp(8), dp(10), dp(10)) }

        val eqHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }

        eqHeaderRow.addView(TextView(this).apply {
            text = "6-BAND PARAMETRIC EQ & TONE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        btnLockTone = makeConsoleButton(
            if (isToneLocked) "🔒 TERKUNCI" else "🔓 UNLOCKED",
            if (isToneLocked) "#B91C1C" else "#1E293B",
            if (isToneLocked) "#F87171" else "#38BDF8",
            "#FFFFFF",
            10f
        ).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
            setOnClickListener {
                isToneLocked = !isToneLocked
                applyLockUiState()
                saveLastSessionState()
                if (isToneLocked) {
                    Toast.makeText(context, "EQ & Knob dikunci aman saat scroll", Toast.LENGTH_SHORT).show()
                }
            }
        }
        eqHeaderRow.addView(btnLockTone)
        eqCard.addView(eqHeaderRow)

        eqView = ParametricEqView(this, defaultFreqs, currentGains) { bandIdx, gainDb ->
            currentGains[bandIdx] = gainDb
            applySingleBandCalculated(bandIdx)
            saveLastSessionState()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(125))
        }
        eqCard.addView(eqView)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val presets = listOf(
            "DANGDUT" to floatArrayOf(6.5f, 4.0f, -1.5f, -3.0f, 3.5f, 5.0f),
            "KARAOKE" to floatArrayOf(4.0f, 2.5f, -2.0f, -4.5f, 3.0f, 4.0f),
            "CAMPURSARI" to floatArrayOf(5.0f, 3.5f, 0.0f, -2.5f, 4.0f, 4.5f),
            "CUSTOM" to loadCustomPreset()
        )
        presets.forEachIndexed { idx, (label, values) ->
            val btn = makeConsoleButton(label, "#1E293B", "#38BDF8", "#F1F5F9", 10f).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                    if (idx < presets.size - 1) marginEnd = dp(6)
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
                    saveLastSessionState()
                    Toast.makeText(context, "EQ Mode: $label", Toast.LENGTH_SHORT).show()
                }
            }
            presetRow.addView(btn)
        }
        eqCard.addView(presetRow)

        val toneContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedBg("#181F2F", "#1E293B", dp(10).toFloat())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        toneContainer.addView(TextView(this).apply {
            text = "QUICK TONE TUNING (BASS • MIDDLE • TREBLE)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        })

        val knobsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        knobBass = RotaryKnobView(this, "MASTER BASS", -12f, 12f, toneBass, "dB", "#F59E0B") { value ->
            toneBass = value
            applyAllBands()
            saveLastSessionState()
        }
        knobMid = RotaryKnobView(this, "MASTER MID", -12f, 12f, toneMid, "dB", "#10B981") { value ->
            toneMid = value
            applyAllBands()
            saveLastSessionState()
        }
        knobTreble = RotaryKnobView(this, "MASTER TREBLE", -12f, 12f, toneTreble, "dB", "#38BDF8") { value ->
            toneTreble = value
            applyAllBands()
            saveLastSessionState()
        }

        val knobLayout = LinearLayout.LayoutParams(0, dp(96), 1f)
        knobsRow.addView(knobBass, knobLayout)
        knobsRow.addView(knobMid, knobLayout)
        knobsRow.addView(knobTreble, knobLayout)
        toneContainer.addView(knobsRow)

        eqCard.addView(toneContainer)
        root.addView(eqCard)
        root.addView(spacer(dp(8)))

        // === 6. INTERACTIVE SOUNDBOARD (8 PAD + LONG PRESS GANTI MP3) ===
        val sbdCard = createCardPanel().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        sbdCard.addView(TextView(this).apply {
            text = "SOUNDBOARD (TEKAN TAHAN PAD UNTUK GANTI FILE MP3)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(dp(2), 0, 0, dp(6))
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
            val btn = makeConsoleButton(
                label,
                if (isStop) "#3B1219" else "#161E2E",
                neonHex,
                if (isStop) "#FCA5A5" else "#FFFFFF",
                10.5f
            ).apply {
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
                height = dp(46)
                columnSpec = GridLayout.spec(idx % 4, 1f)
                rowSpec = GridLayout.spec(idx / 4, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            sbdGrid.addView(btn, gp)
        }
        sbdCard.addView(sbdGrid)
        root.addView(sbdCard)

        rootScroll.addView(root)
        setContentView(rootScroll)

        applyAllBands()
        applyLockUiState()

        ensureVisualizerPermissionAndStart()
    }

    private fun applyLockUiState() {
        eqView.isLocked = isToneLocked
        knobBass.isLocked = isToneLocked
        knobMid.isLocked = isToneLocked
        knobTreble.isLocked = isToneLocked

        if (isToneLocked) {
            btnLockTone.text = "🔒 TERKUNCI"
            btnLockTone.background = makeRoundedBg("#B91C1C", "#F87171", dp(8).toFloat())
        } else {
            btnLockTone.text = "🔓 UNLOCKED"
            btnLockTone.background = makeRoundedBg("#1E293B", "#38BDF8", dp(8).toFloat())
        }
        eqView.invalidate()
        knobBass.invalidate()
        knobMid.invalidate()
        knobTreble.invalidate()
    }

    private fun saveLastSessionState() {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE).edit()
        for (i in 0..5) prefs.putFloat("last_band_$i", currentGains[i])
        prefs.putFloat("last_tone_bass", toneBass)
        prefs.putFloat("last_tone_mid", toneMid)
        prefs.putFloat("last_tone_treble", toneTreble)
        prefs.putBoolean("last_tone_locked", isToneLocked)
        prefs.apply()
    }

    private fun restoreLastSessionState() {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
        val defaultDangdut = floatArrayOf(6.5f, 4.0f, -1.5f, -3.0f, 3.5f, 5.0f)
        for (i in 0..5) {
            currentGains[i] = prefs.getFloat("last_band_$i", defaultDangdut[i])
        }
        toneBass = prefs.getFloat("last_tone_bass", 0f)
        toneMid = prefs.getFloat("last_tone_mid", 0f)
        toneTreble = prefs.getFloat("last_tone_treble", 0f)
        isToneLocked = prefs.getBoolean("last_tone_locked", false)
    }

    override fun onPause() {
        saveLastSessionState()
        super.onPause()
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
        background = makeRoundedBg("#121722", "#263248", dp(10).toFloat())
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
        private val d = context.resources.displayMetrics.density

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B"); strokeWidth = 1f * d
        }
        private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155"); strokeWidth = 1.5f * d
        }
        private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.8f * d; color = Color.parseColor("#22D3EE")
        }
        private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.parseColor("#0F172A")
        }
        private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f * d; color = Color.parseColor("#38BDF8")
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8"); textSize = 10f * d; textAlign = Paint.Align.CENTER
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isLocked) return false

            parent?.requestDisallowInterceptTouchEvent(true)
            val padX = 24f * d
            val usableW = width - padX * 2
            val stepX = usableW / 5f
            val midY = height * 0.45f
            val maxAmpY = height * 0.35f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    var bestDist = 36f * d
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
            val padX = 24f * d
            val usableW = width - padX * 2
            val stepX = usableW / 5f
            val midY = height * 0.45f
            val maxAmpY = height * 0.35f

            for (i in 0..5) {
                val x = padX + i * stepX
                canvas.drawLine(x, 8f * d, x, height - 18f * d, gridPaint)
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
                val r = if (i == selectedBand) 8f * d else 6f * d
                nodeStrokePaint.color = if (isLocked) Color.parseColor("#94A3B8") else Color.parseColor("#38BDF8")
                canvas.drawCircle(pts[i].x, pts[i].y, r, nodeFillPaint)
                canvas.drawCircle(pts[i].x, pts[i].y, r, nodeStrokePaint)
                canvas.drawText(freqLabels[i], pts[i].x, height - 4f * d, textPaint)
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
        private val d = context.resources.displayMetrics.density

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f * d; strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#242D3D")
        }
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f * d; strokeCap = Paint.Cap.ROUND
            color = accentColor
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.parseColor("#141A25")
        }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f * d; color = Color.parseColor("#3B4861")
        }
        private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.2f * d; strokeCap = Paint.Cap.ROUND
            color = accentColor
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8"); textSize = 8.5f * d
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 10f * d
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }

        fun setValue(v: Float) {
            currentVal = v.coerceIn(minVal, maxVal)
            onChange(currentVal)
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isLocked) return false

            parent?.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastY = event.y; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = lastY - event.y
                    lastY = event.y
                    setValue(currentVal + (dy / (90f * d)) * (maxVal - minVal))
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height * 0.36f
            val radius = min(width, height) * 0.23f
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
            canvas.drawText(formatted, cx, cy + radius + 13f * d, valPaint)
            canvas.drawText(label, cx, cy + radius + 24f * d, labelPaint)
        }
    }

    // === STEREO LED VU METER (L & R) ===
    class StereoVuMeterView(context: Context) : View(context) {
        private var levelL = 0.05f
        private var levelR = 0.05f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = context.resources.displayMetrics.density

        fun setStereoLevels(l: Float, r: Float) {
            levelL = l.coerceIn(0.04f, 1f)
            levelR = r.coerceIn(0.04f, 1f)
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val segments = 26
            val gap = 2f * d
            val segW = (width - gap * (segments - 1)) / segments
            val rowH = (height - 2f * d) / 2f

            val levels = floatArrayOf(levelL, levelR)
            for (row in 0..1) {
                val activeCount = (levels[row] * segments).toInt()
                val top = row * (rowH + 2f * d)
                for (i in 0 until segments) {
                    val left = i * (segW + gap)
                    val isLit = i < activeCount
                    paint.color = when {
                        i >= 22 -> if (isLit) Color.parseColor("#EF4444") else Color.parseColor("#3B181E")
                        i >= 17 -> if (isLit) Color.parseColor("#F59E0B") else Color.parseColor("#3B2E16")
                        else -> if (isLit) Color.parseColor("#10B981") else Color.parseColor("#14332B")
                    }
                    canvas.drawRoundRect(left, top, left + segW, top + rowH, 1.5f * d, 1.5f * d, paint)
                }
            }
        }
    }
}