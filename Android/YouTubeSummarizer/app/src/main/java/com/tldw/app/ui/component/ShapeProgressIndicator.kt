package com.tldw.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShapeProgressIndicator(
  progress: Float, // 0f..1f
  modifier: Modifier = Modifier,
  color: Color = Color.Blue,
  trackColor: Color = Color.LightGray,
  strokeWidth: Dp = 6.dp,
  shapePath: (Size) -> Path, // 👈 customizable shape
) {
  val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }

  Canvas(modifier) {
    val path = shapePath(size)

    val pathMeasure = PathMeasure()
    pathMeasure.setPath(path, false)

    val length = pathMeasure.length
    val progressLength = length * progress

    val segment = Path()
    pathMeasure.getSegment(0f, progressLength, segment, true)

    // Draw track (full shape)
    drawPath(path = path, color = trackColor, style = Stroke(width = strokePx))

    // Draw progress (partial path)
    drawPath(path = segment, color = color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
  }
}
