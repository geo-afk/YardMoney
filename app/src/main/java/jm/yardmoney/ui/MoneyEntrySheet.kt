package jm.yardmoney.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val LocalEditDismiss = staticCompositionLocalOf<() -> Unit> { {} }

// Material3 1.4.0 (2025-09-24) is the app's locked, maintained Material version.
// Its SheetValue offers Expanded/PartiallyExpanded/Hidden, not three visible anchors.
// Foundation 1.9.4 (2025-10-22) is already installed with Kotlin 2.3.20 and minSdk 26;
// its AnchoredDraggableState and nested scrolling provide the extra stage without a library.
// Checked 2026-10-04: https://developer.android.com/jetpack/androidx/releases/compose-material3
// https://developer.android.com/jetpack/androidx/releases/compose-foundation
internal fun settledEditStage(
    start: EditStage,
    offset: Float,
    height: Float,
    peek: Float,
    velocity: Float,
): EditStage? {
    val anchors = listOf(0f, height / 2, height - peek)
    // Hidden is reachable only from peek, requiring a separate deliberate pull.
    if (start == EditStage.Peek && offset > height - peek / 2) return null
    val nearest = anchors.indices.minBy { kotlin.math.abs(anchors[it] - offset) }
    val target =
        if (kotlin.math.abs(velocity) > 800f) start.ordinal + if (velocity > 0) 1 else -1
        else nearest
    return EditStage.entries[
            target.coerceIn(
                (start.ordinal - 1).coerceAtLeast(0),
                (start.ordinal + 1).coerceAtMost(2),
            )]
}

