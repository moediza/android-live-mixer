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
import android.media.AudioDeviceInfo
import android.media.AudioDeviceCallback
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    // Status Sistem & DSP
    private var isEqEnabled = true
    private var isToneLocked = false
    private var isDucking = false
    private var isMasterMuted = false
    private var isYtMuted = false
    private var isYtLooping = false
    private var isSubBassActive = false
    // Subwoofer Engine: stereo virtual-bass is available on every output.
    // A detected multichannel endpoint is reported, but this legacy session-0
    // DynamicsProcessing chain cannot yet route an independent physical SUB channel.
    private var isSubwooferEngineEnabled = true
    private var subCrossoverHz = 80f
    private var subLevelDb = 3.0f
    private var deepBassDb = 2.0f
    private var activeOutputDescription = "Mendeteksi perangkat output..."
    private var multichannelOutputDetected = false
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var isClarityActive = false
    private var isReverbActive = false
    private var displayDeckMode = 0 // 0 = YOUTUBE FULL, 1 = RTA DSP
    private var channelRouteMode = 0 // 0 = STEREO L+R, 1 = MONO L+R, 2 = KIRI L ONLY, 3 = KANAN R ONLY
    private var activeDlmsTab = 0 // 0 = EQUALIZER, 1 = DYNAMIC, 2 = CROSSOVER, 3 = TONE & EFEK
    private var currentBank = 0

    // Parameter Gain, Volume & Pitch
    private var boostLeftDb = 0
    private var boostRightDb = 0
    private var normalMusicVolume = 85
    private var currentMusicVolume = 85
    private var sfxVolume = 100
    private var bgmVolume = 45
    private var pitchSteps = 0 // Rentang -5 s/d +5

    // Parameter Dynamic 5-Band (SUB, LOW, MID, HIGH, AIR)
    private var selectedDynBand = 0
    private val dynBandNames = arrayOf("SUB", "LOW", "MID", "HIGH", "AIR")
    private val dynCutoffs = floatArrayOf(120f, 450f, 2200f, 7500f, 20000f)
    private val dynThresholds = floatArrayOf(-18f, -18f, -18f, -18f, -18f)
    private val dynRatios = floatArrayOf(2.0f, 2.0f, 2.0f, 2.0f, 2.0f)
    private val dynPostGains = floatArrayOf(0f, 0f, 0f, 0f, 0f)

    // Parameter Crossover & Limiter
    private var hpfCutoffHz = 30f
    private var lpfCutoffHz = 18000f
    private var limiterThreshDb = -1.5f

    // Live Timer
    private var isTimerRunning = false
    private var timerBaseElapsed = 0L
    private var timerAccumulatedMs = 0L
    private val timerHandler = Handler(Looper.getMainLooper())

    // Komponen UI Utama
    private lateinit var rootScroll: ScrollView
    private lateinit var tvLiveTimer: TextView
    private lateinit var btnTimerToggle: Button
    private lateinit var vuMeterView: StereoLedVuView
    private lateinit var rtaView: RtaSpectrumView
    private lateinit var seekBoostL: SeekBar
    private lateinit var seekBoostR: SeekBar
    private lateinit var tvBoostLVal: TextView
    private lateinit var tvBoostRVal: TextView
    private lateinit var btnRouteMode: Button

    // Display Deck (YOUTUBE FULL & RTA DSP)
    private lateinit var ytCard: LinearLayout
    private lateinit var deckStageFrame: FrameLayout
    private lateinit var ytWebView: WebView
    private lateinit var btnModeYtFull: Button
    private lateinit var btnModeRtaDsp: Button

    private lateinit var eqView: GraphicEq10BandView
    private lateinit var dynView: Dynamic5BandView

    // Tab Containers
    private lateinit var tabBtnEq: Button
    private lateinit var tabBtnDyn: Button
    private lateinit var tabBtnXover: Button
    private lateinit var tabBtnFx: Button
    private lateinit var panelTabEq: LinearLayout
    private lateinit var panelTabDyn: LinearLayout
    private lateinit var panelTabXover: LinearLayout
    private lateinit var panelTabFx: LinearLayout

    // Kontrol Tab Dynamic
    private val dynSelectBtns = ArrayList<Button>()
    private lateinit var tvDynThresh: TextView
    private lateinit var tvDynRatio: TextView
    private lateinit var tvDynGain: TextView
    private lateinit var btnSubBass: Button
    private lateinit var btnClarity: Button

    // Kontrol Tab Crossover & Efek
    private lateinit var tvHpfVal: TextView
    private lateinit var tvLpfVal: TextView
    private lateinit var tvLimVal: TextView
    private lateinit var tvSubwooferRoute: TextView
    private lateinit var btnSubwooferEngine: Button
    private lateinit var tvSubCrossover: TextView
    private lateinit var tvSubLevel: TextView
    private lateinit var tvDeepBass: TextView
    private lateinit var btnReverbToggle: Button

    // Volume & Transport
    private lateinit var seekMusicVol: SeekBar
    private lateinit var tvMusicVolPct: TextView
    private lateinit var seekSfxVol: SeekBar
    private lateinit var tvSfxVolPct: TextView
    private lateinit var seekBgmVol: SeekBar
    private lateinit var tvBgmVolPct: TextView

    private lateinit var btnDucking: Button
    private lateinit var btnMuteAll: Button
    private lateinit var btnMuteYt: Button
    private lateinit var btnEqToggle: Button
    private lateinit var btnLockTone: Button
    private lateinit var btnKeyDisplay: Button
    private lateinit var btnYtLoop: Button
    private lateinit var btnBankA: Button
    private lateinit var btnBankB: Button
    private lateinit var btnBgmPlayStop: Button
    private lateinit var tvBgmTitle: TextView

    private lateinit var knobBass: RotaryKnobView
    private lateinit var knobMid: RotaryKnobView
    private lateinit var knobTreble: RotaryKnobView

    // Soundboard 16 Slot
    private lateinit var soundPool: SoundPool
    private val soundMap = HashMap<String, Int>()
    private val activeStreams = ArrayList<Int>()
    private val padButtons = ArrayList<Button>()
    private var pendingPadSlotForPicker: String? = null

    private val defaultBankALabels = arrayOf("KETAWA", "APPLAUSE", "LAWAN", "GENDANG", "JANGKRIK", "AIRHORN", "INTRO", "CIHUY")
    private val defaultBankAAssets = arrayOf("ketawa.mp3", "applause.mp3", "lawan.mp3", "gendang.mp3", "jangkrik.mp3", "airhorn.mp3", "intro.mp3", "lawan.mp3")
    private val defaultBankBLabels = arrayOf("DRUM ROLL", "TROMPET", "WADUH", "SIRINE", "HORAY", "SUSPENSE", "CLOSING", "EXTRA")
    private val defaultBankBAssets = arrayOf("gendang.mp3", "airhorn.mp3", "jangkrik.mp3", "airhorn.mp3", "applause.mp3", "intro.mp3", "intro.mp3", "ketawa.mp3")

    // BGM Ngobrol
    private var bgmPlayer: MediaPlayer? = null
    private var isBgmPlaying = false

    // Audio DSP
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var fallbackEqualizer: Equalizer? = null
    private var presetReverb: PresetReverb? = null
    private var visualizer: Visualizer? = null

    // 10-Band EQ dengan Resolusi Bass Rapat (Gaya Poweramp)
    private val defaultFreqs10 = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val postEqFreqs = floatArrayOf(45f, 75f, 120f, 350f)
    private val freqLabels10 = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    private val currentGains10 = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    private var toneBass = 0f
    private var toneMid = 0f
    private var toneTreble = 0f

    private val pickSfxLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val slot = pendingPadSlotForPicker
        if (uri != null && slot != null) importCustomSoundToPad(slot, uri)
    }

    private val pickBgmLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) importCustomBgmFile(uri)
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                val totalMs = timerAccumulatedMs + (SystemClock.elapsedRealtime() - timerBaseElapsed)
                updateTimerDisplay(totalMs)
                timerHandler.postDelayed(this, 500)
            }
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun screenHeightPx(): Int = resources.displayMetrics.heightPixels

    private fun makeConsoleButton(
        label: String,
        fillHex: String,
        strokeHex: String,
        textColorHex: String = "#FFFFFF",
        textSp: Float = 9.5f
    ): Button = Button(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(textColorHex))
        background = makeRoundedBg(fillHex, strokeHex, dp(7).toFloat())
        gravity = Gravity.CENTER
        setPadding(dp(4), 0, dp(4), 0)
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
        migrateLegacyAggressiveAudioDefaults()

        setupAudioDspDlms()
        setupSubwooferAutoRouting()
        setupSoundboard16()

        rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0D12"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(8), dp(9), dp(14))
        }

        // === 1. BARIS HEADER & PENGHITUNG DURASI SIARAN (LIVE TIMER) ===
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(5))
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(TextView(this).apply {
            text = "OHO AUDIO DLMS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F8FAFC"))
        })

        val timerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
        }
        tvLiveTimer = TextView(this).apply {
            text = "00:00:00"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#22D3EE"))
            setPadding(0, 0, dp(5), 0)
        }
        btnTimerToggle = makeConsoleButton("START", "#0F172A", "#22D3EE", "#22D3EE", 8f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(20)).apply { marginEnd = dp(3) }
            setOnClickListener {
                if (!isTimerRunning) {
                    isTimerRunning = true
                    timerBaseElapsed = SystemClock.elapsedRealtime()
                    text = "PAUSE"
                    background = makeRoundedBg("#B45309", "#FBBF24", dp(5).toFloat())
                    setTextColor(Color.WHITE)
                    timerHandler.post(timerRunnable)
                } else {
                    isTimerRunning = false
                    timerAccumulatedMs += (SystemClock.elapsedRealtime() - timerBaseElapsed)
                    text = "START"
                    background = makeRoundedBg("#0F172A", "#22D3EE", dp(5).toFloat())
                    setTextColor(Color.parseColor("#22D3EE"))
                }
            }
        }
        val btnTimerReset = makeConsoleButton("RST", "#1E293B", "#475569", "#94A3B8", 8f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(20))
            setOnClickListener {
                isTimerRunning = false
                timerAccumulatedMs = 0L
                timerBaseElapsed = SystemClock.elapsedRealtime()
                updateTimerDisplay(0L)
                btnTimerToggle.text = "START"
                btnTimerToggle.background = makeRoundedBg("#0F172A", "#22D3EE", dp(5).toFloat())
                btnTimerToggle.setTextColor(Color.parseColor("#22D3EE"))
            }
        }
        timerRow.addView(tvLiveTimer)
        timerRow.addView(btnTimerToggle)
        timerRow.addView(btnTimerReset)
        titleBox.addView(timerRow)

        btnEqToggle = makeConsoleButton("DSP ON", "#10B981", "#34D399", "#FFFFFF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(32)).apply { marginEnd = dp(4) }
            setOnClickListener {
                isEqEnabled = !isEqEnabled
                applyDspEnabled(isEqEnabled)
                text = if (isEqEnabled) "DSP ON" else "BYPASS"
                background = if (isEqEnabled) makeRoundedBg("#10B981", "#34D399", dp(7).toFloat())
                else makeRoundedBg("#334155", "#64748B", dp(7).toFloat())
            }
        }

        val btnSave = makeConsoleButton("SAVE", "#1E293B", "#475569", "#E2E8F0", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(32)).apply { marginEnd = dp(4) }
            setOnClickListener {
                saveCustomPreset()
                saveLastSessionState()
                Toast.makeText(context, "Preset DLMS disimpan ke CUSTOM!", Toast.LENGTH_SHORT).show()
            }
        }

        btnMuteAll = makeConsoleButton("MUTE", "#1E293B", "#F59E0B", "#FBBF24", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(32)).apply { marginEnd = dp(4) }
            setOnClickListener {
                isMasterMuted = !isMasterMuted
                if (isMasterMuted) {
                    stopAllSounds()
                    applyYoutubeVolume(0)
                    bgmPlayer?.setVolume(0f, 0f)
                    text = "MUTED"
                    background = makeRoundedBg("#D97706", "#FDE047", dp(7).toFloat())
                    setTextColor(Color.WHITE)
                } else {
                    applyYoutubeVolume(if (isYtMuted) 0 else currentMusicVolume)
                    updateBgmPanAndVolume()
                    text = "MUTE"
                    background = makeRoundedBg("#1E293B", "#F59E0B", dp(7).toFloat())
                    setTextColor(Color.parseColor("#FBBF24"))
                }
            }
        }

        val btnExit = makeConsoleButton("EXIT", "#450A0A", "#EF4444", "#FCA5A5", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(32))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Keluar OHO AUDIO DLMS?")
                    .setMessage("Semua pengaturan EQ, Dynamic, Crossover, dan Volume akan disimpan otomatis.")
                    .setPositiveButton("KELUAR") { _, _ ->
                        saveLastSessionState()
                        stopAllSounds()
                        stopBgmPlayer()
                        finishAndRemoveTask()
                    }
                    .setNegativeButton("BATAL", null)
                    .show()
            }
        }

        header.addView(titleBox)
        header.addView(btnEqToggle)
        header.addView(btnSave)
        header.addView(btnMuteAll)
        header.addView(btnExit)
        root.addView(header)

        // === 2. PANEL MASTER OUTPUT L & R + STEREO LED VU METER + SAKELAR 4-MODE KANAL ===
        val lrCard = createCardPanel().apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val lrRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val lrLeftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        vuMeterView = StereoLedVuView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)).apply {
                bottomMargin = dp(4)
            }
        }
        lrLeftCol.addView(vuMeterView)

        val rowL = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rowL.addView(TextView(this).apply {
            text = "L"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#22D3EE"))
            layoutParams = LinearLayout.LayoutParams(dp(14), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekBoostL = SeekBar(this).apply {
            max = 15
            progress = boostLeftDb
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    boostLeftDb = p
                    tvBoostLVal.text = "+${p}dB"
                    applyChannelBoostAndRouting()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        }
        tvBoostLVal = TextView(this).apply {
            text = "+${boostLeftDb}dB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#34D399"))
            layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        rowL.addView(seekBoostL)
        rowL.addView(tvBoostLVal)

        val rowR = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rowR.addView(TextView(this).apply {
            text = "R"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F59E0B"))
            layoutParams = LinearLayout.LayoutParams(dp(14), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekBoostR = SeekBar(this).apply {
            max = 15
            progress = boostRightDb
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    boostRightDb = p
                    tvBoostRVal.text = "+${p}dB"
                    applyChannelBoostAndRouting()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        }
        tvBoostRVal = TextView(this).apply {
            text = "+${boostRightDb}dB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#34D399"))
            layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        rowR.addView(seekBoostR)
        rowR.addView(tvBoostRVal)

        lrLeftCol.addView(rowL)
        lrLeftCol.addView(rowR)

        btnRouteMode = makeConsoleButton("STEREO\nL + R", "#0F172A", "#22D3EE", "#22D3EE", 8.5f).apply {
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(58)).apply { marginStart = dp(6) }
            setOnClickListener {
                channelRouteMode = (channelRouteMode + 1) % 4
                updateChannelRouteUiAndApply()
                saveLastSessionState()
            }
        }

        lrRow.addView(lrLeftCol)
        lrRow.addView(btnRouteMode)
        lrCard.addView(lrRow)
        root.addView(lrCard)
        root.addView(spacer(dp(6)))

        // === 3. LAYAR UTAMA TERPADU: YOUTUBE FULL (±48% LAYAR) & RTA DSP ===
        ytCard = createCardPanel().apply { setPadding(dp(7), dp(6), dp(7), dp(7)) }

        val deckHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
        }

        btnModeYtFull = makeConsoleButton("YOUTUBE FULL", "#0284C7", "#38BDF8", "#FFFFFF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1.2f).apply { marginEnd = dp(4) }
            setOnClickListener { setDisplayDeckMode(0) }
        }
        btnModeRtaDsp = makeConsoleButton("RTA DSP", "#1E293B", "#475569", "#CBD5E1", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { setDisplayDeckMode(1) }
        }
        val btnSearchYt = makeConsoleButton("🔍 CARI LAGU", "#DC2626", "#F87171", "#FFFFFF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f)
            setOnClickListener {
                if (displayDeckMode != 0) setDisplayDeckMode(0)
                ytWebView.loadUrl("https://m.youtube.com/results?search_query=karaoke+dangdut+campursari+lirik")
            }
        }

        deckHeaderRow.addView(btnModeYtFull)
        deckHeaderRow.addView(btnModeRtaDsp)
        deckHeaderRow.addView(btnSearchYt)
        ytCard.addView(deckHeaderRow)

        val stageHeight = (screenHeightPx() * 0.48f).toInt().coerceIn(dp(320), dp(360))
        deckStageFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, stageHeight)
        }

        // Lapisan Video YouTube Full
        ytWebView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            webViewClient = object : WebViewClient() {
                private val adHosts = listOf(
                    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                    "adservice.google", "pagead2.googlesyndication.com", "ads.youtube.com",
                    "s0.2mdn.net", "static.doubleclick.net", "pubads.g.doubleclick.net",
                    "imasdk.googleapis.com", "securepubads.g.doubleclick.net", "tpc.googlesyndication.com"
                )

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString()?.lowercase() ?: return super.shouldInterceptRequest(view, request)
                    val isAdUrl = adHosts.any { url.contains(it) } ||
                            url.contains("/pagead/") || url.contains("/api/stats/ads") ||
                            url.contains("/ptracking") || url.contains("ad_type=") ||
                            url.contains("adformat=") || url.contains("&adurl=") || url.contains("/get_midroll_info")
                    return if (isAdUrl) WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    else super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    injectEarlyCoreEngine(view)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectEarlyCoreEngine(view)
                    applyYoutubeVolume(if (isMasterMuted || isYtMuted) 0 else currentMusicVolume)
                    applyYoutubePitchStepsCombo(pitchSteps)
                    applyYoutubeChannelMatrix(channelRouteMode)
                    applyYoutubeLoop(isYtLooping)
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl("https://m.youtube.com")

            setOnTouchListener { v, event ->
                v.requestFocus()
                if (event.action == MotionEvent.ACTION_DOWN) {
                    ytWebView.evaluateJavascript("if(typeof window.__ohoUnlockAudioContext==='function'){ window.__ohoUnlockAudioContext(); }", null)
                }
                if (event.action == MotionEvent.ACTION_MOVE) {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            }
        }
        deckStageFrame.addView(ytWebView)

        // Lapisan Grafik RTA 24-Band Modern (Glowing Wave + Gradient Bars + Peak-Hold)
        rtaView = RtaSpectrumView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            setOnClickListener {
                ensureVisualizerPermissionAndStart()
                Toast.makeText(context, "Sensor RTA 24-Band disegarkan", Toast.LENGTH_SHORT).show()
            }
        }
        deckStageFrame.addView(rtaView)

        ytCard.addView(deckStageFrame)

        // Baris Kendali YouTube 1: -10s | PLAY/PAUSE | +10s | MUTE YT
        val ytTransportRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(4))
        }
        val btnRewind = makeConsoleButton("⏪ -10s", "#1E293B", "#475569", "#E2E8F0", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                ytWebView.evaluateJavascript("var v=document.querySelector('video'); if(v){ v.currentTime=Math.max(0,v.currentTime-10); }", null)
            }
        }
        val btnPlayPause = makeConsoleButton("⏯ PLAY/PAUSE", "#0F172A", "#38BDF8", "#38BDF8", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1.3f).apply { marginEnd = dp(4) }
            setOnClickListener {
                ensureVisualizerPermissionAndStart()
                ytWebView.evaluateJavascript(
                    """
                    (function(){
                        if(typeof window.__ohoUnlockAudioContext==='function') window.__ohoUnlockAudioContext();
                        var v=document.querySelector('video');
                        if(v){
                            if(v.paused){
                                window.__ohoUserPaused=false;
                                v.play();
                            } else {
                                window.__ohoUserPaused=true;
                                if(typeof v.__ohoOrigPause==='function') v.__ohoOrigPause.call(v);
                                else v.pause();
                            }
                        }
                    })();
                    """.trimIndent(), null
                )
            }
        }
        val btnForward = makeConsoleButton("+10s ⏩", "#1E293B", "#475569", "#E2E8F0", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                ytWebView.evaluateJavascript("var v=document.querySelector('video'); if(v && isFinite(v.duration)){ v.currentTime=Math.min(v.duration,v.currentTime+10); }", null)
            }
        }
        btnMuteYt = makeConsoleButton("🔇 MUTE YT", "#1E293B", "#EF4444", "#FCA5A5", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1.1f)
            setOnClickListener {
                isYtMuted = !isYtMuted
                if (isYtMuted) {
                    applyYoutubeVolume(0)
                    text = "🔇 YT MUTED"
                    background = makeRoundedBg("#B91C1C", "#FCA5A5", dp(7).toFloat())
                    setTextColor(Color.WHITE)
                } else {
                    applyYoutubeVolume(if (isMasterMuted) 0 else currentMusicVolume)
                    text = "🔇 MUTE YT"
                    background = makeRoundedBg("#1E293B", "#EF4444", dp(7).toFloat())
                    setTextColor(Color.parseColor("#FCA5A5"))
                }
            }
        }
        ytTransportRow1.addView(btnRewind)
        ytTransportRow1.addView(btnPlayPause)
        ytTransportRow1.addView(btnForward)
        ytTransportRow1.addView(btnMuteYt)
        ytCard.addView(ytTransportRow1)

        // Baris Kendali YouTube 2: KEY - | NADA: 0 (-5 s/d +5 Mode Kombinasi Otomatis) | KEY + | LOOP
        val ytTransportRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnKeyDown = makeConsoleButton("♭ KEY -", "#1E293B", "#A855F7", "#E9D5FF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { if (pitchSteps > -5) { pitchSteps--; updatePitchUiAndApply() } }
        }
        btnKeyDisplay = makeConsoleButton("NADA: 0", "#161E2E", "#64748B", "#F8FAFC", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1.1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                pitchSteps = 0
                updatePitchUiAndApply()
                Toast.makeText(context, "Nada dikembalikan ke asli (0)", Toast.LENGTH_SHORT).show()
            }
        }
        val btnKeyUp = makeConsoleButton("♯ KEY +", "#1E293B", "#A855F7", "#E9D5FF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { if (pitchSteps < 5) { pitchSteps++; updatePitchUiAndApply() } }
        }
        btnYtLoop = makeConsoleButton("🔁 LOOP", "#1E293B", "#475569", "#94A3B8", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
            setOnClickListener {
                isYtLooping = !isYtLooping
                applyYoutubeLoop(isYtLooping)
                if (isYtLooping) {
                    background = makeRoundedBg("#065F46", "#34D399", dp(7).toFloat())
                    setTextColor(Color.WHITE)
                } else {
                    background = makeRoundedBg("#1E293B", "#475569", dp(7).toFloat())
                    setTextColor(Color.parseColor("#94A3B8"))
                }
            }
        }
        ytTransportRow2.addView(btnKeyDown)
        ytTransportRow2.addView(btnKeyDisplay)
        ytTransportRow2.addView(btnKeyUp)
        ytTransportRow2.addView(btnYtLoop)
        ytCard.addView(ytTransportRow2)

        root.addView(ytCard)
        root.addView(spacer(dp(6)))

        // === 4. PANEL DUAL VOLUME FADER & SMART DUCKING ===
        val volCard = createCardPanel().apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }

        val musicVolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        musicVolRow.addView(TextView(this).apply {
            text = "MUSIC VOL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekMusicVol = SeekBar(this).apply {
            max = 100
            progress = normalMusicVolume
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    currentMusicVolume = p
                    if (fromUser && !isDucking) normalMusicVolume = p
                    tvMusicVolPct.text = "$p%"
                    if (!isMasterMuted && !isYtMuted) applyYoutubeVolume(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        }
        tvMusicVolPct = TextView(this).apply {
            text = "$normalMusicVolume%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
        }
        btnDucking = makeConsoleButton("DUCKING", "#1E293B", "#475569", "#FFFFFF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(74), dp(28))
            setOnClickListener {
                isDucking = !isDucking
                val startVol = seekMusicVol.progress
                val targetVol = if (isDucking) (normalMusicVolume * 0.40f).toInt().coerceAtLeast(10) else normalMusicVolume
                animateMusicVolumeFade(startVol, targetVol)
                background = if (isDucking) makeRoundedBg("#D97706", "#FBBF24", dp(7).toFloat())
                else makeRoundedBg("#1E293B", "#475569", dp(7).toFloat())
            }
        }
        musicVolRow.addView(seekMusicVol)
        musicVolRow.addView(tvMusicVolPct)
        musicVolRow.addView(btnDucking)
        volCard.addView(musicVolRow)

        val sfxVolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sfxVolRow.addView(TextView(this).apply {
            text = "SFX VOL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F59E0B"))
            layoutParams = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekSfxVol = SeekBar(this).apply {
            max = 100
            progress = sfxVolume
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    sfxVolume = p
                    tvSfxVolPct.text = "$p%"
                    updateActiveSfxPanAndVolume()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        }
        tvSfxVolPct = TextView(this).apply {
            text = "$sfxVolume%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
        }
        sfxVolRow.addView(seekSfxVol)
        sfxVolRow.addView(tvSfxVolPct)
        sfxVolRow.addView(TextView(this).apply {
            text = "INDEPENDENT"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(74), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        volCard.addView(sfxVolRow)

        root.addView(volCard)
        root.addView(spacer(dp(6)))

        // === 5. KONSOL MANAJEMEN SUARA DLMS 4-TAB ===
        val dlmsMainCard = createCardPanel().apply { setPadding(dp(8), dp(7), dp(8), dp(8)) }

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }
        tabBtnEq = makeConsoleButton("EQUALIZER", "#0284C7", "#38BDF8", "#FFFFFF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { switchDlmsTab(0) }
        }
        tabBtnDyn = makeConsoleButton("DYNAMIC", "#1E293B", "#475569", "#94A3B8", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { switchDlmsTab(1) }
        }
        tabBtnXover = makeConsoleButton("CROSSOVER", "#1E293B", "#475569", "#94A3B8", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { switchDlmsTab(2) }
        }
        tabBtnFx = makeConsoleButton("TONE & EFEK", "#1E293B", "#475569", "#94A3B8", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
            setOnClickListener { switchDlmsTab(3) }
        }
        tabBar.addView(tabBtnEq)
        tabBar.addView(tabBtnDyn)
        tabBar.addView(tabBtnXover)
        tabBar.addView(tabBtnFx)
        dlmsMainCard.addView(tabBar)

        // --- TAB 0: 10-BAND GRAPHIC EQ ---
        panelTabEq = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val eqTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(4))
        }
        eqTopRow.addView(TextView(this).apply {
            text = "10-BAND GRAPHIC EQ (GESER FADER)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val btnFlatReset = makeConsoleButton("FLAT 0dB", "#1E293B", "#64748B", "#CBD5E1", 8.5f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply {
                marginEnd = dp(4)
            }
            setOnClickListener {
                if (isToneLocked) {
                    Toast.makeText(context, "Buka kunci dulu untuk reset EQ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                for (i in 0..9) currentGains10[i] = 0f
                toneBass = 0f; toneMid = 0f; toneTreble = 0f
                knobBass.setValue(0f); knobMid.setValue(0f); knobTreble.setValue(0f)
                applyAllBands10()
                eqView.invalidate()
                saveLastSessionState()
            }
        }
        btnLockTone = makeConsoleButton(
            if (isToneLocked) "🔒 TERKUNCI" else "🔓 UNLOCK",
            if (isToneLocked) "#B91C1C" else "#1E293B",
            if (isToneLocked) "#F87171" else "#38BDF8",
            "#FFFFFF",
            8.5f
        ).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26))
            setOnClickListener {
                isToneLocked = !isToneLocked
                applyLockUiState()
                saveLastSessionState()
            }
        }
        eqTopRow.addView(btnFlatReset)
        eqTopRow.addView(btnLockTone)
        panelTabEq.addView(eqTopRow)

        eqView = GraphicEq10BandView(this, freqLabels10, currentGains10) { bandIdx, gainDb ->
            currentGains10[bandIdx] = gainDb
            applySingleBandCalculated10(bandIdx)
            applyChannelBoostAndRouting()
            saveLastSessionState()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(158))
        }
        panelTabEq.addView(eqView)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val presets10 = listOf(
            "DANGDUT" to floatArrayOf(2.0f, 2.0f, 1.5f, 0.5f, -0.5f, -1.0f, 0.0f, 1.0f, 1.5f, 1.0f),
            "KARAOKE" to floatArrayOf(1.5f, 1.0f, 0.5f, -0.5f, -1.0f, -0.5f, 0.5f, 1.0f, 1.5f, 1.5f),
            "CAMPURSARI" to floatArrayOf(1.5f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, 0.5f, 1.0f, 1.5f, 1.5f),
            "CUSTOM" to loadCustomPreset()
        )
        presets10.forEachIndexed { idx, (label, values) ->
            val btn = makeConsoleButton(label, "#1E293B", "#38BDF8", "#F1F5F9", 9f).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                    if (idx < presets10.size - 1) marginEnd = dp(4)
                }
                setOnClickListener {
                    if (isToneLocked) {
                        Toast.makeText(context, "Buka kunci dulu untuk ganti preset", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val target = if (label == "CUSTOM") loadCustomPreset() else values
                    for (i in 0..9) currentGains10[i] = target[i]
                    applyAllBands10()
                    eqView.invalidate()
                    saveLastSessionState()
                    Toast.makeText(context, "EQ Mode: $label", Toast.LENGTH_SHORT).show()
                }
            }
            presetRow.addView(btn)
        }
        panelTabEq.addView(presetRow)
        dlmsMainCard.addView(panelTabEq)

        // --- TAB 1: DYNAMIC 5-BAND COMPRESSOR ---
        panelTabDyn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        dynView = Dynamic5BandView(this, dynBandNames, dynThresholds, dynRatios, dynPostGains).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76))
        }
        panelTabDyn.addView(dynView)

        val dynBandBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(6))
        }
        dynSelectBtns.clear()
        for (i in 0..4) {
            val b = makeConsoleButton(dynBandNames[i], "#1E293B", "#475569", "#CBD5E1", 9f).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply {
                    if (i < 4) marginEnd = dp(4)
                }
                setOnClickListener {
                    selectedDynBand = i
                    refreshDynamicTabUi()
                }
            }
            dynSelectBtns.add(b)
            dynBandBtnRow.addView(b)
        }
        panelTabDyn.addView(dynBandBtnRow)

        val dynParamBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = makeRoundedBg("#0E131D", "#1E293B", dp(8).toFloat())
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val colThresh = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        colThresh.addView(TextView(this).apply {
            text = "THRESHOLD"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        })
        tvDynThresh = TextView(this).apply {
            text = "-14.0 dB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F59E0B"))
            gravity = Gravity.CENTER
        }
        val rowThBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        rowThBtns.addView(makeConsoleButton("-", "#1E293B", "#475569", "#FFFFFF", 11f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(24)).apply { marginEnd = dp(4) }
            setOnClickListener {
                dynThresholds[selectedDynBand] = (dynThresholds[selectedDynBand] - 2f).coerceIn(-30f, 0f)
                applyMultiBandDynamics5(); refreshDynamicTabUi(); saveLastSessionState()
            }
        })
        rowThBtns.addView(makeConsoleButton("+", "#1E293B", "#475569", "#FFFFFF", 11f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(24))
            setOnClickListener {
                dynThresholds[selectedDynBand] = (dynThresholds[selectedDynBand] + 2f).coerceIn(-30f, 0f)
                applyMultiBandDynamics5(); refreshDynamicTabUi(); saveLastSessionState()
            }
        })
        colThresh.addView(tvDynThresh)
        colThresh.addView(rowThBtns)

        val colRatio = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        colRatio.addView(TextView(this).apply {
            text = "RATIO"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        })
        tvDynRatio = TextView(this).apply {
            text = "2.8 : 1"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#22D3EE"))
            gravity = Gravity.CENTER
        }
        val rowRaBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        rowRaBtns.addView(makeConsoleButton("-", "#1E293B", "#475569", "#FFFFFF", 11f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(24)).apply { marginEnd = dp(4) }
            setOnClickListener {
                dynRatios[selectedDynBand] = (dynRatios[selectedDynBand] - 0.5f).coerceIn(1.0f, 10.0f)
                applyMultiBandDynamics5(); refreshDynamicTabUi(); saveLastSessionState()
            }
        })
        rowRaBtns.addView(makeConsoleButton("+", "#1E293B", "#475569", "#FFFFFF", 11f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(24))
            setOnClickListener {
                dynRatios[selectedDynBand] = (dynRatios[selectedDynBand] + 0.5f).coerceIn(1.0f, 10.0f)
                applyMultiBandDynamics5(); refreshDynamicTabUi(); saveLastSessionState()
            }
        })
        colRatio.addView(tvDynRatio)
        colRatio.addView(rowRaBtns)

        val colGain = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        colGain.addView(TextView(this).apply {
            text = "MAKE-UP GAIN"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
        })
        tvDynGain = TextView(this).apply {
            text = "+2.5 dB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#34D399"))
            gravity = Gravity.CENTER
        }
        val rowGaBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        rowGaBtns.addView(makeConsoleButton("-", "#1E293B", "#475569", "#FFFFFF", 11f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(24)).apply { marginEnd = dp(4) }
            setOnClickListener {
                dynPostGains[selectedDynBand] = (dynPostGains[selectedDynBand] - 1.0f).coerceIn(-6f, 10f)
                applyMultiBandDynamics5(); refreshDynamicTabUi(); saveLastSessionState()
            }
        })
        rowGaBtns.addView(makeConsoleButton("+", "#1E293B", "#475569", "#FFFFFF", 11f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(24))
            setOnClickListener {
                dynPostGains[selectedDynBand] = (dynPostGains[selectedDynBand] + 1.0f).coerceIn(-6f, 10f)
                applyMultiBandDynamics5(); refreshDynamicTabUi(); saveLastSessionState()
            }
        })
        colGain.addView(tvDynGain)
        colGain.addView(rowGaBtns)

        dynParamBox.addView(colThresh)
        dynParamBox.addView(colRatio)
        dynParamBox.addView(colGain)
        panelTabDyn.addView(dynParamBox)

        val mbcQuickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        btnSubBass = makeConsoleButton("🔊 SUB-BASS PUNCH", "#1E293B", "#F59E0B", "#FDE68A", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(5) }
            setOnClickListener {
                isSubBassActive = !isSubBassActive
                if (isSubBassActive) {
                    // Let bass transients pass more naturally; the dedicated post-EQ
                    // supplies the punch instead of excessive compressor makeup gain.
                    dynThresholds[0] = -12f; dynRatios[0] = 1.5f; dynPostGains[0] = 0.5f
                    dynThresholds[1] = -12f; dynRatios[1] = 1.5f; dynPostGains[1] = 0.5f
                } else {
                    dynThresholds[0] = -18f; dynRatios[0] = 2.0f; dynPostGains[0] = 0.0f
                    dynThresholds[1] = -18f; dynRatios[1] = 2.0f; dynPostGains[1] = 0.0f
                }
                applyBassPunchPostEq(isSubBassActive)
                applyMultiBandDynamics5()
                applyChannelBoostAndRouting()
                refreshDynamicTabUi(); updateMbcButtonsUi(); saveLastSessionState()
            }
        }
        btnClarity = makeConsoleButton("✨ VOCAL CLARITY", "#1E293B", "#22D3EE", "#CFFAFE", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
            setOnClickListener {
                isClarityActive = !isClarityActive
                if (isClarityActive) {
                    dynPostGains[2] = -0.5f; dynPostGains[3] = 1.0f; dynPostGains[4] = 1.0f
                } else {
                    dynPostGains[2] = 0.0f; dynPostGains[3] = 0.0f; dynPostGains[4] = 0.0f
                }
                applyMultiBandDynamics5(); refreshDynamicTabUi(); updateMbcButtonsUi(); saveLastSessionState()
            }
        }
        mbcQuickRow.addView(btnSubBass)
        mbcQuickRow.addView(btnClarity)
        panelTabDyn.addView(mbcQuickRow)
        dlmsMainCard.addView(panelTabDyn)

        // --- TAB 2: CROSSOVER & LIMITER ---
        panelTabXover = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        // --- SUBWOOFER ENGINE (AUTO ROUTING + VIRTUAL STEREO BASS) ---
        btnSubwooferEngine = makeConsoleButton("🔊 SUBWOOFER ENGINE: ON", "#164E63", "#22D3EE", "#CFFAFE", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
            setOnClickListener {
                isSubwooferEngineEnabled = !isSubwooferEngineEnabled
                applyBassPunchPostEq(isSubBassActive)
                applyChannelBoostAndRouting()
                updateSubwooferEngineUi()
                saveLastSessionState()
            }
        }
        panelTabXover.addView(btnSubwooferEngine)

        tvSubwooferRoute = TextView(this).apply {
            text = activeOutputDescription
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(Color.parseColor("#A5F3FC"))
            setPadding(dp(2), dp(6), dp(2), dp(6))
        }
        panelTabXover.addView(tvSubwooferRoute)

        tvSubCrossover = TextView(this).apply {
            text = "FOKUS BASS VIRTUAL: ${subCrossoverHz.toInt()} Hz"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(Color.WHITE)
        }
        panelTabXover.addView(tvSubCrossover)
        panelTabXover.addView(SeekBar(this).apply {
            max = 120
            progress = (subCrossoverHz - 40f).toInt().coerceIn(0, 120)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    subCrossoverHz = 40f + p
                    tvSubCrossover.text = "FOKUS BASS VIRTUAL: ${subCrossoverHz.toInt()} Hz"
                    applyBassPunchPostEq(isSubBassActive)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        })

        tvSubLevel = TextView(this).apply {
            text = "SUB LEVEL VIRTUAL: +${String.format("%.1f", subLevelDb)} dB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, 0)
        }
        panelTabXover.addView(tvSubLevel)
        panelTabXover.addView(SeekBar(this).apply {
            max = 60
            progress = (subLevelDb * 10f).toInt().coerceIn(0, 60)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    subLevelDb = p / 10f
                    tvSubLevel.text = "SUB LEVEL VIRTUAL: +${String.format("%.1f", subLevelDb)} dB"
                    applyBassPunchPostEq(isSubBassActive)
                    applyChannelBoostAndRouting()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        })

        tvDeepBass = TextView(this).apply {
            text = "DEEP BASS: +${String.format("%.1f", deepBassDb)} dB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, 0)
        }
        panelTabXover.addView(tvDeepBass)
        panelTabXover.addView(SeekBar(this).apply {
            max = 40
            progress = (deepBassDb * 10f).toInt().coerceIn(0, 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    deepBassDb = p / 10f
                    tvDeepBass.text = "DEEP BASS: +${String.format("%.1f", deepBassDb)} dB"
                    applyBassPunchPostEq(isSubBassActive)
                    applyChannelBoostAndRouting()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        })

        val hpfHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hpfHeader.addView(TextView(this).apply {
            text = "HIGH-PASS FILTER (POTONG SUB BERLEBIH)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        tvHpfVal = TextView(this).apply {
            text = "${hpfCutoffHz.toInt()} Hz"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
        }
        hpfHeader.addView(tvHpfVal)
        panelTabXover.addView(hpfHeader)

        panelTabXover.addView(SeekBar(this).apply {
            max = 130
            progress = (hpfCutoffHz - 20f).toInt().coerceIn(0, 130)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    hpfCutoffHz = 20f + p
                    tvHpfVal.text = "${hpfCutoffHz.toInt()} Hz"
                    applyAllBands10()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        })

        val lpfHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        lpfHeader.addView(TextView(this).apply {
            text = "LOW-PASS FILTER (REDAM DESIS TINGGI)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F59E0B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        tvLpfVal = TextView(this).apply {
            text = "${lpfCutoffHz.toInt()} Hz"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
        }
        lpfHeader.addView(tvLpfVal)
        panelTabXover.addView(lpfHeader)

        panelTabXover.addView(SeekBar(this).apply {
            max = 140
            progress = ((lpfCutoffHz - 6000f) / 100f).toInt().coerceIn(0, 140)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    lpfCutoffHz = 6000f + p * 100f
                    tvLpfVal.text = "${lpfCutoffHz.toInt()} Hz"
                    applyAllBands10()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        })

        val limHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        limHeader.addView(TextView(this).apply {
            text = "ANTI-CLIP OUTPUT LIMITER THRESHOLD"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#34D399"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        tvLimVal = TextView(this).apply {
            text = "${String.format("%.1f", limiterThreshDb)} dB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
        }
        limHeader.addView(tvLimVal)
        panelTabXover.addView(limHeader)

        panelTabXover.addView(SeekBar(this).apply {
            max = 120
            progress = ((limiterThreshDb + 12f) * 10f).toInt().coerceIn(0, 120)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    limiterThreshDb = -12f + (p / 10f)
                    tvLimVal.text = "${String.format("%.1f", limiterThreshDb)} dB"
                    applyOutputLimiterSettings()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        })
        dlmsMainCard.addView(panelTabXover)

        // --- TAB 3: TONE KNOBS & HALL REVERB ---
        panelTabFx = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val knobsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = makeRoundedBg("#141B29", "#1E293B", dp(8).toFloat())
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        knobBass = RotaryKnobView(this, "MASTER BASS", -12f, 12f, toneBass, "dB", "#F59E0B") { value ->
            toneBass = value; applyAllBands10(); saveLastSessionState()
        }
        knobMid = RotaryKnobView(this, "MASTER MID", -12f, 12f, toneMid, "dB", "#10B981") { value ->
            toneMid = value; applyAllBands10(); saveLastSessionState()
        }
        knobTreble = RotaryKnobView(this, "MASTER TREBLE", -12f, 12f, toneTreble, "dB", "#38BDF8") { value ->
            toneTreble = value; applyAllBands10(); saveLastSessionState()
        }
        val knobLayout = LinearLayout.LayoutParams(0, dp(92), 1f)
        knobsRow.addView(knobBass, knobLayout)
        knobsRow.addView(knobMid, knobLayout)
        knobsRow.addView(knobTreble, knobLayout)
        panelTabFx.addView(knobsRow)

        btnReverbToggle = makeConsoleButton("🏛 EFEK RUANG (HALL AMBIENCE): OFF", "#1E293B", "#A855F7", "#E9D5FF", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
                topMargin = dp(8)
            }
            setOnClickListener {
                isReverbActive = !isReverbActive
                applyHallReverb(isReverbActive)
                if (isReverbActive) {
                    text = "🏛 EFEK RUANG (HALL AMBIENCE): ON"
                    background = makeRoundedBg("#581C87", "#C084FC", dp(8).toFloat())
                    setTextColor(Color.WHITE)
                } else {
                    text = "🏛 EFEK RUANG (HALL AMBIENCE): OFF"
                    background = makeRoundedBg("#1E293B", "#A855F7", dp(8).toFloat())
                    setTextColor(Color.parseColor("#E9D5FF"))
                }
            }
        }
        panelTabFx.addView(btnReverbToggle)
        dlmsMainCard.addView(panelTabFx)

        root.addView(dlmsMainCard)
        root.addView(spacer(dp(6)))

        // === 6. PAPAN SOUNDBOARD 16 SLOT (BANK A & BANK B) ===
        val sbdCard = createCardPanel().apply { setPadding(dp(7), dp(7), dp(7), dp(7)) }
        val sbdHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(5))
        }
        sbdHeader.addView(TextView(this).apply {
            text = "SOUNDBOARD (TAHAN = EDIT)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        btnBankA = makeConsoleButton("BANK A", "#2563EB", "#60A5FA", "#FFFFFF", 8.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(24)).apply { marginEnd = dp(4) }
            setOnClickListener { switchSoundboardBank(0) }
        }
        btnBankB = makeConsoleButton("BANK B", "#1E293B", "#475569", "#94A3B8", 8.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(24)).apply { marginEnd = dp(5) }
            setOnClickListener { switchSoundboardBank(1) }
        }
        val btnStopAllSfx = makeConsoleButton("⏹ STOP ALL", "#7F1D1D", "#EF4444", "#FCA5A5", 8.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(24))
            setOnClickListener { stopAllSounds() }
        }
        sbdHeader.addView(btnBankA)
        sbdHeader.addView(btnBankB)
        sbdHeader.addView(btnStopAllSfx)
        sbdCard.addView(sbdHeader)

        val sbdGrid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
        }
        val neonColors = arrayOf("#A855F7", "#10B981", "#F59E0B", "#38BDF8", "#06B6D4", "#EF4444", "#EC4899", "#8B5CF6")
        padButtons.clear()
        for (i in 0..7) {
            val btn = makeConsoleButton("", "#161E2E", neonColors[i], "#FFFFFF", 9.5f).apply {
                setOnClickListener { playSoundPad(getSlotKey(currentBank, i)) }
                setOnLongClickListener {
                    val slotKey = getSlotKey(currentBank, i)
                    showEditPadDialog(currentBank, i, slotKey)
                    true
                }
            }
            val gp = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(40)
                columnSpec = GridLayout.spec(i % 4, 1f)
                rowSpec = GridLayout.spec(i / 4, 1f)
                setMargins(dp(2.5f.toInt()), dp(2.5f.toInt()), dp(2.5f.toInt()), dp(2.5f.toInt()))
            }
            padButtons.add(btn)
            sbdGrid.addView(btn, gp)
        }
        sbdCard.addView(sbdGrid)
        root.addView(sbdCard)
        root.addView(spacer(dp(6)))

        // === 7. PEMUTAR BGM NGOBROL AUTO-LOOP ===
        val bgmCard = createCardPanel().apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val bgmTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(3))
        }
        tvBgmTitle = TextView(this).apply {
            text = "🎵 BGM NGOBROL: (Belum dipilih)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#CBD5E1"))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnPickBgm = makeConsoleButton("📂 PILIH MP3", "#1E293B", "#475569", "#E2E8F0", 8.5f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply {
                marginEnd = dp(4)
            }
            setOnClickListener { pickBgmLauncher.launch("audio/*") }
        }
        btnBgmPlayStop = makeConsoleButton("▶ LOOP BGM", "#065F46", "#10B981", "#FFFFFF", 8.5f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26))
            setOnClickListener { toggleBgmPlayback() }
        }
        bgmTopRow.addView(tvBgmTitle)
        bgmTopRow.addView(btnPickBgm)
        bgmTopRow.addView(btnBgmPlayStop)
        bgmCard.addView(bgmTopRow)

        val bgmVolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bgmVolRow.addView(TextView(this).apply {
            text = "BGM VOL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekBgmVol = SeekBar(this).apply {
            max = 100
            progress = bgmVolume
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    bgmVolume = p
                    tvBgmVolPct.text = "$p%"
                    updateBgmPanAndVolume()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        }
        tvBgmVolPct = TextView(this).apply {
            text = "$bgmVolume%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        bgmVolRow.addView(seekBgmVol)
        bgmVolRow.addView(tvBgmVolPct)
        bgmCard.addView(bgmVolRow)
        root.addView(bgmCard)

        rootScroll.addView(root)
        setContentView(rootScroll)
        updateSubwooferEngineUi()

        // Inisialisasi Semua Mesin DSP & Tampilan
        lockHardwareMediaVolumeMax()
        setDisplayDeckMode(0)
        updateChannelRouteUiAndApply(showToast = false)
        applyAllBands10()
        applyMultiBandDynamics5()
        applyOutputLimiterSettings()
        refreshDynamicTabUi()
        updateMbcButtonsUi()
        applyLockUiState()
        switchSoundboardBank(0)
        restoreSavedBgmInfo()
        ensureVisualizerPermissionAndStart()
    }

    // === ENGINE INTI YOUTUBE: ANTI-IKLAN + FULL VIDEO BOX + COMBO PITCH SHIFTER (TEMPO TETAP + AUTO FALLBACK) ===
    private fun injectEarlyCoreEngine(view: WebView?) {
        val js = """
            (function() {
                if (!window.__ohoEngineV71) {
                    window.__ohoEngineV71 = true;

                    try {
                        Object.defineProperty(document, 'hidden', { get: function() { return false; } });
                        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });
                    } catch (e) {}

                    function stripAdKeys(obj) {
                        if (!obj || typeof obj !== 'object') return obj;
                        delete obj.adPlacements;
                        delete obj.playerAds;
                        delete obj.adSlots;
                        delete obj.adBreakHeartbeatParams;
                        return obj;
                    }

                    let _ytInitResp = window.ytInitialPlayerResponse;
                    try {
                        Object.defineProperty(window, 'ytInitialPlayerResponse', {
                            get: function() { return stripAdKeys(_ytInitResp); },
                            set: function(val) { _ytInitResp = stripAdKeys(val); },
                            configurable: true
                        });
                    } catch (e) {}

                    const origFetch = window.fetch;
                    window.fetch = async function(...args) {
                        const res = await origFetch.apply(this, args);
                        const url = (typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url ? args[0].url : '');
                        if (url.includes('/youtubei/v1/player') || url.includes('/youtubei/v1/next')) {
                            try {
                                const clone = res.clone();
                                const json = stripAdKeys(await clone.json());
                                return new Response(JSON.stringify(json), {
                                    status: res.status, statusText: res.statusText, headers: res.headers
                                });
                            } catch (e) {}
                        }
                        return res;
                    };

                    const origXhrOpen = XMLHttpRequest.prototype.open;
                    const origXhrSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.open = function(method, url) {
                        this.__ohoUrl = (url || '').toString();
                        return origXhrOpen.apply(this, arguments);
                    };
                    XMLHttpRequest.prototype.send = function() {
                        if (this.__ohoUrl && (this.__ohoUrl.includes('/youtubei/v1/player') || this.__ohoUrl.includes('/youtubei/v1/next'))) {
                            this.addEventListener('readystatechange', function() {
                                if (this.readyState === 4 && this.responseText) {
                                    try {
                                        const cleaned = JSON.stringify(stripAdKeys(JSON.parse(this.responseText)));
                                        Object.defineProperty(this, 'responseText', { get: function() { return cleaned; } });
                                        Object.defineProperty(this, 'response', { get: function() { return cleaned; } });
                                    } catch (e) {}
                                }
                            });
                        }
                        return origXhrSend.apply(this, arguments);
                    };

                    const style = document.createElement('style');
                    style.innerHTML = `
                        ytm-promoted-sparkles-web-renderer,
                        ytm-companion-slot-renderer,
                        ad-slot-renderer,
                        ytm-ad-slot-renderer,
                        ytm-in-feed-ad-layout-renderer,
                        ytm-statement-banner-renderer,
                        .ad-container, .ytp-ad-overlay-container, .ytp-ad-message-container {
                            display: none !important;
                        }
                        html.oho-watch-full body {
                            overflow: hidden !important;
                            background: #000 !important;
                        }
                        html.oho-watch-full ytm-mobile-topbar-renderer,
                        html.oho-watch-full ytm-single-column-watch-next-results-renderer,
                        html.oho-watch-full .related-chips-slot-wrapper,
                        html.oho-watch-full ytm-item-section-renderer {
                            display: none !important;
                        }
                        html.oho-watch-full #player-container-id {
                            position: fixed !important;
                            top: 0 !important;
                            left: 0 !important;
                            width: 100vw !important;
                            height: 100vh !important;
                            max-width: 100vw !important;
                            max-height: 100vh !important;
                            margin: 0 !important;
                            padding: 0 !important;
                            z-index: 9999 !important;
                            background: #000 !important;
                        }
                        html.oho-watch-full .player-container,
                        html.oho-watch-full #movie_player,
                        html.oho-watch-full .html5-video-player,
                        html.oho-watch-full .html5-video-container,
                        html.oho-watch-full video {
                            width: 100% !important;
                            height: 100% !important;
                            max-width: 100% !important;
                            max-height: 100% !important;
                            top: 0 !important;
                            left: 0 !important;
                            object-fit: fill !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);

                    window.__ohoPitchSteps = 0;
                    window.__ohoRouteMode = 0;
                    window.__ohoSharedCtx = null;

                    window.__ohoUnlockAudioContext = function() {
                        try {
                            if (!window.__ohoSharedCtx) {
                                const Ctx = window.AudioContext || window.webkitAudioContext;
                                window.__ohoSharedCtx = new Ctx();
                            }
                            if (window.__ohoSharedCtx.state === 'suspended') {
                                window.__ohoSharedCtx.resume();
                            }
                        } catch (e) {}
                    };

                    function createGranularPitchNode(ctx) {
                        const bufferTime = 0.08;
                        const delayA = ctx.createDelay(0.5);
                        const delayB = ctx.createDelay(0.5);
                        const gainA = ctx.createGain();
                        const gainB = ctx.createGain();
                        const inputNode = ctx.createGain();
                        const outputNode = ctx.createGain();

                        inputNode.connect(delayA);
                        inputNode.connect(delayB);
                        delayA.connect(gainA);
                        delayB.connect(gainB);
                        gainA.connect(outputNode);
                        gainB.connect(outputNode);

                        const sr = ctx.sampleRate;
                        const len = Math.floor(sr * 2);
                        const curveA = new Float32Array(len);
                        const curveB = new Float32Array(len);
                        const winA = new Float32Array(len);
                        const winB = new Float32Array(len);

                        for (let i = 0; i < len; i++) {
                            const pA = (i / len);
                            const pB = (pA + 0.5) % 1.0;
                            curveA[i] = pA * bufferTime;
                            curveB[i] = pB * bufferTime;
                            winA[i] = Math.sin(Math.PI * pA);
                            winB[i] = Math.sin(Math.PI * pB);
                        }

                        const lfo = ctx.createBufferSource();
                        const lfoBuf = ctx.createBuffer(4, len, sr);
                        lfoBuf.copyToChannel(curveA, 0);
                        lfoBuf.copyToChannel(curveB, 1);
                        lfoBuf.copyToChannel(winA, 2);
                        lfoBuf.copyToChannel(winB, 3);
                        lfo.buffer = lfoBuf;
                        lfo.loop = true;

                        const splitLfo = ctx.createChannelSplitter(4);
                        lfo.connect(splitLfo);

                        const depthA = ctx.createGain();
                        const depthB = ctx.createGain();
                        splitLfo.connect(depthA, 0);
                        splitLfo.connect(depthB, 1);
                        depthA.connect(delayA.delayTime);
                        depthB.connect(delayB.delayTime);

                        gainA.gain.value = 0;
                        gainB.gain.value = 0;
                        splitLfo.connect(gainA.gain, 2);
                        splitLfo.connect(gainB.gain, 3);

                        lfo.start(0);

                        return {
                            input: inputNode,
                            output: outputNode,
                            setSemitones: function(st) {
                                const ratio = Math.pow(2, st / 12);
                                const freq = Math.abs(1 - ratio) / bufferTime;
                                lfo.playbackRate.value = Math.max(0.001, freq / 0.5);
                                const dir = (1 - ratio) >= 0 ? 1 : -1;
                                const baseOffset = dir < 0 ? bufferTime : 0.0;
                                delayA.delayTime.value = baseOffset;
                                delayB.delayTime.value = baseOffset;
                                depthA.gain.value = dir;
                                depthB.gain.value = dir;
                            }
                        };
                    }

                    function applyFallbackPlaybackPitch(v, st) {
                        if (st === 0) {
                            v.preservesPitch = true;
                            v.mozPreservesPitch = true;
                            v.webkitPreservesPitch = true;
                            v.playbackRate = 1.0;
                        } else {
                            v.preservesPitch = false;
                            v.mozPreservesPitch = false;
                            v.webkitPreservesPitch = false;
                            v.playbackRate = Math.pow(2, st / 12);
                        }
                    }

                    window.__ohoUpdateAudioProcessor = function() {
                        const v = document.querySelector('video');
                        if (!v) return;

                        const m = window.__ohoRouteMode || 0;
                        const st = window.__ohoPitchSteps || 0;

                        if (!v.__ohoAudioPipe && m === 0 && st === 0) {
                            v.preservesPitch = true;
                            v.playbackRate = 1.0;
                            return;
                        }

                        if (v.__ohoCorsBlocked) {
                            applyFallbackPlaybackPitch(v, st);
                            return;
                        }

                        window.__ohoUnlockAudioContext();
                        const ctx = window.__ohoSharedCtx;
                        if (!ctx || ctx.state !== 'running') {
                            applyFallbackPlaybackPitch(v, st);
                            return;
                        }

                        if (!v.__ohoAudioPipe) {
                            try {
                                const src = ctx.createMediaElementSource(v);
                                const analyser = ctx.createAnalyser();
                                analyser.fftSize = 64;

                                const splitter = ctx.createChannelSplitter(2);
                                const gLL = ctx.createGain();
                                const gLR = ctx.createGain();
                                const gRL = ctx.createGain();
                                const gRR = ctx.createGain();
                                const merger = ctx.createChannelMerger(2);

                                src.connect(analyser);
                                analyser.connect(splitter);
                                splitter.connect(gLL, 0);
                                splitter.connect(gLR, 0);
                                splitter.connect(gRL, 1);
                                splitter.connect(gRR, 1);

                                gLL.connect(merger, 0, 0);
                                gRL.connect(merger, 0, 0);
                                gLR.connect(merger, 0, 1);
                                gRR.connect(merger, 0, 1);

                                const dryGain = ctx.createGain();
                                const wetGain = ctx.createGain();
                                const pitchNode = createGranularPitchNode(ctx);

                                merger.connect(dryGain);
                                merger.connect(pitchNode.input);
                                pitchNode.output.connect(wetGain);

                                dryGain.connect(ctx.destination);
                                wetGain.connect(ctx.destination);

                                v.__ohoAudioPipe = {
                                    ctx: ctx, analyser: analyser, gLL: gLL, gLR: gLR, gRL: gRL, gRR: gRR,
                                    dryGain: dryGain, wetGain: wetGain, pitchNode: pitchNode, zeroFrames: 0
                                };
                            } catch (e) {
                                v.__ohoCorsBlocked = true;
                                applyFallbackPlaybackPitch(v, st);
                                return;
                            }
                        }

                        const p = v.__ohoAudioPipe;
                        if (p.ctx.state === 'suspended') p.ctx.resume();

                        if (!v.paused && v.volume > 0.05) {
                            const buf = new Uint8Array(p.analyser.frequencyBinCount);
                            p.analyser.getByteTimeDomainData(buf);
                            let energy = 0;
                            for (let k = 0; k < buf.length; k++) energy += Math.abs(buf[k] - 128);
                            if (energy === 0) {
                                p.zeroFrames++;
                                if (p.zeroFrames > 6) {
                                    v.__ohoCorsBlocked = true;
                                    applyFallbackPlaybackPitch(v, st);
                                    return;
                                }
                            } else {
                                p.zeroFrames = 0;
                            }
                        }

                        v.preservesPitch = true;
                        v.playbackRate = 1.0;

                        if (m === 0) {
                            p.gLL.gain.value = 1.0; p.gLR.gain.value = 0.0;
                            p.gRL.gain.value = 0.0; p.gRR.gain.value = 1.0;
                        } else if (m === 1) {
                            p.gLL.gain.value = 0.5; p.gLR.gain.value = 0.5;
                            p.gRL.gain.value = 0.5; p.gRR.gain.value = 0.5;
                        } else if (m === 2) {
                            p.gLL.gain.value = 1.0; p.gLR.gain.value = 1.0;
                            p.gRL.gain.value = 0.0; p.gRR.gain.value = 0.0;
                        } else if (m === 3) {
                            p.gLL.gain.value = 0.0; p.gLR.gain.value = 0.0;
                            p.gRL.gain.value = 1.0; p.gRR.gain.value = 1.0;
                        }

                        if (st === 0) {
                            p.dryGain.gain.value = 1.0;
                            p.wetGain.gain.value = 0.0;
                        } else {
                            p.pitchNode.setSemitones(st);
                            p.dryGain.gain.value = 0.0;
                            p.wetGain.gain.value = 1.0;
                        }
                    };

                    let wasAd = false;
                    let prevMuted = false;
                    setInterval(function() {
                        const isWatchPage = window.location.pathname.includes('/watch');
                        if (isWatchPage) {
                            document.documentElement.classList.add('oho-watch-full');
                        } else {
                            document.documentElement.classList.remove('oho-watch-full');
                        }

                        const player = document.querySelector('.html5-video-player');
                        const video = document.querySelector('video');
                        const isAd = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
                        if (isAd && video) {
                            if (!wasAd) { prevMuted = video.muted; wasAd = true; }
                            video.muted = true;
                            if (isFinite(video.duration) && video.duration > 0) video.currentTime = video.duration;
                            const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                            if (skipBtn) skipBtn.click();
                        } else if (!isAd && wasAd && video) {
                            video.muted = prevMuted;
                            wasAd = false;
                        }

                        if (video && !video.__ohoGuardAttached) {
                            video.__ohoGuardAttached = true;
                            video.__ohoOrigPause = video.pause;
                            video.pause = function() {
                                if (window.__ohoUserPaused === true) {
                                    return video.__ohoOrigPause.apply(this, arguments);
                                }
                            };
                            video.addEventListener('play', function() {
                                window.__ohoUserPaused = false;
                                window.__ohoUpdateAudioProcessor();
                            });
                        }
                    }, 250);
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    // === PENGATUR 2 MODE LAYAR: YOUTUBE FULL & RTA DSP ===
    private fun setDisplayDeckMode(mode: Int) {
        displayDeckMode = mode
        if (mode == 0) {
            btnModeYtFull.background = makeRoundedBg("#0284C7", "#38BDF8", dp(7).toFloat())
            btnModeYtFull.setTextColor(Color.WHITE)
            btnModeRtaDsp.background = makeRoundedBg("#1E293B", "#475569", dp(7).toFloat())
            btnModeRtaDsp.setTextColor(Color.parseColor("#CBD5E1"))

            ytWebView.visibility = View.VISIBLE
            rtaView.visibility = View.GONE
        } else {
            btnModeRtaDsp.background = makeRoundedBg("#0284C7", "#38BDF8", dp(7).toFloat())
            btnModeRtaDsp.setTextColor(Color.WHITE)
            btnModeYtFull.background = makeRoundedBg("#1E293B", "#475569", dp(7).toFloat())
            btnModeYtFull.setTextColor(Color.parseColor("#CBD5E1"))

            ytWebView.visibility = View.VISIBLE
            rtaView.visibility = View.VISIBLE
            rtaView.bringToFront()
            ensureVisualizerPermissionAndStart()
        }

        ytWebView.evaluateJavascript("var v=document.querySelector('video'); if(v && v.paused && window.__ohoUserPaused===false){ v.play(); }", null)
    }

    // === NAVIGASI 4 TAB DLMS ===
    private fun switchDlmsTab(tabIndex: Int) {
        activeDlmsTab = tabIndex
        val tabs = arrayOf(tabBtnEq, tabBtnDyn, tabBtnXover, tabBtnFx)
        val panels = arrayOf(panelTabEq, panelTabDyn, panelTabXover, panelTabFx)

        for (i in 0..3) {
            if (i == tabIndex) {
                tabs[i].background = makeRoundedBg("#0284C7", "#38BDF8", dp(7).toFloat())
                tabs[i].setTextColor(Color.WHITE)
                panels[i].visibility = View.VISIBLE
            } else {
                tabs[i].background = makeRoundedBg("#1E293B", "#475569", dp(7).toFloat())
                tabs[i].setTextColor(Color.parseColor("#94A3B8"))
                panels[i].visibility = View.GONE
            }
        }
    }

    private fun refreshDynamicTabUi() {
        for (i in 0..4) {
            if (i == selectedDynBand) {
                dynSelectBtns[i].background = makeRoundedBg("#0284C7", "#38BDF8", dp(6).toFloat())
                dynSelectBtns[i].setTextColor(Color.WHITE)
            } else {
                dynSelectBtns[i].background = makeRoundedBg("#1E293B", "#475569", dp(6).toFloat())
                dynSelectBtns[i].setTextColor(Color.parseColor("#CBD5E1"))
            }
        }
        tvDynThresh.text = "${String.format("%.1f", dynThresholds[selectedDynBand])} dB"
        tvDynRatio.text = "${String.format("%.1f", dynRatios[selectedDynBand])} : 1"
        val g = dynPostGains[selectedDynBand]
        tvDynGain.text = if (g > 0f) "+${String.format("%.1f", g)} dB" else "${String.format("%.1f", g)} dB"
        dynView.selectedBand = selectedDynBand
        dynView.invalidate()
    }

    // === 4 MODE ROUTING KANAL ===
    private fun updateChannelRouteUiAndApply(showToast: Boolean = true) {
        val modeToast = when (channelRouteMode) {
            0 -> {
                btnRouteMode.text = "STEREO\nL + R"
                btnRouteMode.background = makeRoundedBg("#0F172A", "#22D3EE", dp(7).toFloat())
                btnRouteMode.setTextColor(Color.parseColor("#22D3EE"))
                "Mode Kanal: STEREO (L + R)"
            }
            1 -> {
                btnRouteMode.text = "MONO\nL + R"
                btnRouteMode.background = makeRoundedBg("#065F46", "#34D399", dp(7).toFloat())
                btnRouteMode.setTextColor(Color.WHITE)
                "Mode Kanal: MONO Seimbang (L + R)"
            }
            2 -> {
                btnRouteMode.text = "KIRI\nL ONLY"
                btnRouteMode.background = makeRoundedBg("#1E3A8A", "#60A5FA", dp(7).toFloat())
                btnRouteMode.setTextColor(Color.WHITE)
                "Mode Kanal: KIRI (L Only — Kanan Dibisukan)"
            }
            else -> {
                btnRouteMode.text = "KANAN\nR ONLY"
                btnRouteMode.background = makeRoundedBg("#9A3412", "#FB923C", dp(7).toFloat())
                btnRouteMode.setTextColor(Color.WHITE)
                "Mode Kanal: KANAN (R Only — Vokal Kiri Dibisukan)"
            }
        }
        vuMeterView.routeMode = channelRouteMode
        vuMeterView.invalidate()

        applyChannelBoostAndRouting()
        applyYoutubeChannelMatrix(channelRouteMode)
        updateActiveSfxPanAndVolume()
        updateBgmPanAndVolume()
        if (showToast) Toast.makeText(this, modeToast, Toast.LENGTH_SHORT).show()
    }

    private fun computeSmartPreampTrimDb(): Float {
        // Compensate for the strongest positive EQ/tone boost before the output limiter.
        // This preserves headroom for bass AND presence/treble boosts.
        var peakBoostDb = 0f
        for (i in currentGains10.indices) {
            val toneOffset = when (i) {
                0, 1, 2 -> toneBass
                3, 4, 5, 6 -> toneMid
                else -> toneTreble
            }
            peakBoostDb = max(peakBoostDb, currentGains10[i] + toneOffset)
        }
        // Account for combined Bass Punch + Virtual Subwoofer low-frequency lift.
        val punchPeak = if (isSubBassActive) 3.5f else 0f
        val virtualSubPeak = if (isSubwooferEngineEnabled) subLevelDb else 0f
        peakBoostDb = max(peakBoostDb, punchPeak + virtualSubPeak)
        return -peakBoostDb.coerceIn(0f, 9f)
    }

    private fun applyChannelBoostAndRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                val trimDb = computeSmartPreampTrimDb()
                val (gainL, gainR) = when (channelRouteMode) {
                    1 -> {
                        val avg = ((boostLeftDb + boostRightDb) / 2f) + trimDb
                        Pair(avg, avg)
                    }
                    2 -> Pair(boostLeftDb.toFloat() + trimDb, -90f)
                    3 -> Pair(-90f, boostRightDb.toFloat() + trimDb)
                    else -> Pair(boostLeftDb.toFloat() + trimDb, boostRightDb.toFloat() + trimDb)
                }
                dynamicsProcessing!!.setInputGainbyChannel(0, gainL)
                dynamicsProcessing!!.setInputGainbyChannel(1, gainR)
            }
        } catch (_: Exception) {}
    }

    private fun applyYoutubeChannelMatrix(mode: Int) {
        if (!this::ytWebView.isInitialized) return
        val js = "window.__ohoRouteMode = $mode; if(typeof window.__ohoUpdateAudioProcessor === 'function'){ window.__ohoUpdateAudioProcessor(); }"
        ytWebView.evaluateJavascript(js, null)
    }

    private fun getChannelPanMultipliers(): Pair<Float, Float> = when (channelRouteMode) {
        2 -> Pair(1.0f, 0.0f)
        3 -> Pair(0.0f, 1.0f)
        else -> Pair(1.0f, 1.0f)
    }

    private fun updateActiveSfxPanAndVolume() {
        val baseVol = if (isMasterMuted) 0f else (sfxVolume.coerceIn(0, 100) / 100f)
        val (panL, panR) = getChannelPanMultipliers()
        for (sid in activeStreams) {
            soundPool.setVolume(sid, baseVol * panL, baseVol * panR)
        }
    }

    private fun updateBgmPanAndVolume() {
        val baseVol = if (isMasterMuted) 0f else (bgmVolume.coerceIn(0, 100) / 100f)
        val (panL, panR) = getChannelPanMultipliers()
        bgmPlayer?.setVolume(baseVol * panL, baseVol * panR)
    }

    private fun updateTimerDisplay(totalMs: Long) {
        val totalSec = totalMs / 1000L
        val hrs = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        tvLiveTimer.text = String.format("%02d:%02d:%02d", hrs, mins, secs)
    }

    private fun lockHardwareMediaVolumeMax() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
        } catch (_: Exception) {}
    }

    private fun applyYoutubeVolume(percent: Int) {
        if (!this::ytWebView.isInitialized) return
        val vol = (percent.coerceIn(0, 100) / 100f)
        val js = """
            (function() {
                window.__ohoTargetYtVol = $vol;
                var v = document.querySelector('video');
                if (v) { v.volume = $vol; }
                if (!window.__ohoVolGuard) {
                    window.__ohoVolGuard = setInterval(function() {
                        var vid = document.querySelector('video');
                        if (vid && typeof window.__ohoTargetYtVol === 'number' && Math.abs(vid.volume - window.__ohoTargetYtVol) > 0.02) {
                            vid.volume = window.__ohoTargetYtVol;
                        }
                    }, 400);
                }
            })();
        """.trimIndent()
        ytWebView.evaluateJavascript(js, null)
    }

    private fun updatePitchUiAndApply() {
        val label = when {
            pitchSteps > 0 -> "NADA: +$pitchSteps"
            pitchSteps < 0 -> "NADA: $pitchSteps"
            else -> "NADA: 0"
        }
        btnKeyDisplay.text = label
        btnKeyDisplay.background = if (pitchSteps != 0) makeRoundedBg("#4C1D95", "#C084FC", dp(7).toFloat())
        else makeRoundedBg("#161E2E", "#64748B", dp(7).toFloat())
        applyYoutubePitchStepsCombo(pitchSteps)
    }

    private fun applyYoutubePitchStepsCombo(steps: Int) {
        if (!this::ytWebView.isInitialized) return
        val js = """
            (function() {
                window.__ohoPitchSteps = $steps;
                if (typeof window.__ohoUpdateAudioProcessor === 'function') {
                    window.__ohoUpdateAudioProcessor();
                } else {
                    var v = document.querySelector('video');
                    if (v) {
                        if ($steps === 0) {
                            v.preservesPitch = true;
                            v.playbackRate = 1.0;
                        } else {
                            v.preservesPitch = false;
                            v.playbackRate = Math.pow(2, $steps / 12);
                        }
                    }
                }
            })();
        """.trimIndent()
        ytWebView.evaluateJavascript(js, null)
    }

    private fun applyYoutubeLoop(loop: Boolean) {
        if (!this::ytWebView.isInitialized) return
        ytWebView.evaluateJavascript("var v=document.querySelector('video'); if(v){ v.loop = $loop; }", null)
    }

    private fun animateMusicVolumeFade(fromVol: Int, toVol: Int) {
        ValueAnimator.ofInt(fromVol, toVol).apply {
            duration = 320
            addUpdateListener { anim ->
                val v = anim.animatedValue as Int
                seekMusicVol.progress = v
                currentMusicVolume = v
                tvMusicVolPct.text = "$v%"
                if (!isMasterMuted && !isYtMuted) applyYoutubeVolume(v)
            }
            start()
        }
    }

    private fun updateMbcButtonsUi() {
        btnSubBass.background = if (isSubBassActive) makeRoundedBg("#B45309", "#FBBF24", dp(7).toFloat())
        else makeRoundedBg("#1E293B", "#F59E0B", dp(7).toFloat())
        btnSubBass.setTextColor(if (isSubBassActive) Color.WHITE else Color.parseColor("#FDE68A"))

        btnClarity.background = if (isClarityActive) makeRoundedBg("#0E7490", "#22D3EE", dp(7).toFloat())
        else makeRoundedBg("#1E293B", "#22D3EE", dp(7).toFloat())
        btnClarity.setTextColor(if (isClarityActive) Color.WHITE else Color.parseColor("#CFFAFE"))
    }

    private fun applyLockUiState() {
        eqView.isLocked = isToneLocked
        knobBass.isLocked = isToneLocked
        knobMid.isLocked = isToneLocked
        knobTreble.isLocked = isToneLocked

        if (isToneLocked) {
            btnLockTone.text = "🔒 TERKUNCI"
            btnLockTone.background = makeRoundedBg("#B91C1C", "#F87171", dp(7).toFloat())
        } else {
            btnLockTone.text = "🔓 UNLOCK"
            btnLockTone.background = makeRoundedBg("#1E293B", "#38BDF8", dp(7).toFloat())
        }
        eqView.invalidate()
        knobBass.invalidate()
        knobMid.invalidate()
        knobTreble.invalidate()
    }

    // Detect the active output class. Detection is automatic; actual discrete
    // SUB routing requires a PCM multichannel renderer not present in this project yet.
    private fun setupSubwooferAutoRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioDeviceCallback = object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                        refreshSubwooferOutputRoute()
                    }
                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                        refreshSubwooferOutputRoute()
                    }
                }
                am.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {}
        }
        refreshSubwooferOutputRoute()
    }

    private fun refreshSubwooferOutputRoute() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val selected = outputs.firstOrNull { it.isSink && (
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_HDMI ||
                it.type == AudioDeviceInfo.TYPE_HDMI_ARC
            ) } ?: outputs.firstOrNull { it.isSink }
            multichannelOutputDetected = selected?.channelCounts?.any { it >= 3 } == true
            activeOutputDescription = when {
                selected == null -> "Output belum terdeteksi — Virtual Subwoofer siap"
                multichannelOutputDetected -> "${audioDeviceTypeName(selected.type)} terdeteksi dengan endpoint multichannel. Output fisik SUB terpisah belum aktif pada renderer saat ini; memakai stereo virtual."
                selected.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP stereo terdeteksi — Virtual Subwoofer aktif (tidak ada kanal SUB terpisah)"
                selected.type == AudioDeviceInfo.TYPE_USB_DEVICE || selected.type == AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio terdeteksi — ${selected.channelCounts?.maxOrNull() ?: 2} kanal; Virtual Subwoofer aktif"
                selected.type == AudioDeviceInfo.TYPE_HDMI || selected.type == AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI terdeteksi — Virtual Subwoofer aktif; kanal fisik SUB perlu PCM multichannel renderer"
                else -> "${audioDeviceTypeName(selected.type)} terdeteksi — Virtual Subwoofer stereo aktif"
            }
            runOnUiThread { updateSubwooferEngineUi() }
        } catch (_: Exception) {
            activeOutputDescription = "Deteksi output tidak tersedia — Virtual Subwoofer stereo"
            runOnUiThread { updateSubwooferEngineUi() }
        }
    }

    private fun audioDeviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Audio"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker Android"
        else -> "Audio output"
    }

    private fun updateSubwooferEngineUi() {
        if (!this::btnSubwooferEngine.isInitialized) return
        btnSubwooferEngine.text = if (isSubwooferEngineEnabled) "🔊 SUBWOOFER ENGINE: ON" else "🔇 SUBWOOFER ENGINE: OFF"
        btnSubwooferEngine.background = makeRoundedBg(
            if (isSubwooferEngineEnabled) "#164E63" else "#1E293B",
            if (isSubwooferEngineEnabled) "#22D3EE" else "#475569", dp(7).toFloat()
        )
        tvSubwooferRoute.text = activeOutputDescription
        tvSubwooferRoute.setTextColor(if (multichannelOutputDetected) Color.parseColor("#FCD34D") else Color.parseColor("#A5F3FC"))
        if (this::tvSubCrossover.isInitialized) tvSubCrossover.text = "FOKUS BASS VIRTUAL: ${subCrossoverHz.toInt()} Hz"
        if (this::tvSubLevel.isInitialized) tvSubLevel.text = "SUB LEVEL VIRTUAL: +${String.format("%.1f", subLevelDb)} dB"
        if (this::tvDeepBass.isInitialized) tvDeepBass.text = "DEEP BASS: +${String.format("%.1f", deepBassDb)} dB"
    }

    // === MESIN DSP DLMS (PRE-EQ + MBC + POST-EQ + LIMITER ALA POWERAMP) ===
    private fun setupAudioDspDlms() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    // Audio session 0 targets the Android output mix, so app playback
                    // (WebView/YouTube, SoundPool and MediaPlayer) can share this DSP chain.
                    val cfg = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2,
                        true, 10,
                        true, 5,
                        true, 4,
                        true
                    ).build()
                    dynamicsProcessing = DynamicsProcessing(0, 0, cfg).apply {
                        enabled = true
                        for (ch in 0..1) {
                            val preEq = getPreEqByChannelIndex(ch)
                            preEq.isEnabled = true
                            for (b in 0..9) {
                                val band = preEq.getBand(b)
                                band.isEnabled = true
                                band.cutoffFrequency = defaultFreqs10[b]
                                band.gain = 0f
                                preEq.setBand(b, band)
                            }
                            val mbc = getMbcByChannelIndex(ch)
                            mbc.isEnabled = true

                            val postEq = getPostEqByChannelIndex(ch)
                            postEq.isEnabled = true
                            for (p in 0..3) {
                                val pBand = postEq.getBand(p)
                                pBand.isEnabled = true
                                pBand.cutoffFrequency = postEqFreqs[p]
                                // Bass Punch is a separate low-frequency post-EQ stage.
                                // Keep it disabled unless the user has enabled the button.
                                val punchGains = floatArrayOf(1.5f, 3.5f, 3.0f, 0.5f)
                                pBand.gain = if (isSubBassActive) punchGains[p] else 0.0f
                                postEq.setBand(p, pBand)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    dynamicsProcessing = null
                }
            }
            if (dynamicsProcessing == null) {
                // Fallback also targets the output mix (session 0). Some OEM DSP stacks
                // reject DynamicsProcessing even when the Android version supports it.
                fallbackEqualizer = Equalizer(0, 0).apply { enabled = true }
            }

            presetReverb = PresetReverb(0, 0).apply {
                preset = PresetReverb.PRESET_LARGEHALL
                enabled = false
            }
            // Apply saved Subwoofer Engine + Bass Punch settings after DSP creation.
            applyBassPunchPostEq(isSubBassActive)
            applyChannelBoostAndRouting()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyHallReverb(enable: Boolean) {
        try {
            presetReverb?.enabled = enable
        } catch (_: Exception) {}
    }

    /**
     * Applies a controlled low-end contour to the shared Android output mix.
     * The lift focuses on 75-120 Hz for kick/bass punch, with a smaller 45 Hz
     * extension and only a slight 350 Hz body lift to avoid muddy vocals.
     */
    private fun applyBassPunchPostEq(enable: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                // Four gentle peaking bands form a stereo virtual-sub contour.
                // This is not a discrete crossover or a physical SUB channel.
                val punch = if (enable) floatArrayOf(1.5f, 3.5f, 3.0f, 0.5f)
                            else floatArrayOf(0f, 0f, 0f, 0f)
                val virtualSub = if (isSubwooferEngineEnabled)
                    floatArrayOf(deepBassDb + subLevelDb * 0.45f, subLevelDb, subLevelDb * 0.55f, 0f)
                    else floatArrayOf(0f, 0f, 0f, 0f)
                val center = subCrossoverHz
                val frequencies = floatArrayOf(center * 0.56f, center * 0.94f, center * 1.5f, center * 4.375f)
                for (ch in 0..1) {
                    val postEq = dynamicsProcessing!!.getPostEqByChannelIndex(ch)
                    for (p in 0..3) {
                        val band = postEq.getBand(p)
                        band.isEnabled = true
                        band.cutoffFrequency = frequencies[p].coerceIn(30f, 16000f)
                        band.gain = (punch[p] + virtualSub[p]).coerceIn(-6f, 6f)
                        postEq.setBand(p, band)
                    }
                    dynamicsProcessing!!.setPostEqByChannelIndex(ch, postEq)
                }
            }
        } catch (_: Exception) {}
    }

    private fun applyOutputLimiterSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                for (ch in 0..1) {
                    val limiter = dynamicsProcessing!!.getLimiterByChannelIndex(ch)
                    limiter.isEnabled = true
                    limiter.threshold = limiterThreshDb
                    limiter.ratio = 10.0f
                    limiter.attackTime = 3.0f
                    limiter.releaseTime = 120f
                    dynamicsProcessing!!.setLimiterByChannelIndex(ch, limiter)
                }
            }
        } catch (_: Exception) {}
    }

    private fun applyMultiBandDynamics5() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                for (ch in 0..1) {
                    for (b in 0..4) {
                        val mbcBand = dynamicsProcessing!!.getMbcBandByChannelIndex(ch, b)
                        mbcBand.isEnabled = true
                        mbcBand.cutoffFrequency = dynCutoffs[b]
                        mbcBand.attackTime = when (b) {
                            0 -> 28f
                            1 -> 22f
                            else -> 12f
                        }
                        mbcBand.releaseTime = when (b) {
                            0 -> 150f
                            1 -> 120f
                            else -> 100f
                        }
                        mbcBand.threshold = dynThresholds[b]
                        mbcBand.ratio = dynRatios[b]
                        mbcBand.postGain = dynPostGains[b]
                        dynamicsProcessing!!.setMbcBandByChannelIndex(ch, b, mbcBand)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun applyDspEnabled(enable: Boolean) {
        try {
            dynamicsProcessing?.enabled = enable
            fallbackEqualizer?.enabled = enable
        } catch (_: Exception) {}
    }

    private fun applySingleBandCalculated10(bandIdx: Int) {
        val toneOffset = when (bandIdx) {
            0, 1, 2 -> toneBass
            3, 4, 5, 6 -> toneMid
            else -> toneTreble
        }
        val freq = defaultFreqs10[bandIdx]
        var xoverCut = 0f
        if (freq < hpfCutoffHz) {
            xoverCut -= ((hpfCutoffHz - freq) / hpfCutoffHz) * 18f
        }
        if (freq > lpfCutoffHz) {
            xoverCut -= ((freq - lpfCutoffHz) / freq) * 18f
        }

        val calculatedGain = (currentGains10[bandIdx] + toneOffset + xoverCut).coerceIn(-15f, 15f)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                for (ch in 0..1) {
                    val band = dynamicsProcessing!!.getPreEqBandByChannelIndex(ch, bandIdx)
                    band.cutoffFrequency = freq
                    band.gain = calculatedGain
                    dynamicsProcessing!!.setPreEqBandByChannelIndex(ch, bandIdx, band)
                }
            } else if (fallbackEqualizer != null) {
                val numBands = fallbackEqualizer!!.numberOfBands.toInt()
                val mappedIdx = (bandIdx * numBands) / 10
                if (mappedIdx < numBands) {
                    fallbackEqualizer!!.setBandLevel(mappedIdx.toShort(), (calculatedGain * 100).toInt().toShort())
                }
            }
        } catch (_: Exception) {}
    }

    private fun applyAllBands10() {
        for (i in 0..9) applySingleBandCalculated10(i)
        applyChannelBoostAndRouting()
    }

    // === SOUNDBOARD 16 SLOT ===
    private fun getSlotKey(bank: Int, idx: Int): String = if (bank == 0) "bankA_$idx" else "bankB_$idx"

    private fun getPadLabel(bank: Int, idx: Int): String {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
        val slotKey = getSlotKey(bank, idx)
        val defaultLabel = if (bank == 0) defaultBankALabels[idx] else defaultBankBLabels[idx]
        return prefs.getString("label_$slotKey", defaultLabel) ?: defaultLabel
    }

    private fun savePadLabel(slotKey: String, newLabel: String) {
        getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
            .edit().putString("label_$slotKey", newLabel).apply()
    }

    private fun switchSoundboardBank(bank: Int) {
        currentBank = bank
        if (bank == 0) {
            btnBankA.background = makeRoundedBg("#2563EB", "#60A5FA", dp(6).toFloat())
            btnBankA.setTextColor(Color.WHITE)
            btnBankB.background = makeRoundedBg("#1E293B", "#475569", dp(6).toFloat())
            btnBankB.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            btnBankB.background = makeRoundedBg("#2563EB", "#60A5FA", dp(6).toFloat())
            btnBankB.setTextColor(Color.WHITE)
            btnBankA.background = makeRoundedBg("#1E293B", "#475569", dp(6).toFloat())
            btnBankA.setTextColor(Color.parseColor("#94A3B8"))
        }
        for (i in 0..7) {
            if (i < padButtons.size) padButtons[i].text = getPadLabel(bank, i)
        }
    }

    private fun showEditPadDialog(bank: Int, idx: Int, slotKey: String) {
        val currentName = getPadLabel(bank, idx)
        val input = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
            hint = "Nama tombol (misal: CIHUY)"
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Tombol Soundboard")
            .setMessage("Ubah nama tombol atau ganti file MP3/WAV:")
            .setView(input)
            .setPositiveButton("SIMPAN NAMA") { _, _ ->
                val clean = input.text.toString().trim().uppercase()
                if (clean.isNotEmpty()) {
                    savePadLabel(slotKey, clean)
                    switchSoundboardBank(currentBank)
                }
            }
            .setNeutralButton("PILIH FILE MP3") { _, _ ->
                val clean = input.text.toString().trim().uppercase()
                if (clean.isNotEmpty()) {
                    savePadLabel(slotKey, clean)
                    switchSoundboardBank(currentBank)
                }
                pendingPadSlotForPicker = slotKey
                pickSfxLauncher.launch("audio/*")
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun setupSoundboard16() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()

        for (bank in 0..1) {
            for (i in 0..7) {
                val slotKey = getSlotKey(bank, i)
                val customFile = File(filesDir, "custom_$slotKey.audio")
                if (customFile.exists()) {
                    soundMap[slotKey] = soundPool.load(customFile.absolutePath, 1)
                } else {
                    val assetName = if (bank == 0) defaultBankAAssets[i] else defaultBankBAssets[i]
                    try {
                        val fd = assets.openFd(assetName)
                        soundMap[slotKey] = soundPool.load(fd, 1)
                    } catch (_: IOException) {}
                }
            }
        }
    }

    private fun importCustomSoundToPad(slotKey: String, uri: Uri) {
        try {
            val destFile = File(filesDir, "custom_$slotKey.audio")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            soundMap[slotKey] = soundPool.load(destFile.absolutePath, 1)
            Toast.makeText(this, "Audio baru berhasil dipasang ke tombol!", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Gagal memuat file audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playSoundPad(slotKey: String) {
        if (isMasterMuted) {
            Toast.makeText(this, "Matikan MUTE dulu untuk memutar efek", Toast.LENGTH_SHORT).show()
            return
        }
        val id = soundMap[slotKey] ?: return
        val volFloat = (sfxVolume.coerceIn(0, 100) / 100f)
        val (panL, panR) = getChannelPanMultipliers()
        val streamId = soundPool.play(id, volFloat * panL, volFloat * panR, 1, 0, 1.0f)
        if (streamId != 0) activeStreams.add(streamId)
    }

    private fun stopAllSounds() {
        for (sid in activeStreams) soundPool.stop(sid)
        activeStreams.clear()
    }

    // === BGM NGOBROL AUTO-LOOP ===
    private fun importCustomBgmFile(uri: Uri) {
        try {
            val destFile = File(filesDir, "custom_bgm_loop.audio")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Musik Latar Siap"
            getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
                .edit().putString("bgm_display_name", fileName).apply()
            tvBgmTitle.text = "🎵 BGM: $fileName"
            Toast.makeText(this, "BGM Ngobrol siap diputar!", Toast.LENGTH_SHORT).show()
            if (isBgmPlaying) {
                stopBgmPlayer()
                toggleBgmPlayback()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Gagal memuat file BGM", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreSavedBgmInfo() {
        val bgmFile = File(filesDir, "custom_bgm_loop.audio")
        if (bgmFile.exists()) {
            val name = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
                .getString("bgm_display_name", "Musik Latar Siap")
            tvBgmTitle.text = "🎵 BGM: $name"
        }
    }

    private fun toggleBgmPlayback() {
        val bgmFile = File(filesDir, "custom_bgm_loop.audio")
        if (!bgmFile.exists()) {
            Toast.makeText(this, "Pilih file MP3 dulu lewat tombol PILIH MP3", Toast.LENGTH_SHORT).show()
            pickBgmLauncher.launch("audio/*")
            return
        }
        if (isBgmPlaying) {
            stopBgmPlayer()
        } else {
            try {
                bgmPlayer?.release()
                bgmPlayer = MediaPlayer().apply {
                    setDataSource(bgmFile.absolutePath)
                    isLooping = true
                    val v = if (isMasterMuted) 0f else (bgmVolume / 100f)
                    val (panL, panR) = getChannelPanMultipliers()
                    setVolume(v * panL, v * panR)
                    prepare()
                    start()
                }
                isBgmPlaying = true
                btnBgmPlayStop.text = "⏹ STOP BGM"
                btnBgmPlayStop.background = makeRoundedBg("#B91C1C", "#F87171", dp(7).toFloat())
            } catch (_: Exception) {
                Toast.makeText(this, "Gagal memutar BGM", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopBgmPlayer() {
        try {
            bgmPlayer?.stop()
            bgmPlayer?.release()
        } catch (_: Exception) {}
        bgmPlayer = null
        isBgmPlaying = false
        if (this::btnBgmPlayStop.isInitialized) {
            btnBgmPlayStop.text = "▶ LOOP BGM"
            btnBgmPlayStop.background = makeRoundedBg("#065F46", "#10B981", dp(7).toFloat())
        }
    }

    // === SIMPAN & PULIHKAN SETELAN ===
    private fun saveCustomPreset() {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE).edit()
        for (i in 0..9) prefs.putFloat("band10_$i", currentGains10[i])
        prefs.apply()
    }

    private fun loadCustomPreset(): FloatArray {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
        return FloatArray(10) { i -> prefs.getFloat("band10_$i", 0f) }
    }

    private fun saveLastSessionState() {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE).edit()
        for (i in 0..9) prefs.putFloat("last10_band_$i", currentGains10[i])
        for (i in 0..4) {
            prefs.putFloat("dyn_th_$i", dynThresholds[i])
            prefs.putFloat("dyn_ra_$i", dynRatios[i])
            prefs.putFloat("dyn_ga_$i", dynPostGains[i])
        }
        prefs.putFloat("last_hpf", hpfCutoffHz)
        prefs.putFloat("last_lpf", lpfCutoffHz)
        prefs.putFloat("last_lim", limiterThreshDb)
        prefs.putFloat("last_tone_bass", toneBass)
        prefs.putFloat("last_tone_mid", toneMid)
        prefs.putFloat("last_tone_treble", toneTreble)
        prefs.putBoolean("last_tone_locked", isToneLocked)
        prefs.putBoolean("last_sub_bass", isSubBassActive)
        prefs.putBoolean("last_sub_engine", isSubwooferEngineEnabled)
        prefs.putFloat("last_sub_xover", subCrossoverHz)
        prefs.putFloat("last_sub_level", subLevelDb)
        prefs.putFloat("last_deep_bass", deepBassDb)
        prefs.putBoolean("last_clarity", isClarityActive)
        prefs.putInt("last_route_mode", channelRouteMode)
        prefs.putInt("last_boost_l", boostLeftDb)
        prefs.putInt("last_boost_r", boostRightDb)
        prefs.putInt("last_music_vol", normalMusicVolume)
        prefs.putInt("last_sfx_vol", sfxVolume)
        prefs.putInt("last_bgm_vol", bgmVolume)
        prefs.apply()
    }

    private fun migrateLegacyAggressiveAudioDefaults() {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("audio_safe_defaults_v2", false)) return

        val oldDangdut = floatArrayOf(8.5f, 8.0f, 5.5f, 2.5f, -2.0f, -3.0f, 1.5f, 3.5f, 4.5f, 5.0f)
        val oldKaraoke = floatArrayOf(5.0f, 4.5f, 3.0f, -1.5f, -3.5f, -4.0f, 1.0f, 3.0f, 4.0f, 4.5f)
        val oldCampursari = floatArrayOf(6.5f, 6.0f, 4.0f, 1.0f, -1.5f, -2.0f, 2.0f, 4.0f, 4.5f, 5.0f)
        val safeDangdut = floatArrayOf(2.0f, 2.0f, 1.5f, 0.5f, -0.5f, -1.0f, 0.0f, 1.0f, 1.5f, 1.0f)
        val safeKaraoke = floatArrayOf(1.5f, 1.0f, 0.5f, -0.5f, -1.0f, -0.5f, 0.5f, 1.0f, 1.5f, 1.5f)
        val safeCampursari = floatArrayOf(1.5f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, 0.5f, 1.0f, 1.5f, 1.5f)
        val savedEq = FloatArray(10) { i -> prefs.getFloat("last10_band_$i", Float.NaN) }
        fun matches(a: FloatArray, b: FloatArray): Boolean =
            a.indices.all { a[it].isFinite() && kotlin.math.abs(a[it] - b[it]) < 0.02f }

        val replacement = when {
            matches(savedEq, oldDangdut) -> safeDangdut
            matches(savedEq, oldKaraoke) -> safeKaraoke
            matches(savedEq, oldCampursari) -> safeCampursari
            else -> null
        }
        val edit = prefs.edit()
        if (replacement != null) {
            for (i in 0..9) {
                currentGains10[i] = replacement[i]
                edit.putFloat("last10_band_$i", replacement[i])
            }
            // The old factory EQ was paired with +6 dB channel gain. Remove that stacked gain.
            if (prefs.getInt("last_boost_l", 6) == 6 && prefs.getInt("last_boost_r", 6) == 6) {
                boostLeftDb = 0
                boostRightDb = 0
                edit.putInt("last_boost_l", 0).putInt("last_boost_r", 0)
            }
        }

        val oldDynThresholds = floatArrayOf(-14f, -10f, -10f, -12f, -14f)
        val oldDynRatios = floatArrayOf(2.8f, 2.0f, 2.0f, 2.2f, 2.5f)
        val oldDynGains = floatArrayOf(2.5f, 1.0f, 0.0f, 1.5f, 2.5f)
        val savedTh = FloatArray(5) { i -> prefs.getFloat("dyn_th_$i", Float.NaN) }
        val savedRa = FloatArray(5) { i -> prefs.getFloat("dyn_ra_$i", Float.NaN) }
        val savedGa = FloatArray(5) { i -> prefs.getFloat("dyn_ga_$i", Float.NaN) }
        if (matches(savedTh, oldDynThresholds) && matches(savedRa, oldDynRatios) && matches(savedGa, oldDynGains)) {
            val safeTh = floatArrayOf(-18f, -18f, -18f, -18f, -18f)
            val safeRa = floatArrayOf(2f, 2f, 2f, 2f, 2f)
            for (i in 0..4) {
                dynThresholds[i] = safeTh[i]
                dynRatios[i] = safeRa[i]
                dynPostGains[i] = 0f
                edit.putFloat("dyn_th_$i", safeTh[i])
                    .putFloat("dyn_ra_$i", safeRa[i])
                    .putFloat("dyn_ga_$i", 0f)
            }
        }

        if (prefs.getFloat("last_lim", -1.5f) == -1.0f) {
            limiterThreshDb = -1.5f
            edit.putFloat("last_lim", -1.5f)
        }
        edit.putBoolean("audio_safe_defaults_v2", true).apply()
    }

    private fun restoreLastSessionState() {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
        val safeFlat10 = FloatArray(10) { 0f }
        for (i in 0..9) currentGains10[i] = prefs.getFloat("last10_band_$i", safeFlat10[i])
        for (i in 0..4) {
            dynThresholds[i] = prefs.getFloat("dyn_th_$i", dynThresholds[i])
            dynRatios[i] = prefs.getFloat("dyn_ra_$i", dynRatios[i])
            dynPostGains[i] = prefs.getFloat("dyn_ga_$i", dynPostGains[i])
        }
        hpfCutoffHz = prefs.getFloat("last_hpf", 30f)
        lpfCutoffHz = prefs.getFloat("last_lpf", 18000f)
        limiterThreshDb = prefs.getFloat("last_lim", -1.5f)
        toneBass = prefs.getFloat("last_tone_bass", 0f)
        toneMid = prefs.getFloat("last_tone_mid", 0f)
        toneTreble = prefs.getFloat("last_tone_treble", 0f)
        isToneLocked = prefs.getBoolean("last_tone_locked", false)
        isSubBassActive = prefs.getBoolean("last_sub_bass", false)
        isSubwooferEngineEnabled = prefs.getBoolean("last_sub_engine", true)
        subCrossoverHz = prefs.getFloat("last_sub_xover", 80f).coerceIn(40f, 160f)
        subLevelDb = prefs.getFloat("last_sub_level", 3f).coerceIn(0f, 6f)
        deepBassDb = prefs.getFloat("last_deep_bass", 2f).coerceIn(0f, 4f)
        isClarityActive = prefs.getBoolean("last_clarity", false)
        channelRouteMode = prefs.getInt("last_route_mode", 0)
        boostLeftDb = prefs.getInt("last_boost_l", 0)
        boostRightDb = prefs.getInt("last_boost_r", 0)
        normalMusicVolume = prefs.getInt("last_music_vol", 85)
        currentMusicVolume = normalMusicVolume
        sfxVolume = prefs.getInt("last_sfx_vol", 100)
        bgmVolume = prefs.getInt("last_bgm_vol", 45)
    }

    override fun onResume() {
        super.onResume()
        ensureVisualizerPermissionAndStart()
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

    private fun setupVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            val maxCap = Visualizer.getCaptureSizeRange()[1]
            visualizer = Visualizer(0).apply {
                captureSize = min(512, maxCap)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                        if (waveform != null && waveform.isNotEmpty()) {
                            rtaView.updateWaveformFallback(waveform)
                            vuMeterView.updateFromWaveform(waveform)
                        }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        if (fft != null && fft.size >= 4) {
                            rtaView.updateFftData(fft)
                        }
                    }
                }, Visualizer.getMaxCaptureRate(), true, true)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createCardPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = makeRoundedBg("#121722", "#263248", dp(9).toFloat())
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
        timerHandler.removeCallbacks(timerRunnable)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(audioDeviceCallback)
            }
        } catch (_: Exception) {}
        stopBgmPlayer()
        try { visualizer?.release() } catch (_: Exception) {}
        try { presetReverb?.release() } catch (_: Exception) {}
        try { dynamicsProcessing?.release() } catch (_: Exception) {}
        try { fallbackEqualizer?.release() } catch (_: Exception) {}
        soundPool.release()
        super.onDestroy()
    }

    // === STEREO LED VU METER (VU L & VU R) ===
    class StereoLedVuView(context: Context) : View(context) {
        var routeMode = 0
        private var levelL = 0.05f
        private var levelR = 0.05f
        private val d = context.resources.displayMetrics.density

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0F17"); style = Paint.Style.FILL
        }
        private val segOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B"); style = Paint.Style.FILL
        }
        private val segOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }

        fun updateFromWaveform(waveform: ByteArray) {
            var sumEven = 0f
            var sumOdd = 0f
            val limit = min(waveform.size, 256)
            for (i in 0 until limit) {
                val amp = abs(((waveform[i].toInt() and 0xFF) - 128) / 128f)
                if (i % 2 == 0) sumEven += amp else sumOdd += amp
            }
            val rawA = ((sumEven / (limit / 2f)) * 4.5f).coerceIn(0.03f, 1.0f)
            val rawB = ((sumOdd / (limit / 2f)) * 4.5f).coerceIn(0.03f, 1.0f)

            val (targetL, targetR) = when (routeMode) {
                1 -> {
                    val mono = (rawA + rawB) / 2f
                    Pair(mono, mono)
                }
                2 -> Pair(rawA, 0.0f)
                3 -> Pair(0.0f, rawB)
                else -> Pair(rawA, min(1.0f, rawB * 1.08f))
            }

            levelL = max(targetL, levelL * 0.72f)
            levelR = max(targetR, levelR * 0.72f)
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 5f * d, 5f * d, bgPaint)
            val rowH = (height - 4f * d) / 2f
            drawBar(canvas, "VU L", levelL, 1.5f * d, rowH, "#22D3EE")
            drawBar(canvas, "VU R", levelR, 2.5f * d + rowH, rowH, "#F59E0B")
        }

        private fun drawBar(canvas: Canvas, label: String, level: Float, top: Float, h: Float, labelHex: String) {
            tagPaint.color = Color.parseColor(labelHex)
            canvas.drawText(label, 16f * d, top + h * 0.78f, tagPaint)

            val startX = 33f * d
            val endX = width - 6f * d
            val segments = 24
            val gap = 1.6f * d
            val segW = (endX - startX - gap * (segments - 1)) / segments
            val activeCount = (level * segments).roundToInt()

            for (i in 0 until segments) {
                val left = startX + i * (segW + gap)
                val paint = if (i < activeCount) {
                    segOnPaint.color = when {
                        i >= 20 -> Color.parseColor("#EF4444")
                        i >= 15 -> Color.parseColor("#F59E0B")
                        else -> Color.parseColor("#10B981")
                    }
                    segOnPaint
                } else {
                    segOffPaint
                }
                canvas.drawRoundRect(left, top + 1f * d, left + segW, top + h - 1f * d, 1.5f * d, 1.5f * d, paint)
            }
        }
    }

    // === 24-BAND MODERN HYBRID SPECTRUM ANALYZER (GLOWING WAVE + GRADIENT BARS + PEAK-HOLD) ===
    class RtaSpectrumView(context: Context) : View(context) {
        private val numBands = 24
        private val barLevels = FloatArray(numBands) { 0.08f }
        private val peakLevels = FloatArray(numBands) { 0.12f }
        private val peakHoldTicks = IntArray(numBands) { 0 }
        private var lastFftTimestamp = 0L
        private val d = context.resources.displayMetrics.density

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B8060A13"); style = Paint.Style.FILL
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A334155"); strokeWidth = 1f * d
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val waveLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8"); style = Paint.Style.STROKE; strokeWidth = 2.2f * d
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val waveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4438BDF8"); style = Paint.Style.STROKE; strokeWidth = 6f * d
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val areaFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F43F5E"); style = Paint.Style.FILL
        }
        private val peakCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF1F2"); style = Paint.Style.FILL
        }
        private val freqLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8"); textSize = 7.5f * d; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8"); textSize = 8.5f * d; textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD
        }
        private val dbHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B"); textSize = 7f * d; textAlign = Paint.Align.RIGHT; typeface = Typeface.MONOSPACE
        }

        private val wavePath = Path()
        private val areaPath = Path()
        private var cachedHeight = 0f

        private fun updateBandAndPeak(idx: Int, target: Float) {
            val prev = barLevels[idx]
            val next = if (target >= prev) {
                prev + (target - prev) * 0.72f
            } else {
                max(target, prev * 0.80f)
            }
            barLevels[idx] = next.coerceIn(0.05f, 1.0f)

            if (barLevels[idx] >= peakLevels[idx]) {
                peakLevels[idx] = barLevels[idx]
                peakHoldTicks[idx] = 14
            } else if (peakHoldTicks[idx] > 0) {
                peakHoldTicks[idx]--
            } else {
                peakLevels[idx] = max(barLevels[idx], peakLevels[idx] - 0.015f)
            }
        }

        fun updateFftData(fft: ByteArray) {
            val n = fft.size / 2
            var totalEnergy = 0f
            for (b in 0 until numBands) {
                val ratioStart = b.toDouble() / numBands
                val ratioEnd = (b + 1).toDouble() / numBands
                val startBin = (1 + (ratioStart.pow(1.45) * (n - 2))).toInt().coerceIn(1, n - 1)
                val endBin = (1 + (ratioEnd.pow(1.45) * (n - 2))).toInt().coerceIn(startBin, n - 1)
                var maxMag = 0f
                for (k in startBin..endBin) {
                    val re = fft[2 * k].toFloat()
                    val im = fft[2 * k + 1].toFloat()
                    val mag = hypot(re, im)
                    if (mag > maxMag) maxMag = mag
                }
                totalEnergy += maxMag
                val tiltBoost = 1.0f + (b.toFloat() / numBands) * 0.95f
                val norm = (ln(1f + maxMag * 6.5f * tiltBoost) / 3.6f).coerceIn(0.05f, 1.0f)
                updateBandAndPeak(b, norm)
            }
            if (totalEnergy > 1f) {
                lastFftTimestamp = SystemClock.elapsedRealtime()
            }
            postInvalidate()
        }

        fun updateWaveformFallback(waveform: ByteArray) {
            if (SystemClock.elapsedRealtime() - lastFftTimestamp < 400L) return
            val step = max(1, waveform.size / numBands)
            for (b in 0 until numBands) {
                var sum = 0f
                val offset = b * step
                for (j in 0 until step) {
                    if (offset + j < waveform.size) {
                        sum += abs(((waveform[offset + j].toInt() and 0xFF) - 128) / 128f)
                    }
                }
                val avg = (sum / step) * 4.4f
                val contour = 0.85f + 0.30f * sin((b.toFloat() / numBands) * PI.toFloat() * 1.5f)
                updateBandAndPeak(b, (avg * contour).coerceIn(0.05f, 1.0f))
            }
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRoundRect(0f, 0f, w, h, 8f * d, 8f * d, bgPaint)
            canvas.drawText("OHO DLMS • 24-BAND PEAK-HOLD RTA", 10f * d, 13f * d, titlePaint)
            canvas.drawText("0dB", w - 10f * d, 13f * d, dbHintPaint)

            val topY = 20f * d
            val bottomY = h - 15f * d
            val usableH = bottomY - topY
            val startX = 10f * d
            val endX = w - 10f * d
            val totalW = endX - startX
            val stepX = totalW / numBands
            val barW = stepX * 0.44f

            if (h != cachedHeight && h > 0f) {
                cachedHeight = h
                barPaint.shader = LinearGradient(
                    0f, bottomY, 0f, topY,
                    intArrayOf(
                        Color.parseColor("#06B6D4"),
                        Color.parseColor("#3B82F6"),
                        Color.parseColor("#8B5CF6"),
                        Color.parseColor("#EC4899")
                    ),
                    floatArrayOf(0.0f, 0.35f, 0.72f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                areaFillPaint.shader = LinearGradient(
                    0f, topY, 0f, bottomY,
                    Color.parseColor("#3A38BDF8"),
                    Color.parseColor("#0038BDF8"),
                    Shader.TileMode.CLAMP
                )
            }

            for (g in 1..3) {
                val gy = topY + usableH * (g / 4f)
                canvas.drawLine(startX, gy, endX, gy, gridPaint)
            }

            wavePath.reset()
            areaPath.reset()
            var prevX = startX + stepX * 0.5f
            var prevY = bottomY - barLevels[0] * usableH
            wavePath.moveTo(prevX, prevY)
            areaPath.moveTo(prevX, bottomY)
            areaPath.lineTo(prevX, prevY)

            for (i in 1 until numBands) {
                val cx = startX + i * stepX + stepX * 0.5f
                val cy = bottomY - barLevels[i] * usableH
                val midX = (prevX + cx) / 2f
                wavePath.cubicTo(midX, prevY, midX, cy, cx, cy)
                areaPath.cubicTo(midX, prevY, midX, cy, cx, cy)
                prevX = cx
                prevY = cy
            }
            areaPath.lineTo(prevX, bottomY)
            areaPath.close()
            canvas.drawPath(areaPath, areaFillPaint)
            canvas.drawPath(wavePath, waveGlowPaint)
            canvas.drawPath(wavePath, waveLinePaint)

            for (i in 0 until numBands) {
                val cx = startX + i * stepX + stepX * 0.5f
                val left = cx - barW / 2f
                val right = cx + barW / 2f
                val barTop = bottomY - barLevels[i] * usableH

                canvas.drawRoundRect(left, barTop, right, bottomY, barW / 2f, barW / 2f, barPaint)

                val peakY = bottomY - peakLevels[i] * usableH
                canvas.drawRoundRect(
                    left - 1f * d,
                    peakY - 2.6f * d,
                    right + 1f * d,
                    peakY,
                    1.5f * d,
                    1.5f * d,
                    peakPaint
                )
                canvas.drawRect(
                    left,
                    peakY - 1.8f * d,
                    right,
                    peakY - 0.8f * d,
                    peakCorePaint
                )
            }

            val markers = arrayOf("32Hz", "125Hz", "450Hz", "2.5kHz", "6kHz", "12kHz")
            for (m in markers.indices) {
                val x = (w / 6f) * m + (w / 12f)
                canvas.drawText(markers[m], x, h - 3f * d, freqLabelPaint)
            }
        }
    }

    // === VISUALIZER 5-BAND DYNAMIC COMPRESSOR ===
    class Dynamic5BandView(
        context: Context,
        private val names: Array<String>,
        private val thresholds: FloatArray,
        private val ratios: FloatArray,
        private val gains: FloatArray
    ) : View(context) {
        var selectedBand = 0
        private val d = context.resources.displayMetrics.density

        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
        private val grBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 9.5f * d; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FBBF24"); textSize = 8.5f * d; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val colW = width / 5f
            val pad = 3f * d
            for (i in 0..4) {
                val left = i * colW + pad
                val right = (i + 1) * colW - pad
                val rect = RectF(left, 2f * d, right, height - 2f * d)

                boxPaint.color = if (i == selectedBand) Color.parseColor("#162338") else Color.parseColor("#0D121B")
                strokePaint.color = if (i == selectedBand) Color.parseColor("#38BDF8") else Color.parseColor("#263248")
                canvas.drawRoundRect(rect, 6f * d, 6f * d, boxPaint)
                canvas.drawRoundRect(rect, 6f * d, 6f * d, strokePaint)

                canvas.drawText(names[i], (left + right) / 2f, 14f * d, titlePaint)

                val meterTop = 19f * d
                val meterBottom = height - 16f * d
                val meterW = 10f * d
                val cx = (left + right) / 2f
                val activity = (((thresholds[i] + 30f) / 30f) * 0.5f + (ratios[i] / 10f) * 0.5f).coerceIn(0.15f, 0.95f)
                val fillH = (meterBottom - meterTop) * activity
                grBarPaint.color = if (i == 0) Color.parseColor("#EF4444") else Color.parseColor("#F59E0B")
                canvas.drawRoundRect(cx - meterW / 2f, meterTop, cx + meterW / 2f, meterTop + fillH, 2f * d, 2f * d, grBarPaint)

                val g = gains[i]
                val gStr = if (g > 0f) "+${String.format("%.1f", g)}" else String.format("%.1f", g)
                canvas.drawText(gStr, cx, height - 4.5f * d, subPaint)
            }
        }
    }

    // === 10-BAND GRAPHIC EQ MODEL FADER FISIK + ANGKA DB ===
    class GraphicEq10BandView(
        context: Context,
        private val freqLabels: Array<String>,
        private val gains: FloatArray,
        private val onBandChanged: (Int, Float) -> Unit
    ) : View(context) {

        var isLocked = false
        private var selectedBand = -1
        private val d = context.resources.displayMetrics.density

        private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0F17"); style = Paint.Style.FILL }
        private val trackActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22D3EE"); strokeWidth = 3f * d; strokeCap = Paint.Cap.ROUND }
        private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); strokeWidth = 1.2f * d }
        private val capBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B"); style = Paint.Style.FILL }
        private val capStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38BDF8"); style = Paint.Style.STROKE; strokeWidth = 1.8f * d }
        private val capGripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC"); strokeWidth = 1.4f * d }
        private val dbTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38BDF8"); textSize = 8f * d; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        private val freqTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#94A3B8"); textSize = 8f * d; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isLocked) return false
            parent?.requestDisallowInterceptTouchEvent(true)
            val colW = width / 10f
            val topY = 18f * d
            val bottomY = height - 18f * d
            val midY = (topY + bottomY) / 2f
            val halfSpan = (bottomY - topY) / 2f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val idx = (event.x / colW).toInt().coerceIn(0, 9)
                    selectedBand = idx
                    val normY = ((midY - event.y) / halfSpan).coerceIn(-1f, 1f)
                    val db = (normY * 15f * 2f).roundToInt() / 2f
                    gains[selectedBand] = db
                    onBandChanged(selectedBand, db)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (selectedBand in 0..9) {
                        val normY = ((midY - event.y) / halfSpan).coerceIn(-1f, 1f)
                        val db = (normY * 15f * 2f).roundToInt() / 2f
                        gains[selectedBand] = db
                        onBandChanged(selectedBand, db)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    selectedBand = -1
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val colW = width / 10f
            val topY = 18f * d
            val bottomY = height - 18f * d
            val midY = (topY + bottomY) / 2f
            val halfSpan = (bottomY - topY) / 2f

            canvas.drawLine(8f * d, midY, width - 8f * d, midY, zeroLinePaint)
            val trackW = 4.5f * d
            val capW = min(colW * 0.72f, 21f * d)
            val capH = 13f * d

            for (i in 0..9) {
                val cx = colW * i + colW / 2f
                val gain = gains[i]
                val thumbY = midY - (gain / 15f) * halfSpan

                dbTextPaint.color = when {
                    isLocked -> Color.parseColor("#64748B")
                    i == selectedBand -> Color.parseColor("#FBBF24")
                    gain > 0f -> Color.parseColor("#34D399")
                    gain < 0f -> Color.parseColor("#F87171")
                    else -> Color.parseColor("#94A3B8")
                }
                val dbLabel = if (gain > 0f) "+${String.format("%.1f", gain)}" else String.format("%.1f", gain)
                canvas.drawText(dbLabel, cx, 11f * d, dbTextPaint)

                canvas.drawRoundRect(cx - trackW / 2f, topY, cx + trackW / 2f, bottomY, 2f * d, 2f * d, trackBgPaint)
                trackActivePaint.color = if (isLocked) Color.parseColor("#475569") else Color.parseColor("#22D3EE")
                canvas.drawLine(cx, midY, cx, thumbY, trackActivePaint)

                capBodyPaint.color = if (i == selectedBand) Color.parseColor("#0F172A") else Color.parseColor("#1E293B")
                capStrokePaint.color = when {
                    isLocked -> Color.parseColor("#64748B")
                    i == selectedBand -> Color.parseColor("#FBBF24")
                    else -> Color.parseColor("#38BDF8")
                }
                val capRect = RectF(cx - capW / 2f, thumbY - capH / 2f, cx + capW / 2f, thumbY + capH / 2f)
                canvas.drawRoundRect(capRect, 3f * d, 3f * d, capBodyPaint)
                canvas.drawRoundRect(capRect, 3f * d, 3f * d, capStrokePaint)
                canvas.drawLine(cx - capW * 0.3f, thumbY, cx + capW * 0.3f, thumbY, capGripPaint)
                canvas.drawText(freqLabels[i], cx, height - 4f * d, freqTextPaint)
            }
        }
    }

    // === ROTARY KNOB ===
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

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f * d; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#242D3D") }
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f * d; strokeCap = Paint.Cap.ROUND; color = accentColor }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#141A25") }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * d; color = Color.parseColor("#3B4861") }
        private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * d; strokeCap = Paint.Cap.ROUND; color = accentColor }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#94A3B8"); textSize = 8f * d; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 9.5f * d; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

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

            val formatted = if (minVal < 0 && currentVal > 0.4f) "+${currentVal.toInt()} $unit" else "${currentVal.toInt()} $unit"
            canvas.drawText(formatted, cx, cy + radius + 12f * d, valPaint)
            canvas.drawText(label, cx, cy + radius + 23f * d, labelPaint)
        }
    }
}