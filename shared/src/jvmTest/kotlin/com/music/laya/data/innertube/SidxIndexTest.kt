package com.music.laya.data.innertube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SidxIndexTest {
    @Test
    fun `a malformed extended box cannot request an unbounded read`() {
        val unboundedSidx = u32(1) + "sidx".encodeToByteArray() + u32(0) + u32(1L shl 30)
        assertIs<SidxIndex.Result.Unusable>(SidxIndex.parse(ftyp + moov + unboundedSidx))
    }

    private fun box(type: String, body: ByteArray): ByteArray = u32(8L + body.size) + type.encodeToByteArray() + body
    private fun u32(v: Long) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    /** sidx v0, timescale 44100, refs of the given sizes, each 10 s, first_offset [gap]. */
    private fun sidx(sizes: List<Long>, gap: Long = 0) = box(
        "sidx",
        u32(0) + u32(1) + u32(44_100) + u32(0) + u32(gap) + u16(0) + u16(sizes.size) +
            sizes.fold(ByteArray(0)) { acc, size -> acc + u32(size) + u32(441_000) + u32(0x90000000L) },
    )

    private val ftyp = box("ftyp", "dash".encodeToByteArray() + u32(0))
    private val moov = box("moov", ByteArray(40))

    @Test
    fun `reads the index YouTube puts after the init segment`() {
        val head = ftyp + moov + sidx(listOf(1000, 2000, 3000)) + ByteArray(64)
        val found = assertIs<SidxIndex.Result.Found>(SidxIndex.parse(head)).index

        val init = (ftyp.size + moov.size).toLong()
        assertEquals(init, found.initLength)
        val firstMedia = init + sidx(listOf(1000, 2000, 3000)).size
        assertEquals(listOf(firstMedia, firstMedia + 1000, firstMedia + 3000), found.segments.map { it.offset })
        assertEquals(listOf(1000L, 2000L, 3000L), found.segments.map { it.size })
        assertTrue(found.segments.all { it.seconds == 10.0 })
    }

    @Test
    fun `first_offset moves the media start`() {
        val index = sidx(listOf(500), gap = 100)
        val found = assertIs<SidxIndex.Result.Found>(SidxIndex.parse(ftyp + moov + index)).index
        assertEquals((ftyp.size + moov.size + index.size + 100).toLong(), found.segments.single().offset)
    }

    @Test
    fun `an index cut off by the read asks for exactly the rest`() {
        val index = sidx(List(50) { 1000L })
        val whole = ftyp + moov + index
        val more = assertIs<SidxIndex.Result.NeedMore>(SidxIndex.parse(whole.copyOf(whole.size - 10)))
        assertEquals(whole.size.toLong(), more.bytes)
    }

    @Test
    fun `media before any index is not this layout`() {
        assertIs<SidxIndex.Result.Unusable>(SidxIndex.parse(ftyp + moov + box("moof", ByteArray(8))))
    }

    @Test
    fun `the playlist maps the init segment and byte ranges of the one file`() {
        val found = assertIs<SidxIndex.Result.Found>(SidxIndex.parse(ftyp + moov + sidx(listOf(1000, 2000)))).index
        val text = SidxIndex.playlist(found) { "segment-$it.m4s" }
        assertTrue(text.contains("""#EXT-X-MAP:URI="segment-0.m4s",BYTERANGE="${found.initLength}@0""""), text)
        assertTrue(text.contains("#EXTINF:10.000,\n#EXT-X-BYTERANGE:1000@${found.segments[0].offset}\nsegment-1.m4s"), text)
        assertTrue(text.contains("#EXT-X-BYTERANGE:2000@${found.segments[1].offset}\nsegment-2.m4s"), text)
        assertTrue(text.trimEnd().endsWith("#EXT-X-ENDLIST"))
    }
}
