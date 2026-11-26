package com.music.laya.data.settings

import com.music.laya.data.model.HomeShelf
import com.music.laya.data.model.ShelfItem
import com.music.laya.data.YtMusicRepository
import com.music.laya.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.laya.data.spotify.SpotifyImporter
import com.music.laya.data.spotify.SpotifyLibrary
import com.music.laya.data.spotify.SpotifyPlaylist
import com.russhwolf.settings.Settings
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class HomeSection(val english: String, val korean: String, vararg aliases: String) {
    RECENTS("Recents", "최근 재생", "Recently played", "최근", "최근에 재생한 음악", "Gần đây", "最近"),
    FOR_YOU("For you", "나를 위한 추천", "Recommended for you"),
    ALBUMS("Albums & singles", "앨범 및 싱글", "Albums and singles", "New albums & singles", "New albums and singles", "최신 앨범 및 싱글"),
    POPULAR("Trending", "인기", "인기 급상승", "Popular"),
    LISTEN_AGAIN("Listen again", "다시 듣기"),
    LIBRARY("From your library", "내 보관함에 있는 음악", "내 보관함에서", "Music from your library"),
    THROWBACK("Throwback jams", "Throwback jams"),
    FORGOTTEN("Forgotten favorites", "잊고 있던 좋은 음악", "잊고 있던 최애곡"),
    TRENDING_FOR_YOU("Trending songs for you", "나만을 위한 인기 급상승 곡", "Trending for you", "Trending tracks for you"),
    QUICK_PICKS("Quick picks", "빠른 선곡"),
    SHORTS("Shorts", "쇼츠", "Featured Shorts", "Shorts highlights"),
    COVERS("Covers & remixes", "커버 및 리믹스", "Covers and remixes"),
    COMMUNITY("From the community", "커뮤니티 제공", "Community picks", "커뮤니티에서",
        "Trending community playlists", "인기 커뮤니티 재생목록"),
    KPOP("K-Pop", "K-Pop", "K‑Pop", "K-pop 음악"),
    FEATURED_PLAYLISTS("Featured playlists for you", "나를 위한 추천 재생목록", "Recommended playlists for you"),
    MIXES("Mixed for you", "맞춤 믹스", "나만의 믹스", "Mixes for you"),
    DISCOVERY("Today's discoveries", "오늘의 발견", "Today's discovery", "Discoveries for today"),
    MY_PLAYLIST("My Playlist", "My Playlist");

    val id get() = name
    private val titles = (listOf(english, korean) + aliases).map(::normalizedTitle).toSet()

    companion object {
        private val byTitle = entries.filterNot { it == MY_PLAYLIST }.flatMap { section ->
            section.titles.map { it to section }
        }.toMap()

        fun idFor(shelf: HomeShelf): String {
            val title = normalizedTitle(shelf.title)
            return (byTitle[title] ?: SHORTS.takeIf { title.startsWith("shorts ") })?.id ?: "feed:$title"
        }
    }
}

private fun normalizedTitle(title: String) = title.trim().lowercase().replace('’', '\'')
    .replace(Regex("\\s+"), " ")

@Serializable
data class HomePlaylist(val id: String, val title: String, val image: String?, val subtitle: String) {
    fun asCard() = ShelfItem(title, subtitle, image, videoId = null, browseId = id)

    companion object {
        fun fromSpotify(playlist: SpotifyPlaylist) =
            HomePlaylist(SPOTIFY_PAGE_PREFIX + playlist.id, playlist.name, playlist.imageUrl, "Spotify")

        fun fromCard(card: ShelfItem): HomePlaylist? = card.browseId?.takeIf {
            it.startsWith("VL") && it.length > 2 && !it.startsWith("VLOLAK")
        }?.let { HomePlaylist(it, card.title, card.thumbnailUrl, card.subtitle) }

        fun youtubeBrowseId(link: String): String? {
            val input = link.trim()
            if (!input.startsWith("https://", true) && !input.startsWith("http://", true)) return null
            val url = runCatching { Url(input) }.getOrNull() ?: return null
            if (url.host.lowercase() !in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")) return null
            val playlist = url.parameters["list"]?.takeIf {
                it.matches(Regex("[A-Za-z0-9_-]{2,120}")) && !it.startsWith("RD") && !it.startsWith("OLAK")
            } ?: return null
            return "VL$playlist"
        }

        suspend fun fromLink(link: String): HomePlaylist? {
            SpotifyImporter.extractPlaylistId(link)?.let { return fromSpotify(SpotifyLibrary.playlistForLink(it)) }
            val browseId = youtubeBrowseId(link) ?: return null
            val header = YtMusicRepository.browseSongs(browseId).getOrThrow().header ?: error("Playlist unavailable")
            return HomePlaylist(browseId, header.title, header.thumbnailUrl, header.subtitle)
        }
    }
}

@Serializable
data class HomeLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val playlistsByProfile: Map<String, List<HomePlaylist>> = emptyMap(),
) {
    fun playlists(profile: String?): List<HomePlaylist> = playlistsByProfile[profile ?: "guest"].orEmpty()

    fun applyTo(feed: List<HomeShelf>, profile: String?): List<HomeShelf> {
        val regular = feed.map { HomeSection.idFor(it) to it }
        val recents = regular.filter { it.first == HomeSection.RECENTS.id }.map { it.second }
        val custom = playlists(profile).takeIf { it.isNotEmpty() }?.let {
            HomeSection.MY_PLAYLIST.id to HomeShelf(HomeSection.MY_PLAYLIST.english, it.map(HomePlaylist::asCard))
        }
        val positions = order.withIndex().associate { it.value to it.index }
        return recents + (regular + listOfNotNull(custom))
            .filterNot { it.first == HomeSection.RECENTS.id || it.first in hidden }
            .sortedBy { positions[it.first] ?: Int.MAX_VALUE }
            .map { it.second }
    }

    /** Newly seen recommendations follow the user's order and remain editable. */
    fun editorOrder(feed: List<HomeShelf>): List<String> =
        (order + feed.map(HomeSection::idFor) + HomeSection.entries.map { it.id } + hidden)
            .distinct().filterNot { it == HomeSection.RECENTS.id }
}

object HomeLayoutPreferences {
    private const val KEY = "home_layout_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private var store: Settings? = null
    private val value = MutableStateFlow(HomeLayout())
    val layout = value.asStateFlow()

    internal fun install(settings: Settings) {
        store = settings
    }

    internal fun reload() {
        value.value = store?.getStringOrNull(KEY)?.let {
            runCatching { json.decodeFromString<HomeLayout>(it) }.getOrNull()
        } ?: HomeLayout()
    }

    fun setOrder(order: List<String>) = update(value.value.copy(order = order))
    fun setVisible(id: String, visible: Boolean) =
        update(value.value.copy(hidden = if (visible) value.value.hidden - id else value.value.hidden + id))

    fun setPlaylists(profile: String?, playlists: List<HomePlaylist>) {
        val current = value.value
        val key = profile ?: "guest"
        update(current.copy(playlistsByProfile = if (playlists.isEmpty()) current.playlistsByProfile - key
            else current.playlistsByProfile + (key to playlists.distinctBy { it.id })))
    }

    fun resetSections() = update(value.value.copy(order = emptyList(), hidden = emptySet()))

    private fun update(next: HomeLayout) {
        store?.putString(KEY, json.encodeToString(next))
        value.value = next
    }
}
