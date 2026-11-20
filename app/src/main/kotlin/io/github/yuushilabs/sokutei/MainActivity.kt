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
import io.github.yuushilabs.sokutei.ui.CrossingCountScreen
import io.github.yuushilabs.sokutei.ui.HomeScreen
import io.github.yuushilabs.sokutei.ui.LiveCountScreen
import io.github.yuushilabs.sokutei.ui.SokuteiTheme
import io.github.yuushilabs.sokutei.ui.TallyScreen

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
                    onOpenLive = { navController.navigate("live") },
                    onOpenPhoto = { navController.navigate("photo") },
                    onOpenCrossing = { navController.navigate("crossing") },
                    onOpenTally = { navController.navigate("tally") },
                )
            }
            composable("live") { LiveCountScreen(history = history) }
            composable("photo") { CountScreen(history = history) }
            composable("crossing") { CrossingCountScreen(history = history) }
            composable("tally") { TallyScreen(history = history) }
        }
    }
}
