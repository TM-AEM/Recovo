package com.tmaem.recovo.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tmaem.recovo.core.navigation.RecovoDestinations
import com.tmaem.recovo.feature.home.HomeScreen
import com.tmaem.recovo.feature.library.LibraryScreen
import com.tmaem.recovo.feature.record.RecordScreen

@Composable
fun RecovoApp(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = RecovoDestinations.HOME,
        modifier = modifier.fillMaxSize()
    ) {
        composable(RecovoDestinations.HOME) {
            HomeScreen(
                onNavigateToRecord = { navController.navigate(RecovoDestinations.RECORD) },
                onNavigateToLibrary = { navController.navigate(RecovoDestinations.LIBRARY) }
            )
        }
        composable(RecovoDestinations.RECORD) {
            RecordScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(RecovoDestinations.LIBRARY) {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToRecord = { navController.navigate(RecovoDestinations.RECORD) }
            )
        }
    }
}
