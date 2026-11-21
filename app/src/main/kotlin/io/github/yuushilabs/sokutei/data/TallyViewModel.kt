package io.github.yuushilabs.sokutei.data

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 手動カウンターの状態。Activity スコープで保持するため、画面を戻って
 * バックスタックから破棄されても個数とラベルは消えない。
 */
class TallyViewModel : ViewModel() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    private val _label = MutableStateFlow("")
    val label: StateFlow<String> = _label

    fun increment() {
        _count.value += 1
    }

    fun decrement() {
        if (_count.value > 0) _count.value -= 1
    }

    fun reset() {
        _count.value = 0
    }

    fun setLabel(value: String) {
        _label.value = value
    }
}
