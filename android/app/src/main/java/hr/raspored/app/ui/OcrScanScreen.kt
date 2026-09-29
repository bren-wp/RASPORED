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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.ocr.RecognizedSchedule
import hr.raspored.app.ocr.ScheduleOcrEngine
import hr.raspored.app.ocr.loadBitmap
import java.time.YearMonth

private enum class OcrPhase { Idle, Processing, Success, Error }

@Composable
internal fun OcrScanScreen(
    defaultMonth: YearMonth,
    onSaveSchedule: (YearMonth, Map<Int, String>) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<RecognizedSchedule?>(null) }
    var phase by remember { mutableStateOf(OcrPhase.Idle) }
    var message by remember { mutableStateOf("Slikaj raspored ili odaberi fotografiju iz galerije.") }
    var selectedRow by remember { mutableIntStateOf(0) }
    var employeeMenu by remember { mutableStateOf(false) }
    val editedShifts = remember { mutableStateListOf<String>() }

    fun applyResult(recognized: RecognizedSchedule) {
        result = recognized
        selectedRow = 0
        editedShifts.clear()
        recognized.rows.firstOrNull()?.shifts?.let(editedShifts::addAll)
        if (recognized.rows.isEmpty()) {
            phase = OcrPhase.Error
            message = "Nije pronađen red sa smjenama D, N, GO ili BO. Pokušaj ravniju i oštriju fotografiju."
        } else {
            phase = OcrPhase.Success
            message = "Prepoznato ${recognized.rows.size} redaka. Provjeri svoj redak prije spremanja."
        }
    }

    fun process(source: Bitmap) {
        bitmap = source
        phase = OcrPhase.Processing
        message = "Automatsko prepoznavanje..."
        ScheduleOcrEngine.recognize(
            bitmap = source,
            onSuccess = ::applyResult,
            onError = {
                phase = OcrPhase.Error
                message = "Prepoznavanje nije uspjelo. Pokušaj ponovno ili odaberi drugu fotografiju."
            }
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { captured -> captured?.let(::process) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val loaded = loadBitmap(context, uri)
            if (loaded != null) process(loaded)
            else {
                phase = OcrPhase.Error
                message = "Fotografiju nije moguće otvoriti."
            }
        }
    }

    val activeRow = result?.rows?.getOrNull(selectedRow)
    val recognizedMonth = result?.month ?: defaultMonth

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Skeniraj raspored", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Slikaj raspored s papira ili učitaj fotografiju.\nMi ćemo automatski prepoznati podatke.",
                color = RasporedTokens.Slate
            )
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
                            .height(310.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE7EAEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Fotografija rasporeda za OCR",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
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

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(
                            onClick = { cameraLauncher.launch(null) },
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
                    Text("Odaberi moj redak", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Provjeri je li ispravno prepoznat tvoj redak.",
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
                                activeRow?.name ?: "Nema prepoznatog zaposlenika",
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
                                    text = { Text(row.name) },
                                    onClick = {
                                        selectedRow = index
                                        editedShifts.clear()
                                        editedShifts.addAll(row.shifts)
                                        employeeMenu = false
                                    }
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
                            AssistChip(
                                onClick = {},
                                label = { Text("✓ ${editedShifts.size} dana") }
                            )
                        }
                    }

                    if (editedShifts.isEmpty()) {
                        Text(
                            "Nakon OCR prepoznavanja ovdje će se prikazati smjene.",
                            color = RasporedTokens.Slate,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            editedShifts.forEachIndexed { index, code ->
                                RecognizedDay(
                                    day = index + 1,
                                    month = recognizedMonth,
                                    code = code,
                                    onClick = { editedShifts[index] = nextShiftCode(code) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (editedShifts.isNotEmpty()) {
                                    for (i in editedShifts.indices) {
                                        if (editedShifts[i] !in listOf("D", "N", "GO", "BO")) editedShifts[i] = "D"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Edit, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Uredi")
                        }
                        OutlinedButton(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.DocumentScanner, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Ponovno skeniraj")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val days = minOf(recognizedMonth.lengthOfMonth(), editedShifts.size)
                    onSaveSchedule(
                        recognizedMonth,
                        (1..days).associateWith { editedShifts[it - 1] }
                    )
                },
                enabled = editedShifts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Spremi raspored", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
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
private fun RecognizedDay(day: Int, month: YearMonth, code: String, onClick: () -> Unit) {
    val bg = when (code) {
        "D" -> RasporedTokens.CyanSoft
        "N" -> RasporedTokens.NavyAlt
        "GO" -> RasporedTokens.TealSoft
        "BO" -> RasporedTokens.RedSoft
        else -> Color(0xFFF1F5F9)
    }
    val fg = when (code) {
        "D" -> Color(0xFF087BC9)
        "N" -> Color.White
        "GO" -> Color(0xFF07865F)
        "BO" -> Color(0xFFD22333)
        else -> RasporedTokens.Slate
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = ButtonDefaults.outlinedButtonBorder,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(82.dp).clickable(onClick = onClick)
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
                    code,
                    color = fg,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun nextShiftCode(current: String): String {
    val order = listOf("D", "N", "GO", "BO")
    val index = order.indexOf(current).takeIf { it >= 0 } ?: 0
    return order[(index + 1) % order.size]
}
