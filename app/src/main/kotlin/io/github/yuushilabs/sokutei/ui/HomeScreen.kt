package io.github.yuushilabs.sokutei.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.data.HistoryViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    history: HistoryViewModel,
    onOpenScan: () -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenCount: () -> Unit,
) {
    val records by history.records.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("なんでも測定", style = MaterialTheme.typography.headlineMedium)
            Text(
                "カメラとセンサで、長さ・高さ・個数を測ります。精度の限界は各画面と README に正直に書いてあります。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        item {
            ModeCard(
                title = "📏 上下スキャンで測る",
                description = "スマホを物の下端に近づけて、そのまま上端まで持ち上げると高さが出ます。傾けて測る三角測量モード付き。",
                onClick = onOpenScan,
            )
        }
        item {
            ModeCard(
                title = "🖼️ 静止画で測る",
                description = "写真の中で基準物（クレジットカード等）と測りたい物をタップすると、実寸に換算します。",
                onClick = onOpenPhoto,
            )
        }
        item {
            ModeCard(
                title = "🔢 個数を数える",
                description = "写真の中の物を自動で数えます（大津二値化＋連結成分）。手動タップ数えもできます。",
                onClick = onOpenCount,
            )
        }
        if (records.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "測定履歴",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { history.clear() }) { Text("履歴を消す") }
                }
            }
            items(records) { record ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${record.kind}: ${record.valueText}", style = MaterialTheme.typography.titleSmall)
                        if (record.note.isNotBlank()) {
                            Text(record.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(record.timeMillis)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
