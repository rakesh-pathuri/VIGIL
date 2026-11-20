package com.vigil.security.di

import android.content.Context
import com.vigil.security.core.database.VigilDatabase
import com.vigil.security.core.security.KeyStoreManager
import com.vigil.security.data.repository.ProtectionStateRepository
import com.vigil.security.data.repository.ScanRepository
import com.vigil.security.engine.LinkAnalysisEngine
import com.vigil.security.threatintel.GoogleSafeBrowsingProvider
import com.vigil.security.threatintel.LocalSignatureProvider
import com.vigil.security.threatintel.ThreatDefinitionManager
import com.vigil.security.threatintel.VirusTotalProvider

class VigilContainer(context: Context) {

    val database: VigilDatabase by lazy {
        VigilDatabase.getInstance(context)
    }

    val keyStoreManager: KeyStoreManager by lazy {
        KeyStoreManager(context)
    }

    val scanRepository: ScanRepository by lazy {
        ScanRepository(database.scanRecordDao())
    }

    val protectionStateRepository: ProtectionStateRepository by lazy {
        ProtectionStateRepository(context, keyStoreManager, scanRepository)
    }

    val localSignatureProvider: LocalSignatureProvider by lazy {
        LocalSignatureProvider()
    }

    val threatDefinitionManager: ThreatDefinitionManager by lazy {
        ThreatDefinitionManager(context, localSignatureProvider)
    }

    val googleSafeBrowsingProvider: GoogleSafeBrowsingProvider by lazy {
        GoogleSafeBrowsingProvider(keyStoreManager)
    }

    val virusTotalProvider: VirusTotalProvider by lazy {
        VirusTotalProvider(keyStoreManager)
    }

    val analysisEngine: LinkAnalysisEngine by lazy {
        LinkAnalysisEngine(
            localSignatureProvider = localSignatureProvider,
            googleSafeBrowsingProvider = googleSafeBrowsingProvider,
            virusTotalProvider = virusTotalProvider
        )
    }
}
