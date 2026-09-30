package hr.raspored.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.data.PayrollEstimate
import hr.raspored.app.data.PayrollSettings
import hr.raspored.app.data.PayrollSettingsStore
import hr.raspored.app.data.PublicHealthPayroll
import hr.raspored.app.data.TimeEvidenceEntry
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun PayrollScreen(
    evidenceEntries: List<TimeEvidenceEntry>,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val settingsStore = remember(context) { PayrollSettingsStore(context) }
    val initial = remember(settingsStore) { settingsStore.load() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var roleId by remember { mutableStateOf(initial.roleId) }
    var coefficientText by remember { mutableStateOf(decimalText(initial.coefficient)) }
    var yearsText by remember { mutableStateOf(initial.yearsService.toString()) }
    var extraText by remember { mutableStateOf(decimalText(initial.extraPercent)) }
    var customBaseText by remember { mutableStateOf(initial.customBase?.let(::decimalText).orEmpty()) }
    var secondShift by remember { mutableStateOf(initial.secondShift) }
    var roleMenu by remember { mutableStateOf(false) }

    val role = PublicHealthPayroll.role(roleId)
    val coefficient = coefficientText.toDecimalOrNull()?.coerceIn(1.0, 8.0) ?: role.coefficient
    val years = yearsText.toIntOrNull()?.coerceIn(0, 60) ?: 0
    val extra = extraText.toDecimalOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
    val customBase = customBaseText.toDecimalOrNull()?.takeIf { it > 0.0 }?.coerceAtMost(10_000.0)
    val estimate = remember(month, evidenceEntries, coefficient, years, extra, secondShift, customBase) {
        PublicHealthPayroll.estimate(
            month = month,
            entries = evidenceEntries,
            coefficient = coefficient,
            yearsService = years,
            extraPercent = extra,
            secondShift = secondShift,
            customBase = customBase
        )
    }

    LaunchedEffect(roleId, coefficient, years, extra, secondShift, customBase) {
        settingsStore.save(
            PayrollSettings(
                roleId = roleId,
                coefficient = coefficient,
                yearsService = years,
                extraPercent = extra,
                secondShift = secondShift,
                customBase = customBase
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen-payroll").padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Natrag")
                }
                Column(Modifier.weight(1f)) {
                    Text("Okvirna plaća", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Bruto procjena prema koeficijentu i stvarnoj Evidenciji sati.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
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
                        Text("Mjesec obračuna", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(Icons.Outlined.ChevronLeft, "Prethodni mjesec")
                        }
                        Text(
                            month.month.getDisplayName(TextStyle.FULL, Locale("hr", "HR"))
                                .replaceFirstChar { it.titlecase(Locale("hr", "HR")) } + " " + month.year + ".",
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { month = month.plusMonths(1) }) {
                            Icon(Icons.Outlined.ChevronRight, "Sljedeći mjesec")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Radno mjesto", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(
                            onClick = { roleMenu = true },
                            modifier = Modifier.fillMaxWidth().testTag("payroll-role-selector")
                        ) {
                            Text(role.label, modifier = Modifier.weight(1f), maxLines = 2)
                            Icon(Icons.Outlined.ExpandMore, null)
                        }
                        DropdownMenu(
                            expanded = roleMenu,
                            onDismissRequest = { roleMenu = false },
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            PublicHealthPayroll.roles.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(option.label, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Koef. ${decimalText(option.coefficient)} · ${option.code}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        roleId = option.id
                                        coefficientText = decimalText(option.coefficient)
                                        roleMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(role.officialName, fontWeight = FontWeight.Bold)
                    Text(
                        "Šifra ${role.code} · službeni koeficijent ${decimalText(role.coefficient)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (role.note.isNotBlank()) {
                        Text(role.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PayrollNumberField(
                            label = "Koeficijent",
                            value = coefficientText,
                            onValueChange = { coefficientText = it.take(6) },
                            modifier = Modifier.weight(1f)
                        )
                        PayrollNumberField(
                            label = "Godine staža",
                            value = yearsText,
                            onValueChange = { yearsText = it.filter(Char::isDigit).take(2) },
                            modifier = Modifier.weight(1f),
                            decimal = false
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PayrollNumberField(
                            label = "Ručna osnovica €",
                            value = customBaseText,
                            onValueChange = { customBaseText = it.take(10) },
                            modifier = Modifier.weight(1f),
                            placeholder = "Automatski"
                        )
                        PayrollNumberField(
                            label = "Dodatak po rješenju %",
                            value = extraText,
                            onValueChange = { extraText = it.take(6) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().clickable {
                            secondShift = !secondShift
                        }.padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Druga smjena 10%", fontWeight = FontWeight.Bold)
                            Text(
                                "Uključi samo ako se dodatak za rad u drugoj smjeni primjenjuje na tvoju organizaciju rada.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = secondShift,
                            onCheckedChange = {
                                secondShift = it
                            }
                        )
                    }
                }
            }
        }

        item { PayrollResultCard(estimate, years) }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Obračunski sati i dodaci", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Dodaci se procjenjuju iz stvarne Evidencije sati, ne iz planiranog rasporeda.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PayrollLine("Ukupno evidentirano", minutesLabelPayroll(estimate.evidence.workedMinutes), null)
                    PayrollLine("Noćni rad 22:00–06:00", minutesLabelPayroll(estimate.evidence.nightMinutes), estimate.nightAddition)
                    PayrollLine("Rad subotom", minutesLabelPayroll(estimate.evidence.saturdayMinutes), estimate.saturdayAddition)
                    PayrollLine("Rad nedjeljom", minutesLabelPayroll(estimate.evidence.sundayMinutes), estimate.sundayAddition)
                    PayrollLine("Rad blagdanom / neradnim danom", minutesLabelPayroll(estimate.evidence.holidayMinutes), estimate.holidayAddition)
                    PayrollLine("Prekovremeni iznad mjesečnog fonda", minutesLabelPayroll(estimate.evidence.overtimeMinutes), estimate.overtimeAddition)
                    if (secondShift) {
                        PayrollLine("Druga smjena / turnus 14:00–22:00", minutesLabelPayroll(estimate.evidence.secondShiftMinutes), estimate.secondShiftAddition)
                    }
                    if (estimate.customAddition > 0.0) {
                        PayrollLine("Dodatak po rješenju / ugovoru", "${decimalText(extra)}%", estimate.customAddition)
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Kako je procjena napravljena", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Osnovna bruto plaća = osnovica × koeficijent, uz +0,5% za svaku navršenu godinu staža. " +
                            "Noćni, subotnji, nedjeljni, blagdanski i prekovremeni dodaci procjenjuju se iz Evidencije sati.",
                        modifier = Modifier.padding(top = 7.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        "Izvori parametara: NN 22/2024, NN 11/2026 i TKU NN 29/2024. Za KBC Rijeka naziv radnog mjesta uspoređuje se sa službenom sistematizacijom/natječajima. Važeći sustav koristi koeficijent radnog mjesta, a ne jedan univerzalni bod koji bolnica proizvoljno određuje.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        "Ovo je okvirna bruto procjena, ne obračunska isprava niti neto iznos. Porez, bolovanje, dežurstva, pripravnost i druga individualna prava mogu promijeniti konačan iznos.",
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PayrollResultCard(estimate: PayrollEstimate, years: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Procijenjeni bruto", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(
                euro(estimate.estimatedGross),
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RasporedTokens.Cyan
            )
            Text(
                if (estimate.evidence.workedMinutes > 0L)
                    "Iz ${minutesLabelPayroll(estimate.evidence.workedMinutes)} evidentiranog rada."
                else "Nema evidencije za mjesec; prikazana je osnovna bruto procjena.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayrollMetric("Osnovica", euro(estimate.base), Modifier.weight(1f))
                PayrollMetric("Koeficijent", decimalText(estimate.coefficient), Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PayrollMetric("Staž", "$years god. · +${decimalText(years * 0.5)}%", Modifier.weight(1f))
                PayrollMetric("Mjesečni fond", "${estimate.monthlyFundHours} h", Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PayrollMetric("Osnovna bruto + staž", euro(estimate.basicGross), Modifier.weight(1f))
                PayrollMetric("Procijenjeni dodaci", euro(estimate.additions), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PayrollMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(11.dp)) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PayrollLine(label: String, hours: String, amount: Double?) {
    HorizontalDivider(Modifier.padding(top = 10.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(hours, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(amount?.let(::euro) ?: "—", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PayrollNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    decimal: Boolean = true,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
    )
}

private fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()

private fun decimalText(value: Double): String =
    value.toString().replace('.', ',')

private fun euro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("hr", "HR")).apply {
        currency = java.util.Currency.getInstance("EUR")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)

private fun minutesLabelPayroll(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0L)
    val h = safe / 60L
    val remainder = safe % 60L
    return if (remainder == 0L) "${h} h" else "${h} h ${remainder.toString().padStart(2, '0')} min"
}
