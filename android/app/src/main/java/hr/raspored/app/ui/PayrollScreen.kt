package hr.raspored.app.ui

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
    val current = YearMonth.now()
    val initialMonth = when {
        current.year < 2026 -> YearMonth.of(2026, 1)
        current.year > 2026 -> YearMonth.of(2026, 12)
        else -> current
    }

    var month by remember { mutableStateOf(initialMonth) }
    var institutionId by remember { mutableStateOf(initial.institutionId) }
    var rateProfileId by remember { mutableStateOf(initial.rateProfileId) }
    var roleId by remember { mutableStateOf(initial.roleId) }
    var coefficientText by remember { mutableStateOf(decimalText(initial.coefficient)) }
    var yearsText by remember { mutableStateOf(initial.yearsService.toString()) }
    var extraText by remember { mutableStateOf(decimalText(initial.extraPercent)) }
    var customBaseText by remember { mutableStateOf(initial.customBase?.let(::decimalText).orEmpty()) }
    var overtimeText by remember { mutableStateOf(initial.overtimeHours?.let(::decimalText).orEmpty()) }
    var turnusText by remember { mutableStateOf(initial.turnusHours.takeIf { it > 0.0 }?.let(::decimalText).orEmpty()) }
    var secondShiftText by remember { mutableStateOf(initial.secondShiftHours.takeIf { it > 0.0 }?.let(::decimalText).orEmpty()) }
    var adjustmentText by remember { mutableStateOf(initial.grossAdjustment.takeIf { it != 0.0 }?.let(::decimalText).orEmpty()) }

    var institutionMenu by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var roleMenu by remember { mutableStateOf(false) }

    val institution = PublicHealthPayroll.institution(institutionId)
    val rateProfile = PublicHealthPayroll.rateProfile(rateProfileId)
    val role = PublicHealthPayroll.role(roleId)
    val coefficient = coefficientText.toDecimalOrNull()?.coerceIn(1.0, 8.0) ?: role.coefficient
    val years = yearsText.toIntOrNull()?.coerceIn(0, 60) ?: 0
    val extra = extraText.toDecimalOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
    val customBase = customBaseText.toDecimalOrNull()?.takeIf { it > 0.0 }?.coerceAtMost(10_000.0)
    val overtimeOverride = overtimeText.toDecimalOrNull()?.coerceIn(0.0, 250.0)
    val turnusHours = turnusText.toDecimalOrNull()?.coerceIn(0.0, 300.0) ?: 0.0
    val secondShiftHours = secondShiftText.toDecimalOrNull()?.coerceIn(0.0, 300.0) ?: 0.0
    val grossAdjustment = adjustmentText.toDecimalOrNull()?.coerceIn(-10_000.0, 10_000.0) ?: 0.0

    val estimate = remember(
        month, evidenceEntries, coefficient, years, rateProfileId, extra, customBase,
        overtimeOverride, turnusHours, secondShiftHours, grossAdjustment
    ) {
        PublicHealthPayroll.estimate(
            month = month,
            entries = evidenceEntries,
            coefficient = coefficient,
            yearsService = years,
            rateProfileId = rateProfileId,
            extraPercent = extra,
            customBase = customBase,
            overtimeHoursOverride = overtimeOverride,
            turnusHours = turnusHours,
            secondShiftHours = secondShiftHours,
            grossAdjustment = grossAdjustment
        )
    }

    LaunchedEffect(
        institutionId, rateProfileId, roleId, coefficient, years, extra, customBase,
        overtimeOverride, turnusHours, secondShiftHours, grossAdjustment
    ) {
        settingsStore.save(
            PayrollSettings(
                institutionId = institutionId,
                rateProfileId = rateProfileId,
                roleId = roleId,
                coefficient = coefficient,
                yearsService = years,
                extraPercent = extra,
                customBase = customBase,
                overtimeHours = overtimeOverride,
                turnusHours = turnusHours,
                secondShiftHours = secondShiftHours,
                grossAdjustment = grossAdjustment
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
                        "Bruto procjena prema službenom radnom mjestu i stvarnoj Evidenciji sati.",
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
                    MonthSelector(month = month, onChange = { month = it })

                    Spacer(Modifier.height(10.dp))
                    PayrollDropdown(
                        label = "Bolnica / ustanova",
                        value = institution.name + " — " + institution.city,
                        expanded = institutionMenu,
                        onExpandedChange = { institutionMenu = it },
                        testTag = "payroll-institution-selector"
                    ) {
                        PublicHealthPayroll.institutions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                        Text(option.city, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    institutionId = option.id
                                    rateProfileId = option.defaultRateProfileId
                                    institutionMenu = false
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    PayrollDropdown(
                        label = "Obračunski profil dodataka",
                        value = rateProfile.label,
                        expanded = profileMenu,
                        onExpandedChange = { profileMenu = it },
                        testTag = "payroll-profile-selector"
                    ) {
                        PublicHealthPayroll.rateProfiles.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.label, fontWeight = FontWeight.SemiBold)
                                        Text(option.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    rateProfileId = option.id
                                    profileMenu = false
                                }
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            buildString {
                                append(rateProfile.note)
                                if (institution.verifyRegime) {
                                    append(" Za ovu ustanovu dodatno provjeri primjenu javnoslužbenog režima na svoje zaposlenje.")
                                }
                            },
                            modifier = Modifier.padding(12.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    PayrollDropdown(
                        label = "Radno mjesto",
                        value = role.label,
                        expanded = roleMenu,
                        onExpandedChange = { roleMenu = it },
                        testTag = "payroll-role-selector"
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

                    Text(role.officialName, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "Šifra ${role.code} · službeni koeficijent ${decimalText(role.coefficient)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (role.note.isNotBlank()) {
                        Text(role.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    PayrollTwoFields(
                        top = 12,
                        first = {
                            PayrollNumberField("Koeficijent", coefficientText, { coefficientText = it.take(6) }, Modifier.fillMaxWidth())
                        },
                        second = {
                            PayrollNumberField(
                                "Godine staža", yearsText,
                                { yearsText = it.filter(Char::isDigit).take(2) },
                                Modifier.fillMaxWidth(), decimal = false
                            )
                        }
                    )
                    PayrollTwoFields(
                        top = 10,
                        first = {
                            PayrollNumberField(
                                "Ručna osnovica €", customBaseText, { customBaseText = it.take(10) },
                                Modifier.fillMaxWidth(), placeholder = "Automatski"
                            )
                        },
                        second = {
                            PayrollNumberField(
                                "Dodatak po rješenju %", extraText, { extraText = it.take(6) },
                                Modifier.fillMaxWidth()
                            )
                        }
                    )

                    Text(
                        "Napredni obračunski sati",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "Prekovremeni mogu ostati prazni za automatsku procjenu iz Evidencije. Turnus i druga smjena unose se zasebno da se ne bi pogrešno kumulirali.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PayrollTwoFields(
                        top = 8,
                        first = {
                            PayrollNumberField(
                                "Prekovremeni sati", overtimeText, { overtimeText = it.take(8) },
                                Modifier.fillMaxWidth(), placeholder = "Automatski"
                            )
                        },
                        second = {
                            PayrollNumberField(
                                "Sati turnusa 5%", turnusText, { turnusText = it.take(8) },
                                Modifier.fillMaxWidth(), placeholder = "0"
                            )
                        }
                    )
                    PayrollTwoFields(
                        top = 10,
                        first = {
                            PayrollNumberField(
                                "Druga smjena 10% sati", secondShiftText, { secondShiftText = it.take(8) },
                                Modifier.fillMaxWidth(), placeholder = "0"
                            )
                        },
                        second = {
                            PayrollNumberField(
                                "Korekcija bruto €", adjustmentText, { adjustmentText = it.take(12) },
                                Modifier.fillMaxWidth(), placeholder = "0", signed = true
                            )
                        }
                    )
                    Text(
                        "Korekcija bruto služi za stavke koje se ne mogu pouzdano dobiti iz ulaza/izlaza, npr. razliku naknade godišnjeg odmora po tromjesečnom prosjeku ili bolovanje.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 7.dp)
                    )
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
                        "Noć, subota, nedjelja i blagdan dolaze iz stvarne Evidencije sati.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PayrollLine("Osnovna mjesečna bruto + staž", "Osnovica × koeficijent × staž", estimate.basicGross)
                    PayrollLine("Osnovna vrijednost prekovremenih", "${decimalText(estimate.overtimeHours)} h", estimate.overtimeBase)
                    PayrollLine("Noćni rad", minutesLabelPayroll(estimate.evidence.nightMinutes), estimate.nightAddition)
                    PayrollLine("Rad subotom", minutesLabelPayroll(estimate.evidence.saturdayMinutes), estimate.saturdayAddition)
                    PayrollLine("Rad nedjeljom", minutesLabelPayroll(estimate.evidence.sundayMinutes), estimate.sundayAddition)
                    PayrollLine("Rad blagdanom / neradnim danom", minutesLabelPayroll(estimate.evidence.holidayMinutes), estimate.holidayAddition)
                    PayrollLine("Dodatak za prekovremeni rad", "${decimalText(estimate.overtimeHours)} h", estimate.overtimeAddition)
                    if (turnusHours > 0.0) PayrollLine("Rad u turnusu", "${decimalText(turnusHours)} h · +5%", estimate.turnusAddition)
                    if (secondShiftHours > 0.0) PayrollLine("Rad u drugoj smjeni", "${decimalText(secondShiftHours)} h · +10%", estimate.secondShiftAddition)
                    if (estimate.customPercentAddition > 0.0) PayrollLine("Dodatak po rješenju / ugovoru", "${decimalText(extra)}%", estimate.customPercentAddition)
                    if (estimate.grossAdjustment != 0.0) PayrollLine("Korekcija / naknada bruto", "Ručni obračunski iznos", estimate.grossAdjustment)
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Izvori i granice procjene", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Isti službeni naziv radnog mjesta u javnim službama ima isti koeficijent prema Uredbi NN 22/2024; bolnica nema vlastiti univerzalni “bod”. " +
                            "Osnovice za 2026. dolaze iz NN 11/2026. Cijena sata i dodaci temelje se na TKU-u NN 29/2024, a turnus 5% na službenom tumačenju članka 109.",
                        modifier = Modifier.padding(top = 7.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        "KBC Rijeka ima odvojeni obračunski profil jer su na njegovim obračunskim ispravama iz 2026. potvrđeni noćni dodaci 50%; opći TKU propisuje 40%. " +
                            "Zato aplikacija ne prenosi 50% na druge bolnice bez potvrde.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        "Ovo je okvirna bruto procjena, ne obračunska isprava niti konačan neto iznos. GO, bolovanje, dežurstvo, pripravnost, porezne olakšice i posebna rješenja mogu promijeniti isplatu.",
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
private fun MonthSelector(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Mjesec obračuna", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange(month.minusMonths(1)) },
            enabled = month > YearMonth.of(2026, 1)
        ) { Icon(Icons.Outlined.ChevronLeft, "Prethodni mjesec") }
        Text(
            month.month.getDisplayName(TextStyle.FULL, Locale("hr", "HR"))
                .replaceFirstChar { it.titlecase(Locale("hr", "HR")) } + " " + month.year + ".",
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = { onChange(month.plusMonths(1)) },
            enabled = month < YearMonth.of(2026, 12)
        ) { Icon(Icons.Outlined.ChevronRight, "Sljedeći mjesec") }
    }
}

@Composable
private fun PayrollDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    testTag: String,
    menu: @Composable ColumnScope.() -> Unit
) {
    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth().testTag(testTag)
        ) {
            Text(value, modifier = Modifier.weight(1f), maxLines = 2)
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = 420.dp),
            content = menu
        )
    }
}

@Composable
private fun PayrollTwoFields(
    top: Int,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = top.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(1f)) { second() }
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
                    "Iz ${minutesLabelPayroll(estimate.evidence.workedMinutes)} stvarno evidentiranog rada."
                else "Nema evidencije za mjesec; prikazana je osnovna mjesečna bruto procjena.",
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
                PayrollMetric("Cijena sata + staž", euro(estimate.hourlyGross) + "/h", Modifier.weight(1f))
                PayrollMetric("Osnovna bruto + staž", euro(estimate.basicGross), Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PayrollMetric("Osnovica prekovremenih", euro(estimate.overtimeBase), Modifier.weight(1f))
                PayrollMetric("Dodaci i korekcije", euro(estimate.additions), Modifier.weight(1f))
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
private fun PayrollLine(label: String, hours: String, amount: Double) {
    HorizontalDivider(Modifier.padding(top = 10.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(hours, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(euro(amount), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PayrollNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    decimal: Boolean = true,
    placeholder: String = "",
    signed: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val allowed = raw.filterIndexed { index, ch ->
                ch.isDigit() || ch == ',' || ch == '.' || (signed && ch == '-' && index == 0)
            }
            onValueChange(allowed)
        },
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
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
