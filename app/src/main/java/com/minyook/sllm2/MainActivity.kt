package com.minyook.sllm2

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.minyook.sllm2.data.KnowledgeRepository
import com.minyook.sllm2.data.KnowledgeSeeder
import com.minyook.sllm2.data.ChatHistoryStore
import com.minyook.sllm2.model.DeviceProfile
import com.minyook.sllm2.model.ContextTokenPolicy
import com.minyook.sllm2.model.InferenceBackend
import com.minyook.sllm2.model.InferenceSettings
import com.minyook.sllm2.model.InferenceSettingsPreferences
import com.minyook.sllm2.model.LocalModelRuntime
import com.minyook.sllm2.model.ModelDownloadScheduler
import com.minyook.sllm2.model.ModelPhase
import com.minyook.sllm2.model.ModelPreferences
import com.minyook.sllm2.model.ModelStatus
import com.minyook.sllm2.gas.BleGasClient
import com.minyook.sllm2.gas.GasReading
import com.minyook.sllm2.gas.GasReadingSource
import com.minyook.sllm2.gas.GasReadingStore
import com.minyook.sllm2.gas.GasMonitoringService
import com.minyook.sllm2.gas.GasSimulationService
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val modelPreferences by lazy { ModelPreferences(applicationContext) }
    private val inferenceSettings by lazy { InferenceSettingsPreferences(applicationContext) }
    private val knowledgeRepository by lazy { KnowledgeRepository() }
    private val localModelRuntime by lazy { LocalModelRuntime(applicationContext) }
    private val chatHistoryStore by lazy { ChatHistoryStore(applicationContext) }

    private lateinit var drawer: DrawerLayout
    private lateinit var navigationDrawer: View
    private lateinit var toolbarSurface: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var chatContent: View
    private lateinit var settingsContent: View
    private lateinit var sensorContent: View
    private lateinit var chatModelStatus: TextView
    private lateinit var modelStatus: TextView
    private lateinit var modelBadge: TextView
    private lateinit var modelProgress: ProgressBar
    private lateinit var deviceProfile: TextView
    private lateinit var modelAction: MaterialButton
    private lateinit var knowledgeStatus: TextView
    private lateinit var question: EditText
    private lateinit var answer: TextView
    private lateinit var conversationContainer: LinearLayout
    private lateinit var chatScroll: NestedScrollView
    private lateinit var askButton: MaterialButton
    private lateinit var voiceButton: MaterialButton
    private lateinit var scrollLatestButton: MaterialButton
    private lateinit var backendSpinner: Spinner
    private lateinit var contextSlider: Slider
    private lateinit var responseSlider: Slider
    private lateinit var contextValue: TextView
    private lateinit var responseValue: TextView
    private lateinit var runtimeMetrics: TextView
    private lateinit var settingsSummary: TextView
    private lateinit var chatNavigation: MaterialButton
    private lateinit var settingsNavigation: MaterialButton
    private lateinit var sensorNavigation: MaterialButton
    private lateinit var newChatButton: MaterialButton
    private lateinit var quickPromptContainer: View
    private lateinit var chatHistoryContainer: LinearLayout
    private lateinit var gasStatus: TextView
    private lateinit var oxygenValue: TextView
    private lateinit var h2sValue: TextView
    private lateinit var carbonMonoxideValue: TextView
    private lateinit var lelValue: TextView
    private lateinit var bluetoothStatus: TextView
    private lateinit var bluetoothDeviceContainer: LinearLayout
    private lateinit var bluetoothScanButton: MaterialButton
    private lateinit var bluetoothDisconnectButton: MaterialButton
    private lateinit var bleGasClient: BleGasClient
    private val scannedDevices = linkedMapOf<String, BluetoothDevice>()
    private var scanRequestedAfterPermission = false
    private var pendingGasDevice: BluetoothDevice? = null
    private var activeChatId: String? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var wakeWordEnabled = false
    private var awaitingWakeWord = false
    private var speakNextResponse = false
    private var voiceListening = false
    private var suppressNextVoiceError = false
    private var systemVoiceFallbackInFlight = false

    private val systemVoiceInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        systemVoiceFallbackInFlight = false
        val spoken = if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
        } else {
            ""
        }
        if (spoken.isBlank()) {
            question.error = "음성 입력이 취소되었거나 인식되지 않았습니다."
        } else {
            submitVoiceQuestion(spoken)
        }
    }

    private val gasReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                GasReadingStore.ACTION_READING_CHANGED -> renderGasReading(GasReadingStore.current(this@MainActivity))
                GasMonitoringService.ACTION_CONNECTION_STATUS -> {
                    val status = intent.getStringExtra(GasMonitoringService.EXTRA_STATUS).orEmpty()
                    bluetoothStatus.text = status
                    bluetoothDisconnectButton.visibility = if (status.startsWith("연결")) View.VISIBLE else View.GONE
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            insets
        }

        bindViews()
        configureDrawerWidth()
        configureInferenceControls()
        bindActions()
        configureVoiceConversation()
        bleGasClient = BleGasClient(applicationContext, object : BleGasClient.Listener {
            override fun onScanResult(device: BluetoothDevice, rssi: Int) = runOnUiThread {
                addBluetoothDevice(device, rssi)
            }

            override fun onConnectionStatus(message: String) = runOnUiThread {
                bluetoothStatus.text = message
                bluetoothDisconnectButton.visibility = if (message.startsWith("연결")) View.VISIBLE else View.GONE
            }

            override fun onReading(reading: GasReading) = runOnUiThread { renderGasReading(reading) }
        })
        ContextCompat.registerReceiver(
            this,
            gasReceiver,
            IntentFilter().apply {
                addAction(GasReadingStore.ACTION_READING_CHANGED)
                addAction(GasMonitoringService.ACTION_CONNECTION_STATUS)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        renderDeviceProfile()
        renderModelStatus(modelPreferences.status())
        renderSettingsSummary(inferenceSettings.load())
        renderGasReading(GasReadingStore.current(this))
        renderChatHistory()
        showChat()
        observeModelDownload()
        seedKnowledgeInBackground()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
                    settingsContent.visibility == View.VISIBLE || sensorContent.visibility == View.VISIBLE -> showChat()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        unregisterReceiver(gasReceiver)
        stopVoiceListening()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        if (::bleGasClient.isInitialized) bleGasClient.close()
        localModelRuntime.release()
        super.onDestroy()
    }

    override fun onPause() {
        persistSettingsIfChanged()
        stopVoiceListening()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (wakeWordEnabled) startWakeWordListening()
    }

    private fun bindViews() {
        drawer = findViewById(R.id.drawer_layout)
        navigationDrawer = findViewById(R.id.navigation_drawer)
        toolbarSurface = findViewById(R.id.toolbar_surface)
        toolbar = findViewById(R.id.toolbar)
        chatContent = findViewById(R.id.chat_content)
        settingsContent = findViewById(R.id.settings_content)
        sensorContent = findViewById(R.id.sensor_content)
        chatModelStatus = findViewById(R.id.tv_chat_model_status)
        modelStatus = findViewById(R.id.tv_model_status)
        modelBadge = findViewById(R.id.tv_model_badge)
        modelProgress = findViewById(R.id.progress_model)
        deviceProfile = findViewById(R.id.tv_device_profile)
        modelAction = findViewById(R.id.btn_model_action)
        knowledgeStatus = findViewById(R.id.tv_knowledge_status)
        question = findViewById(R.id.et_question)
        answer = findViewById(R.id.tv_answer)
        conversationContainer = findViewById(R.id.conversation_container)
        chatScroll = findViewById(R.id.chat_scroll)
        askButton = findViewById(R.id.btn_ask)
        voiceButton = findViewById(R.id.btn_voice)
        scrollLatestButton = findViewById(R.id.btn_scroll_latest)
        backendSpinner = findViewById(R.id.spinner_backend)
        contextSlider = findViewById(R.id.slider_context)
        responseSlider = findViewById(R.id.slider_response)
        contextValue = findViewById(R.id.tv_context_value)
        responseValue = findViewById(R.id.tv_response_value)
        runtimeMetrics = findViewById(R.id.tv_runtime_metrics)
        settingsSummary = findViewById(R.id.tv_settings_summary)
        chatNavigation = findViewById(R.id.btn_nav_chat)
        settingsNavigation = findViewById(R.id.btn_nav_settings)
        sensorNavigation = findViewById(R.id.btn_nav_sensor)
        newChatButton = findViewById(R.id.btn_new_chat)
        quickPromptContainer = findViewById(R.id.quick_prompt_container)
        chatHistoryContainer = findViewById(R.id.chat_history_container)
        gasStatus = findViewById(R.id.tv_gas_status)
        oxygenValue = findViewById(R.id.tv_oxygen_value)
        h2sValue = findViewById(R.id.tv_h2s_value)
        carbonMonoxideValue = findViewById(R.id.tv_co_value)
        lelValue = findViewById(R.id.tv_lel_value)
        bluetoothStatus = findViewById(R.id.tv_bluetooth_status)
        bluetoothDeviceContainer = findViewById(R.id.bluetooth_device_container)
        bluetoothScanButton = findViewById(R.id.btn_bluetooth_scan)
        bluetoothDisconnectButton = findViewById(R.id.btn_bluetooth_disconnect)
    }

    private fun configureInferenceControls() {
        backendSpinner.adapter = spinnerAdapter(InferenceBackend.entries.map { it.label })
        val deviceLimit = ContextTokenPolicy.deviceLimit(this)
        contextSlider.valueFrom = ContextTokenPolicy.MIN_CONTEXT_TOKENS.toFloat()
        contextSlider.valueTo = if (deviceLimit == ContextTokenPolicy.MIN_CONTEXT_TOKENS) {
            (deviceLimit + 1_024).toFloat()
        } else {
            deviceLimit.toFloat()
        }
        contextSlider.stepSize = 1_024f
        contextSlider.isEnabled = deviceLimit > ContextTokenPolicy.MIN_CONTEXT_TOKENS
        applySettingsToControls(inferenceSettings.load())
        contextSlider.addOnChangeListener { _, value, _ ->
            configureResponseSlider(value.toInt(), responseSlider.value.toInt())
            renderSettingsSummary(settingsFromControls())
        }
        responseSlider.addOnChangeListener { _, _, _ -> renderSettingsSummary(settingsFromControls()) }
        backendSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                renderSettingsSummary(settingsFromControls())
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
        }
    }

    private fun spinnerAdapter(values: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).also { view ->
                    (view as? TextView)?.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).also { view ->
                    (view as? TextView)?.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                }
        }

    private fun bindActions() {
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        chatNavigation.setOnClickListener { showChat() }
        settingsNavigation.setOnClickListener { showSettings() }
        sensorNavigation.setOnClickListener { showSensor() }
        newChatButton.setOnClickListener { beginNewChat() }
        modelAction.setOnClickListener { startModelDownload() }
        askButton.setOnClickListener { answerQuestion() }
        voiceButton.setOnClickListener { startVoiceQuestion() }
        voiceButton.setOnLongClickListener {
            toggleWakeWordMode()
            true
        }
        scrollLatestButton.setOnClickListener { scrollConversationToBottom() }
        chatScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            scrollLatestButton.visibility = if (chatScroll.canScrollVertically(1)) View.VISIBLE else View.GONE
        }
        bluetoothScanButton.setOnClickListener { requestBluetoothScan() }
        bluetoothDisconnectButton.setOnClickListener {
            GasMonitoringService.disconnect(this)
            bluetoothStatus.text = "가스 측정기 연결을 해제했습니다."
            bluetoothDisconnectButton.visibility = View.GONE
        }
        findViewById<MaterialButton>(R.id.btn_quick_air).setOnClickListener {
            askPreset("밀폐공간 작업 전 공기 측정 기준과 측정 시기를 알려줘")
        }
        findViewById<MaterialButton>(R.id.btn_quick_rescue).setOnClickListener {
            askPreset("밀폐공간에서 작업자가 쓰러졌을 때 구조 절차를 알려줘")
        }
        findViewById<MaterialButton>(R.id.btn_quick_permit).setOnClickListener {
            askPreset("밀폐공간 작업허가 전에 어떤 안전조치를 확인해야 하나요?")
        }
        findViewById<MaterialButton>(R.id.btn_save_settings).setOnClickListener {
            val settings = settingsFromControls()
            inferenceSettings.save(settings)
            localModelRuntime.release()
            renderSettingsSummary(settings, saved = true)
            renderModelStatus(modelPreferences.status())
        }
        question.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                answerQuestion()
                true
            } else false
        }
        question.setOnFocusChangeListener { _, focused -> if (focused) scrollConversationToBottom() }
    }

    private fun showChat() {
        persistSettingsIfChanged()
        chatContent.visibility = View.VISIBLE
        settingsContent.visibility = View.GONE
        sensorContent.visibility = View.GONE
        toolbar.title = "O₂ Field Guard"
        toolbar.subtitle = "문서 기반 안전 채팅 · 기기 내 AI"
        toolbarSurface.setBackgroundColor(ContextCompat.getColor(this, R.color.chat_background))
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.editorial_ink))
        toolbar.setSubtitleTextColor(ContextCompat.getColor(this, R.color.editorial_muted))
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.editorial_ink))
        window.statusBarColor = ContextCompat.getColor(this, R.color.chat_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.chat_background)
        WindowCompat.getInsetsController(window, toolbarSurface).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, toolbarSurface).isAppearanceLightNavigationBars = true
        updateNavigationSelection(chatNavigation)
        drawer.closeDrawer(GravityCompat.START)
    }

    private fun showSettings() {
        chatContent.visibility = View.GONE
        settingsContent.visibility = View.VISIBLE
        sensorContent.visibility = View.GONE
        applySettingsToControls(inferenceSettings.load())
        toolbar.title = "설정"
        toolbar.subtitle = "모델 관리 · 추론 설정"
        toolbarSurface.setBackgroundColor(ContextCompat.getColor(this, R.color.chat_background))
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.editorial_ink))
        toolbar.setSubtitleTextColor(ContextCompat.getColor(this, R.color.editorial_muted))
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.editorial_ink))
        window.statusBarColor = ContextCompat.getColor(this, R.color.chat_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.chat_background)
        WindowCompat.getInsetsController(window, toolbarSurface).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, toolbarSurface).isAppearanceLightNavigationBars = true
        updateNavigationSelection(settingsNavigation)
        drawer.closeDrawer(GravityCompat.START)
    }

    private fun showSensor() {
        persistSettingsIfChanged()
        chatContent.visibility = View.GONE
        settingsContent.visibility = View.GONE
        sensorContent.visibility = View.VISIBLE
        toolbar.title = "센서"
        toolbar.subtitle = "Bluetooth LE 가스 검출기"
        toolbarSurface.setBackgroundColor(ContextCompat.getColor(this, R.color.chat_background))
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.editorial_ink))
        toolbar.setSubtitleTextColor(ContextCompat.getColor(this, R.color.editorial_muted))
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.editorial_ink))
        window.statusBarColor = ContextCompat.getColor(this, R.color.chat_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.chat_background)
        WindowCompat.getInsetsController(window, toolbarSurface).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, toolbarSurface).isAppearanceLightNavigationBars = true
        updateNavigationSelection(sensorNavigation)
        drawer.closeDrawer(GravityCompat.START)
    }

    private fun applySettingsToControls(settings: InferenceSettings) {
        backendSpinner.setSelection(InferenceBackend.entries.indexOf(settings.backend).coerceAtLeast(0))
        contextSlider.value = settings.contextTokens.toFloat()
        configureResponseSlider(settings.contextTokens, settings.responseTokens)
    }

    private fun settingsFromControls(): InferenceSettings = InferenceSettings(
        backend = InferenceBackend.entries.getOrElse(backendSpinner.selectedItemPosition) { InferenceBackend.AUTO },
        contextTokens = contextSlider.value.toInt(),
        responseTokens = responseSlider.value.toInt(),
    )

    private fun renderSettingsSummary(settings: InferenceSettings, saved: Boolean = false) {
        val leading = if (saved) "저장됨 · 다음 질문부터 적용됩니다.\n" else ""
        val safeLimit = ContextTokenPolicy.deviceLimit(this)
        settingsSummary.text = leading + "${settings.backend.label} · 문서 문맥 ${settings.contextTokens} 토큰 · 답변 최대 ${settings.responseTokens} 토큰\nGemma 원본 128,000 토큰 · 이 기기 안정 한도 $safeLimit 토큰 안에서 선택됩니다."
        contextValue.text = "${settings.contextTokens.toDisplayToken()} 토큰"
        responseValue.text = "${settings.responseTokens.toDisplayToken()} 토큰"
        runtimeMetrics.text = buildRuntimeMetrics(settings, safeLimit)
    }

    private fun configureResponseSlider(contextTokens: Int, desiredResponse: Int) {
        val maximum = ContextTokenPolicy.responseLimit(contextTokens)
        responseSlider.value = ContextTokenPolicy.MIN_RESPONSE_TOKENS.toFloat()
        responseSlider.valueFrom = ContextTokenPolicy.MIN_RESPONSE_TOKENS.toFloat()
        responseSlider.valueTo = maximum.toFloat()
        responseSlider.stepSize = 128f
        responseSlider.value = desiredResponse.coerceIn(ContextTokenPolicy.MIN_RESPONSE_TOKENS, maximum).toFloat()
    }

    private fun buildRuntimeMetrics(settings: InferenceSettings, deviceLimit: Int): String {
        val profile = DeviceProfile.read(this)
        val mode = if (profile.recommendedForGemma4E2b) "GPU 가능 · CPU 폴백" else "CPU 안전 모드"
        return "준비 상태 · Gemma 4 E2B / ${settings.backend.label}\n" +
            "RAM ${profile.totalRamGb}GB · 가용 저장 공간 ${profile.availableStorageGb}GB · $mode\n" +
            "컨텍스트 ${settings.contextTokens.toDisplayToken()} / 이 기기 안정 한도 ${deviceLimit.toDisplayToken()} · 답변 ${settings.responseTokens.toDisplayToken()}"
    }

    private fun Int.toDisplayToken(): String = String.format(Locale.KOREA, "%,d", this)

    /** Keeps the design spec's 180–304dp drawer and a 56dp closing gesture margin. */
    private fun configureDrawerWidth() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val displayWidth = resources.displayMetrics.widthPixels
        val targetWidth = (displayWidth - dp(56)).coerceIn(dp(180), dp(304))
        navigationDrawer.layoutParams = (navigationDrawer.layoutParams as DrawerLayout.LayoutParams).apply {
            width = targetWidth
        }
    }

    private fun updateNavigationSelection(selected: MaterialButton) {
        val muted = ContextCompat.getColor(this, R.color.text_secondary)
        val ink = ContextCompat.getColor(this, R.color.editorial_ink)
        listOf(chatNavigation, sensorNavigation, settingsNavigation).forEach { item ->
            val isSelected = item === selected
            item.setBackgroundResource(if (isSelected) R.drawable.bg_drawer_nav_selected else android.R.color.transparent)
            item.setTextColor(if (isSelected) ink else muted)
            item.iconTint = ColorStateList.valueOf(if (isSelected) ink else muted)
        }
    }

    /** Matches the reference app's behaviour: settings survive leaving the screen or app. */
    private fun persistSettingsIfChanged() {
        if (!::backendSpinner.isInitialized) return
        val next = settingsFromControls()
        if (next == inferenceSettings.load()) return
        inferenceSettings.save(next)
        localModelRuntime.release()
    }

    private fun requestBluetoothScan() {
        if (!hasBluetoothPermissions()) {
            scanRequestedAfterPermission = true
            ActivityCompat.requestPermissions(this, bluetoothPermissions(), REQUEST_BLUETOOTH)
            return
        }
        scannedDevices.clear()
        bluetoothDeviceContainer.removeAllViews()
        bluetoothStatus.text = "주변 가스 측정기를 검색하는 중입니다…"
        bleGasClient.scan()
    }

    private fun hasBluetoothPermissions(): Boolean = bluetoothPermissions().all { permission ->
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun bluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH -> {
                if (hasBluetoothPermissions() && scanRequestedAfterPermission) {
                    scanRequestedAfterPermission = false
                    requestBluetoothScan()
                } else {
                    scanRequestedAfterPermission = false
                    bluetoothStatus.text = "가스 측정기 검색에는 Bluetooth 권한이 필요합니다."
                }
            }
            REQUEST_GAS_NOTIFICATION -> {
                pendingGasDevice?.let(::startGasMonitoring)
                pendingGasDevice = null
            }
            REQUEST_RECORD_AUDIO -> {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    if (wakeWordEnabled) startWakeWordListening() else startVoiceQuestion()
                } else {
                    question.error = "음성 질문에는 마이크 권한이 필요합니다."
                }
            }
        }
    }

    private fun addBluetoothDevice(device: BluetoothDevice, rssi: Int) {
        if (scannedDevices.put(device.address, device) != null) return
        val deviceName = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "이름 없는 BLE 기기"
        val button = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(8)
            }
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            text = "$deviceName\n${device.address} · ${rssi}dBm"
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.runtime_card))
            setOnClickListener {
                if (hasBluetoothPermissions()) {
                    requestGasConnection(device)
                }
            }
        }
        bluetoothDeviceContainer.addView(button)
    }

    private fun requestGasConnection(device: BluetoothDevice) {
        pendingGasDevice = device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_GAS_NOTIFICATION,
            )
            return
        }
        startGasMonitoring(device)
        pendingGasDevice = null
    }

    private fun startGasMonitoring(device: BluetoothDevice) {
        val deviceName = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "BLE 가스 측정기"
        bleGasClient.stopScan()
        GasMonitoringService.connect(this, device.address)
        bluetoothStatus.text = "$deviceName 연결을 시작했습니다…"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothStatus.text = "$deviceName 연결을 시작했습니다. 알림 패널 표시는 알림 권한을 허용하면 사용할 수 있습니다."
        }
    }

    private fun renderGasReading(reading: GasReading) {
        oxygenValue.text = formatGas(reading.oxygenPercent, "%")
        h2sValue.text = formatGas(reading.h2sPpm, "ppm")
        carbonMonoxideValue.text = formatGas(reading.carbonMonoxidePpm, "ppm")
        lelValue.text = formatGas(reading.lelPercent, "%LEL")
        gasStatus.text = if (reading.source == GasReadingSource.SIMULATION) {
            "데모 시뮬레이션 · 실제 측정값 아님"
        } else if (reading.receivedAtMillis > 0L) {
            "${reading.deviceName ?: "BLE 측정기"} · ${DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.KOREA).format(Date(reading.receivedAtMillis))}"
        } else {
            "센서 연결 대기 · 실제 측정값만 표시"
        }
    }

    private fun formatGas(value: Double?, unit: String): String = value?.let {
        "${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.US, it)} $unit"
    } ?: "—"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderDeviceProfile() {
        val profile = DeviceProfile.read(this)
        val tier = if (profile.recommendedForGemma4E2b) "GPU 사용 가능 기기" else "CPU 안전 모드 권장"
        deviceProfile.text = "내 기기: RAM ${profile.totalRamGb}GB · 여유 공간 ${profile.availableStorageGb}GB · $tier\n모델 약 2.6GB, Wi-Fi와 4GB 이상 여유 공간을 권장합니다."
    }

    private fun renderModelStatus(status: ModelStatus, progress: Int? = null) {
        when (status.phase) {
            ModelPhase.READY -> {
                GasSimulationService.start(applicationContext)
                modelStatus.text = "설치 완료 · 앱을 다시 열어도 오프라인 준비 상태가 유지됩니다"
                chatModelStatus.text = "Gemma 4 E2B · 준비됨"
                modelBadge.text = "준비됨"
                modelBadge.setTextColor(ContextCompat.getColor(this, R.color.positive))
                modelBadge.setBackgroundResource(R.drawable.bg_chip_warm)
                modelAction.text = "오프라인 모델 준비됨"
                modelAction.isEnabled = false
                modelProgress.visibility = ProgressBar.GONE
            }

            ModelPhase.DOWNLOADING -> {
                modelStatus.text = if (progress != null) "다운로드 중 · $progress%" else "다운로드 중 · 앱을 닫아도 이어서 진행됩니다"
                chatModelStatus.text = "Gemma 4 E2B · 다운로드 중"
                modelBadge.text = "다운로드 중"
                modelBadge.setTextColor(ContextCompat.getColor(this, R.color.warning))
                modelBadge.setBackgroundResource(R.drawable.bg_chip_warm)
                modelAction.text = "다운로드 진행 중"
                modelAction.isEnabled = false
                modelProgress.visibility = ProgressBar.VISIBLE
                modelProgress.progress = progress ?: 0
            }

            ModelPhase.FAILED -> {
                modelStatus.text = "다운로드를 다시 시도해 주세요${status.failureMessage?.let { " · $it" } ?: ""}"
                chatModelStatus.text = "Gemma 4 E2B · 재시도 필요"
                modelBadge.text = "재시도 필요"
                modelBadge.setTextColor(ContextCompat.getColor(this, R.color.critical))
                modelBadge.setBackgroundResource(R.drawable.bg_chip_warm)
                modelAction.text = "다시 다운로드"
                modelAction.isEnabled = true
                modelProgress.visibility = ProgressBar.GONE
            }

            ModelPhase.NOT_INSTALLED -> {
                modelStatus.text = "질문은 문서 검색으로 바로 가능 · 생성 답변은 모델 설치 후 사용"
                chatModelStatus.text = "문서 검색 모드 · 모델 준비 필요"
                modelBadge.text = "준비 필요"
                modelBadge.setTextColor(ContextCompat.getColor(this, R.color.warning))
                modelBadge.setBackgroundResource(R.drawable.bg_chip_warm)
                modelAction.text = "모델 다운로드"
                modelAction.isEnabled = true
                modelProgress.visibility = ProgressBar.GONE
            }
        }
    }

    private fun startModelDownload() {
        val profile = DeviceProfile.read(this)
        if (profile.availableStorageGb < 4) {
            showAnswer("## 저장 공간이 부족합니다\n\n모델 설치에는 최소 **4GB** 이상의 여유 공간이 필요합니다. 저장 공간을 확보한 뒤 다시 시도해 주세요.")
            showChat()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        ModelDownloadScheduler.enqueue(applicationContext)
        renderModelStatus(ModelStatus(ModelPhase.DOWNLOADING, 0))
    }

    private fun observeModelDownload() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(ModelDownloadScheduler.UNIQUE_WORK_NAME)
            .observe(this) { infos ->
                val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                val pending = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                if (running != null) {
                    val downloaded = running.progress.getLong("downloadedBytes", 0L)
                    val total = running.progress.getLong("totalBytes", -1L)
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else null
                    renderModelStatus(ModelStatus(ModelPhase.DOWNLOADING, downloaded), percent)
                } else if (pending) {
                    renderModelStatus(ModelStatus(ModelPhase.DOWNLOADING, 0))
                } else renderModelStatus(modelPreferences.status())
            }
    }

    private fun seedKnowledgeInBackground() {
        thread(name = "knowledge-seed") {
            val result = runCatching { KnowledgeSeeder(applicationContext).seedIfNeeded() }
            runOnUiThread {
                result.onSuccess { count ->
                    knowledgeStatus.text = "제공 문서 2개 · ${count}개 근거 조각을 기기에 저장했습니다"
                }.onFailure {
                    knowledgeStatus.text = "지식베이스 준비에 실패했습니다. 앱을 다시 시작해 주세요."
                }
            }
        }
    }

    private fun askPreset(text: String) {
        question.setText(text)
        answerQuestion()
    }

    /** Uses the on-device recognizer when installed and falls back to the system recognizer otherwise. */
    private fun configureVoiceConversation() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceButton.isEnabled = false
            voiceButton.contentDescription = "이 기기에서는 음성 인식을 사용할 수 없습니다"
            return
        }
        speechRecognizer = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = renderVoiceButton(listening = true)
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    voiceListening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    handleVoiceResult(spoken)
                }

                override fun onError(error: Int) {
                    voiceListening = false
                    if (suppressNextVoiceError) {
                        suppressNextVoiceError = false
                        return
                    }
                    renderVoiceButton()
                    if (wakeWordEnabled && awaitingWakeWord && hasWindowFocus()) {
                        chatScroll.postDelayed({ startWakeWordListening() }, VOICE_RETRY_DELAY_MS)
                    } else {
                        question.error = voiceRecognitionError(error)
                        launchSystemVoiceFallback()
                    }
                }
            })
        }
        textToSpeech = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.language = Locale.KOREAN
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onError(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == VOICE_PROMPT_UTTERANCE) {
                            runOnUiThread { startListening(wakeWordOnly = false) }
                        }
                    }
                })
            }
        }
        renderVoiceButton()
    }

    private fun startVoiceQuestion() {
        wakeWordEnabled = false
        systemVoiceFallbackInFlight = false
        if (!hasRecordAudioPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        startListening(wakeWordOnly = false)
    }

    /** Long press enables an explicit, foreground-only wake phrase listener. */
    private fun toggleWakeWordMode() {
        wakeWordEnabled = !wakeWordEnabled
        if (!wakeWordEnabled) {
            stopVoiceListening()
            renderVoiceButton()
            question.hint = "안전 작업에 대해 물어보세요"
            return
        }
        if (!hasRecordAudioPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        startWakeWordListening()
    }

    private fun startWakeWordListening() {
        if (!wakeWordEnabled || !hasRecordAudioPermission() || chatContent.visibility != View.VISIBLE) return
        question.hint = "‘오투야’라고 부르면 음성 대화를 시작합니다"
        startListening(wakeWordOnly = true)
    }

    private fun startListening(wakeWordOnly: Boolean) {
        val recognizer = speechRecognizer ?: return
        if (voiceListening) return
        awaitingWakeWord = wakeWordOnly
        voiceListening = true
        renderVoiceButton(listening = true)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (wakeWordOnly) "오투야" else "질문을 말씀하세요")
        }
        runCatching {
            recognizer.startListening(intent)
        }.onFailure {
            voiceListening = false
            renderVoiceButton()
            if (wakeWordEnabled && wakeWordOnly) {
                chatScroll.postDelayed({ startWakeWordListening() }, VOICE_RETRY_DELAY_MS)
            } else {
                question.error = "음성 인식을 시작하지 못했습니다. 다시 눌러 주세요."
                launchSystemVoiceFallback()
            }
        }
    }

    private fun stopVoiceListening() {
        val wasListening = voiceListening
        awaitingWakeWord = false
        voiceListening = false
        suppressNextVoiceError = wasListening
        if (wasListening) {
            runCatching { speechRecognizer?.cancel() }
            chatScroll.postDelayed({ suppressNextVoiceError = false }, 300L)
        }
        renderVoiceButton()
    }

    /** Android's standard recognition activity is more reliable on devices with customized voice services. */
    private fun launchSystemVoiceFallback() {
        if (systemVoiceFallbackInFlight || wakeWordEnabled || isFinishing) return
        systemVoiceFallbackInFlight = true
        runCatching {
            systemVoiceInput.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "안전 작업 질문을 말씀하세요")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }.onFailure {
            systemVoiceFallbackInFlight = false
        }
    }

    private fun handleVoiceResult(spoken: String) {
        if (spoken.isBlank()) {
            if (wakeWordEnabled) startWakeWordListening() else {
                renderVoiceButton()
                question.error = "음성을 인식하지 못했습니다. 다시 말씀해 주세요."
            }
            return
        }
        if (awaitingWakeWord) {
            val questionAfterWakeWord = spoken.replace(WAKE_WORD, "").trim(' ', ',', '.', '?', '!')
            if (!WAKE_WORD.containsMatchIn(spoken)) {
                chatScroll.postDelayed({ startWakeWordListening() }, VOICE_RETRY_DELAY_MS)
                return
            }
            awaitingWakeWord = false
            if (questionAfterWakeWord.isNotBlank()) {
                submitVoiceQuestion(questionAfterWakeWord)
            } else {
                speakPromptThenListen()
            }
        } else {
            submitVoiceQuestion(spoken)
        }
    }

    private fun submitVoiceQuestion(spoken: String) {
        renderVoiceButton()
        speakNextResponse = true
        question.setText(normalizeSpokenQuestion(spoken))
        answerQuestion(compactVoiceAnswer = true)
    }

    private fun normalizeSpokenQuestion(spoken: String): String = spoken
        .replace(WAKE_WORD, "")
        .replace(Regex("^(?:저기|음+|어+)[, ]*"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', '.', '?', '!')

    private fun speakPromptThenListen() {
        if (!ttsReady) {
            startListening(wakeWordOnly = false)
            return
        }
        textToSpeech?.speak(
            "네, 말씀하세요.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            VOICE_PROMPT_UTTERANCE,
        )
    }

    private fun speakAnswer(markdown: String) {
        if (!ttsReady) return
        val plain = markdown
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            .replace("**", "")
            .replace("`", "")
            .replace(Regex("(?m)^>\\s*출처:.*$"), "")
            .replace(Regex("\\n{2,}"), ". ")
            .trim()
            .take(3_500)
        if (plain.isNotBlank()) {
            textToSpeech?.speak(plain, TextToSpeech.QUEUE_FLUSH, null, VOICE_ANSWER_UTTERANCE)
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun voiceRecognitionError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "마이크 입력을 확인해 주세요."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "음성 인식 연결이 없습니다. 한국어 오프라인 음성팩을 설치하거나 네트워크를 확인해 주세요."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "음성을 인식하지 못했습니다. 다시 말씀해 주세요."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 준비 중입니다. 잠시 후 다시 눌러 주세요."
        else -> "음성 인식에 실패했습니다. 다시 눌러 주세요."
    }

    private fun renderVoiceButton(listening: Boolean = false) {
        if (!::voiceButton.isInitialized) return
        voiceButton.text = if (listening || wakeWordEnabled) "●" else "◉"
        voiceButton.setTextColor(ContextCompat.getColor(this, if (listening || wakeWordEnabled) R.color.status_danger else R.color.chat_primary))
        voiceButton.contentDescription = when {
            listening && awaitingWakeWord -> "오투야 호출을 듣는 중"
            listening -> "음성 질문을 듣는 중"
            wakeWordEnabled -> "오투야 호출 대기 중. 길게 눌러 끄기"
            else -> "음성으로 질문하기. 길게 누르면 오투야 호출 대기"
        }
    }

    private fun answerQuestion(compactVoiceAnswer: Boolean = false) {
        val asked = question.text?.toString()?.trim().orEmpty()
        if (asked.isBlank()) {
            question.error = "확인할 내용을 입력해 주세요"
            return
        }
        val chatId = activeChatId ?: chatHistoryStore.create(asked).id.also { activeChatId = it }
        addUserMessage(asked)
        quickPromptContainer.visibility = View.GONE
        chatHistoryStore.append(chatId, ChatHistoryStore.Role.USER, asked)
        renderChatHistory()
        question.text?.clear()
        val responseView = addAssistantMessage("_제공 문서에서 근거를 찾는 중입니다…_", pending = true)
        askButton.isEnabled = false
        thread(name = "offline-rag-answer") {
            // Voice and typed chat must retrieve the same full RAG evidence.
            // Only the speech output preference differs after the answer exists.
            val fallback = knowledgeRepository.answerWithoutModel(asked)
            val response = if (modelPreferences.status().phase == ModelPhase.READY) {
                runCatching { localModelRuntime.generate(asked, knowledgeRepository, compactVoiceAnswer) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "$fallback\n\n> 생성 모델 응답을 받지 못해 문서 근거를 우선 보여드립니다."
            } else fallback
            runOnUiThread {
                responseView.setTextColor(ContextCompat.getColor(this, R.color.editorial_ink))
                MarkdownText.applyTo(responseView, response)
                chatHistoryStore.append(chatId, ChatHistoryStore.Role.ASSISTANT, response)
                renderChatHistory()
                askButton.isEnabled = true
                scrollConversationToBottom()
                if (speakNextResponse) {
                    speakNextResponse = false
                    speakAnswer(response)
                }
            }
        }
    }

    private fun showAnswer(markdown: String) {
        addAssistantMessage(markdown)
    }

    private fun addUserMessage(message: String) {
        addMessageBubble(message, fromUser = true, markdown = false)
    }

    private fun addAssistantMessage(markdown: String, pending: Boolean = false): TextView =
        addMessageBubble(markdown, fromUser = false, markdown = true).also { bubble ->
            bubble.setTextColor(ContextCompat.getColor(this, if (pending) R.color.editorial_muted else R.color.editorial_ink))
        }

    private fun addMessageBubble(message: String, fromUser: Boolean, markdown: Boolean): TextView {
        val wrapper = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START
            orientation = LinearLayout.HORIZONTAL
        }
        val bubble = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@MainActivity, if (fromUser) R.color.white else R.color.editorial_ink))
            textSize = 15f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(ContextCompat.getColor(this@MainActivity, if (fromUser) R.color.editorial_coral else R.color.chat_assistant_surface))
                if (!fromUser) setStroke(dp(1), ContextCompat.getColor(this@MainActivity, R.color.editorial_hairline))
            }
            if (markdown) MarkdownText.applyTo(this, message) else text = message
        }
        wrapper.addView(bubble, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { width = (resources.displayMetrics.widthPixels * 0.82f).toInt() })
        conversationContainer.addView(wrapper, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(10) })
        scrollConversationToBottom()
        return bubble
    }

    private fun scrollConversationToBottom() {
        chatScroll.post {
            chatScroll.fullScroll(View.FOCUS_DOWN)
            scrollLatestButton.visibility = View.GONE
        }
    }

    /** Starts a fresh local-only transcript without changing the RAG knowledge base. */
    private fun beginNewChat() {
        activeChatId = null
        conversationContainer.removeAllViews()
        addAssistantMessage("안녕하세요. 밀폐공간 안전작업 가이드와 점검표를 근거로 답변합니다.\n\n무엇을 확인해 드릴까요?")
        quickPromptContainer.visibility = View.VISIBLE
        showChat()
        question.requestFocus()
    }

    private fun renderChatHistory() {
        if (!::chatHistoryContainer.isInitialized) return
        chatHistoryContainer.removeAllViews()
        val sessions = chatHistoryStore.list()
        if (sessions.isEmpty()) {
            chatHistoryContainer.addView(TextView(this).apply {
                text = "저장된 대화가 없습니다"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.chat_muted))
                setPadding(dp(10), dp(8), dp(10), dp(8))
            })
            return
        }
        sessions.forEach { session ->
            chatHistoryContainer.addView(MaterialButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(2) }
                minHeight = dp(42)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                text = session.title
                textSize = 13f
                isAllCaps = false
                setPadding(dp(8), 0, dp(8), 0)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                if (session.id == activeChatId) setBackgroundResource(R.drawable.bg_drawer_nav_selected)
                else setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
                setOnClickListener { loadChatHistory(session.id) }
            })
        }
    }

    private fun loadChatHistory(id: String) {
        val session = chatHistoryStore.find(id) ?: return
        activeChatId = session.id
        conversationContainer.removeAllViews()
        session.turns.forEach { turn ->
            when (turn.role) {
                ChatHistoryStore.Role.USER -> addUserMessage(turn.text)
                ChatHistoryStore.Role.ASSISTANT -> addAssistantMessage(turn.text)
            }
        }
        quickPromptContainer.visibility = View.GONE
        renderChatHistory()
        showChat()
        scrollConversationToBottom()
    }

    private companion object {
        const val REQUEST_BLUETOOTH = 610
        const val REQUEST_GAS_NOTIFICATION = 611
        const val REQUEST_RECORD_AUDIO = 612
        const val VOICE_RETRY_DELAY_MS = 550L
        const val VOICE_PROMPT_UTTERANCE = "voice_prompt"
        const val VOICE_ANSWER_UTTERANCE = "voice_answer"
        val WAKE_WORD = Regex("(?i)(오\\s*투\\s*야|o\\s*2\\s*야)")
    }
}
