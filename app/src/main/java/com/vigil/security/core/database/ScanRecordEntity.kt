package com.vigil.security.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vigil.security.core.model.*

@Entity(tableName = "scan_records")
data class ScanRecordEntity(
    @PrimaryKey
    val id: String,
    val sanitizedUrl: String,
    val normalizedUrl: String,
    val destinationDomain: String,
    val sourcePackage: String?,
    val sourceAppLabel: String?,
    val riskLevel: String,
    val confidence: String,
    val summary: String,
    val findingsJson: String,
    val checksCompletedJson: String,
    val recommendedAction: String,
    val timestamp: Long
) {
    fun toDomainModel(gson: Gson = Gson()): SecurityReport {
        val findingsType = object : TypeToken<List<Evidence>>() {}.type
        val checksType = object : TypeToken<List<AnalysisCheck>>() {}.type

        val findings: List<Evidence> = try {
            gson.fromJson(findingsJson, findingsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val checks: List<AnalysisCheck> = try {
            gson.fromJson(checksCompletedJson, checksType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return SecurityReport(
            reportId = id,
            originalUrl = sanitizedUrl,
            normalizedUrl = normalizedUrl,
            destinationDomain = destinationDomain,
            sourcePackage = sourcePackage,
            sourceAppLabel = sourceAppLabel,
            riskLevel = try { RiskLevel.valueOf(riskLevel) } catch (_: Exception) { RiskLevel.UNKNOWN },
            confidence = try { ConfidenceLevel.valueOf(confidence) } catch (_: Exception) { ConfidenceLevel.MEDIUM },
            summary = summary,
            findings = findings,
            checksCompleted = checks,
            checksUnavailable = emptyList(),
            recommendedAction = recommendedAction,
            timestamp = timestamp
        )
    }

    companion object {
        fun fromDomainModel(report: SecurityReport, gson: Gson = Gson()): ScanRecordEntity {
            return ScanRecordEntity(
                id = report.reportId,
                sanitizedUrl = report.originalUrl,
                normalizedUrl = report.normalizedUrl,
                destinationDomain = report.destinationDomain,
                sourcePackage = report.sourcePackage,
                sourceAppLabel = report.sourceAppLabel,
                riskLevel = report.riskLevel.name,
                confidence = report.confidence.name,
                summary = report.summary,
                findingsJson = gson.toJson(report.findings),
                checksCompletedJson = gson.toJson(report.checksCompleted),
                recommendedAction = report.recommendedAction,
                timestamp = report.timestamp
            )
        }
    }
}
