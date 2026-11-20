package com.vigil.security.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(record: ScanRecordEntity)

    @Query("""
        SELECT * FROM scan_records 
        WHERE id IN (
            SELECT id FROM (
                SELECT id, destinationDomain, 
                       ROW_NUMBER() OVER(PARTITION BY destinationDomain ORDER BY timestamp DESC) as rn 
                FROM scan_records
            ) WHERE rn = 1
        )
        ORDER BY timestamp DESC
    """)
    fun getAllScans(): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scan_records WHERE riskLevel = :riskLevel ORDER BY timestamp DESC")
    fun getScansByRisk(riskLevel: String): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scan_records WHERE id = :id LIMIT 1")
    suspend fun getScanById(id: String): ScanRecordEntity?

    @Query("""
        SELECT * FROM scan_records 
        WHERE destinationDomain = :domain 
           OR sanitizedUrl = :url 
           OR destinationDomain LIKE '%' || :domain 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getScanByUrl(domain: String, url: String): ScanRecordEntity?

    @Query("""
        SELECT * FROM scan_records 
        WHERE destinationDomain LIKE '%' || :query || '%' 
           OR sanitizedUrl LIKE '%' || :query || '%'
           OR sourceAppLabel LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchScans(query: String): Flow<List<ScanRecordEntity>>

    @Query("SELECT COUNT(DISTINCT destinationDomain) FROM scan_records")
    fun getTotalScansCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT destinationDomain) FROM scan_records WHERE riskLevel IN ('DANGEROUS', 'CAUTION')")
    fun getSuspiciousScansCount(): Flow<Int>

    @Query("""
        DELETE FROM scan_records 
        WHERE id NOT IN (
            SELECT id FROM (
                SELECT id, destinationDomain, 
                       ROW_NUMBER() OVER(PARTITION BY destinationDomain ORDER BY timestamp DESC) as rn 
                FROM scan_records
            ) WHERE rn = 1
        )
    """)
    suspend fun pruneDuplicates()

    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun deleteScanById(id: String)

    @Query("DELETE FROM scan_records")
    suspend fun clearAllScans()
}
