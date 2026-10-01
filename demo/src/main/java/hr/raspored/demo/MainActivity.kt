package hr.raspored.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DemoApp() }
    }
}

private val Navy = Color(0xFF0B1F3A)
private val Cyan = Color(0xFF0EA5E9)
private val codes = listOf("D", "N", "GO", "BO", "PD", "SD")

@Composable
private fun DemoApp() {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var editDate by remember { mutableStateOf<LocalDate?>(null) }
    var custom by remember { mutableStateOf("") }
    val shifts = remember { mutableStateMapOf<LocalDate, String>() }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cyan,
            surface = Color.White,
            background = Color(0xFFF3F7FB)
        )
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = Navy) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(68.dp)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "RASPORED",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text("DEMO", color = Color(0xFF8EDBFF), fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 2.dp
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { month = month.minusMonths(1) }) {
                                Icon(Icons.Outlined.ChevronLeft, "Prethodni mjesec")
                            }
                            Text(
                                month.month
                                    .getDisplayName(TextStyle.FULL, Locale("hr", "HR"))
                                    .replaceFirstChar { it.titlecase(Locale("hr", "HR")) } +
                                    " " + month.year + ".",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            IconButton(onClick = { month = month.plusMonths(1) }) {
                                Icon(Icons.Outlined.ChevronRight, "Sljedeći mjesec")
                            }
                        }
                        DemoCalendar(
                            month = month,
                            shifts = shifts,
                            onDay = {
                                editDate = it
                                custom = shifts[it].orEmpty()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(6.dp)
                        )
                    }
                }
            }
        }

        editDate?.let { date ->
            AlertDialog(
                onDismissRequest = { editDate = null },
                title = {
                    Text("Oznaka za " + date.dayOfMonth + "." + date.monthValue + "." + date.year + ".")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        codes.chunked(3).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { code ->
                                    FilledTonalButton(
                                        onClick = {
                                            shifts[date] = code
                                            editDate = null
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(code, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = custom,
                            onValueChange = {
                                custom = it
                                    .uppercase(Locale("hr", "HR"))
                                    .filter { ch -> ch.isLetterOrDigit() }
                                    .take(8)
                            },
                            label = { Text("Vlastita oznaka") },
                            placeholder = { Text("npr. J, S, P1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = custom.isNotBlank(),
                        onClick = {
                            shifts[date] = custom
                            editDate = null
                        }
                    ) {
                        Text("Spremi")
                    }
                },
                dismissButton = {
                    Row {
                        if (shifts.containsKey(date)) {
                            TextButton(
                                onClick = {
                                    shifts.remove(date)
                                    editDate = null
                                }
                            ) {
                                Text("Očisti")
                            }
                        }
                        TextButton(onClick = { editDate = null }) {
                            Text("Odustani")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DemoCalendar(
    month: YearMonth,
    shifts: Map<LocalDate, String>,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val first = month.atDay(1)
    val mondayOffset = first.dayOfWeek.value - 1
    val cells = (0 until 42).map {
        first.minusDays(mondayOffset.toLong()).plusDays(it.toLong())
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Pon", "Uto", "Sri", "Čet", "Pet", "Sub", "Ned").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                week.forEach { date ->
                    val inside = YearMonth.from(date) == month
                    val code = shifts[date]
                    Surface(
                        onClick = { if (inside) onDay(date) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (code != null) Color(0xFFDFF4FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.dp,
                            if (inside) Color(0xFFD4DFEB) else Color(0xFFE8EDF3)
                        )
                    ) {
                        Box(Modifier.fillMaxSize().padding(5.dp)) {
                            Text(
                                date.dayOfMonth.toString(),
                                modifier = Modifier.align(Alignment.TopStart),
                                color = if (inside) Navy else Color(0xFFB8C2CF),
                                fontSize = 12.sp
                            )
                            if (code != null) {
                                Text(
                                    code,
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Cyan,
                                    fontSize = if (code.length > 2) 15.sp else 21.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
