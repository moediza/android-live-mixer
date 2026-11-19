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
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
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
    private var isClarityActive = false
    private var ytSizeMode = 0
    private var currentBank = 0 // 0 = BANK A, 1 = BANK B

    // Parameter Volume, Gain & Pitch
    private var masterBoostDb = 6 // 0..15 dB penguat daya ke soundcard
    private var normalMusicVolume = 85
    private var currentMusicVolume = 85
    private var sfxVolume = 100
    private var bgmVolume = 45
    private var pitchSteps = 0 // -2..+2 semitone

    // Live Timer
    private var isTimerRunning = false
    private var timerBaseElapsed = 0L
    private var timerAccumulatedMs = 0L
    private val timerHandler = Handler(Looper.getMainLooper())

    // Komponen UI Utama
    private lateinit var tvLiveTimer: TextView
    private lateinit var btnTimerToggle: Button
    private lateinit var ytWebView: WebView
    private lateinit var eqView: GraphicEq10BandView
    private lateinit var vuMeter: StereoVuMeterView

    private lateinit var seekMasterBoost: SeekBar
    private lateinit var tvMasterBoostVal: TextView
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
    private lateinit var btnSubBass: Button
    private lateinit var btnClarity: Button
    private lateinit var btnBankA: Button
    private lateinit var btnBankB: Button
    private lateinit var btnBgmPlayStop: Button
    private lateinit var tvBgmTitle: TextView

    private lateinit var knobBass: RotaryKnobView
    private lateinit var knobMid: RotaryKnobView
    private lateinit var knobTreble: RotaryKnobView

    // Soundboard 16 Slot (Bank A & Bank B)
    private lateinit var soundPool: SoundPool
    private val soundMap = HashMap<String, Int>()
    private val activeStreams = ArrayList<Int>()
    private val padButtons = ArrayList<Button>()
    private var pendingPadSlotForPicker: String? = null

    private val defaultBankALabels = arrayOf("KETAWA", "APPLAUSE", "LAWAN", "GENDANG", "JANGKRIK", "AIRHORN", "INTRO", "CIHUY")
    private val defaultBankAAssets = arrayOf("ketawa.mp3", "applause.mp3", "lawan.mp3", "gendang.mp3", "jangkrik.mp3", "airhorn.mp3", "intro.mp3", "lawan.mp3")
    private val defaultBankBLabels = arrayOf("DRUM ROLL", "TROMPET", "WADUH", "SIRINE", "HORAY", "SUSPENSE", "CLOSING", "EXTRA")
    private val defaultBankBAssets = arrayOf("gendang.mp3", "airhorn.mp3", "jangkrik.mp3", "airhorn.mp3", "applause.mp3", "intro.mp3", "intro.mp3", "ketawa.mp3")

    // BGM Ngobrol Player
    private var bgmPlayer: MediaPlayer? = null
    private var isBgmPlaying = false

    // Audio DSP
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var fallbackEqualizer: Equalizer? = null
    private var visualizer: Visualizer? = null

    // 10-Band EQ Standar Pro
    private val defaultFreqs10 = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val freqLabels10 = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    private val currentGains10 = floatArrayOf(6.0f, 5.5f, 3.5f, -1.0f, -2.5f, -2.0f, 1.5f, 3.5f, 4.5f, 5.0f)

    private var toneBass = 0f
    private var toneMid = 0f
    private var toneTreble = 0f

    // Picker Audio Soundboard
    private val pickSfxLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val slot = pendingPadSlotForPicker
        if (uri != null && slot != null) {
            importCustomSoundToPad(slot, uri)
        }
    }

    // Picker Audio BGM Ngobrol
    private val pickBgmLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            importCustomBgmFile(uri)
        }
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

    private fun makeConsoleButton(
        label: String,
        fillHex: String,
        strokeHex: String,
        textColorHex: String = "#FFFFFF",
        textSp: Float = 10f
    ): Button = Button(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(textColorHex))
        background = makeRoundedBg(fillHex, strokeHex, dp(8).toFloat())
        gravity = Gravity.CENTER
        setPadding(dp(5), 0, dp(5), 0)
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

        setupAudioDspPro()
        setupSoundboard16()

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0E14"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(16))
        }

        // === 1. TOP MASTER HEADER & LIVE TIMER ===
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(TextView(this).apply {
            text = "OHO AUDIO MIXER"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F8FAFC"))
        })

        // Baris Live Timer
        val timerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
        }
        tvLiveTimer = TextView(this).apply {
            text = "00:00:00"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, dp(6), 0)
        }
        btnTimerToggle = makeConsoleButton("START", "#0F172A", "#38BDF8", "#38BDF8", 8.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(22)).apply { marginEnd = dp(4) }
            setOnClickListener {
                if (!isTimerRunning) {
                    isTimerRunning = true
                    timerBaseElapsed = SystemClock.elapsedRealtime()
                    text = "PAUSE"
                    background = makeRoundedBg("#B45309", "#FBBF24", dp(6).toFloat())
                    setTextColor(Color.WHITE)
                    timerHandler.post(timerRunnable)
                } else {
                    isTimerRunning = false
                    timerAccumulatedMs += (SystemClock.elapsedRealtime() - timerBaseElapsed)
                    text = "START"
                    background = makeRoundedBg("#0F172A", "#38BDF8", dp(6).toFloat())
                    setTextColor(Color.parseColor("#38BDF8"))
                }
            }
        }
        val btnTimerReset = makeConsoleButton("RST", "#1E293B", "#475569", "#94A3B8", 8.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(22))
            setOnClickListener {
                isTimerRunning = false
                timerAccumulatedMs = 0L
                timerBaseElapsed = SystemClock.elapsedRealtime()
                updateTimerDisplay(0L)
                btnTimerToggle.text = "START"
                btnTimerToggle.background = makeRoundedBg("#0F172A", "#38BDF8", dp(6).toFloat())
                btnTimerToggle.setTextColor(Color.parseColor("#38BDF8"))
            }
        }
        timerRow.addView(tvLiveTimer)
        timerRow.addView(btnTimerToggle)
        timerRow.addView(btnTimerReset)
        titleBox.addView(timerRow)

        btnEqToggle = makeConsoleButton("EQ ON", "#10B981", "#34D399", "#FFFFFF", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(34)).apply { marginEnd = dp(4) }
            setOnClickListener {
                isEqEnabled = !isEqEnabled
                applyDspEnabled(isEqEnabled)
                text = if (isEqEnabled) "EQ ON" else "BYPASS"
                background = if (isEqEnabled) makeRoundedBg("#10B981", "#34D399", dp(8).toFloat())
                else makeRoundedBg("#334155", "#64748B", dp(8).toFloat())
            }
        }

        val btnSave = makeConsoleButton("SAVE", "#1E293B", "#475569", "#E2E8F0", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(34)).apply { marginEnd = dp(4) }
            setOnClickListener {
                saveCustomPreset()
                saveLastSessionState()
                Toast.makeText(context, "Setelan 10-Band EQ tersimpan ke CUSTOM!", Toast.LENGTH_SHORT).show()
            }
        }

        btnMuteAll = makeConsoleButton("MUTE", "#1E293B", "#F59E0B", "#FBBF24", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(34)).apply { marginEnd = dp(4) }
            setOnClickListener {
                isMasterMuted = !isMasterMuted
                if (isMasterMuted) {
                    stopAllSounds()
                    applyYoutubeVolume(0)
                    bgmPlayer?.setVolume(0f, 0f)
                    text = "MUTED"
                    background = makeRoundedBg("#D97706", "#FDE047", dp(8).toFloat())
                    setTextColor(Color.WHITE)
                } else {
                    applyYoutubeVolume(if (isYtMuted) 0 else currentMusicVolume)
                    val bVol = bgmVolume / 100f
                    bgmPlayer?.setVolume(bVol, bVol)
                    text = "MUTE"
                    background = makeRoundedBg("#1E293B", "#F59E0B", dp(8).toFloat())
                    setTextColor(Color.parseColor("#FBBF24"))
                }
            }
        }

        val btnExit = makeConsoleButton("EXIT", "#450A0A", "#EF4444", "#FCA5A5", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(34))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Keluar OHO AUDIO MIXER?")
                    .setMessage("Semua pengaturan 10-Band EQ, Master Boost, dan Volume akan disimpan otomatis.")
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

        // === STEREO LED VU METER ===
        val vuBarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }
        vuBarRow.addView(TextView(this).apply {
            text = "OUT VU"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, dp(6), 0)
        })
        vuMeter = StereoVuMeterView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(13), 1f)
        }
        vuBarRow.addView(vuMeter)
        root.addView(vuBarRow)

        // === 2. YOUTUBE KARAOKE PLAYER (NO ADS) & PITCH / TRANSPORT BAR ===
        val ytCard = createCardPanel().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val ytBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }

        ytBar.addView(TextView(this).apply {
            text = "▶ YOUTUBE (NO ADS)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F87171"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val btnSearchYt = makeConsoleButton("🔍 CARI LAGU", "#DC2626", "#F87171", "#FFFFFF", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                ytWebView.loadUrl("https://m.youtube.com/results?search_query=karaoke+dangdut+campursari+lirik")
            }
        }

        val btnResizeYt = makeConsoleButton("PERBESAR / LIPAT", "#1E293B", "#475569", "#E2E8F0", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30))
        }

        ytBar.addView(btnSearchYt)
        ytBar.addView(btnResizeYt)
        ytCard.addView(ytBar)

        ytWebView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(235))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            webViewClient = object : WebViewClient() {
                private val adHosts = listOf(
                    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                    "adservice.google", "pagead2.googlesyndication.com", "ads.youtube.com"
                )

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString()?.lowercase() ?: return super.shouldInterceptRequest(view, request)
                    val isAdUrl = adHosts.any { url.contains(it) } ||
                            url.contains("/pagead/") || url.contains("/api/stats/ads") || url.contains("/ptracking")
                    return if (isAdUrl) WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    else super.shouldInterceptRequest(view, request)
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
                                'ytm-promoted-sparkles-web-renderer', 'ytm-companion-slot-renderer',
                                'ad-slot-renderer', 'ytm-statement-banner-renderer',
                                '.ad-container', '.ytp-ad-overlay-container', '.ytp-ad-message-container'
                            ];
                            setInterval(function() {
                                selectors.forEach(function(sel) {
                                    document.querySelectorAll(sel).forEach(function(el) { el.style.display = 'none'; });
                                });
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
                            }, 300);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(adBlockJs, null)
                    applyYoutubeVolume(if (isMasterMuted || isYtMuted) 0 else currentMusicVolume)
                    applyYoutubePitchSteps(pitchSteps)
                    applyYoutubeLoop(isYtLooping)
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

        // Baris 1 Kendali YouTube: -10s | PLAY/PAUSE | +10s | MUTE YT
        val ytTransportRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(4))
        }

        val btnRewind = makeConsoleButton("⏪ -10s", "#1E293B", "#475569", "#E2E8F0", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                ytWebView.evaluateJavascript("var v=document.querySelector('video'); if(v){ v.currentTime=Math.max(0,v.currentTime-10); }", null)
            }
        }

        val btnPlayPause = makeConsoleButton("⏯ PLAY/PAUSE", "#0F172A", "#38BDF8", "#38BDF8", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1.3f).apply { marginEnd = dp(4) }
            setOnClickListener {
                ytWebView.evaluateJavascript("var v=document.querySelector('video'); if(v){ if(v.paused) v.play(); else v.pause(); }", null)
            }
        }

        val btnForward = makeConsoleButton("+10s ⏩", "#1E293B", "#475569", "#E2E8F0", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                ytWebView.evaluateJavascript("var v=document.querySelector('video'); if(v && isFinite(v.duration)){ v.currentTime=Math.min(v.duration,v.currentTime+10); }", null)
            }
        }

        btnMuteYt = makeConsoleButton("🔇 MUTE YT", "#1E293B", "#EF4444", "#FCA5A5", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1.1f)
            setOnClickListener {
                isYtMuted = !isYtMuted
                if (isYtMuted) {
                    applyYoutubeVolume(0)
                    text = "🔇 YT MUTED"
                    background = makeRoundedBg("#B91C1C", "#FCA5A5", dp(8).toFloat())
                    setTextColor(Color.WHITE)
                } else {
                    applyYoutubeVolume(if (isMasterMuted) 0 else currentMusicVolume)
                    text = "🔇 MUTE YT"
                    background = makeRoundedBg("#1E293B", "#EF4444", dp(8).toFloat())
                    setTextColor(Color.parseColor("#FCA5A5"))
                }
            }
        }

        ytTransportRow1.addView(btnRewind)
        ytTransportRow1.addView(btnPlayPause)
        ytTransportRow1.addView(btnForward)
        ytTransportRow1.addView(btnMuteYt)
        ytCard.addView(ytTransportRow1)

        // Baris 2 Kendali YouTube: KEY - | KEY 0 (Reset) | KEY + | LOOP
        val ytTransportRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnKeyDown = makeConsoleButton("♭ KEY -", "#1E293B", "#A855F7", "#E9D5FF", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                if (pitchSteps > -2) {
                    pitchSteps--
                    updatePitchUiAndApply()
                }
            }
        }

        btnKeyDisplay = makeConsoleButton("NADA: 0", "#161E2E", "#64748B", "#F8FAFC", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1.1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                pitchSteps = 0
                updatePitchUiAndApply()
                Toast.makeText(context, "Nada dikembalikan ke asli (0)", Toast.LENGTH_SHORT).show()
            }
        }

        val btnKeyUp = makeConsoleButton("♯ KEY +", "#1E293B", "#A855F7", "#E9D5FF", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                if (pitchSteps < 2) {
                    pitchSteps++
                    updatePitchUiAndApply()
                }
            }
        }

        btnYtLoop = makeConsoleButton("🔁 LOOP", "#1E293B", "#475569", "#94A3B8", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
            setOnClickListener {
                isYtLooping = !isYtLooping
                applyYoutubeLoop(isYtLooping)
                if (isYtLooping) {
                    background = makeRoundedBg("#065F46", "#34D399", dp(8).toFloat())
                    setTextColor(Color.WHITE)
                } else {
                    background = makeRoundedBg("#1E293B", "#475569", dp(8).toFloat())
                    setTextColor(Color.parseColor("#94A3B8"))
                }
            }
        }

        ytTransportRow2.addView(btnKeyDown)
        ytTransportRow2.addView(btnKeyDisplay)
        ytTransportRow2.addView(btnKeyUp)
        ytTransportRow2.addView(btnYtLoop)
        ytCard.addView(ytTransportRow2)

        btnResizeYt.setOnClickListener {
            ytSizeMode = (ytSizeMode + 1) % 3
            val lp = ytWebView.layoutParams
            when (ytSizeMode) {
                0 -> { ytWebView.visibility = View.VISIBLE; lp.height = dp(235); btnResizeYt.text = "PERBESAR / LIPAT" }
                1 -> { ytWebView.visibility = View.VISIBLE; lp.height = dp(370); btnResizeYt.text = "LIPAT VIDEO" }
                2 -> { ytWebView.visibility = View.GONE; btnResizeYt.text = "BUKA VIDEO" }
            }
            ytWebView.layoutParams = lp
        }

        root.addView(ytCard)
        root.addView(spacer(dp(8)))

        // === 3. MASTER BOOST + DUAL CHANNEL FADER (MUSIC & SFX + SMART DUCKING) ===
        val volCard = createCardPanel().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }

        // Baris 1: MASTER BOOST (Penguat Daya ke Soundcard)
        val boostRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        boostRow.addView(TextView(this).apply {
            text = "PWR BOOST"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#10B981"))
            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekMasterBoost = SeekBar(this).apply {
            max = 15
            progress = masterBoostDb
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4); marginEnd = dp(4)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    masterBoostDb = p
                    tvMasterBoostVal.text = "+${p}dB"
                    applyMasterBoostGain(p.toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        }
        tvMasterBoostVal = TextView(this).apply {
            text = "+${masterBoostDb}dB"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#34D399"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
        }
        boostRow.addView(seekMasterBoost)
        boostRow.addView(tvMasterBoostVal)
        boostRow.addView(TextView(this).apply {
            text = "LIMITER ON"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#34D399"))
            gravity = Gravity.CENTER
            background = makeRoundedBg("#064E3B", "#10B981", dp(6).toFloat())
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(82), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        volCard.addView(boostRow)

        // Baris 2: MUSIC VOL + SMART DUCKING
        val musicVolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        musicVolRow.addView(TextView(this).apply {
            text = "MUSIC VOL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekMusicVol = SeekBar(this).apply {
            max = 100
            progress = normalMusicVolume
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4); marginEnd = dp(4)
            }
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
        }
        btnDucking = makeConsoleButton("DUCKING", "#1E293B", "#475569", "#FFFFFF", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(82), dp(30))
            setOnClickListener {
                isDucking = !isDucking
                val startVol = seekMusicVol.progress
                val targetVol = if (isDucking) (normalMusicVolume * 0.40f).toInt().coerceAtLeast(10) else normalMusicVolume
                animateMusicVolumeFade(startVol, targetVol)
                background = if (isDucking) makeRoundedBg("#D97706", "#FBBF24", dp(8).toFloat())
                else makeRoundedBg("#1E293B", "#475569", dp(8).toFloat())
            }
        }
        musicVolRow.addView(seekMusicVol)
        musicVolRow.addView(tvMusicVolPct)
        musicVolRow.addView(btnDucking)
        volCard.addView(musicVolRow)

        // Baris 3: SFX VOL (Independent Soundboard)
        val sfxVolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sfxVolRow.addView(TextView(this).apply {
            text = "SFX VOL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F59E0B"))
            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekSfxVol = SeekBar(this).apply {
            max = 100
            progress = sfxVolume
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4); marginEnd = dp(4)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    sfxVolume = p
                    tvSfxVolPct.text = "$p%"
                    val volFloat = sfxVolume / 100f
                    for (sid in activeStreams) soundPool.setVolume(sid, volFloat, volFloat)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        }
        tvSfxVolPct = TextView(this).apply {
            text = "$sfxVolume%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
        }
        sfxVolRow.addView(seekSfxVol)
        sfxVolRow.addView(tvSfxVolPct)
        sfxVolRow.addView(TextView(this).apply {
            text = "SFX 100%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(82), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        volCard.addView(sfxVolRow)

        root.addView(volCard)
        root.addView(spacer(dp(8)))

        // === 4. 10-BAND PHYSICAL FADER EQ + SUB-BASS / CLARITY + TONE LOCK ===
        val eqCard = createCardPanel().apply { setPadding(dp(8), dp(8), dp(8), dp(10)) }

        val eqHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }
        eqHeaderRow.addView(TextView(this).apply {
            text = "10-BAND GRAPHIC EQ"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val btnFlatReset = makeConsoleButton("FLAT 0dB", "#1E293B", "#64748B", "#CBD5E1", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)).apply {
                marginEnd = dp(5)
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
                Toast.makeText(context, "Semua 10 Band & Tone di-reset ke 0 dB", Toast.LENGTH_SHORT).show()
            }
        }

        btnLockTone = makeConsoleButton(
            if (isToneLocked) "🔒 TERKUNCI" else "🔓 UNLOCKED",
            if (isToneLocked) "#B91C1C" else "#1E293B",
            if (isToneLocked) "#F87171" else "#38BDF8",
            "#FFFFFF",
            9f
        ).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28))
            setOnClickListener {
                isToneLocked = !isToneLocked
                applyLockUiState()
                saveLastSessionState()
            }
        }
        eqHeaderRow.addView(btnFlatReset)
        eqHeaderRow.addView(btnLockTone)
        eqCard.addView(eqHeaderRow)

        // 10-Band Vertical Physical Faders + Angka dB
        eqView = GraphicEq10BandView(this, freqLabels10, currentGains10) { bandIdx, gainDb ->
            currentGains10[bandIdx] = gainDb
            applySingleBandCalculated10(bandIdx)
            saveLastSessionState()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(168))
        }
        eqCard.addView(eqView)

        // Baris Preset 10-Band
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(6))
        }
        val presets10 = listOf(
            "DANGDUT" to floatArrayOf(6.0f, 5.5f, 3.5f, -1.0f, -2.5f, -2.0f, 1.5f, 3.5f, 4.5f, 5.0f),
            "KARAOKE" to floatArrayOf(4.0f, 3.5f, 2.0f, -1.5f, -3.5f, -4.0f, 1.0f, 3.0f, 4.0f, 4.5f),
            "CAMPURSARI" to floatArrayOf(5.0f, 4.5f, 3.0f, 0.5f, -1.5f, -2.0f, 2.0f, 4.0f, 4.5f, 5.0f),
            "CUSTOM" to loadCustomPreset()
        )
        presets10.forEachIndexed { idx, (label, values) ->
            val btn = makeConsoleButton(label, "#1E293B", "#38BDF8", "#F1F5F9", 9.5f).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    if (idx < presets10.size - 1) marginEnd = dp(5)
                }
                setOnClickListener {
                    if (isToneLocked) {
                        Toast.makeText(context, "Buka kunci (🔒 TERKUNCI) dulu untuk ganti preset", Toast.LENGTH_SHORT).show()
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
        eqCard.addView(presetRow)

        // Baris Tombol Multi-Band Compressor: SUB-BASS BOOST & CLARITY
        val mbcRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        btnSubBass = makeConsoleButton("🔊 SUB-BASS PUNCH", "#1E293B", "#F59E0B", "#FDE68A", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(6) }
            setOnClickListener {
                isSubBassActive = !isSubBassActive
                applyMultiBandDynamics()
                updateMbcButtonsUi()
                saveLastSessionState()
            }
        }
        btnClarity = makeConsoleButton("✨ VOCAL CLARITY", "#1E293B", "#22D3EE", "#CFFAFE", 9.5f).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
            setOnClickListener {
                isClarityActive = !isClarityActive
                applyMultiBandDynamics()
                updateMbcButtonsUi()
                saveLastSessionState()
            }
        }
        mbcRow.addView(btnSubBass)
        mbcRow.addView(btnClarity)
        eqCard.addView(mbcRow)

        // 3 Rotary Knobs
        val toneContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedBg("#181F2F", "#1E293B", dp(10).toFloat())
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        val knobsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
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
        val knobLayout = LinearLayout.LayoutParams(0, dp(90), 1f)
        knobsRow.addView(knobBass, knobLayout)
        knobsRow.addView(knobMid, knobLayout)
        knobsRow.addView(knobTreble, knobLayout)
        toneContainer.addView(knobsRow)

        eqCard.addView(toneContainer)
        root.addView(eqCard)
        root.addView(spacer(dp(8)))

        // === 5. SOUNDBOARD BANK A / BANK B (16 SLOT + UBAH NAMA & GANTI MP3) ===
        val sbdCard = createCardPanel().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val sbdHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }
        sbdHeader.addView(TextView(this).apply {
            text = "SOUNDBOARD (TAHAN PAD = EDIT)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        btnBankA = makeConsoleButton("BANK A", "#2563EB", "#60A5FA", "#FFFFFF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(26)).apply { marginEnd = dp(4) }
            setOnClickListener { switchSoundboardBank(0) }
        }
        btnBankB = makeConsoleButton("BANK B", "#1E293B", "#475569", "#94A3B8", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(26)).apply { marginEnd = dp(6) }
            setOnClickListener { switchSoundboardBank(1) }
        }
        val btnStopAllSfx = makeConsoleButton("⏹ STOP ALL", "#7F1D1D", "#EF4444", "#FCA5A5", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(26))
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
            val btn = makeConsoleButton("", "#161E2E", neonColors[i], "#FFFFFF", 10f).apply {
                setOnClickListener {
                    val slotKey = getSlotKey(currentBank, i)
                    playSoundPad(slotKey)
                }
                setOnLongClickListener {
                    val slotKey = getSlotKey(currentBank, i)
                    showEditPadDialog(currentBank, i, slotKey)
                    true
                }
            }
            val gp = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(44)
                columnSpec = GridLayout.spec(i % 4, 1f)
                rowSpec = GridLayout.spec(i / 4, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            padButtons.add(btn)
            sbdGrid.addView(btn, gp)
        }
        sbdCard.addView(sbdGrid)
        root.addView(sbdCard)
        root.addView(spacer(dp(8)))

        // === 6. PEMUTAR KHUSUS BGM NGOBROL (AUTO-LOOP MP3) ===
        val bgmCard = createCardPanel().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val bgmTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        tvBgmTitle = TextView(this).apply {
            text = "🎵 BGM NGOBROL: (Belum dipilih)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#CBD5E1"))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnPickBgm = makeConsoleButton("📂 PILIH MP3", "#1E293B", "#475569", "#E2E8F0", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)).apply {
                marginEnd = dp(4)
            }
            setOnClickListener { pickBgmLauncher.launch("audio/*") }
        }
        btnBgmPlayStop = makeConsoleButton("▶ LOOP BGM", "#065F46", "#10B981", "#FFFFFF", 9f).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        seekBgmVol = SeekBar(this).apply {
            max = 100
            progress = bgmVolume
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    bgmVolume = p
                    tvBgmVolPct.text = "$p%"
                    if (!isMasterMuted) {
                        val v = p / 100f
                        bgmPlayer?.setVolume(v, v)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { saveLastSessionState() }
            })
        }
        tvBgmVolPct = TextView(this).apply {
            text = "$bgmVolume%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        bgmVolRow.addView(seekBgmVol)
        bgmVolRow.addView(tvBgmVolPct)
        bgmCard.addView(bgmVolRow)
        root.addView(bgmCard)

        rootScroll.addView(root)
        setContentView(rootScroll)

        // Inisialisasi Audio, DSP & Tampilan Awal
        lockHardwareMediaVolumeMax()
        applyMasterBoostGain(masterBoostDb.toFloat())
        applyAllBands10()
        applyMultiBandDynamics()
        updateMbcButtonsUi()
        applyLockUiState()
        switchSoundboardBank(0)
        restoreSavedBgmInfo()
        ensureVisualizerPermissionAndStart()
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
        btnKeyDisplay.background = if (pitchSteps != 0) makeRoundedBg("#4C1D95", "#C084FC", dp(8).toFloat())
        else makeRoundedBg("#161E2E", "#64748B", dp(8).toFloat())
        applyYoutubePitchSteps(pitchSteps)
    }

    private fun applyYoutubePitchSteps(steps: Int) {
        if (!this::ytWebView.isInitialized) return
        val rate = 2.0.pow(steps / 12.0)
        val js = """
            (function() {
                var v = document.querySelector('video');
                if (v) {
                    v.preservesPitch = false;
                    v.mozPreservesPitch = false;
                    v.webkitPreservesPitch = false;
                    v.playbackRate = $rate;
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
        btnSubBass.background = if (isSubBassActive) makeRoundedBg("#B45309", "#FBBF24", dp(8).toFloat())
        else makeRoundedBg("#1E293B", "#F59E0B", dp(8).toFloat())
        btnSubBass.setTextColor(if (isSubBassActive) Color.WHITE else Color.parseColor("#FDE68A"))

        btnClarity.background = if (isClarityActive) makeRoundedBg("#0E7490", "#22D3EE", dp(8).toFloat())
        else makeRoundedBg("#1E293B", "#22D3EE", dp(8).toFloat())
        btnClarity.setTextColor(if (isClarityActive) Color.WHITE else Color.parseColor("#CFFAFE"))
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

    // === DSP ENGINE: 10-BAND PRE-EQ + 3-BAND MBC (SUB-BASS/CLARITY) + INPUT GAIN + LIMITER ===
    private fun setupAudioDspPro() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, 10, // 10-Band Pre-EQ
                    true, 3,  // 3-Band Multi-Band Compressor (Sub-Bass, Mid, Clarity)
                    false, 0,
                    true      // Output Limiter
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, 0, cfg).apply {
                    enabled = true
                    setInputGainAllChannelsTo(masterBoostDb.toFloat())
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

                        val limiter = getLimiterByChannelIndex(ch)
                        limiter.isEnabled = true
                        limiter.threshold = -1.0f
                        limiter.ratio = 12f
                        limiter.attackTime = 1.5f
                        limiter.releaseTime = 50f
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

    private fun applyMasterBoostGain(gainDb: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dynamicsProcessing?.setInputGainAllChannelsTo(gainDb)
            }
        } catch (_: Exception) {}
    }

    private fun applyMultiBandDynamics() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                for (ch in 0..1) {
                    // Band 0: Sub-Bass (<= 140 Hz)
                    val bSub = dynamicsProcessing!!.getMbcBandByChannelIndex(ch, 0)
                    bSub.isEnabled = true
                    bSub.cutoffFrequency = 140f
                    bSub.attackTime = 15f
                    bSub.releaseTime = 120f
                    bSub.threshold = if (isSubBassActive) -18f else -6f
                    bSub.ratio = if (isSubBassActive) 3.5f else 1.2f
                    bSub.postGain = if (isSubBassActive) 4.5f else 0f
                    dynamicsProcessing!!.setMbcBandByChannelIndex(ch, 0, bSub)

                    // Band 1: Midrange (140 Hz - 3500 Hz)
                    val bMid = dynamicsProcessing!!.getMbcBandByChannelIndex(ch, 1)
                    bMid.isEnabled = true
                    bMid.cutoffFrequency = 3500f
                    bMid.attackTime = 10f
                    bMid.releaseTime = 100f
                    bMid.threshold = if (isClarityActive) -16f else -6f
                    bMid.ratio = if (isClarityActive) 2.5f else 1.1f
                    bMid.postGain = if (isClarityActive) -1.5f else 0f
                    dynamicsProcessing!!.setMbcBandByChannelIndex(ch, 1, bMid)

                    // Band 2: High Clarity (>= 3500 Hz)
                    val bHigh = dynamicsProcessing!!.getMbcBandByChannelIndex(ch, 2)
                    bHigh.isEnabled = true
                    bHigh.cutoffFrequency = 20000f
                    bHigh.attackTime = 8f
                    bHigh.releaseTime = 80f
                    bHigh.threshold = if (isClarityActive) -18f else -6f
                    bHigh.ratio = if (isClarityActive) 2.8f else 1.1f
                    bHigh.postGain = if (isClarityActive) 4.0f else 0f
                    dynamicsProcessing!!.setMbcBandByChannelIndex(ch, 2, bHigh)
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
        val calculatedGain = (currentGains10[bandIdx] + toneOffset).coerceIn(-15f, 15f)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamicsProcessing != null) {
                for (ch in 0..1) {
                    val band = dynamicsProcessing!!.getPreEqBandByChannelIndex(ch, bandIdx)
                    band.cutoffFrequency = defaultFreqs10[bandIdx]
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
    }

    // === SOUNDBOARD 16 SLOT (BANK A & BANK B) ===
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
            if (i < padButtons.size) {
                padButtons[i].text = getPadLabel(bank, i)
            }
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
        val streamId = soundPool.play(id, volFloat, volFloat, 1, 0, 1.0f)
        if (streamId != 0) activeStreams.add(streamId)
    }

    private fun stopAllSounds() {
        for (sid in activeStreams) soundPool.stop(sid)
        activeStreams.clear()
    }

    // === PEMUTAR KHUSUS BGM NGOBROL (AUTO-LOOP) ===
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
                    setVolume(v, v)
                    prepare()
                    start()
                }
                isBgmPlaying = true
                btnBgmPlayStop.text = "⏹ STOP BGM"
                btnBgmPlayStop.background = makeRoundedBg("#B91C1C", "#F87171", dp(8).toFloat())
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
            btnBgmPlayStop.background = makeRoundedBg("#065F46", "#10B981", dp(8).toFloat())
        }
    }

    // === PENYIMPANAN SESI & PRESET ===
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
        prefs.putFloat("last_tone_bass", toneBass)
        prefs.putFloat("last_tone_mid", toneMid)
        prefs.putFloat("last_tone_treble", toneTreble)
        prefs.putBoolean("last_tone_locked", isToneLocked)
        prefs.putBoolean("last_sub_bass", isSubBassActive)
        prefs.putBoolean("last_clarity", isClarityActive)
        prefs.putInt("last_master_boost", masterBoostDb)
        prefs.putInt("last_music_vol", normalMusicVolume)
        prefs.putInt("last_sfx_vol", sfxVolume)
        prefs.putInt("last_bgm_vol", bgmVolume)
        prefs.apply()
    }

    private fun restoreLastSessionState() {
        val prefs = getSharedPreferences("OhoMixerPrefs", Context.MODE_PRIVATE)
        val defaultDangdut10 = floatArrayOf(6.0f, 5.5f, 3.5f, -1.0f, -2.5f, -2.0f, 1.5f, 3.5f, 4.5f, 5.0f)
        for (i in 0..9) {
            currentGains10[i] = prefs.getFloat("last10_band_$i", defaultDangdut10[i])
        }
        toneBass = prefs.getFloat("last_tone_bass", 0f)
        toneMid = prefs.getFloat("last_tone_mid", 0f)
        toneTreble = prefs.getFloat("last_tone_treble", 0f)
        isToneLocked = prefs.getBoolean("last_tone_locked", false)
        isSubBassActive = prefs.getBoolean("last_sub_bass", false)
        isClarityActive = prefs.getBoolean("last_clarity", false)
        masterBoostDb = prefs.getInt("last_master_boost", 6)
        normalMusicVolume = prefs.getInt("last_music_vol", 85)
        currentMusicVolume = normalMusicVolume
        sfxVolume = prefs.getInt("last_sfx_vol", 100)
        bgmVolume = prefs.getInt("last_bgm_vol", 45)
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
        timerHandler.removeCallbacks(timerRunnable)
        stopBgmPlayer()
        try { visualizer?.release() } catch (_: Exception) {}
        try { dynamicsProcessing?.release() } catch (_: Exception) {}
        try { fallbackEqualizer?.release() } catch (_: Exception) {}
        soundPool.release()
        super.onDestroy()
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

        private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0F17")
            style = Paint.Style.FILL
        }
        private val trackActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22D3EE")
            strokeWidth = 3f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            strokeWidth = 1.2f * d
        }
        private val capBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        private val capStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * d
        }
        private val capGripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            strokeWidth = 1.4f * d
        }
        private val dbTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 8.5f * d
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val freqTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 8.5f * d
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isLocked) return false

            parent?.requestDisallowInterceptTouchEvent(true)
            val colW = width / 10f
            val topY = 20f * d
            val bottomY = height - 20f * d
            val midY = (topY + bottomY) / 2f
            val halfSpan = (bottomY - topY) / 2f

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val idx = (event.x / colW).toInt().coerceIn(0, 9)
                    selectedBand = idx
                    val normY = ((midY - event.y) / halfSpan).coerceIn(-1f, 1f)
                    val db = (normY * 15f * 2f).roundToInt() / 2f // Kelipatan 0.5 dB agar presisi
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
            val topY = 20f * d
            val bottomY = height - 20f * d
            val midY = (topY + bottomY) / 2f
            val halfSpan = (bottomY - topY) / 2f

            // Garis referensi tengah 0 dB
            canvas.drawLine(8f * d, midY, width - 8f * d, midY, zeroLinePaint)

            val trackW = 5f * d
            val capW = min(colW * 0.72f, 22f * d)
            val capH = 14f * d

            for (i in 0..9) {
                val cx = colW * i + colW / 2f
                val gain = gains[i]
                val thumbY = midY - (gain / 15f) * halfSpan

                // Angka dB di bagian atas masing-masing fader
                dbTextPaint.color = when {
                    isLocked -> Color.parseColor("#64748B")
                    i == selectedBand -> Color.parseColor("#FBBF24")
                    gain > 0f -> Color.parseColor("#34D399")
                    gain < 0f -> Color.parseColor("#F87171")
                    else -> Color.parseColor("#94A3B8")
                }
                val dbLabel = if (gain > 0f) "+${String.format("%.1f", gain)}" else String.format("%.1f", gain)
                canvas.drawText(dbLabel, cx, 12f * d, dbTextPaint)

                // Rel Fader Vertikal
                canvas.drawRoundRect(
                    cx - trackW / 2f, topY, cx + trackW / 2f, bottomY,
                    2.5f * d, 2.5f * d, trackBgPaint
                )

                // Garis menyala dari 0 dB ke posisi fader
                trackActivePaint.color = if (isLocked) Color.parseColor("#475569") else Color.parseColor("#22D3EE")
                canvas.drawLine(cx, midY, cx, thumbY, trackActivePaint)

                // Kepala Tuas (Fader Cap)
                capBodyPaint.color = if (i == selectedBand) Color.parseColor("#0F172A") else Color.parseColor("#1E293B")
                capStrokePaint.color = when {
                    isLocked -> Color.parseColor("#64748B")
                    i == selectedBand -> Color.parseColor("#FBBF24")
                    else -> Color.parseColor("#38BDF8")
                }
                val capRect = RectF(cx - capW / 2f, thumbY - capH / 2f, cx + capW / 2f, thumbY + capH / 2f)
                canvas.drawRoundRect(capRect, 3f * d, 3f * d, capBodyPaint)
                canvas.drawRoundRect(capRect, 3f * d, 3f * d, capStrokePaint)

                // Garis tengah pada kepala tuas fisik
                canvas.drawLine(cx - capW * 0.3f, thumbY, cx + capW * 0.3f, thumbY, capGripPaint)

                // Label Frekuensi di bawah
                canvas.drawText(freqLabels[i], cx, height - 4f * d, freqTextPaint)
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
            color = Color.parseColor("#94A3B8"); textSize = 8f * d
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 9.5f * d
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
            canvas.drawText(formatted, cx, cy + radius + 12f * d, valPaint)
            canvas.drawText(label, cx, cy + radius + 23f * d, labelPaint)
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