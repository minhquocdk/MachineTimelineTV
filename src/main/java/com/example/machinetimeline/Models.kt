package com.example.machinetimeline

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ---------- Spec structures ----------
data class TimeSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val color: Color,
    val label: String? = null
)

data class UIProgressSegment(
    val startPct: Float, // 0.0f..1.0f
    val endPct: Float,   // 0.0f..1.0f
    val color: Color
)

@Immutable
data class UIProgressSegmentList(val segments: List<UIProgressSegment>)

data class MachineTimelineUIState(
    val machineId: String,
    val planSegments: List<UIProgressSegment>,
    val workSegments: List<UIProgressSegment>,
    val statusSegments: List<UIProgressSegment>,
    val currentTimePct: Float?,
    // Thông tin chi tiết (thay cho tooltip chuột) – ViewModel tính sẵn
    val currentStatus: String? = null,
    val currentWork: String? = null,
    val currentPlan: String? = null,
    val statusSummary: String = ""
)

data class AxisTick(val pct: Float, val text: String)

// ---------- Domain (đã null-safe: bản ghi hỏng bị loại ở Repository) ----------
data class StatusRecord(val machine: String, val startMs: Long, val endMs: Long?, val status: String)
data class WorkRecord(val machine: String, val startMs: Long, val endMs: Long?, val content: String, val employee: String)
data class PlanRecord(val machine: String, val startMs: Long, val endMs: Long?, val detail: String, val category: Int)
data class MachineTeam(val machine: String, val team: String)

enum class ViewMode(val label: String) {
    ALL("1: All"), STATUS("2: Status"), PLAN_WORK("3: Plan/Work")
}

data class TimelineScreenState(
    val rows: List<MachineTimelineUIState> = emptyList(),
    val axisTicks: List<AxisTick> = emptyList(),
    val teamLabel: String = "All",
    val machineLabel: String = "All",
    val modeLabel: String = ViewMode.STATUS.label,
    val textScale: Float = 1f,
    val loading: Boolean = false,
    val error: String? = null,
    val info: String = ""
)

object Palette {
    fun status(s: String): Color = when (s) {
        "Run" -> Color(0xFF16A34A)
        "Wait" -> Color(0xFFCA8A04)
        "Setup" -> Color(0xFF2563EB)
        "Break" -> Color(0xFF991B1B)
        "Off" -> Color(0xFF4B5563)
        "Available" -> Color(0xFF1F2937)
        "Ready" -> Color(0xFFEAB308)
        else -> Color(0xFF9CA3AF)
    }
    val plan = listOf(0xFF1E3A8A, 0xFF1E40AF, 0xFF1D4ED8, 0xFF2563EB, 0xFF3B82F6).map { Color(it) }
    val work = Color(0xFF0891B2)

    val legend = listOf(
        "Run" to status("Run"), "Break" to status("Break"), "Wait" to status("Wait"),
        "Setup" to status("Setup"), "Ready" to status("Ready"), "Off" to status("Off"),
        "Plan" to Color(0xFF1E40AF), "Work" to work
    )
}
