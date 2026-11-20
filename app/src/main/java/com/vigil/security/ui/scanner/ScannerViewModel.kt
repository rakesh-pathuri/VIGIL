package com.vigil.security.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vigil.security.core.model.SecurityReport
import com.vigil.security.data.repository.ScanRepository
import com.vigil.security.engine.LinkAnalysisEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScanStatus {
    object Idle : ScanStatus
    data class Analyzing(val currentStage: String, val progress: Float) : ScanStatus
    data class Success(val report: SecurityReport) : ScanStatus
    data class Error(val message: String) : ScanStatus
}

data class ScannerUiState(
    val inputUrl: String = "",
    val scanStatus: ScanStatus = ScanStatus.Idle
)

class ScannerViewModel(
    private val analysisEngine: LinkAnalysisEngine,
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onUrlInputChanged(newUrl: String) {
        _uiState.update { it.copy(inputUrl = newUrl) }
    }

    fun clearInput() {
        _uiState.update { it.copy(inputUrl = "", scanStatus = ScanStatus.Idle) }
    }

    fun submitUrlForAnalysis() {
        val rawUrl = _uiState.value.inputUrl.trim()
        if (rawUrl.isEmpty()) {
            _uiState.update { it.copy(scanStatus = ScanStatus.Error("Please enter or paste a valid URL to analyze.")) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(scanStatus = ScanStatus.Analyzing("Normalizing URL structure...", 0.15f))
            }
            delay(120)

            _uiState.update {
                it.copy(scanStatus = ScanStatus.Analyzing("Inspecting homoglyphs & Unicode...", 0.35f))
            }
            delay(120)

            _uiState.update {
                it.copy(scanStatus = ScanStatus.Analyzing("Analyzing brand typosquatting & entropy...", 0.60f))
            }
            delay(120)

            _uiState.update {
                it.copy(scanStatus = ScanStatus.Analyzing("Querying threat intelligence database...", 0.85f))
            }

            try {
                val report = analysisEngine.analyzeUrl(
                    rawUrl = rawUrl,
                    sourcePackage = null,
                    sourceAppLabel = "Manual Scanner"
                )
                // Persist locally
                scanRepository.saveScan(report)

                _uiState.update {
                    it.copy(scanStatus = ScanStatus.Success(report))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(scanStatus = ScanStatus.Error("Analysis failed: ${e.localizedMessage ?: "Unknown error"}"))
                }
            }
        }
    }
}

class ScannerViewModelFactory(
    private val engine: LinkAnalysisEngine,
    private val scanRepo: ScanRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScannerViewModel(engine, scanRepo) as T
    }
}
