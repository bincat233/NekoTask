package me.superbear.todolist.ui.settings

import ai.koog.prompt.llm.LLMProvider
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.assistant.LlmRuntime
import me.superbear.todolist.assistant.subtask.DivisionStrategy

data class ProviderInfo(
    val displayName: String,
    val defaultModel: String,
    val fallbackModels: List<String>
)

/**
 * Owns AI provider/API-key/model settings, app-level AI preferences (useAI/maxSubtasks/strategy),
 * and language switching. Pushes provider/key/model changes into the shared [LlmRuntime].
 */
class SettingsCoordinator(
    private val prefs: SharedPreferences,
    private val llmRuntime: LlmRuntime
) {
    val PROVIDER_INFO = mapOf(
        LLMProvider.OpenAI to ProviderInfo("OpenAI", "gpt-5-mini", listOf(
            "gpt-5", "gpt-5-mini", "gpt-5-nano",
            "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
            "gpt-4o", "gpt-4o-mini",
            "o3-mini", "o4-mini"
        )),
        LLMProvider.DeepSeek to ProviderInfo("DeepSeek", "deepseek-v4-flash", listOf(
            "deepseek-v4-flash", "deepseek-v4-pro"
        ))
    )

    private val _selectedProvider = MutableStateFlow<LLMProvider>(LLMProvider.OpenAI)
    val selectedProvider: StateFlow<LLMProvider> = _selectedProvider.asStateFlow()

    private val _currentApiKey = MutableStateFlow("")
    val currentApiKey: StateFlow<String> = _currentApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _settingsState = MutableStateFlow(
        SettingsState(
            useAI = prefs.getBoolean("settings_use_ai", true),
            maxSubtasks = prefs.getInt("settings_max_subtasks", 5),
            aiDivisionStrategy = prefs.getString("settings_ai_strategy", null)
                ?.let { runCatching { DivisionStrategy.valueOf(it) }.getOrNull() }
                ?: DivisionStrategy.BALANCED,
            currentLanguage = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "auto"
        )
    )
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    init {
        // Migrate old keys to provider-prefixed format
        val oldKey = prefs.getString("api_key", null)
        if (oldKey != null && prefs.getString("openai_api_key", null) == null) {
            prefs.edit().putString("openai_api_key", oldKey).remove("api_key").apply()
        }
        val oldModel = prefs.getString("selected_model", null)
        if (oldModel != null && prefs.getString("openai_selected_model", null) == null) {
            prefs.edit().putString("openai_selected_model", oldModel).remove("selected_model").apply()
        }

        val initialProviderStr = prefs.getString("selected_provider", null)
        val initialProvider = when (initialProviderStr) {
            "openai" -> LLMProvider.OpenAI
            "deepseek" -> LLMProvider.DeepSeek
            else -> LLMProvider.OpenAI
        }
        val initialInfo = PROVIDER_INFO[initialProvider] ?: PROVIDER_INFO[LLMProvider.OpenAI]!!

        val initialKey = loadApiKey(initialProvider)
        val initialModel = loadModel(initialProvider, initialInfo)

        llmRuntime.setApiKey(initialProvider, initialKey)
        llmRuntime.selectProvider(initialProvider)
        llmRuntime.selectModelByName(initialModel)

        _selectedProvider.value = initialProvider
        _currentApiKey.value = initialKey
        _selectedModel.value = initialModel

        refreshModels()
    }

    private fun loadApiKey(provider: LLMProvider): String {
        val key = prefs.getString("${provider.id.lowercase()}_api_key", null)
        return key?.takeIf { it.isNotBlank() } ?: BuildConfig.OPENAI_API_KEY
    }

    private fun loadModel(provider: LLMProvider, info: ProviderInfo = PROVIDER_INFO[provider]!!): String {
        val model = prefs.getString("${provider.id.lowercase()}_selected_model", null)
        return model?.takeIf { it.isNotBlank() } ?: info.defaultModel
    }

    fun selectProvider(provider: LLMProvider) {
        val info = PROVIDER_INFO[provider] ?: return
        prefs.edit().putString("selected_provider", provider.id).apply()

        val key = loadApiKey(provider)
        val model = loadModel(provider, info)
        llmRuntime.setApiKey(provider, key)
        llmRuntime.selectProvider(provider)
        llmRuntime.selectModelByName(model)

        _selectedProvider.value = provider
        _currentApiKey.value = key
        _selectedModel.value = model
        refreshModels()
    }

    fun setApiKey(provider: LLMProvider, key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("${provider.id.lowercase()}_api_key", trimmed).apply()
        if (_selectedProvider.value == provider) {
            llmRuntime.setApiKey(provider, trimmed)
            _currentApiKey.value = trimmed
        }
    }

    fun selectModel(model: String) {
        val provider = _selectedProvider.value
        prefs.edit().putString("${provider.id.lowercase()}_selected_model", model).apply()
        llmRuntime.selectModelByName(model)
        _selectedModel.value = model
    }

    fun refreshModels() {
        val info = PROVIDER_INFO[_selectedProvider.value]
        if (info != null) {
            _availableModels.value = info.fallbackModels
        }
    }

    fun updateSettings(settings: SettingsState) {
        prefs.edit()
            .putBoolean("settings_use_ai", settings.useAI)
            .putInt("settings_max_subtasks", settings.maxSubtasks)
            .putString("settings_ai_strategy", settings.aiDivisionStrategy.name)
            .apply()
        _settingsState.value = settings
    }

    fun updateLanguage(languageTag: String) {
        val appLocale: LocaleListCompat = if (languageTag == "auto") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
        _settingsState.update { it.copy(currentLanguage = languageTag) }
    }
}
