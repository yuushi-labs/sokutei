package io.github.yuushilabs.sokutei.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.data.CountRecord
import io.github.yuushilabs.sokutei.data.HistoryViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    history: HistoryViewModel,
    onOpenLive: () -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenCrossing: () -> Unit,
    onOpenTally: () -> Unit,
) {
    val records by history.records.collectAsState()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CountRecord?>(null) }

    ConfirmDialog(
        show = confirmClear,
        title = "履歴をすべて消す",
        message = "保存した履歴 ${records.size} 件をすべて消します。元に戻せません。よろしいですか？",
        confirmLabel = "すべて消す",
        onConfirm = {
            confirmClear = false
            history.clear()
        },
        onDismiss = { confirmClear = false },
    )

    editing?.let { record ->
        var valueText by remember(record.id) { mutableStateOf(record.valueText) }
        var note by remember(record.id) { mutableStateOf(record.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("履歴を訂正") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text("結果") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("メモ") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    history.update(record.id, valueText.trim(), note.trim())
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("キャンセル") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("かぞえる", style = MaterialTheme.typography.headlineMedium)
            Text(
                "個数を数えるためのアプリです。自動で数える仕組みは背景とコントラストがはっきりした物が前提で、限界は各画面と README に正直に書いてあります。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        item {
            ModeCard(
                title = "🎥 ライブで数える",
                description = "カメラをかざすだけで、写っている物をその場で自動で数えます。撮らなくて大丈夫です。",
                onClick = onOpenLive,
            )
        }
        item {
            ModeCard(
                title = "🖼️ 写真で数える",
                description = "写真の中の物を自動で数えます（大津二値化＋連結成分）。タップで数えることもできます。",
                onClick = onOpenPhoto,
            )
        }
        item {
            ModeCard(
                title = "🚶 通過カウント",
                description = "画面中央の線を横切った物の数を、方向別に数えます。カメラを固定して使います。",
                onClick = onOpenCrossing,
            )
        }
        item {
            ModeCard(
                title = "🔢 手動カウンター",
                description = "ボタンを押して数えるだけのシンプルなカウンター。カメラは使いません。",
                onClick = onOpenTally,
            )
        }
        if (records.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "履歴",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { confirmClear = true }) { Text("すべて消す") }
                }
            }
            items(records, key = { it.id }) { record ->
                val dateText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(record.timeMillis))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${record.kind}: ${record.valueText}", style = MaterialTheme.typography.titleSmall)
                        if (record.note.isNotBlank()) {
                            Text(record.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(dateText, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                val text = buildString {
                                    append("${record.kind}: ${record.valueText}")
                                    if (record.note.isNotBlank()) append("（${record.note}）")
                                    append(" — $dateText")
                                }
                                val intent = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(intent, "履歴を共有"))
                            }) { Text("共有") }
                            TextButton(onClick = { editing = record }) { Text("訂正") }
                            TextButton(onClick = { history.remove(record.id) }) { Text("削除") }
                        }
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
