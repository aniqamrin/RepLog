package com.replog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale

private data class HeatCellData(val epochDay: Long?, val level: Int?)

@Composable
fun HeatmapCalendar(
    daysAscending: List<Long>,
    levels: Map<Long, Int>,
    darkTheme: Boolean,
    selectedDay: Long?,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    cellSizeDp: Int = 14,
    gapDp: Int = 3
) {
    val today = remember { LocalDate.now().toEpochDay() }
    val heat = com.replog.app.ui.theme.heatColors(darkTheme)
    val pitch = cellSizeDp + gapDp
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0 && scrollState.value == 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val columns: List<List<HeatCellData>> = remember(daysAscending, levels) {
        val levelByDay = levels
        val cols = mutableListOf<List<HeatCellData>>()
        var current = mutableListOf<HeatCellData>()
        val firstDay = daysAscending.firstOrNull() ?: return@remember emptyList()
        val leadingPad = LocalDate.ofEpochDay(firstDay).dayOfWeek.value - 1
        repeat(leadingPad) { current.add(HeatCellData(null, null)) }
        for (day in daysAscending) {
            current.add(HeatCellData(day, levelByDay[day]))
            if (current.size == 7) {
                cols.add(current.toList())
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) {
            while (current.size < 7) current.add(HeatCellData(null, null))
            cols.add(current.toList())
        }
        cols.toList()
    }

    val monthLabels: Map<Int, String> = remember(columns) {
        val out = mutableMapOf<Int, String>()
        var lastMonth = -1
        columns.forEachIndexed { index, col ->
            val firstReal = col.firstNotNullOfOrNull { it.epochDay }
            if (firstReal != null) {
                val date = LocalDate.ofEpochDay(firstReal)
                if (date.monthValue != lastMonth) {
                    out[index] = date.month.getDisplayName(
                        java.time.format.TextStyle.SHORT, Locale.ENGLISH
                    )
                    lastMonth = date.monthValue
                }
            }
        }
        out
    }

    Column(modifier.horizontalScroll(scrollState)) {
        Row {
            Spacer(Modifier.width(26.dp))
            columns.forEachIndexed { index, _ ->
                Box(Modifier.width(pitch.dp), contentAlignment = Alignment.BottomStart) {
                    monthLabels[index]?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column {
                listOf("Mon", "", "Wed", "", "Fri", "", "").forEach { label ->
                    Box(
                        Modifier.width(26.dp).height((cellSizeDp).dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (label.isNotEmpty()) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(gapDp.dp))
                }
            }
            columns.forEach { col ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    col.forEach { cell ->
                        val isFuture = cell.epochDay != null && cell.epochDay > today
                        when {
                            cell.epochDay == null || isFuture -> Spacer(
                                Modifier.size(cellSizeDp.dp)
                            )
                            else -> HeatSquare(
                                color = heat[(cell.level ?: 0).coerceIn(0, 4)],
                                selected = selectedDay != null && cell.epochDay == selectedDay,
                                sizeDp = cellSizeDp,
                                onClick = { cell.epochDay?.let(onDayClick) }
                            )
                        }
                        Spacer(Modifier.height(gapDp.dp))
                    }
                }
                Spacer(Modifier.width(gapDp.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        LegendRow(heat)
    }
}

@Composable
private fun HeatSquare(
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    sizeDp: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(3.dp)
    var base = Modifier
        .size(sizeDp.dp)
        .clip(shape)
        .background(color)
    if (selected) {
        base = base.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.onSurface,
            shape = shape
        )
    }
    Box(base.clickable(onClick = onClick))
}

@Composable
private fun LegendRow(heat: List<androidx.compose.ui.graphics.Color>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        heat.forEach { c ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c)
            )
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(2.dp))
        Text(
            "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
