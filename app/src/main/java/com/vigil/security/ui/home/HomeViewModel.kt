package com.vigil.security.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vigil.security.core.model.ProtectionState
import com.vigil.security.core.model.SecurityReport
import com.vigil.security.data.repository.ProtectionStateRepository
import com.vigil.security.data.repository.ScanRepository
import com.vigil.security.threatintel.OpenPhishStats
import com.vigil.security.threatintel.ThreatDefinitionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val protectionState: ProtectionState = ProtectionState(),
    val recentScans: List<SecurityReport> = emptyList(),
    val openPhishStats: OpenPhishStats = OpenPhishStats(),
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val protectionStateRepository: ProtectionStateRepository,
    private val scanRepository: ScanRepository,
    private val threatDefinitionManager: ThreatDefinitionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        viewModelScope.launch {
            threatDefinitionManager.refreshOpenPhishStats()
        }
    }

    fun refreshState() {
        protectionStateRepository.refresh()
        viewModelScope.launch {
            val stateFlow = protectionStateRepository.getProtectionStateFlow().first()
            val recentScans = scanRepository.getAllScans().first().take(4)
            threatDefinitionManager.refreshOpenPhishStats()
            _uiState.update {
                it.copy(
                    protectionState = stateFlow,
                    recentScans = recentScans,
                    openPhishStats = threatDefinitionManager.openPhishStats.value,
                    isLoading = false
                )
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                protectionStateRepository.getProtectionStateFlow(),
                scanRepository.getAllScans(),
                threatDefinitionManager.openPhishStats
            ) { protectionState, allScans, openPhish ->
                HomeUiState(
                    protectionState = protectionState,
                    recentScans = allScans.take(4),
                    openPhishStats = openPhish,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun toggleMonitoring(enabled: Boolean) {
        protectionStateRepository.isMonitoringEnabled = enabled
        refreshState()
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

class HomeViewModelFactory(
    private val protectionRepo: ProtectionStateRepository,
    private val scanRepo: ScanRepository,
    private val threatDefinitionManager: ThreatDefinitionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(protectionRepo, scanRepo, threatDefinitionManager) as T
    }
}
