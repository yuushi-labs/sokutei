package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LineCrossingCounterTest {

    private fun counter() = LineCrossingCounter(lineY = 0.5)

    private fun feed(c: LineCrossingCounter, vararg frames: List<Pair<Double, Double>>) {
        for (f in frames) c.update(f)
    }

    @Test
    fun `上から下への横切りを 1 回数える`() {
        val c = counter()
        feed(
            c,
            listOf(0.5 to 0.30), listOf(0.5 to 0.38), listOf(0.5 to 0.46),
            listOf(0.5 to 0.54), listOf(0.5 to 0.62),
        )
        assertEquals(1, c.downCount)
        assertEquals(0, c.upCount)
    }

    @Test
    fun `下から上への横切りを 1 回数える`() {
        val c = counter()
        feed(
            c,
            listOf(0.5 to 0.70), listOf(0.5 to 0.62), listOf(0.5 to 0.54),
            listOf(0.5 to 0.46), listOf(0.5 to 0.38),
        )
        assertEquals(0, c.downCount)
        assertEquals(1, c.upCount)
    }

    @Test
    fun `線際のデッドバンド内の震えは数えない`() {
        val c = counter()
        feed(
            c,
            listOf(0.5 to 0.45),
            listOf(0.5 to 0.49), listOf(0.5 to 0.51),
            listOf(0.5 to 0.49), listOf(0.5 to 0.51),
        )
        assertEquals(0, c.totalCount)
    }

    @Test
    fun `2 個が同時に横切れば 2 回数える`() {
        val c = counter()
        feed(
            c,
            listOf(0.3 to 0.38, 0.7 to 0.38),
            listOf(0.3 to 0.46, 0.7 to 0.46),
            listOf(0.3 to 0.54, 0.7 to 0.54),
        )
        assertEquals(2, c.downCount)
    }

    @Test
    fun `線を越える前に消えた物は数えない`() {
        val c = counter()
        feed(c, listOf(0.5 to 0.38), listOf(0.5 to 0.44))
        repeat(6) { c.update(emptyList()) } // 許容を超えて見失う
        feed(c, listOf(0.5 to 0.60)) // 別物として現れる
        assertEquals(0, c.totalCount)
    }

    @Test
    fun `往復すれば下りと上りを 1 回ずつ数える`() {
        val c = counter()
        feed(
            c,
            listOf(0.5 to 0.38), listOf(0.5 to 0.46), listOf(0.5 to 0.54),
            listOf(0.5 to 0.46), listOf(0.5 to 0.38),
        )
        assertEquals(1, c.downCount)
        assertEquals(1, c.upCount)
    }

    @Test
    fun `追跡だけを破棄すると幻の横切りを数えず累積は残る`() {
        val c = counter()
        // まず1回横切らせて累積を作る
        feed(c, listOf(0.5 to 0.38), listOf(0.5 to 0.46), listOf(0.5 to 0.54))
        assertEquals(1, c.downCount)
        // 別の物が線の上に現れた状態で追跡を破棄する（設定変更を想定）。
        // 破棄後にその物が線の下へ移っても、横切りとしては数えない
        feed(c, listOf(0.5 to 0.54, 0.2 to 0.44))
        c.clearTracks()
        feed(c, listOf(0.2 to 0.54))
        assertEquals(1, c.downCount)
        assertEquals(0, c.upCount)
    }

    @Test
    fun `速い物が 1 フレームで線を越えても数える`() {
        // 1フレームで 0.16 移動（通常半径 0.10 を超える）。緩和マッチングで数える
        val c = counter()
        feed(c, listOf(0.5 to 0.40), listOf(0.5 to 0.56))
        assertEquals(1, c.downCount)
        assertEquals(0, c.upCount)
    }

    @Test
    fun `1 フレーム検出落ちした後に線の向こうで再出現しても数える`() {
        val c = counter()
        feed(c, listOf(0.5 to 0.44), emptyList(), listOf(0.5 to 0.56))
        assertEquals(1, c.downCount)
        assertEquals(0, c.upCount)
    }

    @Test
    fun `曖昧な 2 候補しかない場合は緩和マッチングしない`() {
        // 追跡1つに対して等距離（0.20）の候補が2つ → どちらとも対応付けない
        val c = counter()
        feed(c, listOf(0.5 to 0.40), listOf(0.38 to 0.56, 0.62 to 0.56))
        assertEquals(0, c.totalCount)
    }

    @Test
    fun `リセットで回数と追跡が消える`() {
        val c = counter()
        feed(c, listOf(0.5 to 0.38), listOf(0.5 to 0.46), listOf(0.5 to 0.54))
        assertEquals(1, c.totalCount)
        c.reset()
        assertEquals(0, c.totalCount)
        feed(c, listOf(0.5 to 0.60))
        assertEquals(0, c.totalCount)
    }
}
