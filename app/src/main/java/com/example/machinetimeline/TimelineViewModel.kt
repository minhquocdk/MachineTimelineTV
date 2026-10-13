package com.example.machinetimeline

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val MIN = 60_000L
private const val HOUR = 60 * MIN
private const val DAY = 24 * HOUR

data class Inputs(
    val status: List<StatusRecord> = emptyList(),
    val work: List<WorkRecord> = emptyList(),
    val plan: List<PlanRecord> = emptyList(),
    val teams: List<MachineTeam> = emptyList(),
    val windowStart: Long = 0L,
    val windowEnd: Long = 1L,
    val nowMs: Long = 0L,
    val team: String? = "CNC",          // mặc định giống bản web
    val machine: String? = null,
    val mode: ViewMode = ViewMode.STATUS,
    val textScale: Float = 1f,
    val loading: Boolean = false,
    val error: String? = null
)

class TimelineViewModel(
    private val repo: TimelineRepository = TimelineRepository()
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    private val clockFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    private var timeLag = 0L
    private var lagSynced = false
    private var teamResolved = false
    private fun now() = System.currentTimeMillis() - timeLag

    private fun window(n: Long): Pair<Long, Long> {
        val aligned = Instant.ofEpochMilli(n).atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli()
        return (aligned - 12 * HOUR) to (aligned + 12 * HOUR)
    }

    private fun day(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    private val inputs = MutableStateFlow(
        window(now()).let { (s, e) -> Inputs(windowStart = s, windowEnd = e, nowMs = now()) }
    )

    // Mọi phép tính thời gian/tỉ lệ chạy ở Default dispatcher, UI chỉ nhận float 0..1
    val uiState: StateFlow<TimelineScreenState> = inputs
        .map { buildState(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimelineScreenState())

    val clock: StateFlow<String> = flow {
        while (true) {
            emit(clockFmt.format(Instant.ofEpochMilli(now()).atZone(zone)))
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(60_000) // auto refresh mỗi phút
            }
        }
    }

    fun refreshNow() { viewModelScope.launch { refresh() } }

    // ---------------- Data loading ----------------
    private suspend fun refresh() {
        inputs.update { it.copy(loading = true) }
        try {
            if (!lagSynced) repo.serverTimeMs()?.let {
                timeLag = System.currentTimeMillis() - it; lagSynced = true
            }
            val n = now()
            val (ws, we) = window(n)
            coroutineScope {
                val s = async { repo.statuses(day(ws), day(we)) }
                val w = async { repo.work(day(ws - DAY), day(we)) }
                val p = async { repo.plans(day(ws - 3 * DAY), day(ws + 3 * DAY)) }
                val t = async { runCatching { repo.teams() }.getOrDefault(emptyList()) }
                val sv = s.await(); val wv = w.await(); val pv = p.await(); val tv = t.await()
                inputs.update { cur ->
                    val team = if (!teamResolved && tv.isNotEmpty() && tv.none { it.team == cur.team }) null else cur.team
                    cur.copy(
                        status = sv, work = wv, plan = pv, teams = tv,
                        windowStart = ws, windowEnd = we, nowMs = n,
                        team = team, loading = false, error = null
                    )
                }
                if (tv.isNotEmpty()) teamResolved = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            inputs.update { it.copy(loading = false, nowMs = now(), error = "Cập nhật lỗi: ${e.message}. Đang hiển thị dữ liệu cũ.") }
        }
    }

    // ---------------- Actions (D-pad cycle) ----------------
    fun cycleTeam() = inputs.update { cur ->
        val opts = listOf<String?>(null) + cur.teams.map { it.team }.distinct().sorted()
        cur.copy(team = opts[(opts.indexOf(cur.team) + 1).mod(opts.size)], machine = null)
    }

    fun cycleMachine() = inputs.update { cur ->
        val opts = listOf<String?>(null) + machineOptions(cur)
        cur.copy(machine = opts[(opts.indexOf(cur.machine) + 1).mod(opts.size)])
    }

    fun cycleMode() = inputs.update { cur ->
        val all = ViewMode.entries
        cur.copy(mode = all[(cur.mode.ordinal + 1) % all.size], machine = null)
    }

    fun changeTextScale(delta: Float) = inputs.update {
        it.copy(textScale = ((it.textScale + delta) * 10).toInt().coerceIn(7, 18) / 10f)
    }

    // ---------------- Build UI state ----------------
    private fun machineOptions(i: Inputs): List<String> {
        val base: Set<String> = when (i.mode) {
            ViewMode.ALL -> i.teams.map { it.machine }
            ViewMode.STATUS -> i.status.map { it.machine }
            ViewMode.PLAN_WORK -> i.plan.map { it.machine } + i.work.map { it.machine }
        }.toSortedSet()
        val inTeam = i.team?.let { t -> i.teams.filter { it.team == t }.map { it.machine }.toSet() }
        return if (inTeam != null) base.filter { it in inTeam } else base.toList()
    }

    private fun buildState(i: Inputs): TimelineScreenState {
        val ws = i.windowStart; val we = i.windowEnd; val now = i.nowMs
        val options = machineOptions(i)
        val shown = i.machine?.let { m -> options.filter { it == m } } ?: options

        val statusBy = i.status.groupBy { it.machine }
        val workBy = i.work.groupBy { it.machine }
        val planBy = i.plan.groupBy { it.machine }
        val nowPct = if (now in ws..we) (now - ws).toFloat() / (we - ws) else null

        val rows = shown.map { m ->
            val statusSegs = buildStatus(statusBy[m].orEmpty(), ws, we, now)
            val workSegs = workBy[m].orEmpty().mapNotNull { r ->
                val end = r.endMs ?: min(now, we)
                TimeSegment(r.startMs, end, Palette.work, r.content)
            }
            val planSegs = planBy[m].orEmpty().mapNotNull { r ->
                val c = Palette.plan.getOrElse(r.category) { Palette.plan[1] }
                TimeSegment(r.startMs, r.endMs ?: we, c, r.detail)
            }
            MachineTimelineUIState(
                machineId = m,
                planSegments = planSegs.toUi(ws, we),
                workSegments = workSegs.toUi(ws, we),
                statusSegments = statusSegs.toUi(ws, we),
                currentTimePct = nowPct,
                currentStatus = statusSegs.lastOrNull { it.startTimeMs <= now }?.label,
                currentWork = workSegs.firstOrNull { it.startTimeMs <= now && it.endTimeMs >= now }?.label,
                currentPlan = planSegs.firstOrNull { it.startTimeMs <= now && it.endTimeMs >= now }?.label,
                statusSummary = summarize(statusSegs)
            )
        }
        return TimelineScreenState(
            rows = rows,
            axisTicks = buildTicks(ws, we),
            teamLabel = i.team ?: "All",
            machineLabel = i.machine ?: "All",
            modeLabel = i.mode.label,
            textScale = i.textScale,
            loading = i.loading,
            error = i.error,
            info = "${rows.size} machines | Status: ${i.status.size}, Work: ${i.work.size}, Plan: ${i.plan.size}"
        )
    }

    /** Port logic JS: lấp khoảng trống bằng trạng thái trước đó, kéo dài tới 'now'. */
    private fun buildStatus(history: List<StatusRecord>, ws: Long, we: Long, now: Long): List<TimeSegment> {
        val out = ArrayList<TimeSegment>()
        fun add(s: Long, e: Long, st: String) {
            if (e <= s) return
            val last = out.lastOrNull()
            if (last != null && last.label == st) out[out.lastIndex] = last.copy(endTimeMs = e)
            else out.add(TimeSegment(s, e, Palette.status(st), st))
        }
        var cur = ws
        var last = "Off"
        history.sortedBy { it.startMs }
            .filter { it.startMs <= we && (it.endMs ?: now) >= ws }
            .forEach { r ->
                val s = max(ws, r.startMs)
                val e = min(we, r.endMs ?: now)
                if (cur < s) add(cur, s, last)
                add(max(s, cur), e, r.status)
                last = r.status
                cur = max(cur, e)
            }
        val limit = min(now, we)
        if (cur < limit) add(cur, limit, last)
        return out
    }

    private fun List<TimeSegment>.toUi(ws: Long, we: Long): List<UIProgressSegment> {
        val span = (we - ws).toFloat()
        return mapNotNull { s ->
            val a = max(s.startTimeMs, ws); val b = min(s.endTimeMs, we)
            if (b <= a) null
            else {
                val sp = (a - ws) / span; val ep = (b - ws) / span
                if (ep - sp > 0.0005f) UIProgressSegment(sp, ep, s.color) else null
            }
        }
    }

    private fun summarize(segs: List<TimeSegment>): String =
        segs.filter { it.label != null && it.label != "Off" }
            .groupBy { it.label!! }
            .map { (k, v) -> k to v.sumOf { it.endTimeMs - it.startTimeMs } }
            .filter { it.second > 0 }
            .joinToString("  •  ") { (k, d) -> "$k ${fmtDur(d)}" }

    private fun fmtDur(ms: Long): String {
        val h = ms / HOUR; val m = (ms / MIN) % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun buildTicks(ws: Long, we: Long): List<AxisTick> {
        val total = we - ws
        if (total <= 0) return emptyList()
        val hours = total / HOUR.toDouble()
        val step = when {
            hours <= 4 -> 30 * MIN
            hours <= 12 -> HOUR
            hours <= 28 -> 2 * HOUR
            hours <= 60 -> 6 * HOUR
            hours <= 120 -> 12 * HOUR
            else -> DAY
        }
        val multi = hours > 28
        fun fmt(ms: Long): String {
            val d = Instant.ofEpochMilli(ms).atZone(zone)
            return (if (multi || (d.hour == 0 && d.minute == 0)) dateTimeFmt else timeFmt).format(d)
        }
        val out = mutableListOf(AxisTick(0f, fmt(ws)), AxisTick(1f, fmt(we)))
        var t = ceil(ws / step.toDouble()).toLong() * step
        while (t < we) {
            val p = (t - ws).toFloat() / total
            if (p > 0.07f && p < 0.93f) out.add(AxisTick(p, fmt(t)))
            t += step
        }
        return out.sortedBy { it.pct }
    }
}
