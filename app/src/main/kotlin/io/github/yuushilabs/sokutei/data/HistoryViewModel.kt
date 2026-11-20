package io.github.yuushilabs.sokutei.data

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 測定結果1件。永続化はせず、アプリ起動中だけ保持する（SPEC.md の制約参照）。 */
data class MeasurementRecord(
    val kind: String,
    val valueText: String,
    val note: String,
    val timeMillis: Long,
)

class HistoryViewModel : ViewModel() {
    private val _records = MutableStateFlow<List<MeasurementRecord>>(emptyList())
    val records: StateFlow<List<MeasurementRecord>> = _records

    fun add(kind: String, valueText: String, note: String) {
        val record = MeasurementRecord(kind, valueText, note, System.currentTimeMillis())
        _records.value = listOf(record) + _records.value
    }

    fun clear() {
        _records.value = emptyList()
    }
}
