package com.vigil.security.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vigil.security.core.model.ThreatDefinitionInfo
import com.vigil.security.core.security.KeyStoreManager
import com.vigil.security.data.repository.ProtectionStateRepository
import com.vigil.security.data.repository.ScanRepository
import com.vigil.security.threatintel.ThreatDefinitionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isMonitoringEnabled: Boolean = true,
    val isLocalHeuristicsEnabled: Boolean = true,
    val showOverlayOnSafe: Boolean = false,
    val gsbApiKey: String = "",
    val vtApiKey: String = "",
    val isAccessibilityGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val threatDefinitions: ThreatDefinitionInfo = ThreatDefinitionInfo(),
    val isCheckingForUpdates: Boolean = false,
    val isApplyingUpdate: Boolean = false,
    val updateMessage: String? = null
)

class SettingsViewModel(
    private val keyStoreManager: KeyStoreManager,
    private val protectionRepo: ProtectionStateRepository,
    private val scanRepo: ScanRepository,
    private val threatDefinitionManager: ThreatDefinitionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            threatDefinitionManager.definitionState.collect { defs ->
                _uiState.update { it.copy(threatDefinitions = defs) }
            }
        }
    }

    fun loadSettings() {
        _uiState.update {
            it.copy(
                isMonitoringEnabled = keyStoreManager.isMonitoringEnabled,
                isLocalHeuristicsEnabled = keyStoreManager.isLocalHeuristicsEnabled,
                showOverlayOnSafe = keyStoreManager.showOverlayOnSafe,
                gsbApiKey = keyStoreManager.googleSafeBrowsingApiKey,
                vtApiKey = keyStoreManager.virusTotalApiKey,
                isAccessibilityGranted = protectionRepo.isAccessibilityEnabled(),
                isOverlayGranted = protectionRepo.isOverlayGranted()
            )
        }
    }

    fun checkForDefinitionUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingForUpdates = true, updateMessage = null) }
            val (hasUpdate, message) = threatDefinitionManager.checkForUpdates()
            _uiState.update {
                it.copy(
                    isCheckingForUpdates = false,
                    updateMessage = message
                )
            }
        }
    }

    fun applyDefinitionUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingUpdate = true) }
            val (success, message) = threatDefinitionManager.applyDefinitionUpdate()
            _uiState.update {
                it.copy(
                    isApplyingUpdate = false,
                    updateMessage = message
                )
            }
        }
    }

    fun toggleMonitoring(enabled: Boolean) {
        keyStoreManager.isMonitoringEnabled = enabled
        _uiState.update { it.copy(isMonitoringEnabled = enabled) }
    }

    fun toggleLocalHeuristics(enabled: Boolean) {
        keyStoreManager.isLocalHeuristicsEnabled = enabled
        _uiState.update { it.copy(isLocalHeuristicsEnabled = enabled) }
    }

    fun toggleShowOverlayOnSafe(enabled: Boolean) {
        keyStoreManager.showOverlayOnSafe = enabled
        _uiState.update { it.copy(showOverlayOnSafe = enabled) }
    }

    fun saveGsbApiKey(key: String) {
        keyStoreManager.googleSafeBrowsingApiKey = key.trim()
        _uiState.update { it.copy(gsbApiKey = key.trim()) }
    }

    fun saveVtApiKey(key: String) {
        keyStoreManager.virusTotalApiKey = key.trim()
        _uiState.update { it.copy(vtApiKey = key.trim()) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            scanRepo.clearHistory()
        }
    }
}

class SettingsViewModelFactory(
    private val keyStoreManager: KeyStoreManager,
    private val protectionRepo: ProtectionStateRepository,
    private val scanRepo: ScanRepository,
    private val threatDefinitionManager: ThreatDefinitionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(keyStoreManager, protectionRepo, scanRepo, threatDefinitionManager) as T
    }
}
