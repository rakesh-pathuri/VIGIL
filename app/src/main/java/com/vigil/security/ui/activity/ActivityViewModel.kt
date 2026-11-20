package com.vigil.security.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.model.SecurityReport
import com.vigil.security.data.repository.ScanRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ActivityUiState(
    val scans: List<SecurityReport> = emptyList(),
    val selectedFilter: RiskLevel? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

class ActivityViewModel(
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow<RiskLevel?>(null)

    val uiState: StateFlow<ActivityUiState> = combine(
        scanRepository.getAllScans(),
        _searchQuery,
        _selectedFilter
    ) { allScans, query, filter ->
        val filtered = allScans.filter { report ->
            val matchesFilter = (filter == null || report.riskLevel == filter)
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                report.destinationDomain.contains(query, ignoreCase = true) ||
                        report.originalUrl.contains(query, ignoreCase = true) ||
                        (report.sourceAppLabel?.contains(query, ignoreCase = true) == true)
            }
            matchesFilter && matchesQuery
        }

        ActivityUiState(
            scans = filtered,
            selectedFilter = filter,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActivityUiState())

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onFilterSelected(filter: RiskLevel?) {
        _selectedFilter.value = filter
    }

    fun deleteScan(id: String) {
        viewModelScope.launch {
            scanRepository.deleteScan(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            scanRepository.clearHistory()
        }
    }
}

class ActivityViewModelFactory(
    private val scanRepo: ScanRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ActivityViewModel(scanRepo) as T
    }
}
