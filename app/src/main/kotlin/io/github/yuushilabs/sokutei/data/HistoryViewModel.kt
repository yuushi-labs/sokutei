package io.github.yuushilabs.sokutei.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** 数えた結果1件。アプリ内部ストレージに JSON で永続化する（SPEC.md の履歴の節）。 */
data class CountRecord(
    val id: String,
    val kind: String,
    val valueText: String,
    val note: String,
    val timeMillis: Long,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val file = File(application.filesDir, HISTORY_FILE_NAME)

    // 保存は1スレッドに直列化し、連続した変更でも書く順序が入れ替わらないようにする
    private val ioDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // 読み込みは生成時に同期で行う。小さいファイル（履歴数百件でも数十KB）であり、
    // 非同期にすると「読み込み完了前の追加」が保存で上書きされる競合が起きるため
    private val _records = MutableStateFlow<List<CountRecord>>(load())
    val records: StateFlow<List<CountRecord>> = _records

    fun add(kind: String, valueText: String, note: String) {
        val record = CountRecord(
            id = UUID.randomUUID().toString(),
            kind = kind,
            valueText = valueText,
            note = note,
            timeMillis = System.currentTimeMillis(),
        )
        _records.value = listOf(record) + _records.value
        persist()
    }

    fun remove(id: String) {
        _records.value = _records.value.filterNot { it.id == id }
        persist()
    }

    fun update(id: String, valueText: String, note: String) {
        _records.value = _records.value.map {
            if (it.id == id) it.copy(valueText = valueText, note = note) else it
        }
        persist()
    }

    fun clear() {
        _records.value = emptyList()
        persist()
    }

    override fun onCleared() {
        ioDispatcher.close()
    }

    private fun persist() {
        val snapshot = _records.value
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val array = JSONArray()
                for (record in snapshot) {
                    array.put(
                        JSONObject()
                            .put("id", record.id)
                            .put("kind", record.kind)
                            .put("valueText", record.valueText)
                            .put("note", record.note)
                            .put("timeMillis", record.timeMillis),
                    )
                }
                file.writeText(array.toString())
            }
        }
    }

    private fun load(): List<CountRecord> = runCatching {
        if (!file.isFile) return emptyList()
        val array = JSONArray(file.readText())
        val loaded = mutableListOf<CountRecord>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            loaded.add(
                CountRecord(
                    id = obj.getString("id"),
                    kind = obj.getString("kind"),
                    valueText = obj.getString("valueText"),
                    note = obj.optString("note"),
                    timeMillis = obj.getLong("timeMillis"),
                ),
            )
        }
        loaded
    }.getOrDefault(emptyList())

    private companion object {
        const val HISTORY_FILE_NAME = "history.json"
    }
}
