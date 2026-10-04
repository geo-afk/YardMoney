package jm.yardmoney.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoneyEntrySheet(
    title: String,
    action: String,
    busy: Boolean,
    canSave: Boolean,
    save: () -> Unit,
    close: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val latestBusy by rememberUpdatedState(busy)
    val state =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { !latestBusy },
        )
    val scroll = rememberScrollState()
    val latestClose by rememberUpdatedState(close)
    var viewportHeight by remember { mutableIntStateOf(0) }
    val defaultThreshold = with(LocalDensity.current) { 120.dp.toPx() }
    // A deliberate second pull must remain reachable in landscape and above the keyboard.
    val pullThreshold =
        if (viewportHeight > 0) minOf(defaultThreshold, viewportHeight * .45f) else defaultThreshold
    val gate = remember(pullThreshold) { FormDismissGate(pullThreshold) }
    val scrollConnection =
        remember(scroll, gate) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (scroll.value == 0 && available.y > 0f) {
                        if (source == NestedScrollSource.UserInput && !latestBusy)
                            gate.pull(available.y)
                        return Offset(0f, available.y)
                    }
                    if (source == NestedScrollSource.UserInput && available.y < 0f)
                        gate.pull(available.y)
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = Offset(0f, available.y)

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity = available
            }
        }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) close() },
        sheetState = state,
        sheetGesturesEnabled = false,
        sheetMaxWidth = 640.dp,
        dragHandle = null,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Scroll to the top, then pull down again to close.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = close, enabled = !busy) {
                    Icon(Icons.Default.Close, "Close money entry")
                }
            }
            HorizontalDivider()
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .testTag("money-entry-scroll")
                    .onSizeChanged { viewportHeight = it.height }
                    .pointerInput(scroll, gate) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            gate.begin(scroll.value == 0 && !latestBusy)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                            } while (event.changes.any { it.pressed })
                            if (gate.finish() && !latestBusy) latestClose()
                        }
                    }
                    .nestedScroll(scrollConnection)
                    .verticalScroll(scroll, overscrollEffect = null)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(LocalLayoutSpacing.current),
                content = content,
            )
            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!canSave && !busy)
                    Text(
                        "Enter a valid amount and choose the accounts to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                Button(
                    onClick = save,
                    enabled = !busy && canSave,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(if (busy) "Saving…" else action)
                }
            }
        }
    }
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
