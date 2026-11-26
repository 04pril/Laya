package com.music.laya.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Drag-to-reorder for a list of rows of about one height, driven from a handle on each row (so a drag anywhere else
 * still scrolls the list). The order changes live under the finger and is handed to `onCommit` once, when the finger
 * lifts: committing persists and rebuilds things that should not be rebuilt on every frame of a drag.
 *
 * The target slot is worked out from how far the finger has travelled since the drag began, in rows of the dragged
 * row's own height, rather than from the rows it passes: the boundaries are then fixed for the whole gesture and do
 * not move underneath it as rows trade places.
 */
@Stable
class ReorderState<K> internal constructor(initial: List<K>) {
    /** The order to draw: the caller's, or the one under the finger while a drag is on. */
    var order by mutableStateOf(initial)
        private set

    /** The row being dragged, if any. */
    var dragging by mutableStateOf<K?>(null)
        private set

    /** How far the dragged row is drawn from the place its slot gives it. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var base: List<K> = initial
    private var startIndex = 0
    private var travelled = 0f
    private var pitch = 1f
    private val heights = HashMap<K, Int>()

    internal var onCommit: (List<K>) -> Unit = {}

    /** The caller's order, taken unless a drag is on (then it lands with the commit). */
    internal fun sync(external: List<K>) {
        if (dragging == null) { order = external; base = external }
    }

    internal fun measured(key: K, height: Int) { heights[key] = height }

    internal fun start(key: K) {
        base = order
        startIndex = base.indexOf(key).takeIf { it >= 0 } ?: return
        travelled = 0f
        offset = 0f
        pitch = (heights[key] ?: 0).toFloat().takeIf { it > 0f } ?: 1f
        dragging = key
    }

    internal fun drag(dy: Float) {
        if (dragging == null) return
        travelled += dy
        val target = (startIndex + (travelled / pitch).roundToInt()).coerceIn(0, base.lastIndex)
        order = base.toMutableList().apply { add(target, removeAt(startIndex)) }
        offset = travelled - (target - startIndex) * pitch
    }

    internal fun end() {
        if (dragging == null) return
        dragging = null
        offset = 0f
        val result = order
        if (result != base) {
            base = result
            onCommit(result)
        }
    }
}

/** A [ReorderState] that follows [items] between drags and hands a finished drag's order to [onCommit]. */
@Composable
fun <K> rememberReorderState(items: List<K>, onCommit: (List<K>) -> Unit): ReorderState<K> {
    val state = remember { ReorderState(items) }
    val commit by rememberUpdatedState(onCommit)
    state.onCommit = { commit(it) }
    // After composition, not during it: the caller's list changing (a commit landing, a section appearing) is taken
    // on the next frame rather than written back into the composition that read it.
    LaunchedEffect(items) { state.sync(items) }
    return state
}

/** On each row: lifts the dragged one above the rest and draws it where the finger is. */
fun <K> Modifier.reorderableRow(state: ReorderState<K>, key: K): Modifier = this
    .onSizeChanged { state.measured(key, it.height) }
    .zIndex(if (state.dragging == key) 1f else 0f)
    .graphicsLayer {
        val lifted = state.dragging == key
        translationY = if (lifted) state.offset else 0f
        shadowElevation = if (lifted) 8.dp.toPx() else 0f
    }

/** The grip a row is dragged by. */
@Composable
fun <K> DragHandle(state: ReorderState<K>, key: K, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.DragHandle,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.pointerInput(key) {
            detectDragGestures(
                onDragStart = { state.start(key) },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                },
            )
        },
    )
}

/**
 * Scrolls [list] while the dragged row is held near its top or bottom edge, so a row can be carried past what is on
 * screen. What the list scrolls is counted as travel, which keeps the row under the finger.
 */
@Composable
fun <K> AutoScrollWhileDragging(state: ReorderState<K>, list: LazyListState) {
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val maxStep = with(LocalDensity.current) { 14.dp.toPx() }
    val dragging = state.dragging
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val key = state.dragging ?: break
            val info = list.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
            val top = row.offset + state.offset
            val bottom = top + row.size
            val step = when {
                top < info.viewportStartOffset + edge -> -maxStep * ((info.viewportStartOffset + edge - top) / edge).coerceIn(0f, 1f)
                bottom > info.viewportEndOffset - edge -> maxStep * ((bottom - (info.viewportEndOffset - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step == 0f) continue
            val scrolled = list.scrollBy(step)
            if (scrolled != 0f) state.drag(scrolled)
        }
    }
}
