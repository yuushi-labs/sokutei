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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.data.HistoryViewModel
import io.github.yuushilabs.sokutei.data.TallyViewModel

/**
 * 手動カウンター。ボタンを押して数えるだけの画面。カメラは使わない。
 * 個数とラベルは [TallyViewModel]（Activity スコープ）で保持し、
 * 画面を戻っても消えない。
 */
@Composable
fun TallyScreen(history: HistoryViewModel, tally: TallyViewModel) {
    val count by tally.count.collectAsState()
    val label by tally.label.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    ConfirmDialog(
        show = confirmReset,
        title = "カウンターをリセット",
        message = "数えた個数を 0 に戻します。よろしいですか？",
        confirmLabel = "リセットする",
        onConfirm = {
            confirmReset = false
            tally.reset()
        },
        onDismiss = { confirmReset = false },
    )

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
            onClick = { tally.increment() },
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) {
            Text("＋1", style = MaterialTheme.typography.headlineMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { tally.decrement() },
                enabled = count > 0,
                modifier = Modifier.weight(1f),
            ) { Text("−1") }
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.weight(1f),
            ) { Text("リセット") }
        }
        OutlinedTextField(
            value = label,
            onValueChange = { tally.setLabel(it) },
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
