package com.vigil.security.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.model.SecurityReport
import com.vigil.security.data.repository.ScanRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReportUiState(
    val selectedReport: SecurityReport? = null,
    val allReports: List<SecurityReport> = emptyList(),
    val filteredReports: List<SecurityReport> = emptyList(),
    val selectedFilter: RiskLevel? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

class ReportViewModel(
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _selectedReportId = MutableStateFlow<String?>(null)
    private val _selectedFilter = MutableStateFlow<RiskLevel?>(null)
    private val _searchQuery = MutableStateFlow("")

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                scanRepository.getAllScans(),
                _selectedReportId,
                _selectedFilter,
                _searchQuery
            ) { reports, selectedId, filter, query ->
                val selected = if (selectedId != null) {
                    reports.firstOrNull { it.reportId == selectedId }
                } else {
                    null
                }

                val filtered = reports.filter { report ->
                    val matchesFilter = filter == null || report.riskLevel == filter
                    val matchesQuery = query.isBlank() ||
                        report.destinationDomain.contains(query, ignoreCase = true) ||
                        report.originalUrl.contains(query, ignoreCase = true) ||
                        (report.sourceAppLabel?.contains(query, ignoreCase = true) == true)
                    matchesFilter && matchesQuery
                }

                ReportUiState(
                    selectedReport = selected,
                    allReports = reports,
                    filteredReports = filtered,
                    selectedFilter = filter,
                    searchQuery = query,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectReportById(id: String) {
        _selectedReportId.value = id
    }

    fun clearSelectedReport() {
        _selectedReportId.value = null
    }

    fun onFilterSelected(filter: RiskLevel?) {
        _selectedFilter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}

class ReportViewModelFactory(
    private val scanRepo: ScanRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReportViewModel(scanRepo) as T
    }
}
