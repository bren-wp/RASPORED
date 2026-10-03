package hr.raspored.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.data.ScheduleStore
import hr.raspored.app.ocr.RecognizedSchedule
import hr.raspored.app.ocr.RecognizedScheduleRow
import hr.raspored.app.ocr.RecognitionCellReview
import hr.raspored.app.ocr.AiScheduleVerifier
import hr.raspored.app.ocr.ScheduleOcrEngine
import hr.raspored.app.ocr.ScheduleTableDetector
import hr.raspored.app.ocr.createOcrCaptureUri
import hr.raspored.app.ocr.loadBitmap
import hr.raspored.app.ocr.createSinglePersonOcrBitmap
import hr.raspored.app.ocr.rotateOcrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlin.math.abs

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
    var message by remember { mutableStateOf("Dodaj fotografiju i odaberi redak osobe.") }
    var selectedRow by remember { mutableIntStateOf(-1) }
    var selectedMonth by remember { mutableStateOf(defaultMonth) }
    var employeeMenu by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiConsentOpen by remember { mutableStateOf(false) }
    var aiConsentGranted by remember { mutableStateOf(false) }
    val reviewCells = remember { mutableStateListOf<RecognitionCellReview>() }
    var reviewConflictIndex by remember { mutableIntStateOf(-1) }
    var customConflictCode by remember { mutableStateOf("") }
    var ocrGeneration by remember { mutableIntStateOf(0) }
    var rosterIncomplete by remember { mutableStateOf(false) }
    var singlePersonMode by remember { mutableStateOf(true) }
    var personCropRange by remember { mutableStateOf(0.34f..0.38f) }
    var detectedCropRanges by remember { mutableStateOf<List<ClosedFloatingPointRange<Float>>>(emptyList()) }
    var detectedCropIndex by remember { mutableIntStateOf(-1) }
    val editedShifts = remember { mutableStateMapOf<Int, String>() }

    fun selectDetectedCrop(index: Int) {
        if (detectedCropRanges.isEmpty()) return
        val safeIndex = index.coerceIn(0, detectedCropRanges.lastIndex)
        if (safeIndex !in detectedCropRanges.indices) return
        detectedCropIndex = safeIndex
        personCropRange = detectedCropRanges[safeIndex]
        message = "Odabran je redak ${safeIndex + 1} od ${detectedCropRanges.size}. Provjeri plavi pojas i pokreni skeniranje."
    }

    fun moveCrop(delta: Float, announce: Boolean = true) {
        personCropRange = shiftCropRange(personCropRange, delta)
        detectedCropIndex = -1
        if (announce) {
            message = "Odabir je pomaknut. Provjeri plavi okvir pa pokreni prepoznavanje."
        }
    }


    fun detectSinglePersonRows(source: Bitmap, generation: Int = ocrGeneration) {
        if (source.isRecycled) return
        phase = OcrPhase.Processing
        scope.launch {
            val ranges = runCatching {
                withContext(Dispatchers.Default) {
                    ScheduleTableDetector.detectEmployeeRowBands(source, rowsPerBand = 1)
                        .mapNotNull { band ->
                            val top = band.bodyTop.toFloat() / source.height.toFloat()
                            val bottom = band.bodyBottom.toFloat() / source.height.toFloat()
                            if (bottom - top >= 0.008f) {
                                top.coerceIn(0f, 0.99f)..bottom.coerceIn(0.01f, 1f)
                            } else {
                                null
                            }
                        }
                }
            }.getOrElse { emptyList() }

            if (ocrGeneration != generation || bitmap !== source || source.isRecycled) {
                if (bitmap !== source && !source.isRecycled) source.recycle()
                return@launch
            }

            phase = OcrPhase.Idle
            detectedCropRanges = ranges
            if (ranges.isNotEmpty()) {
                val center = (personCropRange.start + personCropRange.endInclusive) / 2f
                val best = ranges.indices.minByOrNull { index ->
                    val range = ranges[index]
                    abs(((range.start + range.endInclusive) / 2f) - center)
                } ?: 0
                detectedCropIndex = best
                personCropRange = ranges[best]
                message = "Pronađeno je ${ranges.size} redaka. Dodirni željenu osobu na slici ili koristi prethodni/sljedeći redak."
            } else {
                detectedCropIndex = -1
                message = "Mreža redaka nije dovoljno jasna za automatsko poravnanje. Namjesti plavi pojas ručno preko jedne osobe."
            }
        }
    }

    fun applyResult(recognized: RecognizedSchedule, keepReviewCells: Boolean = false) {
        val scoped = if (singlePersonMode) focusSinglePersonResult(recognized) else recognized
        if (!keepReviewCells) {
            reviewCells.clear()
            reviewConflictIndex = -1
            customConflictCode = ""
        }
        result = scoped
        selectedMonth = scoped.month ?: defaultMonth
        editedShifts.clear()
        editMode = false
        val totalRecognizedDays = scoped.rows.sumOf { it.dayShifts.size }
        val emptyRows = scoped.rows.count { it.dayShifts.isEmpty() }
        val numberedRows = scoped.rows
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
            scoped.expectedRowCount,
            numberedExpectedRows
        ).maxOrNull()
        val foundRows = scoped.rows.size
        val severeIncomplete = !singlePersonMode && expectedRows?.let { expected ->
            foundRows * 100 < expected * 65
        } == true
        rosterIncomplete = !singlePersonMode && expectedRows?.let { expected ->
            foundRows * 100 < expected * 88
        } == true
        val bestRowIndex = bestRecognizedRowIndex(scoped.rows)
        val rosterWarning = expectedRows?.let { expected ->
            if (!singlePersonMode && foundRows * 100 < expected * 88) {
                " Upozorenje: tablica izgleda kao raspored s približno $expected redaka, a pouzdano je očitano $foundRows. Ponovi fotografiju tako da cijela tablica ostane oštra."
            } else {
                ""
            }
        }.orEmpty()
        when {
            scoped.rows.isEmpty() -> {
                selectedRow = -1
                phase = OcrPhase.Error
                message = "Nije pronađena osoba s oznakama D, N, GO, BO, PD ili SD. Provjeri fotografiju i pokušaj ponovno."
            }
            totalRecognizedDays == 0 -> {
                selectedRow = -1
                phase = OcrPhase.Error
                message = "Osoba je pronađena, ali dani nisu dovoljno jasno očitani. Poravnaj fotografiju i pokušaj ponovno."
            }
            severeIncomplete -> {
                selectedRow = bestRowIndex
                if (bestRowIndex >= 0) editedShifts.putAll(scoped.rows[bestRowIndex].dayShifts)
                phase = OcrPhase.Success
                message = "Skeniranje cijelog tima nije dovoljno potpuno." + rosterWarning +
                    " Osobni raspored možeš provjeriti i spremiti; timski uvoz ostaje blokiran dok popis nije potpun."
            }
            scoped.rows.size == 1 -> {
                selectedRow = 0
                editedShifts.putAll(scoped.rows.first().dayShifts)
                phase = OcrPhase.Success
                val recognizedDays = scoped.rows.first().dayShifts.size
                message = if (singlePersonMode) {
                    "Prepoznata je odabrana osoba i $recognizedDays oznaka dana. Provjeri raspored prije spremanja."
                } else {
                    "Prepoznata je 1 osoba i $recognizedDays oznaka dana. Provjeri raspored prije spremanja."
                }
                if (scoped.month == null) message += " Provjeri odabrani mjesec."
            }
            else -> {
                selectedRow = bestRowIndex
                if (bestRowIndex >= 0) editedShifts.putAll(scoped.rows[bestRowIndex].dayShifts)
                phase = OcrPhase.Success
                val countLabel = if (scoped.rows.size in 2..4) "${scoped.rows.size} osobe" else "${scoped.rows.size} osoba"
                message = "Prepoznate su $countLabel i ukupno $totalRecognizedDays oznaka dana." +
                    rosterWarning +
                    (if (emptyRows > 0) " $emptyRows redaka nema dovoljno jasne oznake; provjeri ih." else "") +
                    " Odabrana je najpotpunije prepoznata osoba."
                if (scoped.month == null) message += " Provjeri odabrani mjesec."
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
        rosterIncomplete = false
        detectedCropRanges = emptyList()
        detectedCropIndex = -1
        editedShifts.clear()
        editMode = false
        employeeMenu = false
        phase = OcrPhase.Processing
        message = if (singlePersonMode) "Prepoznavanje označene osobe..." else "Automatsko prepoznavanje..."
        val ocrSource = if (singlePersonMode) {
            createSinglePersonOcrBitmap(
                source = source,
                topFraction = personCropRange.start,
                bottomFraction = personCropRange.endInclusive
            )
        } else {
            source
        }
        ScheduleOcrEngine.recognize(
            bitmap = ocrSource,
            onSuccess = { recognized ->
                if (ocrGeneration == generation) {
                    applyResult(recognized)
                } else if (!source.isRecycled) {
                    source.recycle()
                }
                if (ocrSource !== source && !ocrSource.isRecycled) ocrSource.recycle()
            },
            onError = {
                if (ocrGeneration == generation) {
                    phase = OcrPhase.Error
                    message = "Prepoznavanje nije uspjelo. Pokušaj ponovno ili odaberi drugu fotografiju."
                } else if (!source.isRecycled) {
                    source.recycle()
                }
                if (ocrSource !== source && !ocrSource.isRecycled) ocrSource.recycle()
            }
        )
    }

    fun rotatePhoto(degrees: Int) {
        val source = bitmap ?: return
        if (source.isRecycled || phase == OcrPhase.Processing) return
        val generation = ocrGeneration + 1
        ocrGeneration = generation
        phase = OcrPhase.Processing
        message = "Okrećem fotografiju..."
        result = null
        selectedRow = -1
        editedShifts.clear()
        scope.launch {
            val rotated = runCatching {
                withContext(Dispatchers.Default) { rotateOcrBitmap(source, degrees) }
            }.getOrNull()
            if (ocrGeneration != generation || rotated == null) {
                if (rotated != null && rotated !== source && !rotated.isRecycled) rotated.recycle()
                if (rotated == null) {
                    phase = OcrPhase.Error
                    message = "Fotografiju nije moguće okrenuti."
                }
                return@launch
            }
            bitmap = rotated
            if (rotated !== source && !source.isRecycled) source.recycle()
            personCropRange = 0.34f..0.38f
            detectedCropRanges = emptyList()
            detectedCropIndex = -1
            if (singlePersonMode) {
                phase = OcrPhase.Idle
                message = "Tražim retke osoba..."
                detectSinglePersonRows(rotated, generation)
            } else {
                process(rotated, generation)
            }
        }
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
                if (singlePersonMode) {
                    bitmap = loaded
                    phase = OcrPhase.Idle
                    message = "Tražim retke tablice..."
                    detectSinglePersonRows(loaded, generation)
                } else {
                    process(loaded, generation)
                }
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
    val unresolvedActiveConflicts = activeRow?.let { row ->
        reviewCells.count { cell ->
            cell.conflict && !cell.manuallyConfirmed &&
                (cell.employeeRow?.let { row.rowNumber == it }
                    ?: row.name.trim().equals(cell.employeeName.trim(), ignoreCase = true))
        }
    } ?: 0

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
            "Raspored je spreman za uvoz. Sve nejasne stavke su potvrđene."
        } else {
            "Za provjeru je ostalo " + remaining + " nejasnih stavki."
        }
    }

    fun runAiVerification() {
        val source = bitmap ?: return
        val token = remoteAccountToken ?: return
        if (source.isRecycled || aiBusy) return
        val local = result
        val verificationSource = if (singlePersonMode) {
            createSinglePersonOcrBitmap(
                source = source,
                topFraction = personCropRange.start,
                bottomFraction = personCropRange.endInclusive
            )
        } else {
            source
        }
        aiBusy = true
        message = if (singlePersonMode) "Dodatna provjera označene osobe..." else "Dodatna provjera cijele tablice..."
        phase = OcrPhase.Processing
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    AiScheduleVerifier.verify(
                        bitmap = verificationSource,
                        token = token,
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
                    " " + conflictCount + " stavki razlikuje se od prvog prepoznavanja i treba ručnu provjeru."
                } else {
                    " Nisu pronađene nejasne stavke."
                }
                message += conflictText
            }.onFailure { error ->
                phase = if (local != null) OcrPhase.Success else OcrPhase.Error
                message = error.message
                    ?: "Dodatna provjera nije uspjela. Prethodno prepoznavanje je i dalje dostupno."
            }
            if (verificationSource !== source && !verificationSource.isRecycled) {
                verificationSource.recycle()
            }
            aiBusy = false
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
                    Text("Uvezi raspored", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Dodaj fotografiju, poravnaj tablicu, odaberi osobu i provjeri oznake prije spremanja.",
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
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().testTag("scan-source-step")
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScanStepHeader(
                        number = "1",
                        title = "Dodaj fotografiju",
                        subtitle = "Fotografiraj cijelu tablicu ili odaberi postojeću fotografiju. Najbolji rezultat daje ravna i oštra slika bez odsjaja."
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { launchCamera() },
                            modifier = Modifier.weight(1f).heightIn(min = 66.dp).testTag("scan-source-camera"),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Fotografiraj", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).heightIn(min = 66.dp).testTag("scan-source-gallery"),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Outlined.Image, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Odaberi sliku", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(RasporedTokens.RadiusLarge),
                color = RasporedTokens.Navy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    ScanStepHeader(
                        number = "2",
                        title = "Poravnaj i odaberi",
                        subtitle = "Provjeri orijentaciju. Ako uvoziš samo jednu osobu, označi njezin vodoravni redak.",
                        dark = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 430.dp)
                            .aspectRatio(previewAspect)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("scan-image-stage")
                            .pointerInput(singlePersonMode, detectedCropRanges, bitmap) {
                                if (!singlePersonMode || bitmap == null) return@pointerInput
                                detectTapGestures { offset ->
                                    val image = bitmap ?: return@detectTapGestures
                                    val geometry = scanPreviewGeometry(
                                        boxWidth = size.width.toFloat(),
                                        boxHeight = size.height.toFloat(),
                                        imageWidth = image.width.toFloat(),
                                        imageHeight = image.height.toFloat()
                                    ) ?: return@detectTapGestures
                                    val fraction = ((offset.y - geometry.top) / geometry.height).coerceIn(0f, 1f)
                                    if (detectedCropRanges.isNotEmpty()) {
                                        val best = detectedCropRanges.indices.minByOrNull { index ->
                                            val range = detectedCropRanges[index]
                                            abs(((range.start + range.endInclusive) / 2f) - fraction)
                                        } ?: 0
                                        selectDetectedCrop(best)
                                    } else {
                                        val halfHeight = 0.02f
                                        val start = (fraction - halfHeight).coerceIn(0f, 1f - halfHeight * 2f)
                                        personCropRange = start..(start + halfHeight * 2f)
                                        detectedCropIndex = -1
                                        message = "Odabir je postavljen na dodirnuti redak. Po potrebi ga povuci ili fino pomakni."
                                    }
                                }
                            }
                            .pointerInput(singlePersonMode, bitmap) {
                                if (!singlePersonMode || bitmap == null) return@pointerInput
                                detectDragGestures(
                                    onDragEnd = {
                                        message = "Odabir je pomaknut. Provjeri plavi okvir pa pokreni prepoznavanje."
                                    },
                                    onDragCancel = {
                                        message = "Pomicanje odabira je prekinuto."
                                    }
                                ) { change, dragAmount ->
                                    val image = bitmap ?: return@detectDragGestures
                                    val geometry = scanPreviewGeometry(
                                        boxWidth = size.width.toFloat(),
                                        boxHeight = size.height.toFloat(),
                                        imageWidth = image.width.toFloat(),
                                        imageHeight = image.height.toFloat()
                                    ) ?: return@detectDragGestures
                                    change.consume()
                                    moveCrop(dragAmount.y / geometry.height, announce = false)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Fotografija rasporeda",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            if (singlePersonMode) {
                                Canvas(Modifier.matchParentSize()) {
                                    val image = bitmap ?: return@Canvas
                                    val boxAspect = size.width / size.height
                                    val imageAspect = image.width.toFloat() / image.height.toFloat()
                                    val displayWidth: Float
                                    val displayHeight: Float
                                    val left: Float
                                    val imageTop: Float
                                    if (imageAspect > boxAspect) {
                                        displayWidth = size.width
                                        displayHeight = size.width / imageAspect
                                        left = 0f
                                        imageTop = (size.height - displayHeight) / 2f
                                    } else {
                                        displayHeight = size.height
                                        displayWidth = size.height * imageAspect
                                        left = (size.width - displayWidth) / 2f
                                        imageTop = 0f
                                    }
                                    val top = imageTop + displayHeight * personCropRange.start
                                    val bottom = imageTop + displayHeight * personCropRange.endInclusive
                                    drawRect(
                                        color = Color(0x99000000),
                                        topLeft = androidx.compose.ui.geometry.Offset(left, imageTop),
                                        size = androidx.compose.ui.geometry.Size(displayWidth, top - imageTop)
                                    )
                                    drawRect(
                                        color = Color(0x99000000),
                                        topLeft = androidx.compose.ui.geometry.Offset(left, bottom),
                                        size = androidx.compose.ui.geometry.Size(displayWidth, imageTop + displayHeight - bottom)
                                    )
                                    drawRect(
                                        color = RasporedTokens.Cyan,
                                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(displayWidth, bottom - top),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                                    )
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Raspored nije učitan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ScanFrame()
                    }

                    if (bitmap != null) {
                        Text(
                            if (singlePersonMode) {
                                "Plavi okvir treba obuhvatiti samo jednu osobu i njezin redak kroz sve dane. Gornji red s brojevima dana aplikacija zadržava automatski."
                            } else {
                                "Prepoznavanje koristi cijelu fotografiju. U okviru trebaju biti vidljiva sva imena i svi dani mjeseca."
                            },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    if (bitmap != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(
                                onClick = { rotatePhoto(-90) },
                                enabled = phase != OcrPhase.Processing,
                                modifier = Modifier.weight(1f).testTag("scan-rotate-left")
                            ) {
                                Icon(Icons.Outlined.RotateLeft, null, tint = Color.White)
                                Spacer(Modifier.width(5.dp))
                                Text("Okreni lijevo", color = Color.White, fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = { rotatePhoto(90) },
                                enabled = phase != OcrPhase.Processing,
                                modifier = Modifier.weight(1f).testTag("scan-rotate-right")
                            ) {
                                Icon(Icons.Outlined.RotateRight, null, tint = Color.White)
                                Spacer(Modifier.width(5.dp))
                                Text("Okreni desno", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x1AFFFFFF),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = singlePersonMode,
                                    onCheckedChange = { enabled ->
                                        singlePersonMode = enabled
                                        result = null
                                        selectedRow = -1
                                        editedShifts.clear()
                                        if (bitmap != null) {
                                            phase = OcrPhase.Idle
                                            message = if (enabled) {
                                                bitmap?.let { detectSinglePersonRows(it) }
                                                "Tražim retke tablice..."
                                            } else {
                                                "Pokreni ponovno skeniranje cijele tablice."
                                            }
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Jedna osoba · preporučeno", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Isključi samo kada namjerno uvoziš cijeli tim", color = Color(0xFFC6D4EA), fontSize = 11.sp)
                                }
                            }
                            if (singlePersonMode && bitmap != null) {
                                Text(
                                    if (detectedCropRanges.isNotEmpty()) {
                                        "Redak ${detectedCropIndex + 1} / ${detectedCropRanges.size} · dodirni osobu na slici"
                                    } else {
                                        "Položaj označenog retka"
                                    },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                if (detectedCropRanges.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { selectDetectedCrop(detectedCropIndex - 1) },
                                            enabled = detectedCropIndex > 0,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.KeyboardArrowUp, null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Prethodni")
                                        }
                                        OutlinedButton(
                                            onClick = { selectDetectedCrop(detectedCropIndex + 1) },
                                            enabled = detectedCropIndex >= 0 && detectedCropIndex < detectedCropRanges.lastIndex,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Sljedeći")
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Outlined.KeyboardArrowDown, null)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { moveCrop(-0.015f) },
                                        modifier = Modifier.weight(1f).testTag("scan-crop-up")
                                    ) {
                                        Icon(Icons.Outlined.KeyboardArrowUp, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pomakni gore")
                                    }
                                    OutlinedButton(
                                        onClick = { moveCrop(0.015f) },
                                        modifier = Modifier.weight(1f).testTag("scan-crop-down")
                                    ) {
                                        Icon(Icons.Outlined.KeyboardArrowDown, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pomakni dolje")
                                    }
                                }
                                Text(
                                    "Fino podešavanje visine odabira",
                                    color = Color(0xFFC6D4EA),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                RangeSlider(
                                    value = personCropRange,
                                    onValueChange = { range ->
                                        val minHeight = 0.02f
                                        val start = range.start.coerceIn(0f, 0.98f)
                                        val end = range.endInclusive.coerceIn(start + minHeight, 1f)
                                        personCropRange = start..end
                                        detectedCropIndex = -1
                                    },
                                    valueRange = 0f..1f
                                )
                                Button(
                                    onClick = {
                                        val source = bitmap
                                        if (source != null && !source.isRecycled) {
                                            val generation = ocrGeneration + 1
                                            ocrGeneration = generation
                                            process(source, generation)
                                        }
                                    },
                                    enabled = phase != OcrPhase.Processing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Crop, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Skeniraj označenu osobu")
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RasporedTokens.Navy.copy(alpha = .90f),
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
                    ScanStepHeader(
                        number = "3",
                        title = "Odaberi osobu i mjesec",
                        subtitle = "Provjeri ime osobe prije uvoza. Rasporedi različitih osoba nikada se ne spajaju."
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (allowTeamImport) {
                            "Odaberi jednu osobu za osobni kalendar. Ako radiš rasporede za tim, možeš spremiti sve pouzdano prepoznate djelatnike kao odvojene rasporede na ovom uređaju."
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
                            ScanStepHeader(
                                number = "4",
                                title = "Provjeri i spremi",
                                subtitle = "Pregledaj prepoznate oznake i dodirni oznaku za ispravak. Prazan dan ostaje „Nije označeno”."
                            )
                            Text(
                                "D i N su primarne 12-satne smjene; J je dodatna jutarnja smjena.",
                                color = RasporedTokens.Slate,
                                fontSize = 12.sp
                            )
                        }
                        if (editedShifts.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    "✓ " + editedShifts.size + " oznaka",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    if (editedShifts.isEmpty()) {
                        Text(
                            "Nakon prepoznavanja ovdje će se prikazati raspored odabrane osobe.",
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
                                if (aiConsentGranted) {
                                    runAiVerification()
                                } else {
                                    aiConsentOpen = true
                                }
                            },
                            enabled = !aiBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (aiBusy) "Dodatna provjera..." else "Dodatna provjera cijelog rasporeda")
                        }
                        Text(
                            "Opcionalno: fotografija napušta uređaj samo kada pokreneš dodatnu provjeru i šalje se vanjskom servisu za analizu.",
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
                    enabled = selectedRow >= 0 && editedShifts.isNotEmpty() && unresolvedActiveConflicts == 0,
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
                        enabled = !rosterIncomplete &&
                            result?.rows.orEmpty().any { it.dayShifts.isNotEmpty() } &&
                            unresolvedConflicts == 0,
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

    if (aiConsentOpen) {
        AlertDialog(
            onDismissRequest = { aiConsentOpen = false },
            icon = { Icon(Icons.Outlined.PrivacyTip, null, tint = RasporedTokens.Cyan) },
            title = { Text("Dodatna provjera fotografije") },
            text = {
                Text(
                    "Za ovu opcionalnu provjeru fotografija napušta uređaj i šalje se Takto poslužitelju te vanjskom servisu za analizu. Prepoznavanje na uređaju radi i bez ove provjere."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        aiConsentOpen = false
                        aiConsentGranted = true
                        runAiVerification()
                    }
                ) { Text("Pokreni dodatnu provjeru") }
            },
            dismissButton = {
                TextButton(onClick = { aiConsentOpen = false }) { Text("Ostani na prvom prepoznavanju") }
            }
        )
    }

    if (reviewConflictIndex >= 0) {
        val conflict = reviewCells.getOrNull(reviewConflictIndex)
        if (conflict != null) {
            AlertDialog(
                onDismissRequest = { reviewConflictIndex = -1 },
                icon = { Icon(Icons.Outlined.WarningAmber, null, tint = RasporedTokens.Amber) },
                title = { Text("Nejasna stavka") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            (conflict.employeeRow?.let { it.toString() + ". " } ?: "") +
                                conflict.employeeName + " · dan " + conflict.day,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Prvo prepoznavanje: " + (conflict.localCode ?: "prazno") +
                                " · Dodatna provjera: " + (conflict.aiCode ?: "prazno"),
                            color = RasporedTokens.Slate
                        )
                        conflict.localCode?.let { code ->
                            Button(
                                onClick = { resolveConflict(code) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Zadrži prvo: " + code) }
                        }
                        conflict.aiCode?.let { code ->
                            OutlinedButton(
                                onClick = { resolveConflict(code) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Odaberi dodatnu provjeru: " + code) }
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
                    Text("• Kalendar i prepoznavanje na uređaju rade bez računa. Android Postavke nemaju prijavu ni registraciju; osnovni rad ne ovisi o mreži.")
                    Text("• Prazna kućica ostaje „Nije označeno” i ne tretira se automatski kao SD. SD odaberi samo ako je izričito upisan ili odobren u izvornom rasporedu.")
                    Text("• Provjeri D, N, J, GO, BO, PD i SD prije spremanja. D i N su primarne 12-satne smjene, J je jutarnja 8-satna smjena; druge kratke oznake aplikacija čuva bez izmišljanja značenja.")
                }
            },
            confirmButton = {
                TextButton(onClick = { helpOpen = false }) { Text("U redu") }
            }
        )
    }
}

@Composable
private fun ScanStepHeader(
    number: String,
    title: String,
    subtitle: String,
    dark: Boolean = false
) {
    val titleColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (dark) Color(0xFFC6D4EA) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = RasporedTokens.Cyan,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = subtitleColor, fontSize = 11.sp, lineHeight = 15.sp)
        }
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
        "J" -> RasporedTokens.Sky
        "GO" -> RasporedTokens.TealSoft
        "BO" -> RasporedTokens.Amber
        "PD" -> RasporedTokens.RedSoft
        "SD" -> Color(0xFF20314A)
        else -> Color(0xFF20314A)
    }
    val fg = when (code) {
        "D", "N", "J", "GO", "PD" -> Color.White
        "BO" -> RasporedTokens.Navy
        "SD" -> Color(0xFFD7E3F4)
        else -> Color(0xFFD7E3F4)
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
    val order = listOf("", "D", "N", "J", "GO", "BO", "PD", "SD")
    val index = order.indexOf(current).takeIf { it >= 0 } ?: 0
    return order[(index + 1) % order.size]
}


internal fun bestRecognizedRowIndex(rows: List<RecognizedScheduleRow>): Int =
    rows.indices.maxWithOrNull(
        compareBy<Int> { rows[it].dayShifts.size }
            .thenByDescending { rows[it].rowNumber ?: Int.MAX_VALUE }
    ) ?: -1


internal data class ScanPreviewGeometry(
    val top: Float,
    val height: Float
)

internal fun scanPreviewGeometry(
    boxWidth: Float,
    boxHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): ScanPreviewGeometry? {
    if (boxWidth <= 0f || boxHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) return null
    val boxAspect = boxWidth / boxHeight
    val imageAspect = imageWidth / imageHeight
    val renderedHeight = if (imageAspect > boxAspect) boxWidth / imageAspect else boxHeight
    return ScanPreviewGeometry(
        top = (boxHeight - renderedHeight) / 2f,
        height = renderedHeight
    )
}

internal fun shiftCropRange(
    range: ClosedFloatingPointRange<Float>,
    delta: Float
): ClosedFloatingPointRange<Float> {
    val height = (range.endInclusive - range.start).coerceIn(0.02f, 0.25f)
    val start = (range.start + delta).coerceIn(0f, 1f - height)
    return start..(start + height)
}


internal fun focusSinglePersonResult(schedule: RecognizedSchedule): RecognizedSchedule {
    if (schedule.rows.size <= 1) return schedule.copy(expectedRowCount = schedule.rows.size.coerceAtMost(1))
    val index = bestRecognizedRowIndex(schedule.rows)
    val selected = schedule.rows.getOrNull(index)
    return schedule.copy(
        rows = listOfNotNull(selected),
        expectedRowCount = if (selected == null) 0 else 1
    )
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
                            selectedCode = null,
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
