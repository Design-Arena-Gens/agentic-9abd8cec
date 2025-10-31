package com.max.pandaai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.max.pandaai.ai.AIService
import com.max.pandaai.data.ChatMessage
import com.max.pandaai.data.ChatRepository
import com.max.pandaai.settings.AssistantSettings
import com.max.pandaai.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Centralises chat state, persistent history, and AI calls.
class MainViewModel(
    private val repository: ChatRepository,
    private val settingsManager: SettingsManager,
    private val aiService: AIService
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assistantSettings: StateFlow<AssistantSettings> =
        settingsManager.settingsFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AssistantSettings()
        )

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    fun addMessage(content: String, fromUser: Boolean) {
        viewModelScope.launch {
            repository.addMessage(
                ChatMessage(
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    fromUser = fromUser
                )
            )
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clear()
        }
    }

    suspend fun requestAiResponse(prompt: String): String {
        _isProcessing.value = true
        val assistantName = assistantSettings.first().assistantName
        return try {
            aiService.requestResponse(prompt, assistantName)
        } finally {
            _isProcessing.value = false
        }
    }

    class Factory(
        private val repository: ChatRepository,
        private val settingsManager: SettingsManager,
        private val aiService: AIService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository, settingsManager, aiService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
