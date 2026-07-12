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
import me.superbear.todolist.assistant.SearchRuntime
import me.superbear.todolist.assistant.search.SearchCapabilityKind
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
    private val llmRuntime: LlmRuntime,
    private val searchRuntime: SearchRuntime
) {
    val PROVIDER_INFO = mapOf(
        LLMProvider.OpenAI to ProviderInfo("OpenAI", "gpt-5-mini", listOf(
            "gpt-5.5", "gpt-5.4", "gpt-5.4-mini", "gpt-5.4-nano",
            "gpt-5.2", "gpt-5.1",
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

    private val _selectedSearchCapability = MutableStateFlow(SearchCapabilityKind.TAVILY)
    val selectedSearchCapability: StateFlow<SearchCapabilityKind> = _selectedSearchCapability.asStateFlow()

    private val _currentSearchApiKey = MutableStateFlow("")
    val currentSearchApiKey: StateFlow<String> = _currentSearchApiKey.asStateFlow()

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

        // Debug-only, one-time seed: copy the developer's local.properties test keys into prefs
        // so debug builds work out of the box, the same way SeedManager seeds sample tasks once.
        // Runs exactly once, ever (guarded by DEBUG_KEYS_SEEDED_KEY) — after that, loadApiKey()
        // has no BuildConfig fallback, so explicitly clearing a key in Settings actually sticks
        // instead of reappearing on the next launch.
        if (BuildConfig.DEBUG && !prefs.getBoolean(DEBUG_KEYS_SEEDED_KEY, false)) {
            seedDebugKeyIfBlank(LLMProvider.OpenAI, BuildConfig.OPENAI_API_KEY)
            seedDebugKeyIfBlank(LLMProvider.DeepSeek, BuildConfig.DEEPSEEK_API_KEY)
            seedDebugKeyIfBlank(SEARCH_API_KEY_PREF, BuildConfig.TAVILY_API_KEY)
            prefs.edit().putBoolean(DEBUG_KEYS_SEEDED_KEY, true).apply()
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

        val initialSearchCapability = loadSearchCapability()
        val initialSearchKey = loadSearchApiKey()
        searchRuntime.setSearchCapability(initialSearchCapability)
        searchRuntime.setSearchApiKey(initialSearchKey)
        _selectedSearchCapability.value = initialSearchCapability
        _currentSearchApiKey.value = initialSearchKey
    }

    private fun loadApiKey(provider: LLMProvider): String {
        return prefs.getString("${provider.id.lowercase()}_api_key", null) ?: ""
    }

    private fun seedDebugKeyIfBlank(provider: LLMProvider, debugKey: String) {
        seedDebugKeyIfBlank(prefKey = "${provider.id.lowercase()}_api_key", debugKey = debugKey)
    }

    private fun seedDebugKeyIfBlank(prefKey: String, debugKey: String) {
        if (debugKey.isBlank()) return
        if (prefs.getString(prefKey, null).isNullOrBlank()) {
            prefs.edit().putString(prefKey, debugKey).apply()
        }
    }

    private fun loadSearchApiKey(): String {
        return prefs.getString(SEARCH_API_KEY_PREF, null) ?: ""
    }

    private fun loadSearchCapability(): SearchCapabilityKind {
        return prefs.getString(SEARCH_CAPABILITY_PREF, null)
            ?.let { runCatching { SearchCapabilityKind.valueOf(it) }.getOrNull() }
            ?: SearchCapabilityKind.TAVILY
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

    fun setSearchCapability(kind: SearchCapabilityKind) {
        prefs.edit().putString(SEARCH_CAPABILITY_PREF, kind.name).apply()
        searchRuntime.setSearchCapability(kind)
        _selectedSearchCapability.value = kind
    }

    fun setSearchApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(SEARCH_API_KEY_PREF, trimmed).apply()
        searchRuntime.setSearchApiKey(trimmed)
        _currentSearchApiKey.value = trimmed
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

    companion object {
        private const val DEBUG_KEYS_SEEDED_KEY = "debug_keys_seeded_v1"
        private const val SEARCH_API_KEY_PREF = "tavily_api_key"
        private const val SEARCH_CAPABILITY_PREF = "search_capability_kind"
    }
}
