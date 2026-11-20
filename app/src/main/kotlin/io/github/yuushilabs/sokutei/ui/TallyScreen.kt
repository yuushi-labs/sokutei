package io.github.yuushilabs.sokutei.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.data.HistoryViewModel

/** 手動カウンター。ボタンを押して数えるだけの画面。カメラは使わない。 */
@Composable
fun TallyScreen(history: HistoryViewModel) {
    var count by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("手動カウンター", style = MaterialTheme.typography.headlineSmall)
        Text(
            "$count",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Button(
            onClick = { count++ },
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) {
            Text("＋1", style = MaterialTheme.typography.headlineMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (count > 0) count-- },
                enabled = count > 0,
                modifier = Modifier.weight(1f),
            ) { Text("−1") }
            OutlinedButton(
                onClick = { count = 0 },
                modifier = Modifier.weight(1f),
            ) { Text("リセット") }
        }
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("何を数えているか（任意）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                history.add("個数（手動カウンター）", "$count 個", label.trim())
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("履歴に保存") }
    }
}
