package hr.raspored.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.data.EvidenceAnalytics
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun TimeEvidenceScreen(
    scheduleCodes: Map<String, String>,
    onBack: () -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val summary = remember(month, scheduleCodes.toMap()) {
        EvidenceAnalytics.summarize(month, scheduleCodes)
    }
    val monthTitle = month.month
        .getDisplayName(TextStyle.FULL, Locale("hr", "HR"))
        .replaceFirstChar { it.titlecase(Locale("hr", "HR")) } + " " + month.year + "."

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-hours")
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Natrag") }
                Spacer(Modifier.width(2.dp))
                Column {
                    Text("Evidencija sati", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Iz potvrđenog kalendara — bez ručnog evidentiranja ulaza i izlaza.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(Icons.Outlined.ChevronLeft, "Prethodni mjesec")
                        }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Razdoblje", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(monthTitle, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        IconButton(onClick = { month = month.plusMonths(1) }) {
                            Icon(Icons.Outlined.ChevronRight, "Sljedeći mjesec")
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EvidenceMetric("Odrađeno", minutesLabel(summary.workedMinutes), Modifier.weight(1f))
                        EvidenceMetric("Mjesečni fond", minutesLabel(summary.plannedMinutes), Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EvidenceMetric("Prekovremeni", minutesLabel(summary.overtimeMinutes), Modifier.weight(1f))
                        EvidenceMetric("Saldo", signedEvidenceMinutes(summary.balanceMinutes), Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EvidenceMetric("Noćni sati", minutesLabel(summary.nightMinutes), Modifier.weight(1f))
                        EvidenceMetric("Obračunski sati", minutesLabel(summary.accountedMinutes), Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EvidenceMetric("Subote", minutesLabel(summary.saturdayMinutes), Modifier.weight(1f))
                        EvidenceMetric("Nedjelje", minutesLabel(summary.sundayMinutes), Modifier.weight(1f))
                    }
                    Text(
                        "D i N koriste definirani 12-satni model iz kalendara. Za vlastite oznake aplikacija ne izmišlja trajanje.",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = RasporedTokens.Cyan)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Dnevna evidencija", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                "Detaljan zapis izveden iz rasporeda u kalendaru.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }

                    var hasRows = false
                    for (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        val code = scheduleCodes[date.toString()].orEmpty()
                        if (code.isBlank()) continue
                        hasRows = true
                        HorizontalDivider(Modifier.padding(top = 10.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.width(74.dp)) {
                                Text(
                                    "%02d.%02d.".format(day, month.monthValue),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("hr", "HR")),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    EvidenceAnalytics.shiftLabel(code),
                                    fontWeight = FontWeight.Bold
                                )
                                val extra = buildList {
                                    if (EvidenceAnalytics.isWeekendOrHoliday(date)) add("vikend/blagdan")
                                    if (code !in setOf("D", "N", "GO", "BO", "PD", "SD")) add("vlastita oznaka")
                                }
                                if (extra.isNotEmpty()) {
                                    Text(
                                        extra.joinToString(" · "),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        EvidenceAnalytics.hoursLabel(code),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (!hasRows) {
                        Text(
                            "Za ovaj mjesec nema oznaka u kalendaru.",
                            modifier = Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        }
    }
}

private fun minutesLabel(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0L)
    val hours = safe / 60L
    val remainder = safe % 60L
    return if (remainder == 0L) hours.toString() + " h"
    else hours.toString() + " h " + remainder.toString().padStart(2, '0') + " min"
}

private fun signedEvidenceMinutes(minutes: Long): String {
    val sign = when {
        minutes > 0L -> "+"
        minutes < 0L -> "−"
        else -> ""
    }
    return sign + minutesLabel(kotlin.math.abs(minutes))
}