@Composable
internal fun StagedEditSheet(
    title: String,
    keyValue: String,
    busy: Boolean,
    dirty: Boolean,
    close: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var stage by rememberSaveable { mutableStateOf(EditStage.Full) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val scroll = rememberScrollState()
    val latestBusy by rememberUpdatedState(busy)
    val latestDirty by rememberUpdatedState(dirty)
    val latestClose by rememberUpdatedState(close)
    fun dismiss() {
        if (!latestBusy) {
            if (latestDirty) discard = true else latestClose()
        }
    }
    Dialog(
        onDismissRequest = ::dismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CompositionLocalProvider(LocalEditDismiss provides ::dismiss) {
            BoxWithConstraints(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            ) {
                val sheetHeight = maxHeight
                val density = LocalDensity.current
                val height = with(density) { sheetHeight.toPx() }
                val headerHeight =
                    minOf(sheetHeight * .4f, 112.dp * density.fontScale.coerceAtLeast(1f))
                val peek = with(density) { headerHeight.toPx() }
                val anchors =
                    remember(height, peek) {
                        DraggableAnchors {
                            0 at 0f
                            1 at height / 2
                            2 at height - peek
                            3 at height
                        }
                    }
                val drag = remember { AnchoredDraggableState(stage.ordinal, anchors) }
                SideEffect { drag.updateAnchors(anchors, stage.ordinal) }
                fun move(delta: Float): Float {
                    // Each gesture reaches at most its neighboring anchor. Only peek can reach
                    // hidden.
                    val minimum = if (stage == EditStage.Peek) height / 2 else 0f
                    val maximum =
                        when (stage) {
                            EditStage.Full -> height / 2
                            EditStage.Half -> height - peek
                            EditStage.Peek -> height
                        }
                    val allowed =
                        (drag.requireOffset() + delta).coerceIn(minimum, maximum) -
                            drag.requireOffset()
                    return drag.dispatchRawDelta(allowed)
                }
                suspend fun settle(velocity: Float) {
                    val target =
                        settledEditStage(
                            stage,
                            drag.requireOffset(),
                            height,
                            peek,
                            velocity / density.density,
                        )
                    if (target == null) dismiss() else stage = target
                    drag.animateTo(stage.ordinal, motion.floatSpec())
                }
                val ime = WindowInsets.ime.getBottom(density)
                LaunchedEffect(ime) { if (ime > 0) stage = EditStage.Full }
                LaunchedEffect(stage, height) { drag.animateTo(stage.ordinal, motion.floatSpec()) }
                val connection =
                    remember(height, peek, stage, busy, scrollContent) {
                        object : NestedScrollConnection {
                            private var dragging = false
                            private var contentGesture: Boolean? = null

                            private fun beginDrag() {
                                if (!dragging) {
                                    dragging = true
                                    scope.launch {
                                        drag.anchoredDrag(
                                            androidx.compose.foundation.MutatePriority.UserInput
                                        ) {}
                                    }
                                }
                            }

                            override fun onPreScroll(
                                available: Offset,
                                source: NestedScrollSource,
                            ): Offset {
                                if (latestBusy || source != NestedScrollSource.UserInput)
                                    return Offset.Zero
                                // A gesture that starts within scrolled content returns it to the
                                // top; only a new pull from the top may move the sheet.
                                if (
                                    contentGesture == null &&
                                        (available.y > 0 || drag.requireOffset() > 0)
                                ) {
                                    contentGesture = scrollContent && scroll.value > 0
                                }
                                if (contentGesture == true) return Offset.Zero
                                if (
                                    available.y < 0 && drag.requireOffset() > 0 ||
                                        available.y > 0 && scrollContent && scroll.value == 0
                                ) {
                                    beginDrag()
                                    return Offset(0f, move(available.y))
                                }
                                return Offset.Zero
                            }

                            override fun onPostScroll(
                                consumed: Offset,
                                available: Offset,
                                source: NestedScrollSource,
                            ): Offset {
                                if (latestBusy || source != NestedScrollSource.UserInput)
                                    return Offset.Zero
                                if (contentGesture == true || available.y == 0f) return Offset.Zero
                                beginDrag()
                                return Offset(0f, move(available.y))
                            }

                            override suspend fun onPreFling(available: Velocity): Velocity {
                                if (dragging && drag.requireOffset() > 0f) {
                                    settle(available.y)
                                    return available
                                }
                                return Velocity.Zero
                            }

                            override suspend fun onPostFling(
                                consumed: Velocity,
                                available: Velocity,
                            ): Velocity {
                                // A fling wholly consumed by the form must not collapse the sheet.
                                contentGesture = null
                                if (!dragging) return Velocity.Zero
                                dragging = false
                                settle(available.y)
                                return available
                            }
                        }
                    }
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = .25f))
                        .clickable(onClick = ::dismiss)
                )
                Surface(
                    modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .offset { IntOffset(0, drag.requireOffset().roundToInt()) }
                        .height(sheetHeight)
                        .nestedScroll(connection),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column {
                        Column(
                            Modifier.fillMaxWidth()
                                .height(headerHeight)
                                .testTag("edit-sheet-handle")
                                .draggable(
                                    rememberDraggableState { delta ->
                                        move(delta)
                                    },
                                    Orientation.Vertical,
                                    enabled = !busy,
                                    onDragStarted = {
                                        drag.anchoredDrag(
                                            androidx.compose.foundation.MutatePriority.UserInput
                                        ) {}
                                    },
                                    onDragStopped = { settle(it) },
                                )
                                .semantics(mergeDescendants = true) {
                                    stateDescription = stage.name
                                    expand {
                                        stage =
                                            EditStage.entries[(stage.ordinal - 1).coerceAtLeast(0)]
                                        true
                                    }
                                    collapse {
                                        stage =
                                            EditStage.entries[(stage.ordinal + 1).coerceAtMost(2)]
                                        true
                                    }
                                    dismiss {
                                        dismiss()
                                        true
                                    }
                                    customActions =
                                        EditStage.entries.map { target ->
                                            CustomAccessibilityAction("Move to ${target.name}") {
                                                stage = target
                                                true
                                            }
                                        }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.padding(top = 8.dp)
                                    .size(40.dp, 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        MaterialTheme.shapes.small,
                                    )
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow =
                                            androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        keyValue,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow =
                                            androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = ::dismiss,
                                    enabled = !busy,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Default.Close, "Close editor")
                                }
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth()
                                .height(
                                    (sheetHeight -
                                            with(density) { drag.requireOffset().toDp() } -
                                            headerHeight)
                                        .coerceAtLeast(0.dp)
                                )
                        ) {
                            Column(
                                Modifier.weight(1f)
                                    .fillMaxWidth()
                                    .testTag("money-entry-scroll")
                                    .then(
                                        if (scrollContent) Modifier.verticalScroll(scroll)
                                        else Modifier
                                    )
                                    .padding(24.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(LocalLayoutSpacing.current),
                                content = content,
                            )
                            actions()
                        }
                    }
                }
            }
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard unsaved edits?") },
            text = { Text("Your changes have not been saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        close()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        )
}

@Composable
internal fun MoneyEntrySheet(
    title: String,
    action: String,
    busy: Boolean,
    canSave: Boolean,
    save: () -> Unit,
    close: () -> Unit,
    dirty: Boolean = true,
    keyValue: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    StagedEditSheet(
        title,
        keyValue,
        busy,
        dirty,
        close,
        actions = {
            Button(
                onClick = save,
                enabled = !busy && canSave,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .heightIn(min = 48.dp),
            ) {
                Text(if (busy) "Saving…" else action)
            }
        },
        content = content,
    )
}

@Composable
internal fun MoneyEntrySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(LocalLayoutSpacing.current),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun EditFormSheet(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    busy: Boolean = false,
    dirty: Boolean = true,
    keyValue: String = "",
    titleText: String = "Edit details",
) {
    StagedEditSheet(
        titleText,
        keyValue,
        busy,
        dirty,
        onDismissRequest,
        actions = {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                dismissButton()
                confirmButton()
            }
        },
    ) {
        title()
        text()
    }
}
