package io.github.yuushilabs.sokutei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.yuushilabs.sokutei.data.HistoryViewModel
import io.github.yuushilabs.sokutei.ui.CountScreen
import io.github.yuushilabs.sokutei.ui.HomeScreen
import io.github.yuushilabs.sokutei.ui.PhotoMeasureScreen
import io.github.yuushilabs.sokutei.ui.ScanScreen
import io.github.yuushilabs.sokutei.ui.SokuteiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SokuteiTheme {
                SokuteiApp()
            }
        }
    }
}

@Composable
fun SokuteiApp(history: HistoryViewModel = viewModel()) {
    val navController = rememberNavController()
    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    history = history,
                    onOpenScan = { navController.navigate("scan") },
                    onOpenPhoto = { navController.navigate("photo") },
                    onOpenCount = { navController.navigate("count") },
                )
            }
            composable("scan") { ScanScreen(history = history) }
            composable("photo") { PhotoMeasureScreen(history = history) }
            composable("count") { CountScreen(history = history) }
        }
    }
}
