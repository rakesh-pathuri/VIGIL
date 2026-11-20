package com.vigil.security

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.vigil.security.core.design.VigilTheme
import com.vigil.security.ui.activity.ActivityScreen
import com.vigil.security.ui.activity.ActivityViewModel
import com.vigil.security.ui.activity.ActivityViewModelFactory
import com.vigil.security.ui.home.HomeScreen
import com.vigil.security.ui.home.HomeViewModel
import com.vigil.security.ui.home.HomeViewModelFactory
import com.vigil.security.ui.navigation.VigilDestination
import com.vigil.security.ui.reports.ReportViewModel
import com.vigil.security.ui.reports.ReportViewModelFactory
import com.vigil.security.ui.reports.ReportsScreen
import com.vigil.security.ui.scanner.ScannerScreen
import com.vigil.security.ui.scanner.ScannerViewModel
import com.vigil.security.ui.scanner.ScannerViewModelFactory
import com.vigil.security.ui.settings.SettingsScreen
import com.vigil.security.ui.settings.SettingsViewModel
import com.vigil.security.ui.settings.SettingsViewModelFactory
import com.vigil.security.ui.splash.SplashScreen

class MainActivity : ComponentActivity() {

    private var navController: androidx.navigation.NavHostController? = null

    private val homeViewModel: HomeViewModel by viewModels {
        val app = application as VigilApplication
        HomeViewModelFactory(
            app.container.protectionStateRepository,
            app.container.scanRepository,
            app.container.threatDefinitionManager
        )
    }

    private val scannerViewModel: ScannerViewModel by viewModels {
        val app = application as VigilApplication
        ScannerViewModelFactory(app.container.analysisEngine, app.container.scanRepository)
    }

    private val activityViewModel: ActivityViewModel by viewModels {
        val app = application as VigilApplication
        ActivityViewModelFactory(app.container.scanRepository)
    }

    private val reportViewModel: ReportViewModel by viewModels {
        val app = application as VigilApplication
        ReportViewModelFactory(app.container.scanRepository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as VigilApplication
        SettingsViewModelFactory(
            app.container.keyStoreManager,
            app.container.protectionStateRepository,
            app.container.scanRepository,
            app.container.threatDefinitionManager
        )
    }

    override fun onStart() {
        super.onStart()
        try {
            val intent = Intent(this, com.vigil.security.service.overlay.VigilOverlayService::class.java).apply {
                action = com.vigil.security.service.overlay.VigilOverlayService.ACTION_DISMISS
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialReportId = intent?.getStringExtra("NAVIGATE_TO_REPORT_ID")
        if (initialReportId != null) {
            intent?.removeExtra("NAVIGATE_TO_REPORT_ID")
            reportViewModel.selectReportById(initialReportId)
        }

        setContent {
            VigilTheme {
                val nc = rememberNavController()
                navController = nc
                val navBackStackEntry by nc.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: VigilDestination.HOME.route

                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    if (initialReportId != null) {
                        nc.navigate(VigilDestination.REPORTS.route) {
                            launchSingleTop = true
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                VigilDestination.values().forEach { destination ->
                                    val selected = currentRoute == destination.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            if (currentRoute != destination.route) {
                                                if (destination == VigilDestination.HOME) {
                                                    nc.navigate(VigilDestination.HOME.route) {
                                                        popUpTo(nc.graph.findStartDestination().id) {
                                                            inclusive = false
                                                        }
                                                        launchSingleTop = true
                                                    }
                                                } else {
                                                    nc.navigate(destination.route) {
                                                        popUpTo(nc.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            } else {
                                                if (destination == VigilDestination.REPORTS) {
                                                    reportViewModel.clearSelectedReport()
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = destination.icon,
                                                contentDescription = destination.title
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = destination.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = nc,
                            startDestination = VigilDestination.HOME.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(VigilDestination.HOME.route) {
                                HomeScreen(
                                    viewModel = homeViewModel,
                                    onNavigateToScanner = {
                                        nc.navigate(VigilDestination.SCANNER.route)
                                    },
                                    onNavigateToActivity = {
                                        nc.navigate(VigilDestination.ACTIVITY.route)
                                    },
                                    onSelectReport = { reportId ->
                                        reportViewModel.selectReportById(reportId)
                                        nc.navigate(VigilDestination.REPORTS.route)
                                    }
                                )
                            }

                            composable(VigilDestination.SCANNER.route) {
                                ScannerScreen(
                                    viewModel = scannerViewModel,
                                    onViewFullReport = { reportId ->
                                        reportViewModel.selectReportById(reportId)
                                        nc.navigate(VigilDestination.REPORTS.route)
                                    }
                                )
                            }

                            composable(VigilDestination.ACTIVITY.route) {
                                ActivityScreen(
                                    viewModel = activityViewModel,
                                    onSelectReport = { reportId ->
                                        reportViewModel.selectReportById(reportId)
                                        nc.navigate(VigilDestination.REPORTS.route)
                                    }
                                )
                            }

                            composable(VigilDestination.REPORTS.route) {
                                ReportsScreen(
                                    viewModel = reportViewModel,
                                    onNavigateToScanner = {
                                        nc.navigate(VigilDestination.SCANNER.route)
                                    }
                                )
                            }

                            composable(VigilDestination.SETTINGS.route) {
                                SettingsScreen(
                                    viewModel = settingsViewModel
                                )
                            }
                        }
                    }

                    // Cyber Animated Splash Screen
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(),
                        exit = fadeOut(animationSpec = tween(durationMillis = 400))
                    ) {
                        SplashScreen(
                            onDismiss = { showSplash = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.refreshState()
        settingsViewModel.loadSettings()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val reportId = intent.getStringExtra("NAVIGATE_TO_REPORT_ID")
        if (reportId != null) {
            intent.removeExtra("NAVIGATE_TO_REPORT_ID")
            reportViewModel.selectReportById(reportId)
            navController?.navigate(VigilDestination.REPORTS.route) {
                launchSingleTop = true
            }
        }
    }
}
