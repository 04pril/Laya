package com.music.laya.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.laya.data.YtMusicRepository
import com.music.laya.data.model.HomeShelf
import com.music.laya.data.model.UiState
import com.music.laya.data.settings.AppSettings
import com.music.laya.data.settings.HomeLayoutPreferences
import com.music.laya.data.settings.HomePlaylist
import com.music.laya.data.settings.HomeSection
import com.music.laya.sharedui.resources.*
import com.music.laya.ui.components.AutoScrollWhileDragging
import com.music.laya.ui.components.DragHandle
import com.music.laya.ui.components.SearchField
import com.music.laya.ui.components.reorderableRow
import com.music.laya.ui.components.rememberReorderState
import com.music.laya.ui.player.appLanguageTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.music.laya.data.spotify.SpotifyLibrary
import com.music.laya.data.spotify.SPOTIFY_PAGE_PREFIX
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeLayoutScreen(
    feed: List<HomeShelf>,
    profile: String?,
    contentPadding: PaddingValues,
) {
    val layout by HomeLayoutPreferences.layout.collectAsStateWithLifecycle()
    val order = remember(layout.order, layout.hidden, feed) { layout.editorOrder(feed) }
    val observed = remember(feed) { feed.associateBy(HomeSection::idFor) }
    val known = remember { HomeSection.entries.associateBy { it.id } }
    val korean = appLanguageTag().startsWith("ko")
    val selected = layout.playlists(profile)
    var choosing by remember(profile) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(order) { HomeLayoutPreferences.setOrder(it) }
    AutoScrollWhileDragging(reorder, listState)
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item {
            Text(
                stringResource(Res.string.home_sections),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
            Text(
                stringResource(Res.string.home_sections_recents_pinned),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            SettingsGroup(header = HomeSection.MY_PLAYLIST.english) {
                SettingsRow(
                    title = stringResource(Res.string.home_select_playlists),
                    subtitle = stringResource(Res.string.home_selected_playlists, selected.size),
                    onClick = { choosing = true },
                )
            }
        }
        items(reorder.order, key = { it }) { id ->
            val section = known[id]
            val title = if (section != null) {
                if (korean) section.korean else section.english
            } else observed[id]?.title?.let { localizeShelfTitle(it) } ?: id.removePrefix("feed:")
            ListItem(
                modifier = Modifier.reorderableRow(reorder, id),
                headlineContent = { Text(title) },
                // The grip leads, the switch trails: dragging is done at the left edge, away from the toggle.
                leadingContent = {
                    DragHandle(
                        reorder, id,
                        contentDescription = stringResource(Res.string.drag_to_reorder),
                        modifier = Modifier.size(40.dp).padding(8.dp),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = layout.isVisible(id, profile),
                        onCheckedChange = { HomeLayoutPreferences.setVisible(id, it) },
                        enabled = section != HomeSection.MY_PLAYLIST || selected.isNotEmpty(),
                        modifier = Modifier.semantics { contentDescription = title },
                    )
                },
            )
        }
        if (layout.order.isNotEmpty() || layout.hidden.isNotEmpty()) item {
            TextButton(onClick = HomeLayoutPreferences::resetSections, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(Res.string.home_reset_sections))
            }
        }
    }
    if (choosing) {
        HomePlaylistPicker(
            signedIn = profile != null,
            selected = selected,
            onAdd = { item ->
                val current = HomeLayoutPreferences.layout.value.playlists(profile)
                HomeLayoutPreferences.setPlaylists(profile, current + item)
            },
            onToggle = { item ->
                val current = HomeLayoutPreferences.layout.value.playlists(profile)
                HomeLayoutPreferences.setPlaylists(profile,
                    if (current.any { it.id == item.id }) current.filterNot { it.id == item.id } else current + item)
            },
            onDismiss = { choosing = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePlaylistPicker(
    signedIn: Boolean,
    selected: List<HomePlaylist>,
    onToggle: (HomePlaylist) -> Unit,
    onAdd: (HomePlaylist) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var urlMessage by remember { mutableStateOf<StringResource?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var retryYouTube by remember { mutableIntStateOf(0) }
    var retrySpotify by remember { mutableIntStateOf(0) }
    var youtube by remember { mutableStateOf<UiState<List<HomePlaylist>>>(UiState.Loading) }
    var spotify by remember { mutableStateOf<UiState<List<HomePlaylist>>>(UiState.Loading) }
    val spotifyConnected = AppSettings.spotifySpdcToken.collectAsStateWithLifecycle().value.isNotBlank()
    LaunchedEffect(signedIn, retryYouTube) {
        if (!signedIn) return@LaunchedEffect
        youtube = UiState.Loading
        youtube = YtMusicRepository.libraryPlaylists(allPages = true).fold(
            onSuccess = { UiState.Success(it.mapNotNull(HomePlaylist::fromCard).distinctBy(HomePlaylist::id)) },
            onFailure = { if (it is CancellationException) throw it; UiState.Error("") },
        )
    }
    LaunchedEffect(spotifyConnected, retrySpotify) {
        if (!spotifyConnected) return@LaunchedEffect
        spotify = UiState.Loading
        spotify = try {
            UiState.Success(SpotifyLibrary.playlists().map(HomePlaylist::fromSpotify).distinctBy(HomePlaylist::id))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            UiState.Error("")
        }
    }

    val checked = selected.mapTo(mutableSetOf()) { it.id }
    fun addUrl() {
        if (adding || url.isBlank()) return
        val input = url
        adding = true
        urlMessage = null
        scope.launch {
            try {
                val item = HomePlaylist.fromLink(input)
                if (item == null) {
                    urlMessage = Res.string.home_playlist_link_invalid
                } else {
                    onAdd(item)
                    url = ""
                    urlMessage = Res.string.home_playlist_added
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                urlMessage = Res.string.home_playlist_link_failed
            } finally {
                adding = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.home_select_playlists), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
            }
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onSubmit = {},
                placeholder = stringResource(Res.string.home_playlist_search),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(Res.string.home_playlist_link), style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it; urlMessage = null },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { addUrl() }),
                                placeholder = { Text(stringResource(Res.string.home_playlist_link_hint)) },
                                singleLine = true,
                                enabled = !adding,
                            )
                            TextButton(onClick = ::addUrl, enabled = !adding && url.isNotBlank()) {
                                if (adding) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(stringResource(Res.string.home_playlist_add))
                            }
                        }
                        urlMessage?.let {
                            Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                                color = if (it == Res.string.home_playlist_added) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error)
                        }
                    }
                }
                playlistGroup(
                    title = Res.string.home_playlist_section_youtube,
                    result = youtube,
                    connected = signedIn,
                    disconnected = Res.string.home_playlists_sign_in,
                    query = query,
                    selected = selected.filterNot { it.id.startsWith(SPOTIFY_PAGE_PREFIX) },
                    checked = checked,
                    onRetry = { retryYouTube++ },
                    onToggle = onToggle,
                )
                playlistGroup(
                    title = Res.string.home_playlist_section_spotify,
                    result = spotify,
                    connected = spotifyConnected,
                    disconnected = Res.string.home_playlist_spotify_connect,
                    query = query,
                    selected = selected.filter { it.id.startsWith(SPOTIFY_PAGE_PREFIX) },
                    checked = checked,
                    onRetry = { retrySpotify++ },
                    onToggle = onToggle,
                )
            }
        }
    }
}

private fun LazyListScope.playlistGroup(
    title: StringResource,
    result: UiState<List<HomePlaylist>>,
    connected: Boolean,
    disconnected: StringResource,
    query: String,
    selected: List<HomePlaylist>,
    checked: Set<String>,
    onRetry: () -> Unit,
    onToggle: (HomePlaylist) -> Unit,
) {
    val available = if (connected) (result as? UiState.Success)?.data.orEmpty() else emptyList()
    val matching = (selected + available).distinctBy(HomePlaylist::id).filter {
        it.title.contains(query.trim(), ignoreCase = true)
    }
    item {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
    }
    when {
        !connected -> item {
            Text(stringResource(disconnected), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        result == UiState.Loading -> item { CircularProgressIndicator(Modifier.padding(20.dp)) }
        result is UiState.Error -> item {
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(Res.string.retry))
            }
        }
        matching.isEmpty() -> item {
            Text(stringResource(Res.string.home_no_playlists), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }
    items(matching, key = { it.id }) { playlist ->
        ListItem(
            headlineContent = { Text(playlist.title) },
            trailingContent = { Checkbox(checked = playlist.id in checked, onCheckedChange = null) },
            modifier = Modifier.clickable { onToggle(playlist) },
        )
    }
}
