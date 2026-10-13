package com.example.machinetimeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val ScreenBg = Color(0xFF1F2937)
private val CardBg = Color(0xFF374151)
private val CardBgFocused = Color(0xFF4B5563)
private val Muted = Color(0xFF9CA3AF)
private val FocusBorder = Color(0xFF60A5FA)

private val LIST_PAD = 8.dp
private val ROW_PAD = 10.dp
private val NAME_W = 120.dp
private val GAP = 8.dp
private val LABEL_W = 24.dp
private val BAR_H = 26.dp

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TimelineScreen(vm: TimelineViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    var focusedId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(ScreenBg).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Header(state, clock, vm)
        Spacer(Modifier.height(8.dp))
        TimeAxis(state.axisTicks)

        if (state.rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (state.loading) "Loading..." else "No machines with data for selected filters",
                    color = Muted, fontSize = 18.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().focusRestorer(),
                contentPadding = PaddingValues(horizontal = LIST_PAD, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.rows, key = { it.machineId }) { row ->
                    MachineRow(row, onFocused = { focusedId = row.machineId })
                }
            }
        }
        DetailPanel(state.rows.firstOrNull { it.machineId == focusedId })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Header(state: TimelineScreenState, clock: String, vm: TimelineViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Machine Timelines", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(24.dp))
        Text(clock, color = Muted, fontSize = 16.sp)
        Spacer(Modifier.width(16.dp))
        if (state.loading) Text("Loading...", color = Color(0xFFFBBF24), fontSize = 16.sp)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = vm::cycleTeam) { Text("Tổ: ${state.teamLabel}", fontSize = 16.sp) }
        Button(onClick = vm::cycleMachine) { Text("M: ${state.machineLabel}", fontSize = 16.sp) }
        Button(onClick = vm::cycleMode) { Text("Mode ${state.modeLabel}", fontSize = 16.sp) }
        Button(onClick = { vm.changeTextScale(-0.1f) }) { Text("A−", fontSize = 16.sp) }
        Text("${(state.textScale * 100).toInt()}%", color = Muted, fontSize = 16.sp)
        Button(onClick = { vm.changeTextScale(+0.1f) }) { Text("A+", fontSize = 16.sp) }
        Button(onClick = vm::refreshNow) { Text("Load", fontSize = 16.sp) }
        Spacer(Modifier.width(8.dp))
        Text(state.info, color = Muted, fontSize = 14.sp)
    }
    state.error?.let { Text(it, color = Color(0xFFF87171), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Palette.legend.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Text(name, color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TimeAxis(ticks: List<AxisTick>) {
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .padding(start = LIST_PAD + ROW_PAD + NAME_W + GAP + LABEL_W, end = LIST_PAD + ROW_PAD)
            .height(24.dp)
    ) {
        val w = maxWidth
        ticks.forEach { t ->
            val m = when {
                t.pct <= 0f -> Modifier.align(Alignment.TopStart)
                t.pct >= 1f -> Modifier.align(Alignment.TopEnd)
                else -> Modifier.offset(x = w * t.pct - 32.dp)
            }
            Text(t.text, color = Muted, fontSize = 13.sp, maxLines = 1, modifier = m)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MachineRow(row: MachineTimelineUIState, onFocused: () -> Unit) {
    val plan = remember(row.planSegments) { UIProgressSegmentList(row.planSegments) }
    val work = remember(row.workSegments) { UIProgressSegmentList(row.workSegments) }
    val status = remember(row.statusSegments) { UIProgressSegmentList(row.statusSegments) }

    Surface(
        onClick = { /* có thể mở màn hình chi tiết máy */ },
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = CardBg, focusedContainerColor = CardBgFocused,
            pressedContainerColor = CardBgFocused,
            contentColor = Color.White, focusedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, FocusBorder), shape = RoundedCornerShape(8.dp))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f)
    ) {
        Row(Modifier.padding(ROW_PAD), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(NAME_W).height(BAR_H * 3 + 8.dp)
                    .background(Color(0xFF4B5563), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(row.machineId, color = Color(0xFFE5E7EB), fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(GAP))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BarLine("P", plan, row.currentTimePct)
                BarLine("W", work, row.currentTimePct)
                BarLine("S", status, row.currentTimePct)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BarLine(label: String, segs: UIProgressSegmentList, nowPct: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(LABEL_W))
        TimelineCanvasBar(segs, nowPct, Modifier.weight(1f).height(BAR_H))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailPanel(row: MachineTimelineUIState?) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(CardBg, RoundedCornerShape(8.dp)).padding(12.dp)) {
        if (row == null) {
            Text("Dùng D-pad để chọn máy xem chi tiết", color = Muted, fontSize = 16.sp)
        } else {
            Text("${row.machineId}  •  Trạng thái: ${row.currentStatus ?: "—"}",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Kế hoạch: ${row.currentPlan ?: "—"}", color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Công việc: ${row.currentWork ?: "—"}", color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.statusSummary.isNotEmpty())
                Text("Thời gian: ${row.statusSummary}", color = Muted, fontSize = 15.sp)
        }
    }
}
