package hr.raspored.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.data.TimeEvidenceEntry
import hr.raspored.app.data.TimeEvidenceStore
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TimeEvidenceScreen(
    plannedShiftCode: String?,
    plannedShiftLabel: String,
    onBack: () -> Unit,
    onEvidenceChanged: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { TimeEvidenceStore(context) }
    val entries = remember { mutableStateListOf<TimeEvidenceEntry>() }
    var note by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(evidenceNow()) }

    fun refresh() {
        entries.clear()
        entries.addAll(store.load())
        note = entries.lastOrNull { it.endedAt == null }?.note
            ?: entries.lastOrNull()?.note.orEmpty()
    }

    LaunchedEffect(store) { refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            now = evidenceNow()
            delay(60_000)
        }
    }

    val active = entries.lastOrNull { it.endedAt == null }
    val latest = active ?: entries.lastOrNull()
    val zone = ZoneId.systemDefault()
    val currentMonth = YearMonth.from(LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone))
    val monthEntries = entries.filter {
        YearMonth.from(LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startedAt), zone)) == currentMonth
    }
    val totalMinutes = monthEntries.filter { it.endedAt != null }.sumOf { it.durationMinutes(now) }
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("hr", "HR"))
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy.", Locale("hr", "HR"))

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Natrag") }
                Spacer(Modifier.width(2.dp))
                Column {
                    Text("Evidencija sati", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Bilježi stvarni ulaz i izlaz.", color = RasporedTokens.Slate)
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
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Današnji status", color = RasporedTokens.Slate, fontSize = 12.sp)
                            Text(
                                when {
                                    active != null -> "Rad je u tijeku"
                                    latest?.endedAt != null -> "Evidencija je spremljena"
                                    else -> "Nema evidentiranog ulaza"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (active != null) "U tijeku" else if (latest != null) "Završeno" else "Spremno") }
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, null, tint = RasporedTokens.Cyan)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Planirana smjena", fontSize = 12.sp, color = RasporedTokens.Slate)
                                Text(
                                    if (plannedShiftCode != null) "$plannedShiftCode · $plannedShiftLabel" else "Nema planirane smjene",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EvidenceMetric(
                            "Ulaz",
                            latest?.startedAt?.let { epochToTime(it, timeFormatter, zone) } ?: "—",
                            Modifier.weight(1f)
                        )
                        EvidenceMetric(
                            "Izlaz",
                            latest?.endedAt?.let { epochToTime(it, timeFormatter, zone) } ?: "—",
                            Modifier.weight(1f)
                        )
                        EvidenceMetric(
                            "Odrađeno",
                            latest?.let { durationLabel(it.durationMinutes(now)) } ?: "0h 00min",
                            Modifier.weight(1f)
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                store.clockIn(evidenceNow())
                                refresh()
                                onEvidenceChanged()
                            },
                            enabled = active == null,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Evidentiraj ulaz")
                        }
                        OutlinedButton(
                            onClick = {
                                store.clockOut(evidenceNow(), note.trim())
                                refresh()
                                onEvidenceChanged()
                            },
                            enabled = active != null,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(Icons.Outlined.Schedule, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Evidentiraj izlaz")
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        label = { Text("Bilješka") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
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
                        Column(Modifier.weight(1f)) {
                            Text("Ovaj mjesec", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                currentMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("hr", "HR"))
                                    .replaceFirstChar { it.titlecase(Locale("hr", "HR")) } + " " + currentMonth.year + ".",
                                color = RasporedTokens.Slate,
                                fontSize = 12.sp
                            )
                        }
                        Text(durationLabel(totalMinutes), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = RasporedTokens.Cyan)
                    }

                    if (monthEntries.isEmpty()) {
                        Text(
                            "Još nema evidentiranih sati za ovaj mjesec.",
                            color = RasporedTokens.Slate,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        monthEntries.asReversed().forEach { entry ->
                            val started = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.startedAt), zone)
                            HorizontalDivider(Modifier.padding(top = 10.dp))
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.width(72.dp)) {
                                    Text(started.format(dateFormatter), fontWeight = FontWeight.Bold)
                                    Text(
                                        started.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("hr", "HR")),
                                        color = RasporedTokens.Slate,
                                        fontSize = 11.sp
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        epochToTime(entry.startedAt, timeFormatter, zone) + " – " +
                                            (entry.endedAt?.let { epochToTime(it, timeFormatter, zone) } ?: "u tijeku"),
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (entry.note.isNotBlank()) Text(entry.note, color = RasporedTokens.Slate, fontSize = 11.sp)
                                }
                                Text(durationLabel(entry.durationMinutes(now)), fontWeight = FontWeight.Bold)
                            }
                        }
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
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(11.dp)) {
            Text(label, color = RasporedTokens.Slate, fontSize = 11.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

private fun epochToTime(epoch: Long, formatter: DateTimeFormatter, zone: ZoneId): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zone).format(formatter)

private fun durationLabel(minutes: Long): String =
    "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}min"

private fun evidenceNow(): Long = System.currentTimeMillis()
