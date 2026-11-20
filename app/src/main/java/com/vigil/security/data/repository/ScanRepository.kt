package com.vigil.security.data.repository

import com.vigil.security.core.database.ScanRecordDao
import com.vigil.security.core.database.ScanRecordEntity
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.model.SecurityReport
import com.vigil.security.core.security.UrlSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ScanRepository(
    private val scanRecordDao: ScanRecordDao
) {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scanRecordDao.pruneDuplicates()
            } catch (_: Exception) {}
        }
    }

    suspend fun saveScan(report: SecurityReport) {
        val sanitizedUrl = UrlSanitizer.sanitizeUrl(report.originalUrl)
        val domain = extractSimpleDomain(sanitizedUrl).ifEmpty { report.destinationDomain }

        // Deduplication: check if this domain or URL was already scanned
        val existing = scanRecordDao.getScanByUrl(domain, sanitizedUrl)
        val targetId = existing?.id ?: report.reportId

        val updatedReport = report.copy(
            reportId = targetId,
            originalUrl = sanitizedUrl,
            destinationDomain = if (domain.isNotEmpty()) domain else report.destinationDomain,
            timestamp = System.currentTimeMillis()
        )

        scanRecordDao.insertScan(ScanRecordEntity.fromDomainModel(updatedReport))
        scanRecordDao.pruneDuplicates()
    }

    fun getAllScans(): Flow<List<SecurityReport>> {
        return scanRecordDao.getAllScans().map { entities ->
            entities.map { it.toDomainModel() }
                .distinctBy { it.destinationDomain.lowercase() }
        }
    }

    fun getScansByRisk(riskLevel: RiskLevel): Flow<List<SecurityReport>> {
        return scanRecordDao.getScansByRisk(riskLevel.name).map { entities ->
            entities.map { it.toDomainModel() }
                .distinctBy { it.destinationDomain.lowercase() }
        }
    }

    suspend fun getScanById(id: String): SecurityReport? {
        return scanRecordDao.getScanById(id)?.toDomainModel()
    }

    suspend fun getScanByUrl(url: String): SecurityReport? {
        val sanitized = UrlSanitizer.sanitizeUrl(url)
        val domain = extractSimpleDomain(sanitized)
        return scanRecordDao.getScanByUrl(domain, sanitized)?.toDomainModel()
    }

    private fun extractSimpleDomain(url: String): String {
        return try {
            val trimmed = url.trim()
            val withoutProtocol = if (trimmed.contains("://")) trimmed.substringAfter("://") else trimmed
            withoutProtocol.substringBefore("/").substringBefore("?").substringBefore(":")
        } catch (_: Exception) {
            ""
        }
    }

    fun searchScans(query: String): Flow<List<SecurityReport>> {
        return scanRecordDao.searchScans(query).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getTotalScansCount(): Flow<Int> = scanRecordDao.getTotalScansCount()

    fun getSuspiciousScansCount(): Flow<Int> = scanRecordDao.getSuspiciousScansCount()

    suspend fun deleteScan(id: String) {
        scanRecordDao.deleteScanById(id)
    }

    suspend fun clearHistory() {
        scanRecordDao.clearAllScans()
    }
}
