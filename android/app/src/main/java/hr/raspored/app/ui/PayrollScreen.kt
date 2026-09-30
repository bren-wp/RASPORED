package hr.raspored.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
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
import hr.raspored.app.data.PayrollInstitution
import hr.raspored.app.data.PayrollRegime
import hr.raspored.app.data.PayrollSettings
import hr.raspored.app.data.PayrollSettingsStore
import hr.raspored.app.data.PublicSectorPayroll
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

    var month by remember {
        mutableStateOf(
            YearMonth.now().let {
                when {
                    it.year < PublicSectorPayroll.YEAR -> YearMonth.of(PublicSectorPayroll.YEAR, 1)
                    it.year > PublicSectorPayroll.YEAR -> YearMonth.of(PublicSectorPayroll.YEAR, 12)
                    else -> it
                }
            }
        )
    }
    var county by remember { mutableStateOf(initial.county) }
    var residence by remember { mutableStateOf(initial.residence) }
    var taxLowerText by remember { mutableStateOf(decimalText(initial.taxLower)) }
    var taxHigherText by remember { mutableStateOf(decimalText(initial.taxHigher)) }
    var sector by remember { mutableStateOf(initial.sector) }
    var institutionName by remember { mutableStateOf(initial.institution) }
    var manualInstitution by remember {
        mutableStateOf(PublicSectorPayroll.institutions.none { it.name == initial.institution })
    }
    var customInstitutionText by remember {
        mutableStateOf(if (manualInstitution) initial.institution else "")
    }
    var regimeId by remember { mutableStateOf(initial.regimeId) }
    var roleId by remember { mutableStateOf(initial.roleId) }
    var coefficientText by remember { mutableStateOf(decimalText(initial.coefficient)) }
    var yearsText by remember { mutableStateOf(initial.yearsService.toString()) }
    var personalAllowanceText by remember { mutableStateOf(decimalText(initial.personalAllowance)) }
    var extraText by remember { mutableStateOf(decimalText(initial.extraPercent)) }
    var customBaseText by remember { mutableStateOf(initial.customBase?.let(::decimalText).orEmpty()) }
    var secondShift by remember { mutableStateOf(initial.secondShift) }
    var turnus by remember { mutableStateOf(initial.turnus) }

    val taxLocality = PublicSectorPayroll.taxLocalities.firstOrNull { it.name == residence }
    val regime = PublicSectorPayroll.regime(regimeId)
    val sectorInstitutions = remember(sector, county) {
        payrollInstitutionsFor(sector, county)
    }
    val roles = remember(regimeId) { PublicSectorPayroll.rolesFor(regimeId) }
    val role = roles.firstOrNull { it.id == roleId } ?: roles.firstOrNull()
        ?: PublicSectorPayroll.role("manual", regimeId)

    val coefficient = coefficientText.toDecimalOrNull()?.coerceIn(0.1, 10.0)
        ?: role.coefficient ?: 1.0
    val years = yearsText.toIntOrNull()?.coerceIn(0, 60) ?: 0
    val personalAllowance = personalAllowanceText.toDecimalOrNull()
        ?.coerceIn(0.0, 10_000.0) ?: PublicSectorPayroll.BASIC_PERSONAL_ALLOWANCE
    val taxLower = taxLowerText.toDecimalOrNull()?.coerceIn(0.0, 50.0) ?: 20.0
    val taxHigher = taxHigherText.toDecimalOrNull()?.coerceIn(0.0, 50.0) ?: 30.0
    val extra = extraText.toDecimalOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
    val customBase = customBaseText.toDecimalOrNull()
        ?.takeIf { it > 0.0 }?.coerceAtMost(10_000.0)

    val estimate = remember(
        month, evidenceEntries, regimeId, coefficient, years, personalAllowance,
        taxLower, taxHigher, extra, secondShift, turnus, customBase
    ) {
        PublicSectorPayroll.estimate(
            month = month,
            entries = evidenceEntries,
            regimeId = regimeId,
            coefficient = coefficient,
            yearsService = years,
            personalAllowance = personalAllowance,
            taxLower = taxLower,
            taxHigher = taxHigher,
            extraPercent = extra,
            secondShift = secondShift,
            turnus = turnus,
            customBase = customBase
        )
    }

    LaunchedEffect(
        county, residence, taxLower, taxHigher, sector, institutionName, manualInstitution,
        customInstitutionText, regimeId, roleId, coefficient, years, personalAllowance,
        extra, secondShift, turnus, customBase
    ) {
        settingsStore.save(
            PayrollSettings(
                county = county,
                residence = residence,
                taxLower = taxLower,
                taxHigher = taxHigher,
                sector = sector,
                institution = if (manualInstitution) {
                    customInstitutionText.trim().ifBlank { genericInstitutionLabel(sector) }
                } else {
                    institutionName
                },
                regimeId = regimeId,
                roleId = roleId,
                coefficient = coefficient,
                yearsService = years,
                personalAllowance = personalAllowance,
                extraPercent = extra,
                secondShift = secondShift,
                turnus = turnus,
                customBase = customBase
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-payroll")
            .padding(horizontal = 16.dp),
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
                        "Javni sektor RH · službeni parametri gdje postoje · stvarna Evidencija sati",
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
                    MonthSelector(
                        month = month,
                        onPrevious = {
                            if (month > YearMonth.of(PublicSectorPayroll.YEAR, 1)) {
                                month = month.minusMonths(1)
                            }
                        },
                        onNext = {
                            if (month < YearMonth.of(PublicSectorPayroll.YEAR, 12)) {
                                month = month.plusMonths(1)
                            }
                        }
                    )

                    PayrollDropdown(
                        label = "Županija ustanove",
                        value = county,
                        options = PublicSectorPayroll.counties,
                        modifier = Modifier.padding(top = 10.dp),
                        testTag = "payroll-county"
                    ) { selected ->
                        county = selected
                        val candidates = payrollInstitutionsFor(sector, selected)
                        val selectedInstitution = candidates.firstOrNull()
                        manualInstitution = selectedInstitution == null
                        customInstitutionText = ""
                        institutionName = selectedInstitution?.name ?: genericInstitutionLabel(sector)
                        regimeId = selectedInstitution?.regimeId
                            ?: PublicSectorPayroll.defaultRegimeForSector(sector).id
                        val nextRole = PublicSectorPayroll.rolesFor(regimeId).firstOrNull()
                        roleId = nextRole?.id ?: "manual"
                        coefficientText = decimalText(nextRole?.coefficient ?: 1.0)
                        secondShift = false
                        turnus = false
                    }

                    PayrollDropdown(
                        label = "Grad/općina prebivališta — porez",
                        value = taxLocality?.let {
                            it.name + " · " + decimalText(it.lowerRate) + "% / " +
                                decimalText(it.higherRate) + "%"
                        } ?: "Drugo mjesto — ručni unos",
                        options = PublicSectorPayroll.taxLocalities.map {
                            it.name + " · " + decimalText(it.lowerRate) + "% / " +
                                decimalText(it.higherRate) + "%"
                        } + "Drugo mjesto — ručni unos",
                        modifier = Modifier.padding(top = 10.dp),
                        testTag = "payroll-residence"
                    ) { selected ->
                        val chosen = PublicSectorPayroll.taxLocalities.firstOrNull {
                            selected.startsWith(it.name + " ·")
                        }
                        residence = chosen?.name ?: "Drugo"
                        chosen?.let {
                            taxLowerText = decimalText(it.lowerRate)
                            taxHigherText = decimalText(it.higherRate)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PayrollNumberField(
                            label = "Niža porezna stopa %",
                            value = taxLowerText,
                            onValueChange = {
                                if (taxLocality == null) taxLowerText = it.take(6)
                            },
                            modifier = Modifier.weight(1f),
                            readOnly = taxLocality != null
                        )
                        PayrollNumberField(
                            label = "Viša porezna stopa %",
                            value = taxHigherText,
                            onValueChange = {
                                if (taxLocality == null) taxHigherText = it.take(6)
                            },
                            modifier = Modifier.weight(1f),
                            readOnly = taxLocality != null
                        )
                    }
                    Text(
                        taxLocality?.let {
                            it.name + ": " + decimalText(it.lowerRate) + "% / " +
                                decimalText(it.higherRate) + "% · " + it.source
                        } ?: "Za drugo mjesto unesi stope prema važećoj odluci grada/općine.",
                        modifier = Modifier.padding(top = 5.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PayrollDropdown(
                        label = "Sektor",
                        value = sector,
                        options = PublicSectorPayroll.sectors,
                        modifier = Modifier.padding(top = 12.dp),
                        testTag = "payroll-sector"
                    ) { selected ->
                        sector = selected
                        val candidates = payrollInstitutionsFor(selected, county)
                        val selectedInstitution = candidates.firstOrNull()
                        manualInstitution = selectedInstitution == null
                        customInstitutionText = ""
                        institutionName = selectedInstitution?.name ?: genericInstitutionLabel(selected)
                        regimeId = selectedInstitution?.regimeId
                            ?: PublicSectorPayroll.defaultRegimeForSector(selected).id
                        val nextRole = PublicSectorPayroll.rolesFor(regimeId).firstOrNull()
                        roleId = nextRole?.id ?: "manual"
                        coefficientText = decimalText(nextRole?.coefficient ?: 1.0)
                        secondShift = false
                        turnus = false
                    }

                    PayrollDropdown(
                        label = "Ustanova / tijelo",
                        value = if (manualInstitution) genericInstitutionLabel(sector) else institutionName,
                        options = sectorInstitutions.map { it.name } + genericInstitutionLabel(sector),
                        modifier = Modifier.padding(top = 10.dp),
                        testTag = "payroll-institution"
                    ) { selected ->
                        val selectedInstitution = sectorInstitutions.firstOrNull { it.name == selected }
                        manualInstitution = selectedInstitution == null
                        customInstitutionText = ""
                        institutionName = selectedInstitution?.name ?: genericInstitutionLabel(sector)
                        regimeId = selectedInstitution?.regimeId
                            ?: PublicSectorPayroll.defaultRegimeForSector(sector).id
                        val nextRole = PublicSectorPayroll.rolesFor(regimeId).firstOrNull()
                        roleId = nextRole?.id ?: "manual"
                        coefficientText = decimalText(nextRole?.coefficient ?: 1.0)
                        secondShift = false
                        turnus = false
                    }

                    if (manualInstitution) {
                        OutlinedTextField(
                            value = customInstitutionText,
                            onValueChange = { customInstitutionText = it.take(160) },
                            label = { Text("Naziv ustanove") },
                            placeholder = { Text("Upiši naziv ustanove") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }

                    PayrollDropdown(
                        label = "Radno mjesto",
                        value = role.label,
                        options = roles.map { it.label },
                        modifier = Modifier.padding(top = 12.dp),
                        testTag = "payroll-role-selector"
                    ) { selected ->
                        val next = roles.firstOrNull { it.label == selected }
                        if (next != null) {
                            roleId = next.id
                            next.coefficient?.let { coefficientText = decimalText(it) }
                        }
                    }

                    Text(
                        role.officialName + " · " + role.code,
                        modifier = Modifier.padding(top = 5.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
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
                            placeholder = if (regime.baseType == "manual") "Obavezno" else "Automatski"
                        )
                        PayrollNumberField(
                            label = "Osobni odbitak €",
                            value = personalAllowanceText,
                            onValueChange = { personalAllowanceText = it.take(10) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    PayrollNumberField(
                        label = "Dodatak po rješenju / ugovoru %",
                        value = extraText,
                        onValueChange = { extraText = it.take(6) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )

                    PayrollToggle(
                        title = "Druga smjena",
                        subtitle = if (regime.rates.secondShift != null) {
                            "Automatski preset: +" + percent(regime.rates.secondShift) +
                                " za odgovarajuće sate."
                        } else {
                            "Nema potvrđene univerzalne stope za ovaj režim."
                        },
                        checked = secondShift,
                        enabled = regime.rates.secondShift != null,
                        onChecked = { secondShift = it },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    PayrollToggle(
                        title = "Turnus",
                        subtitle = if (regime.rates.turnus != null) {
                            "Automatski preset: +" + percent(regime.rates.turnus) +
                                "; ne zbraja se na iste sate s drugom smjenom."
                        } else {
                            "Turnus nije automatski pretpostavljen za ovaj režim."
                        },
                        checked = turnus,
                        enabled = regime.rates.turnus != null,
                        onChecked = { turnus = it },
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(regime.label, fontWeight = FontWeight.Bold)
                            if (regime.note.isNotBlank()) {
                                Text(
                                    regime.note,
                                    modifier = Modifier.padding(top = 3.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (regime.baseType == "manual" && customBase == null) {
                                Text(
                                    "Za ovaj sektor nema jedne nacionalne osnovice. " +
                                        "Unesi osnovicu iz važećeg kolektivnog ugovora ili odluke.",
                                    modifier = Modifier.padding(top = 5.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        item { PayrollResultCard(estimate, years, estimate.base > 0.0) }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Obračunski sati i dodaci", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Koristi stvarnu Evidenciju sati. Planirani raspored se ne pretvara automatski u odrađene sate.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PayrollLine(
                        "Ukupno evidentirano",
                        minutesLabelPayroll(estimate.evidence.workedMinutes),
                        null
                    )
                    if (regime.rates.night != null) {
                        PayrollLine(
                            "Noćni rad 22:00–06:00",
                            minutesLabelPayroll(estimate.evidence.nightMinutes),
                            estimate.nightAddition
                        )
                    }
                    if (regime.rates.saturday != null) {
                        PayrollLine(
                            "Rad subotom",
                            minutesLabelPayroll(estimate.evidence.saturdayMinutes),
                            estimate.saturdayAddition
                        )
                    }
                    if (regime.rates.sunday != null) {
                        PayrollLine(
                            "Rad nedjeljom",
                            minutesLabelPayroll(estimate.evidence.sundayMinutes),
                            estimate.sundayAddition
                        )
                    }
                    if (regime.rates.holiday != null) {
                        PayrollLine(
                            "Rad blagdanom / neradnim danom",
                            minutesLabelPayroll(estimate.evidence.holidayMinutes),
                            estimate.holidayAddition
                        )
                    }
                    if (regime.rates.overtime != null) {
                        PayrollLine(
                            "Prekovremeni iznad mjesečnog fonda",
                            minutesLabelPayroll(estimate.evidence.overtimeMinutes),
                            estimate.overtimeAddition
                        )
                    }
                    if (secondShift && regime.rates.secondShift != null) {
                        PayrollLine(
                            "Druga smjena 14:00–22:00",
                            minutesLabelPayroll(estimate.evidence.secondShiftMinutes),
                            estimate.secondShiftAddition
                        )
                    }
                    if (turnus && regime.rates.turnus != null) {
                        PayrollLine(
                            "Rad u turnusu",
                            "prema evidentiranim satima",
                            estimate.turnusAddition
                        )
                    }
                    if (estimate.customAddition > 0.0) {
                        PayrollLine(
                            "Dodatak po rješenju / ugovoru",
                            decimalText(extra) + "%",
                            estimate.customAddition
                        )
                    }
                    PayrollLine(
                        "Mirovinski doprinosi iz bruto procjene",
                        "20%",
                        -estimate.pensionContribution
                    )
                    PayrollLine(
                        "Okvirni porez na dohodak",
                        decimalText(taxLower) + "% / " + decimalText(taxHigher) + "%",
                        -estimate.incomeTax
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Točnost i izvori", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Javne i državne službe koriste objavljene osnovice 2026. i službene " +
                            "koeficijente kada je radno mjesto jednoznačno. Porez se bira prema " +
                            "gradu/općini prebivališta, ne prema županiji poslodavca.",
                        modifier = Modifier.padding(top = 7.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        automaticRatesText(regime),
                        modifier = Modifier.padding(top = 7.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        "Vrtići, lokalna uprava i dio vatrogasnih prava ovise o lokalnim aktima " +
                            "ili kolektivnom ugovoru. Aplikacija tada traži ručni unos umjesto " +
                            "izmišljanja vrijednosti.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        "Okvirni neto nije obračunska isprava. Ne uključuje automatski bolovanje, " +
                            "godišnji odmor po prosjeku, dežurstva, pripravnost, posebne uvjete rada, " +
                            "prijevoz, neoporezive primitke ni obustave.",
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        "Izvori: NN 22/2024, NN 11/2026, NN 29/2024, NN 4/2025, " +
                            "NN 85/2024, NN 152/2024 i Ministarstvo zdravstva.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Mjesec obračuna", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(
            onClick = onPrevious,
            enabled = month > YearMonth.of(PublicSectorPayroll.YEAR, 1)
        ) {
            Icon(Icons.Outlined.ChevronLeft, "Prethodni mjesec")
        }
        Text(
            month.month.getDisplayName(TextStyle.FULL, Locale("hr", "HR"))
                .replaceFirstChar { it.titlecase(Locale("hr", "HR")) } + " " + month.year + ".",
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = onNext,
            enabled = month < YearMonth.of(PublicSectorPayroll.YEAR, 12)
        ) {
            Icon(Icons.Outlined.ChevronRight, "Sljedeći mjesec")
        }
    }
}

@Composable
private fun PayrollDropdown(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Box(Modifier.padding(top = 5.dp)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(value, modifier = Modifier.weight(1f), maxLines = 2)
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 440.dp)
            ) {
                options.distinct().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PayrollToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChecked(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun PayrollResultCard(
    estimate: PayrollEstimate,
    years: Int,
    baseAvailable: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Procijenjeni bruto",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                if (baseAvailable) euro(estimate.estimatedGross) else "Unesi osnovicu",
                fontSize = if (baseAvailable) 38.sp else 25.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (baseAvailable) RasporedTokens.Cyan else MaterialTheme.colorScheme.error
            )
            Text(
                if (estimate.evidence.workedMinutes > 0L) {
                    "Iz " + minutesLabelPayroll(estimate.evidence.workedMinutes) +
                        " evidentiranog rada."
                } else {
                    "Nema evidencije za mjesec; dodaci iz rada nisu pretpostavljeni."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(Modifier.padding(13.dp)) {
                    Text(
                        "Okvirni neto",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        if (baseAvailable) euro(estimate.estimatedNet) else "—",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Neto koristi uneseni osobni odbitak i porez prebivališta.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PayrollMetric(
                    "Osnovica",
                    if (baseAvailable) euro(estimate.base) else "ručni unos",
                    Modifier.weight(1f)
                )
                PayrollMetric(
                    "Koeficijent",
                    decimalText(estimate.coefficient),
                    Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PayrollMetric(
                    "Staž",
                    years.toString() + " god. · +" + decimalText(years * 0.5) + "%",
                    Modifier.weight(1f)
                )
                PayrollMetric(
                    "Mjesečni fond",
                    estimate.monthlyFundHours.toString() + " h",
                    Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PayrollMetric(
                    "Bruto satnica",
                    if (baseAvailable) euro(estimate.hourlyGross) else "—",
                    Modifier.weight(1f)
                )
                PayrollMetric(
                    "Radni dani",
                    if (estimate.evidence.workedDays > 0) {
                        estimate.evidence.workedDays.toString()
                    } else {
                        (estimate.monthlyFundHours / 8).toString() + " plan."
                    },
                    Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PayrollMetric(
                    "Prosj. bruto / dan",
                    if (baseAvailable) euro(estimate.dailyGross) else "—",
                    Modifier.weight(1f)
                )
                PayrollMetric(
                    "Prosj. neto / dan",
                    if (baseAvailable) euro(estimate.dailyNet) else "—",
                    Modifier.weight(1f)
                )
            }
            Text(
                "Vrijednost po radnom danu nije službena dnevnica za službeni put.",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PayrollMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(11.dp)) {
            Text(
                label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PayrollLine(label: String, detail: String, amount: Double?) {
    HorizontalDivider(Modifier.padding(top = 10.dp))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(amount?.let(::signedEuro) ?: "—", fontWeight = FontWeight.Bold)
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
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = true,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier
    )
}

private fun payrollInstitutionsFor(
    sector: String,
    county: String
): List<PayrollInstitution> = PublicSectorPayroll.institutionsFor(sector, county)

private fun genericInstitutionLabel(sector: String): String = when (sector) {
    "Zdravstvo" -> "Druga zdravstvena ustanova"
    "Školstvo i obrazovanje" -> "Škola / učenički dom — ručni naziv"
    "Policija" -> "MUP / policijska uprava ili postaja"
    "Vatrogastvo" -> "Javna vatrogasna postrojba"
    "Vrtići" -> "Gradski/općinski vrtić — ručni naziv"
    "Lokalna i regionalna uprava" -> "Grad/općina/županija — ručni naziv"
    "Državna služba" -> "Ministarstvo / državno tijelo — ručni naziv"
    "Socijalna skrb" -> "Javna ustanova socijalne skrbi — ručni naziv"
    "Kultura" -> "Javna ustanova u kulturi — ručni naziv"
    "Znanost i visoko obrazovanje" -> "Javna visokoškolska/znanstvena ustanova — ručni naziv"
    else -> "Druga javna ustanova — ručni naziv"
}

private fun automaticRatesText(regime: PayrollRegime): String {
    val rates = buildList {
        regime.rates.night?.let { add("noć " + percent(it)) }
        regime.rates.saturday?.let { add("subota " + percent(it)) }
        regime.rates.sunday?.let { add("nedjelja " + percent(it)) }
        regime.rates.holiday?.let { add("blagdan " + percent(it)) }
        regime.rates.overtime?.let { add("prekovremeni " + percent(it)) }
        regime.rates.secondShift?.let { add("druga smjena " + percent(it)) }
        regime.rates.turnus?.let { add("turnus " + percent(it)) }
    }
    return if (rates.isEmpty()) {
        "Za ovaj režim dodaci nisu automatski pretpostavljeni; provjeri kolektivni ugovor ili akt poslodavca."
    } else {
        "Automatski preset dodataka: " + rates.joinToString(", ") + "."
    }
}

private fun percent(value: Double): String = decimalText(value * 100) + "%"

private fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()

private fun decimalText(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
            .replace('.', ',')
    }

private fun euro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("hr", "HR")).apply {
        currency = java.util.Currency.getInstance("EUR")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)

private fun signedEuro(value: Double): String =
    if (value < 0.0) "−" + euro(-value) else euro(value)

private fun minutesLabelPayroll(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0L)
    val h = safe / 60L
    val remainder = safe % 60L
    return if (remainder == 0L) {
        h.toString() + " h"
    } else {
        h.toString() + " h " + remainder.toString().padStart(2, '0') + " min"
    }
}
