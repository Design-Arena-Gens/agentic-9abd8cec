package com.max.pandaai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.max.pandaai.R
import com.max.pandaai.ai.AIService
import com.max.pandaai.data.ChatRepository
import com.max.pandaai.data.PandaDatabase
import com.max.pandaai.databinding.ActivityMainBinding
import com.max.pandaai.intent.IntentHandler
import com.max.pandaai.settings.PrivacyPolicyActivity
import com.max.pandaai.settings.SettingsActivity
import com.max.pandaai.settings.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

// Hosts the chat UI, handles voice input/output, and coordinates AI + smart intents.
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var viewModel: MainViewModel
    private lateinit var intentHandler: IntentHandler
    private var textToSpeech: TextToSpeech? = null
    private var isTextToSpeechReady = false
    private var recognitionJob: Job? = null
    private var pendingCommand: String? = null
    private var greetingShown = false

    // Re-uses a single activity result launcher for speech-to-text prompts.
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        stopListeningAnimation()
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                handleUserInput(spokenText)
            } else {
                Snackbar.make(binding.root, "I didn't catch that. Please try again.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        // Build all runtime dependencies manually (simple service locator).
        val database = PandaDatabase.getInstance(this)
        val repository = ChatRepository(database.chatDao())
        val settingsManager = SettingsManager(this)
        val aiService = AIService()
        val factory = MainViewModel.Factory(repository, settingsManager, aiService)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
        intentHandler = IntentHandler(this)

        setupRecyclerView()
        setupToolbar()
        setupMicButton()

        textToSpeech = TextToSpeech(this, this)

        observeMessages()
        observeProcessing()
        requestInitialPermissions()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(this)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.chatRecyclerView.adapter = adapter
    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_clear_chat -> {
                    viewModel.clearChat()
                    greetingShown = false
                    Snackbar.make(binding.root, "Chat cleared.", Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_privacy -> {
                    startActivity(Intent(this, PrivacyPolicyActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupMicButton() {
        binding.microphoneButton.setOnClickListener {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Snackbar.make(binding.root, "Speech recognition is not available on this device.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkAndStartListening()
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
                    adapter.submitList(messages)
                    if (messages.isEmpty()) {
                        binding.emptyState.visibility = android.view.View.VISIBLE
                    } else {
                        binding.emptyState.visibility = android.view.View.GONE
                        binding.chatRecyclerView.post {
                            binding.chatRecyclerView.scrollToPosition(messages.size - 1)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.assistantSettings.collect { settings ->
                    if (isTextToSpeechReady) {
                        applyVoiceSettings(settings.assistantVoice)
                    }
                    AppCompatDelegate.setDefaultNightMode(
                        if (settings.darkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    if (!greetingShown && adapter.itemCount == 0) {
                        val greeting = getString(R.string.assistant_greeting).replace("Panda", settings.assistantName)
                        viewModel.addMessage(greeting, fromUser = false)
                        speak(greeting)
                        greetingShown = true
                    }
                }
            }
        }
    }

    private fun observeProcessing() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.isProcessing.collect { processing ->
                    binding.loadingIndicator.visibility = if (processing) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        }
    }

    private fun handleUserInput(input: String) {
        if (!ensurePermissionsIfNeeded(input)) {
            pendingCommand = input
            return
        }
        processCommand(input, addUserMessage = true)
    }

    private fun processCommand(input: String, addUserMessage: Boolean) {
        if (addUserMessage) {
            viewModel.addMessage(input, fromUser = true)
        }

        recognitionJob?.cancel()
        recognitionJob = lifecycleScope.launch {
            val commandResult = intentHandler.handleCommand(input)
            if (commandResult.handled && !commandResult.message.isNullOrBlank()) {
                viewModel.addMessage(commandResult.message, fromUser = false)
                speak(commandResult.message)
            }

            val response = viewModel.requestAiResponse(input)
            viewModel.addMessage(response, fromUser = false)
            speak(response)
            pendingCommand = null
        }
    }

    private fun speak(text: String) {
        if (!isTextToSpeechReady) return
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PANDA_AI_RESPONSE")
    }

    private fun checkAndStartListening() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceRecognition()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.permission_required))
                    .setMessage(getString(R.string.permission_microphone_rationale))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            REQUEST_CODE_RECORD_AUDIO
                        )
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            else -> ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_RECORD_AUDIO
            )
        }
    }

    private fun ensurePermissionsIfNeeded(input: String): Boolean {
        val lower = input.lowercase(Locale.getDefault())
        if (lower.startsWith("call ") &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CODE_CALL_PHONE
            )
        viewModel.addMessage(getString(R.string.permission_call_rationale), fromUser = false)
        speak(getString(R.string.permission_call_rationale))
            return false
        }

        if ((lower.startsWith("send sms") || lower.startsWith("send text")) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                REQUEST_CODE_SEND_SMS
            )
            viewModel.addMessage(getString(R.string.permission_sms_rationale), fromUser = false)
            speak(getString(R.string.permission_sms_rationale))
            return false
        }
        return true
    }

    private fun startVoiceRecognition() {
        playListeningTone()
        startListeningAnimation()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speak_now))
        }
        speechRecognizerLauncher.launch(intent)
    }

    private fun playListeningTone() {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    private fun startListeningAnimation() {
        binding.listeningGlow.visibility = android.view.View.VISIBLE
        binding.listeningGlow.playAnimation()
        Snackbar.make(binding.root, getString(R.string.listening), Snackbar.LENGTH_SHORT).show()
    }

    private fun stopListeningAnimation() {
        binding.listeningGlow.cancelAnimation()
        binding.listeningGlow.visibility = android.view.View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_RECORD_AUDIO -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    startVoiceRecognition()
                } else {
                    showPermissionSnackbar()
                }
            }
            REQUEST_CODE_CALL_PHONE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    pendingCommand?.let { processCommand(it, addUserMessage = false) }
                } else {
                    Snackbar.make(binding.root, getString(R.string.permission_call_rationale), Snackbar.LENGTH_LONG).show()
                }
            }
            REQUEST_CODE_SEND_SMS -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    pendingCommand?.let { processCommand(it, addUserMessage = false) }
                } else {
                    Snackbar.make(binding.root, getString(R.string.permission_sms_rationale), Snackbar.LENGTH_LONG).show()
                }
            }
            REQUEST_CODE_INITIAL_PERMISSIONS -> {
                val smsIndex = permissions.indexOf(Manifest.permission.SEND_SMS)
                if (smsIndex >= 0 && grantResults.size > smsIndex &&
                    grantResults[smsIndex] != PackageManager.PERMISSION_GRANTED
                ) {
                    Snackbar.make(binding.root, getString(R.string.permission_sms_rationale), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPermissionSnackbar() {
        Snackbar.make(binding.root, getString(R.string.permission_microphone_rationale), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.action_open_settings)) {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            }.show()
    }

    private fun requestInitialPermissions() {
        val requiredPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (requiredPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, requiredPermissions.toTypedArray(), REQUEST_CODE_INITIAL_PERMISSIONS)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTextToSpeechReady = true
            textToSpeech?.language = Locale.getDefault()
            lifecycleScope.launch {
                val settings = viewModel.assistantSettings.value
                applyVoiceSettings(settings.assistantVoice)
            }
        } else {
            Toast.makeText(this, "Text to speech unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyVoiceSettings(voicePreference: String) {
        val tts = textToSpeech ?: return
        val availableVoices = tts.voices ?: return
        val preferred = when (voicePreference.lowercase(Locale.getDefault())) {
            "bright" -> availableVoices.firstOrNull { it.name.contains("en-us", true) && it.name.contains("fema", true) }
            "warm" -> availableVoices.firstOrNull { it.name.contains("en-gb", true) && it.name.contains("male", true) }
            "energetic" -> availableVoices.firstOrNull { it.name.contains("en-us", true) && it.name.contains("male", true) }
            else -> availableVoices.firstOrNull { it.name.contains("en-us", true) }
        }
        preferred?.let { tts.voice = it }
    }

    override fun onDestroy() {
        recognitionJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_RECORD_AUDIO = 100
        private const val REQUEST_CODE_SEND_SMS = 200
        private const val REQUEST_CODE_CALL_PHONE = 250
        private const val REQUEST_CODE_INITIAL_PERMISSIONS = 300
    }
}
