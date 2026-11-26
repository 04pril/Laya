package com.music.laya.data.sources

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The built-in Qobuz and Tidal placed among the addons, and the one-time move to that order. */
class SourceOrderTest {
    private val before = SourceRegistry.configs.value

    @AfterTest
    fun tearDown() {
        SourceRegistry.configs.value = before
    }

    private fun addon(name: String) = SourceConfig(kind = SourceKind.ADDON, label = name, baseUrl = "https://$name.example.com")

    private fun names() = SourceRegistry.configs.value.map { it.label.ifBlank { it.kind.name } }

    @Test
    fun `upgrading keeps the order sources were tried in`() {
        // As an older install stores it: built-ins seeded first, addons appended after.
        val stored = listOf(
            SourceConfig(kind = SourceKind.TIDAL), SourceConfig(kind = SourceKind.QOBUZ),
            SourceConfig(kind = SourceKind.JIOSAAVN), SourceConfig(kind = SourceKind.YOUTUBE),
            addon("a"), addon("b"),
        )
        assertEquals(
            listOf("a", "b", "TIDAL", "QOBUZ", "JIOSAAVN", "YOUTUBE"),
            SourceRegistry.unifiedOrder(stored).map { it.label.ifBlank { it.kind.name } },
        )
    }

    @Test
    fun `qobuz can be moved above an addon`() {
        val a = addon("a")
        val tidal = SourceConfig(kind = SourceKind.TIDAL)
        val qobuz = SourceConfig(kind = SourceKind.QOBUZ)
        SourceRegistry.configs.value = listOf(a, tidal, qobuz, SourceConfig(kind = SourceKind.JIOSAAVN), SourceConfig(kind = SourceKind.YOUTUBE))

        SourceRegistry.reorderAddons(listOf(qobuz.id, a.id, tidal.id))

        assertEquals(listOf("QOBUZ", "a", "TIDAL", "JIOSAAVN", "YOUTUBE"), names())
    }

    @Test
    fun `an addons-only drag leaves the built-ins where they are`() {
        val a = addon("a")
        val b = addon("b")
        val qobuz = SourceConfig(kind = SourceKind.QOBUZ)
        SourceRegistry.configs.value = listOf(a, qobuz, b, SourceConfig(kind = SourceKind.YOUTUBE))

        SourceRegistry.reorderAddons(listOf(b.id, a.id))

        assertEquals(listOf("b", "QOBUZ", "a", "YOUTUBE"), names())
    }

    @Test
    fun `jiosaavn and youtube cannot be dragged`() {
        val a = addon("a")
        val qobuz = SourceConfig(kind = SourceKind.QOBUZ)
        val youtube = SourceConfig(kind = SourceKind.YOUTUBE)
        SourceRegistry.configs.value = listOf(a, qobuz, youtube)

        SourceRegistry.reorderAddons(listOf(youtube.id, qobuz.id, a.id))

        assertEquals(listOf("QOBUZ", "a", "YOUTUBE"), names())
    }
}
