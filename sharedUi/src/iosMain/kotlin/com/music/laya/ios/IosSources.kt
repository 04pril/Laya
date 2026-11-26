package com.music.laya.ios

import com.music.laya.data.sources.JioSaavnSource
import com.music.laya.data.sources.MusicSource
import com.music.laya.data.sources.SourceConfig
import com.music.laya.data.sources.SourceKind
import com.music.laya.data.sources.SourcesHost
import platform.Foundation.NSBundle

/**
 * iOS's answers for the shared source machinery. The source list lives in the Keychain (an addon's
 * address can carry its owner's token in the path, which is why Android keeps it encrypted too).
 * Addons, JioSaavn and YouTube exist here; modules need a JS sandbox this build doesn't carry.
 */
internal object IosSourcesHost : SourcesHost {
    override val appVersion: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0"

    override fun supports(kind: SourceKind) = super.supports(kind) || kind == SourceKind.JIOSAAVN

    override fun build(config: SourceConfig): MusicSource? = if (config.kind == SourceKind.JIOSAAVN) JioSaavnSource(config) else null

    // Built-in Tidal bridges its own FLAC manifests before this gate. Generic addon manifests
    // are still unsupported by the byte-range loader, including those carrying Atmos.
    override fun acceptTransport(url: String, transport: String) = false

    // What AVPlayer plays out of a plain file: FLAC and ALAC natively, plus the usual lossy ones.
    // Ogg/Opus/Vorbis/WebM are not among them. Unknown (null) is tried rather than refused.
    override fun canDecode(codec: String?) = codec == null || codec in PLAYABLE

    override var storedSources: String?
        get() = Keychain.get(KEY)
        set(value) = Keychain.set(KEY, value)

    override var jioSaavnOptInDone = true

    // Beside the list itself in the Keychain, so a reinstall that keeps one keeps the other.
    override var sourceOrderUnified: Boolean
        get() = Keychain.get(ORDER_KEY) == "1"
        set(value) = Keychain.set(ORDER_KEY, if (value) "1" else null)

    private const val KEY = "sources"
    private const val ORDER_KEY = "sources_order_unified_v1"
    private val PLAYABLE = setOf("flac", "alac", "wav", "aiff", "mp3", "aac", "he-aac", "m4a", "mp4")
}
