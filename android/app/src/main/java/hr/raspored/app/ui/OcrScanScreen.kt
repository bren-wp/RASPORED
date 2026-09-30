package hr.raspored.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.data.ScheduleStore
import hr.raspored.app.ocr.RecognizedSchedule
import hr.raspored.app.ocr.RecognizedScheduleRow
import hr.raspored.app.ocr.RecognitionCellReview
import hr.raspored.app.ocr.AiScheduleVerifier
import hr.raspored.app.ocr.ScheduleOcrEngine
import hr.raspored.app.ocr.createOcrCaptureUri
import hr.raspored.app.ocr.loadBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

private enum class OcrPhase { Idle, Processing, Success, Error }

@Composable
internal fun OcrScanScreen(
    defaultMonth: YearMonth,
    allowTeamImport: Boolean = true,
    remoteAccountToken: String? = null,
    onSaveTeamSchedules: (YearMonth, List<hr.raspored.app.ocr.RecognizedScheduleRow>) -> Unit = { _, _ -> },
    onSaveSchedule: (YearMonth, Map<Int, String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var result by remember { mutableStateOf<RecognizedSchedule?>(null) }
    var phase by remember { mutableStateOf(OcrPhase.Idle) }
    var message by remember { mutableStateOf("Slikaj raspored ili odaberi fotografiju iz galerije.") }
    var selectedRow by remember { mutableIntStateOf(-1) }
    var selectedMonth by remember { mutableStateOf(defaultMonth) }
    var employeeMenu by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    val reviewCells = remember { mutableStateListOf<RecognitionCellReview>() }
    var reviewConflictIndex by remember { mutableIntStateOf(-1) }
    var customConflictCode by remember { mutableStateOf("") }
    var ocrGeneration by remember { mutableIntStateOf(0) }
    val editedShifts = remember { mutableStateMapOf<Int, String>() }

    fun applyResult(recognized: RecognizedSchedule, keepReviewCells: Boolean = false) {
        if (!keepReviewCells) {
            reviewCells.clear()
            reviewConflictIndex = -1
            customConflictCode = ""
        }
        result = recognized
        selectedMonth = recognized.month ?: defaultMonth
        editedShifts.clear()
        editMode = false
        val totalRecognizedDays = recognized.rows.sumOf { it.dayShifts.size }
        val emptyRows = recognized.rows.count { it.dayShifts.isEmpty() }
        val numberedRows = recognized.rows
            .mapNotNull { it.rowNumber }
            .filter { it in 1..100 }
            .distinct()
            .sorted()
        val numberedExpectedRows = if (
            numberedRows.size >= 5 &&
            numberedRows.firstOrNull()?.let { it <= 3 } == true
        ) {
            val first = numberedRows.first()
            val last = numberedRows.last()
            (last - first + 1).takeIf { it >= 8 }
        } else {
            null
        }
        val expectedRows = listOfNotNull(
            recognized.expectedRowCount,
            numberedExpectedRows
        ).maxOrNull()
        val foundRows = recognized.rows.size
        val severeIncomplete = expectedRows?.let { expected ->
            foundRows * 100 < expected * 65
        } == true
        val rosterWarning = expectedRows?.let { expected ->
            if (foundRows * 100 < expected * 88) {
                " Upozorenje: tablica izgleda kao raspored s približno $expected redaka, a pouzdano je očitano $foundRows. Za potpuni uvoz ponovi fotografiju tako da cijela tablica i svi stupci ostanu oštri."
            } else {
                ""
            }
        }.orEmpty()
        when {
            recognized.rows.isEmpty() -> {
                selectedRow = -1
                phase = OcrPhase.Error
                message = "Nije pronađena osoba s oznakama D, N, GO, BO, PD ili SD. Pokušaj obuhvatiti cijelu tablicu, posebno zaglavlje s brojevima dana."
            }
            totalRecognizedDays == 0 -> {
                selectedRow = -1
                phase = OcrPhase.Error
                message = "Osobe su pronađene, ali stupci dana nisu dovoljno pouzdano očitani. Ponovi fotografiju tako da se vide svi brojevi dana i cijela širina tablice."
            }
            severeIncomplete -> {
                selectedRow = -1
                phase = OcrPhase.Error
                message = "Skeniranje nije dovoljno potpuno." + rosterWarning +
                    " Rezultat nije označen kao dovršen kako se ne bi tiho izgubile osobe ili smjene."
            }
            recognized.rows.size == 1 -> {
                selectedRow = 0
                editedShifts.putAll(recognized.rows.first().dayShifts)
                phase = OcrPhase.Success
                val recognizedDays = recognized.rows.first().dayShifts.size
                message = "Prepoznata je 1 osoba i $recognizedDays oznaka dana. Provjeri raspored prije spremanja." +
                    if (recognized.month == null) " Mjesec nije pouzdano prepoznat; provjeri ga." else ""
            }
            else -> {
                selectedRow = -1
                phase = OcrPhase.Success
                val countLabel = if (recognized.rows.size in 2..4) "${recognized.rows.size} osobe" else "${recognized.rows.size} osoba"
                message = "Prepoznate su $countLabel i ukupno $totalRecognizedDays oznaka dana." +
                    rosterWarning +
                    if (emptyRows > 0) " $emptyRows numeriranih redaka nema pouzdano očitanu smjenu; provjeri ih." else "" +
                    " Odaberi ime i prezime osobe čiji raspored želiš uvesti." +
                    if (recognized.month == null) " Mjesec nije pouzdano prepoznat; provjeri ga." else ""
            }
        }

    }

    fun process(source: Bitmap, generation: Int) {
        if (ocrGeneration != generation) {
            source.recycle()
            return
        }
        bitmap = source
        result = null
        selectedRow = -1
        editedShifts.clear()
        editMode = false
        employeeMenu = false
        phase = OcrPhase.Processing
        message = "Automatsko prepoznavanje..."
        ScheduleOcrEngine.recognize(
            bitmap = source,
            onSuccess = { recognized ->
                if (ocrGeneration == generation) {
                    applyResult(recognized)
                } else if (!source.isRecycled) {
                    source.recycle()
                }
            },
            onError = {
                if (ocrGeneration == generation) {
                    phase = OcrPhase.Error
                    message = "Prepoznavanje nije uspjelo. Pokušaj ponovno ili odaberi drugu fotografiju."
                } else if (!source.isRecycled) {
                    source.recycle()
                }
            }
        )
    }

    fun loadAndProcess(uri: android.net.Uri, errorMessage: String) {
        val previousBitmap = bitmap
        val previousWasProcessing = phase == OcrPhase.Processing
        val generation = ocrGeneration + 1
        ocrGeneration = generation
        if (!previousWasProcessing && previousBitmap != null && !previousBitmap.isRecycled) {
            previousBitmap.recycle()
            if (bitmap === previousBitmap) bitmap = null
        }
        result = null
        selectedRow = -1
        editedShifts.clear()
        editMode = false
        employeeMenu = false
        phase = OcrPhase.Processing
        message = "Učitavanje fotografije..."
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
            if (ocrGeneration != generation) {
                loaded?.recycle()
                return@launch
            }
            if (loaded != null) {
                process(loaded, generation)
            } else {
                phase = OcrPhase.Error
                message = errorMessage
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ocrGeneration += 1
            if (phase != OcrPhase.Processing) {
                bitmap?.takeIf { !it.isRecycled }?.recycle()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            loadAndProcess(uri, "Snimljenu fotografiju nije moguće otvoriti.")
        }
    }

    fun launchCamera() {
        runCatching { createOcrCaptureUri(context) }
            .onSuccess { uri ->
                cameraUri = uri
                cameraLauncher.launch(uri)
            }
            .onFailure {
                phase = OcrPhase.Error
                message = "Kameru nije moguće pripremiti. Pokušaj ponovno."
            }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            loadAndProcess(uri, "Fotografiju nije moguće otvoriti.")
        }
    }

    val activeRow = result?.rows?.getOrNull(selectedRow)
    val recognizedMonth = selectedMonth
    val unresolvedConflicts = reviewCells.count { it.conflict && !it.manuallyConfirmed }

    fun rowMatchesConflict(row: RecognizedScheduleRow, conflict: RecognitionCellReview): Boolean =
        conflict.employeeRow?.let { row.rowNumber == it }
            ?: row.name.trim().equals(conflict.employeeName.trim(), ignoreCase = true)

    fun resolveConflict(selected: String?) {
        val index = reviewConflictIndex
        val conflict = reviewCells.getOrNull(index) ?: return
        val normalized = selected?.takeIf { it.isNotBlank() }?.let { ScheduleStore.normalizeCode(it) }
        if (!selected.isNullOrBlank() && normalized == null) {
            message = "Oznaka nije valjana. Koristi 1–8 slova ili brojki bez razmaka."
            return
        }

        reviewCells[index] = conflict.copy(
            selectedCode = normalized,
            source = "manual",
            conflict = false,
            manuallyConfirmed = true
        )

        result = result?.let { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (!rowMatchesConflict(row, conflict)) {
                        row
                    } else {
                        val shifts = row.dayShifts.toMutableMap()
                        if (normalized == null) shifts.remove(conflict.day) else shifts[conflict.day] = normalized
                        row.copy(dayShifts = shifts.toSortedMap())
                    }
                }
            )
        }

        result?.rows?.getOrNull(selectedRow)?.takeIf { rowMatchesConflict(it, conflict) }?.let { row ->
            editedShifts.clear()
            editedShifts.putAll(row.dayShifts)
        }

        reviewConflictIndex = reviewCells.indexOfFirst { it.conflict && !it.manuallyConfirmed }
        customConflictCode = ""
        val remaining = reviewCells.count { it.conflict && !it.manuallyConfirmed }
        message = if (remaining == 0) {
            "Raspored je spreman za uvoz. Svi AI/OCR konflikti su ručno potvrđeni."
        } else {
            "Za provjeru je ostalo " + remaining + " nejasnih stavki."
        }
    }
    val previewAspect = bitmap
        ?.takeIf { !it.isRecycled && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
        ?.coerceIn(0.72f, 2.20f)
        ?: 1.30f

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen-scan").padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Skeniraj raspored", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Slikaj cijelu tablicu ili učitaj fotografiju. Važno je da su vidljivi svi redci osoba i zaglavlje sa svim danima.",
                        color = RasporedTokens.Slate
                    )
                }
                IconButton(onClick = { helpOpen = true }) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Pomoć za skeniranje", tint = RasporedTokens.Slate)
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(RasporedTokens.RadiusLarge),
                color = Color(0xFF755E49),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 430.dp)
                            .aspectRatio(previewAspect)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE7EAEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Fotografija rasporeda za OCR",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.DocumentScanner,
                                    contentDescription = null,
                                    tint = RasporedTokens.Navy,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Raspored nije učitan", color = RasporedTokens.Navy)
                            }
                        }
                        ScanFrame()
                    }

                    if (bitmap != null) {
                        Text(
                            "OCR obrađuje cijelu fotografiju. U plavom okviru mora biti vidljiv cijeli raspored: sva imena i svi dani mjeseca.",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White)
                            Spacer(Modifier.width(7.dp))
                            Text("Ponovno skeniraj", color = Color.White, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Image, null, tint = Color.White)
                            Spacer(Modifier.width(7.dp))
                            Text("Odaberi iz galerije", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xE60B1F44),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (phase) {
                                    OcrPhase.Processing -> CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = RasporedTokens.Cyan
                                    )
                                    OcrPhase.Success -> Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF7CE6B9))
                                    OcrPhase.Error -> Icon(Icons.Outlined.ErrorOutline, null, tint = Color(0xFFFF9A9A))
                                    OcrPhase.Idle -> Icon(Icons.Outlined.Info, null, tint = Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(message, color = Color.White, fontSize = 12.sp)
                            }
                            if (phase == OcrPhase.Processing) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                                    color = RasporedTokens.Cyan
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Odaberi osobu", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (allowTeamImport) {
                            "Odaberi jednu osobu za osobni kalendar. Ako radiš rasporede za tim, možeš spremiti sve pouzdano prepoznate djelatnike kao odvojene lokalne rasporede bez registracije."
                        } else {
                            "Ako raspored sadrži više osoba, odaberi samo jednu osobu čiji će se raspored uvesti."
                        },
                        color = RasporedTokens.Slate,
                        fontSize = 13.sp
                    )
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        OutlinedButton(
                            onClick = { if (!result?.rows.isNullOrEmpty()) employeeMenu = true },
                            enabled = !result?.rows.isNullOrEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Person, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                activeRow?.let { row -> (row.rowNumber?.let { number -> number.toString() + ". " } ?: "") + row.name }
                                    ?: if ((result?.rows?.size ?: 0) > 1) "Odaberi ime i prezime" else "Nema prepoznate osobe",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Outlined.ExpandMore, null)
                        }
                        DropdownMenu(
                            expanded = employeeMenu,
                            onDismissRequest = { employeeMenu = false }
                        ) {
                            result?.rows?.forEachIndexed { index, row ->
                                DropdownMenuItem(
                                    enabled = true,
                                    text = {
                                        Column {
                                            Text(
                                                (row.rowNumber?.let { number -> number.toString() + ". " } ?: "") + row.name,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                row.dayShifts.size.toString() + " prepoznatih dana",
                                                fontSize = 11.sp,
                                                color = RasporedTokens.Slate
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedRow = index
                                        editedShifts.clear()
                                        editedShifts.putAll(row.dayShifts)
                                        editMode = false
                                        employeeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mjesec rasporeda", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                            Icon(Icons.Outlined.ChevronLeft, "Prethodni mjesec")
                        }
                        Text(
                            selectedMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("hr", "HR"))
                                .replaceFirstChar { it.titlecase(java.util.Locale("hr", "HR")) } + " " + selectedMonth.year + ".",
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                            Icon(Icons.Outlined.ChevronRight, "Sljedeći mjesec")
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Provjera rasporeda", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Pregledaj prepoznate smjene i dodirni oznaku za ispravak.",
                                color = RasporedTokens.Slate,
                                fontSize = 12.sp
                            )
                        }
                        if (editedShifts.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFD9F9EC)
                            ) {
                                Text(
                                    "✓ " + editedShifts.size + " oznaka",
                                    color = Color(0xFF07865F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    if (editedShifts.isEmpty()) {
                        Text(
                            "Nakon OCR prepoznavanja ovdje će se prikazati raspored odabrane osobe.",
                            color = RasporedTokens.Slate,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        val emptyDays = recognizedMonth.lengthOfMonth() - editedShifts.keys.count {
                            it in 1..recognizedMonth.lengthOfMonth()
                        }
                        Text(
                            "Prepoznato: " + editedShifts.size + " oznaka · bez oznake: " +
                                emptyDays.coerceAtLeast(0) + " dana. Prazan dan ostaje bez smjene.",
                            modifier = Modifier.padding(top = 8.dp),
                            color = RasporedTokens.Slate,
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..recognizedMonth.lengthOfMonth()).forEach { day ->
                                val code = editedShifts[day].orEmpty()
                                RecognizedDay(
                                    day = day,
                                    month = recognizedMonth,
                                    code = code,
                                    enabled = editMode,
                                    onClick = {
                                        val next = nextShiftCode(code)
                                        if (next.isBlank()) editedShifts.remove(day) else editedShifts[day] = next
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (selectedRow >= 0) editMode = !editMode },
                            enabled = selectedRow >= 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(if (editMode) Icons.Outlined.Check else Icons.Outlined.Edit, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (editMode) "Završi uređivanje" else "Uredi")
                        }
                        OutlinedButton(
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.DocumentScanner, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Ponovno skeniraj")
                        }
                    }
                    if (remoteAccountToken != null && bitmap != null) {
                        OutlinedButton(
                            onClick = {
                                val source = bitmap ?: return@OutlinedButton
                                if (source.isRecycled || aiBusy) return@OutlinedButton
                                val local = result
                                aiBusy = true
                                message = "AI provjera cijele tablice..."
                                phase = OcrPhase.Processing
                                scope.launch {
                                    val outcome = runCatching {
                                        withContext(Dispatchers.IO) {
                                            AiScheduleVerifier.verify(
                                                bitmap = source,
                                                token = remoteAccountToken,
                                                monthHint = local?.month ?: selectedMonth,
                                                localOcrText = local?.rawText.orEmpty()
                                            )
                                        }
                                    }
                                    outcome.onSuccess { ai ->
                                        val mergedResult = mergeForAiReview(local, ai)
                                        applyResult(mergedResult.schedule, keepReviewCells = true)
                                        reviewCells.clear()
                                        reviewCells.addAll(mergedResult.cells)
                                        reviewConflictIndex = -1
                                        val conflictCount = mergedResult.cells.count { it.conflict }
                                        val conflictText = if (conflictCount > 0) {
                                            " " + conflictCount + " ćelija razlikuje se od lokalnog OCR-a i ostavljena je za ručnu provjeru."
                                        } else {
                                            " Nisu pronađeni sukobi s lokalnim OCR-om."
                                        }
                                        message += conflictText
                                    }.onFailure { error ->
                                        phase = if (local != null) OcrPhase.Success else OcrPhase.Error
                                        message = error.message
                                            ?: "AI provjera nije uspjela. Lokalni OCR i dalje je dostupan."
                                    }
                                    aiBusy = false
                                }
                            },
                            enabled = !aiBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (aiBusy) "AI provjera..." else "AI provjera cijelog rasporeda")
                        }
                        Text(
                            "Opcionalno: slika se šalje RASPORED poslužitelju i OpenAI API-ju samo kad ovo pokreneš. API ključ nije spremljen u Android aplikaciji.",
                            modifier = Modifier.padding(top = 5.dp),
                            color = RasporedTokens.Slate,
                            fontSize = 10.sp
                        )
                        if (unresolvedConflicts > 0) {
                            Text(
                                "Za provjeru: ⚠ " + unresolvedConflicts + " nejasnih ćelija",
                                modifier = Modifier.padding(top = 10.dp),
                                color = RasporedTokens.Amber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            FilledTonalButton(
                                onClick = {
                                    reviewConflictIndex = reviewCells.indexOfFirst {
                                        it.conflict && !it.manuallyConfirmed
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Icon(Icons.Outlined.RateReview, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pregledaj " + unresolvedConflicts + " nejasne stavke")
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSaveSchedule(
                            recognizedMonth,
                            editedShifts
                                .filterKeys { it in 1..recognizedMonth.lengthOfMonth() }
                                .mapNotNull { (day, rawCode) ->
                                    ScheduleStore.normalizeCode(rawCode)?.let { day to it }
                                }
                                .toMap()
                        )
                    },
                    enabled = selectedRow >= 0 && editedShifts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Spremi raspored", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (allowTeamImport && (result?.rows?.size ?: 0) > 1) {
                    OutlinedButton(
                        onClick = {
                            val rows = result?.rows.orEmpty().filter { it.dayShifts.isNotEmpty() }
                            if (rows.isNotEmpty()) onSaveTeamSchedules(recognizedMonth, rows)
                        },
                        enabled = result?.rows.orEmpty().any { it.dayShifts.isNotEmpty() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Outlined.Groups, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Spremi sve prepoznate djelatnike")
                    }
                }
            }
        }
    }

    if (reviewConflictIndex >= 0) {
        val conflict = reviewCells.getOrNull(reviewConflictIndex)
        if (conflict != null) {
            AlertDialog(
                onDismissRequest = { reviewConflictIndex = -1 },
                icon = { Icon(Icons.Outlined.WarningAmber, null, tint = RasporedTokens.Amber) },
                title = { Text("Nejasna OCR stavka") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            (conflict.employeeRow?.let { it.toString() + ". " } ?: "") +
                                conflict.employeeName + " · dan " + conflict.day,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Lokalni OCR: " + (conflict.localCode ?: "prazno") +
                                " · AI: " + (conflict.aiCode ?: "prazno"),
                            color = RasporedTokens.Slate
                        )
                        conflict.localCode?.let { code ->
                            Button(
                                onClick = { resolveConflict(code) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Zadrži lokalno: " + code) }
                        }
                        conflict.aiCode?.let { code ->
                            OutlinedButton(
                                onClick = { resolveConflict(code) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Odaberi AI: " + code) }
                        }
                        OutlinedButton(
                            onClick = { resolveConflict(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ostavi prazno") }
                        OutlinedTextField(
                            value = customConflictCode,
                            onValueChange = { customConflictCode = it.take(8) },
                            label = { Text("Druga oznaka") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = ScheduleStore.normalizeCode(customConflictCode) != null,
                        onClick = { resolveConflict(customConflictCode) }
                    ) { Text("Primijeni oznaku") }
                },
                dismissButton = {
                    TextButton(onClick = { reviewConflictIndex = -1 }) { Text("Kasnije") }
                }
            )
        }
    }

    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            icon = { Icon(Icons.Outlined.DocumentScanner, null, tint = RasporedTokens.Cyan) },
            title = { Text("Kako dobiti dobar rezultat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Obuhvati cijelu tablicu: prvi i zadnji redak osobe te sve stupce od 1. do zadnjeg dana mjeseca.")
                    Text("• Fotografija se u pregledu prikazuje cijela; okvir više ne reže rubove rasporeda.")
                    Text("• Za široke mjesečne tablice fotografiraj vodoravno kako bi stupci dana imali više piksela.")
                    Text("• Izbjegni sjene, odsjaj i zamućenje.")
                    Text("• Kalendar i lokalni OCR rade bez računa. Prijava u Postavkama otključava opcionalnu AI provjeru i mrežne funkcije računa.")
                    Text("• Prazna kućica ostaje prazna kao redovni slobodni dan. SD odaberi samo ako je SD izričito upisan/odobren u izvornom rasporedu.")
                    Text("• Provjeri D, N, GO, BO, PD i SD oznake prije spremanja. Kratke radne oznake specifične ustanovi (npr. J, S ili P1) aplikacija čuva bez izmišljanja značenja.")
                }
            },
            confirmButton = {
                TextButton(onClick = { helpOpen = false }) { Text("U redu") }
            }
        )
    }
}

@Composable
private fun ScanFrame() {
    Box(Modifier.fillMaxSize()) {
        val color = RasporedTokens.Cyan
        val width = 52.dp
        val thickness = 6.dp
        Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Box(Modifier.width(width).height(thickness).background(color))
            Box(Modifier.width(thickness).height(width).background(color))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Box(Modifier.width(width).height(thickness).background(color).align(Alignment.TopEnd))
            Box(Modifier.width(thickness).height(width).background(color).align(Alignment.TopEnd))
        }
        Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Box(Modifier.width(width).height(thickness).background(color).align(Alignment.BottomStart))
            Box(Modifier.width(thickness).height(width).background(color).align(Alignment.BottomStart))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
            Box(Modifier.width(width).height(thickness).background(color).align(Alignment.BottomEnd))
            Box(Modifier.width(thickness).height(width).background(color).align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun RecognizedDay(day: Int, month: YearMonth, code: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = when (code) {
        "D" -> RasporedTokens.CyanSoft
        "N" -> RasporedTokens.NavyAlt
        "GO" -> RasporedTokens.TealSoft
        "BO" -> RasporedTokens.RedSoft
        "PD" -> Color(0xFFFFF3D6)
        "SD" -> Color(0xFFE9EEF5)
        else -> Color(0xFFF1F5F9)
    }
    val fg = when (code) {
        "D" -> Color(0xFF087BC9)
        "N" -> Color.White
        "GO" -> Color(0xFF07865F)
        "BO" -> Color(0xFFD22333)
        "PD" -> Color(0xFF9A6500)
        "SD" -> Color(0xFF475569)
        else -> RasporedTokens.Slate
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = if (enabled) androidx.compose.foundation.BorderStroke(2.dp, RasporedTokens.Cyan) else ButtonDefaults.outlinedButtonBorder(enabled = enabled),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(82.dp).clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%02d.%02d.".format(day, month.monthValue),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(7.dp))
            Surface(shape = RoundedCornerShape(9.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (code.isBlank()) "" else code,
                    color = fg,
                    fontWeight = if (code.isBlank()) FontWeight.Medium else FontWeight.ExtraBold,
                    fontSize = if (code.isBlank()) 10.sp else 14.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun nextShiftCode(current: String): String {
    val order = listOf("", "D", "N", "GO", "BO", "PD", "SD")
    val index = order.indexOf(current).takeIf { it >= 0 } ?: 0
    return order[(index + 1) % order.size]
}


internal data class AiMergeResult(
    val schedule: RecognizedSchedule,
    val cells: List<RecognitionCellReview>
)

internal fun mergeForAiReview(
    local: RecognizedSchedule?,
    ai: RecognizedSchedule
): AiMergeResult {
    fun key(row: RecognizedScheduleRow): String =
        row.rowNumber?.let { "row:" + it }
            ?: "name:" + row.name.trim().uppercase(java.util.Locale("hr", "HR"))

    if (local == null) {
        val aiCells = ai.rows.flatMap { row ->
            row.dayShifts.map { (day, code) ->
                RecognitionCellReview(
                    employeeRow = row.rowNumber,
                    employeeName = row.name,
                    day = day,
                    localCode = null,
                    aiCode = code,
                    selectedCode = code,
                    source = "ai",
                    confidence = null,
                    conflict = false,
                    manuallyConfirmed = false
                )
            }
        }
        return AiMergeResult(ai, aiCells)
    }

    val mergedRows = linkedMapOf<String, RecognizedScheduleRow>()
    local.rows.forEach { row -> mergedRows[key(row)] = row }
    val cells = linkedMapOf<String, RecognitionCellReview>()

    local.rows.forEach { row ->
        row.dayShifts.forEach { (day, code) ->
            cells[key(row) + ":" + day] = RecognitionCellReview(
                employeeRow = row.rowNumber,
                employeeName = row.name,
                day = day,
                localCode = code,
                aiCode = null,
                selectedCode = code,
                source = "local",
                confidence = null,
                conflict = false,
                manuallyConfirmed = false
            )
        }
    }

    ai.rows.forEach { aiRow ->
        val rowKey = key(aiRow)
        val existing = mergedRows[rowKey]
        if (existing == null) {
            mergedRows[rowKey] = aiRow
            aiRow.dayShifts.forEach { (day, aiCode) ->
                cells[rowKey + ":" + day] = RecognitionCellReview(
                    employeeRow = aiRow.rowNumber,
                    employeeName = aiRow.name,
                    day = day,
                    localCode = null,
                    aiCode = aiCode,
                    selectedCode = aiCode,
                    source = "ai",
                    confidence = null,
                    conflict = false,
                    manuallyConfirmed = false
                )
            }
        } else {
            val shifts = existing.dayShifts.toMutableMap()
            aiRow.dayShifts.forEach { (day, aiCode) ->
                val localCode = shifts[day]
                val cellKey = rowKey + ":" + day
                when {
                    localCode == null -> {
                        shifts[day] = aiCode
                        cells[cellKey] = RecognitionCellReview(
                            employeeRow = existing.rowNumber ?: aiRow.rowNumber,
                            employeeName = if (aiRow.name.length > existing.name.length) aiRow.name else existing.name,
                            day = day,
                            localCode = null,
                            aiCode = aiCode,
                            selectedCode = aiCode,
                            source = "ai",
                            confidence = null,
                            conflict = false,
                            manuallyConfirmed = false
                        )
                    }
                    localCode == aiCode -> {
                        cells[cellKey] = RecognitionCellReview(
                            employeeRow = existing.rowNumber ?: aiRow.rowNumber,
                            employeeName = existing.name,
                            day = day,
                            localCode = localCode,
                            aiCode = aiCode,
                            selectedCode = localCode,
                            source = "local+ai",
                            confidence = null,
                            conflict = false,
                            manuallyConfirmed = false
                        )
                    }
                    else -> {
                        cells[cellKey] = RecognitionCellReview(
                            employeeRow = existing.rowNumber ?: aiRow.rowNumber,
                            employeeName = existing.name,
                            day = day,
                            localCode = localCode,
                            aiCode = aiCode,
                            selectedCode = localCode,
                            source = "local+ai",
                            confidence = null,
                            conflict = true,
                            manuallyConfirmed = false
                        )
                    }
                }
            }
            mergedRows[rowKey] = existing.copy(
                name = if (aiRow.name.length > existing.name.length) aiRow.name else existing.name,
                dayShifts = shifts.toSortedMap(),
                supportCount = existing.supportCount + aiRow.supportCount
            )
        }
    }

    val rows = mergedRows.values.sortedWith(
        compareBy<RecognizedScheduleRow> { it.rowNumber ?: Int.MAX_VALUE }
            .thenBy { it.name }
    )
    return AiMergeResult(
        schedule = RecognizedSchedule(
            month = local.month ?: ai.month,
            rows = rows,
            rawText = local.rawText,
            expectedRowCount = listOfNotNull(local.expectedRowCount, ai.expectedRowCount).maxOrNull()
        ),
        cells = cells.values.sortedWith(
            compareBy<RecognitionCellReview> { it.employeeRow ?: Int.MAX_VALUE }
                .thenBy { it.employeeName }
                .thenBy { it.day }
        )
    )
}
