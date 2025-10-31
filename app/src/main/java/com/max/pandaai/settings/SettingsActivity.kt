package com.max.pandaai.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.max.pandaai.R
import com.max.pandaai.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        settingsManager = SettingsManager(this)

        val voices = listOf("Default", "Bright", "Warm", "Energetic")
        binding.voiceSelector.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, voices)
        )

        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            binding.assistantNameInput.setText(settings.assistantName)
            binding.voiceSelector.setText(settings.assistantVoice, false)
            binding.darkModeSwitch.isChecked = settings.darkModeEnabled
        }

        binding.saveButton.setOnClickListener {
            lifecycleScope.launch {
                val newSettings = AssistantSettings(
                    assistantName = binding.assistantNameInput.text?.toString()
                        ?.ifBlank { AssistantSettings().assistantName }
                        ?: AssistantSettings().assistantName,
                    assistantVoice = binding.voiceSelector.text?.toString()
                        ?.ifBlank { AssistantSettings().assistantVoice }
                        ?: AssistantSettings().assistantVoice,
                    darkModeEnabled = binding.darkModeSwitch.isChecked
                )
                settingsManager.saveSettings(newSettings)
                AppCompatDelegate.setDefaultNightMode(
                    if (newSettings.darkModeEnabled)
                        AppCompatDelegate.MODE_NIGHT_YES
                    else
                        AppCompatDelegate.MODE_NIGHT_NO
                )
                Toast.makeText(
                    this@SettingsActivity,
                    "Settings saved",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
