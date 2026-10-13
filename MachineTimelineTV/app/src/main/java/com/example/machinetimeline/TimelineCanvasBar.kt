package com.example.machinetimeline

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val BarBackground = Color(0xFF1F2937)
private val IndicatorColor = Color(0xFFEF4444)
private val HatchBase = Color(0xFF2B3340)
private val HatchLine = Color(0xFF4B5563)

/**
 * Thanh timeline tối ưu 4K. Không có phép tính thời gian / toPx() trong vòng vẽ.
 * Danh sách rỗng (dữ liệu null/hỏng) -> vẽ nền gạch chéo xám thay vì crash.
 */
@Composable
fun TimelineCanvasBar(
    uiSegmentsState: UIProgressSegmentList,
    currentTimePct: Float?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val verticalPaddingPx = with(density) { 2.dp.toPx() }
    val cornerRadiusPx = with(density) { 2.dp.toPx() }
    val strokeWidthPx = with(density) { 2.dp.toPx() }
    val hatchStepPx = with(density) { 10.dp.toPx() }
    val hatchStrokePx = with(density) { 1.5.dp.toPx() }

    Spacer(
        modifier = modifier.drawWithCache {
            val cornerRadius = CornerRadius(cornerRadiusPx)

            onDrawBehind {
                val canvasWidth = size.width
                val canvasHeight = size.height

                drawRect(color = BarBackground, size = size)

                val segmentList = uiSegmentsState.segments
                val segmentCount = segmentList.size

                if (segmentCount == 0) {
                    clipRect {
                        drawRect(HatchBase, Offset(0f, verticalPaddingPx),
                            Size(canvasWidth, canvasHeight - verticalPaddingPx * 2))
                        var x = -canvasHeight
                        while (x < canvasWidth) {
                            drawLine(HatchLine, Offset(x, canvasHeight), Offset(x + canvasHeight, 0f), hatchStrokePx)
                            x += hatchStepPx
                        }
                    }
                }

                for (i in 0 until segmentCount) {
                    val seg = segmentList[i]
                    val x = seg.startPct * canvasWidth
                    val w = (seg.endPct - seg.startPct) * canvasWidth
                    if (w > 0f) {
                        drawRoundRect(
                            color = seg.color,
                            topLeft = Offset(x, verticalPaddingPx),
                            size = Size(w, canvasHeight - (verticalPaddingPx * 2)),
                            cornerRadius = cornerRadius
                        )
                    }
                }

                if (currentTimePct != null && currentTimePct in 0f..1f) {
                    val currentX = currentTimePct * canvasWidth
                    drawLine(
                        color = IndicatorColor,
                        start = Offset(currentX, 0f),
                        end = Offset(currentX, canvasHeight),
                        strokeWidth = strokeWidthPx
                    )
                }
            }
        }
    )
}
