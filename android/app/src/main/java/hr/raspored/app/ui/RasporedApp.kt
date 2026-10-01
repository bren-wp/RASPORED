package hr.raspored.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.R
import hr.raspored.app.data.ScheduleStore
import hr.raspored.app.data.CroatianHolidays
import hr.raspored.app.data.TeamStore
import hr.raspored.app.data.UiSettingsStore
import hr.raspored.app.data.EvidenceAnalytics
import hr.raspored.app.data.TimeEvidenceEntry
import hr.raspored.app.data.TimeEvidenceStore
import hr.raspored.app.data.ReportExporter
import hr.raspored.app.data.RemoteAccountClient
import hr.raspored.app.data.RemoteAccountStore
import hr.raspored.app.data.RemoteSessionInvalidException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Navy=RasporedTokens.Navy
private val Cyan=RasporedTokens.Cyan
private val Teal=RasporedTokens.Teal
private val Bg=RasporedTokens.Background
private val Red=RasporedTokens.Red
private val Slate=RasporedTokens.Slate
private val Dbg=RasporedTokens.CyanSoft
private val Nbg=RasporedTokens.NavyAlt
private val GObg=RasporedTokens.TealSoft
private val BObg=RasporedTokens.RedSoft
private val PDbg=Color(0xFFFFF3D6)
private val SDbg=Color(0xFFE9EEF5)

private enum class Screen { Home, Calendar, Scan, Stats, Payroll, Hours, Settings }
private data class Shift(val code:String,val name:String,val time:String,val hours:Int)
private val D=Shift("D","Dnevna smjena","07:00 – 19:00 (12h)",12)
private val N=Shift("N","Noćna smjena","19:00 – 07:00 (12h)",12)
private val GO=Shift("GO","Godišnji odmor","—",0)
private val BO=Shift("BO","Bolovanje","—",0)
private val PD=Shift("PD","Plaćeni dopust","—",0)
private val SD=Shift("SD","Slobodan dan","—",0)
private val NONE=Shift("","Redovni slobodni dan","—",0)

@Composable fun RasporedApp(){
    var screen by remember { mutableStateOf(Screen.Calendar) }
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ScheduleStore(context) }
    val uiSettings = remember(context) { UiSettingsStore(context) }
    val evidenceStore = remember(context) { TimeEvidenceStore(context) }
    val teamStore = remember(context) { TeamStore(context) }
    val remoteAccountStore = remember(context) { RemoteAccountStore(context) }
    var remoteToken by remember { mutableStateOf(remoteAccountStore.token) }
    var evidenceRevision by remember { mutableIntStateOf(0) }
    val evidenceEntries = remember(evidenceRevision) { evidenceStore.load() }
    var darkMode by remember { mutableStateOf(uiSettings.darkMode) }
    var reducedMotion by remember { mutableStateOf(uiSettings.reducedMotion) }
    val scheduleCodes = remember { mutableStateMapOf<String, String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) {
        scheduleCodes.clear()
        scheduleCodes.putAll(store.load())
    }
    BackHandler(enabled = screen != Screen.Calendar) {
        screen = when (screen) {
            Screen.Payroll -> Screen.Stats
            Screen.Calendar -> Screen.Calendar
            else -> Screen.Calendar
        }
    }

    LaunchedEffect(remoteToken) {
        val token = remoteToken ?: return@LaunchedEffect
        val validation = withContext(Dispatchers.IO) {
            runCatching { RemoteAccountClient.current(token) }
        }
        validation.onSuccess {
            // Existing encrypted tokens remain valid for optional AI verification.
        }.onFailure { error ->
            if (error is RemoteSessionInvalidException) {
                remoteAccountStore.clear()
                remoteToken = null
                snackbarHostState.showSnackbar(
                    "Postojeća mrežna sesija je istekla ili je opozvana. Lokalne funkcije nastavljaju raditi."
                )
            }
        }
    }
    val upcomingHeaderShift = (0L..31L).firstNotNullOfOrNull { offset ->
        val date = appDate().plusDays(offset)
        shiftFromCode(scheduleCodes[date.toString()].orEmpty())
            ?.takeIf { it.code == "D" || it.code == "N" }
            ?.let { date to it }
    }
    val colors = if(darkMode) {
        darkColorScheme(
            primary=Cyan,
            secondary=Teal,
            background=Color(0xFF07162D),
            surface=Color(0xFF0D2446),
            surfaceVariant=Color(0xFF102B52),
            onBackground=Color(0xFFF8FAFC),
            onSurface=Color(0xFFF8FAFC),
            onSurfaceVariant=Color(0xFFB7C6D9),
            error=Red
        )
    } else {
        lightColorScheme(
            primary=Cyan,
            secondary=Teal,
            background=Bg,
            surface=Color.White,
            surfaceVariant=Color(0xFFF1F5F9),
            onBackground=Navy,
            onSurface=Navy,
            onSurfaceVariant=Slate,
            error=Red
        )
    }
    MaterialTheme(
        colorScheme=colors,
        typography=Typography()
    ){
        Scaffold(
            containerColor=MaterialTheme.colorScheme.background,
            snackbarHost={ SnackbarHost(snackbarHostState) },
            topBar={
                if(screen==Screen.Scan) ScanHeader(onBack={screen=Screen.Calendar})
                else BrandHeader(
                    screen=screen,
                    onScan={screen=Screen.Scan},
                    hasNotification=upcomingHeaderShift!=null,
                    onNotify={
                        val message=upcomingHeaderShift?.let { (date,shift) ->
                            val whenText=if(date==appDate()) "Danas" else date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.",Locale("hr","HR")))
                            whenText+" · "+shift.name+" · "+shift.time
                        } ?: "Nema novih obavijesti."
                        scope.launch{snackbarHostState.showSnackbar(message)}
                    },
                    onSync={
                        scheduleCodes.clear()
                        scheduleCodes.putAll(store.load())
                        evidenceRevision++
                        scope.launch{snackbarHostState.showSnackbar("Podaci su osvježeni.")}
                    }
                )
            },
            bottomBar={
                if(screen!=Screen.Scan) BottomNav(screen){screen=it}
            }
        ){ padding ->
            Box(Modifier.padding(padding).fillMaxSize()){
                when(screen){
                    Screen.Home->HomeScreen(scheduleCodes,evidenceEntries){screen=it}
                    Screen.Calendar->CalendarScreen(
                        scheduleCodes=scheduleCodes,
                        onShiftChange={date,code->
                            store.record(date,code)
                            if(code==null) scheduleCodes.remove(date.toString())
                            else scheduleCodes[date.toString()]=code
                        }
                    )
                    Screen.Scan->OcrScanScreen(
                        defaultMonth=YearMonth.from(appDate()),
                        allowTeamImport=true,
                        remoteAccountToken=remoteToken,
                        onSaveTeamSchedules={month,rows->
                            teamStore.saveRecognizedMonth(
                                month,
                                rows.map { row -> row.name to row.dayShifts }
                            )
                            scope.launch{
                                snackbarHostState.showSnackbar("Rasporedi tima su spremljeni odvojeno.")
                            }
                            screen=Screen.Calendar
                        },
                        onSaveSchedule={month,shifts->
                            store.saveMonth(month, shifts)
                            (1..month.lengthOfMonth()).forEach { scheduleCodes.remove(month.atDay(it).toString()) }
                            shifts.forEach { (day, code) -> scheduleCodes[month.atDay(day).toString()] = code }
                            screen = Screen.Calendar
                        }
                    )
                    Screen.Stats->StatsScreen(scheduleCodes,evidenceEntries,onPayroll={screen=Screen.Payroll})
                    Screen.Payroll->PayrollScreen(evidenceEntries,scheduleCodes,onBack={screen=Screen.Stats})
                    Screen.Hours->{
                        val shift=currentShiftAt(appDateTime(),scheduleCodes)?.second
                        TimeEvidenceScreen(
                            plannedShiftCode=shift?.code,
                            plannedShiftLabel=shift?.let{it.name+" · "+it.time} ?: "—",
                            onBack={screen=Screen.Calendar},
                            onEvidenceChanged={evidenceRevision++}
                        )
                    }
                    Screen.Settings->SettingsScreen(
                        darkMode=darkMode,
                        reducedMotion=reducedMotion,
                        scheduleCodes=scheduleCodes,
                        evidenceEntries=evidenceEntries,
                        onDarkModeChange={
                            darkMode=it
                            uiSettings.darkMode=it
                        },
                        onReducedMotionChange={
                            reducedMotion=it
                            uiSettings.reducedMotion=it
                        }
                    )
                }
            }
        }
    }
}

@Composable private fun BrandHeader(screen:Screen,onScan:()->Unit,hasNotification:Boolean,onNotify:()->Unit,onSync:()->Unit){
    Surface(color=Navy,modifier=Modifier.fillMaxWidth()){
        Row(
            Modifier.statusBarsPadding().height(76.dp).padding(horizontal=18.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            BrandMark()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){
                Text("RASPORED",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
                Text("Shift planner & evidencija sati",color=Color(0xFFC6D4EA),fontSize=10.sp)
            }
            if(screen==Screen.Calendar){
                IconButton(onClick=onScan){
                    Icon(Icons.Outlined.DocumentScanner,"Skeniraj raspored",tint=Color.White)
                }
            }
            if(screen==Screen.Stats){
                IconButton(onClick=onSync){
                    Icon(Icons.Outlined.Refresh,"Osvježi lokalne podatke",tint=Color.White)
                }
            }else{
                IconButton(onClick=onNotify){
                    BadgedBox(
                        badge={ if(hasNotification&&(screen==Screen.Home||screen==Screen.Calendar)) Badge(containerColor=Color(0xFFFF4861)) }
                    ){
                        Icon(Icons.Outlined.Notifications,"Obavijesti",tint=Color.White)
                    }
                }
            }
        }
    }
}

@Composable private fun ScanHeader(onBack:()->Unit){
    Surface(color=Navy,modifier=Modifier.fillMaxWidth()){
        Row(
            Modifier.statusBarsPadding().height(68.dp).padding(horizontal=10.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            IconButton(onClick=onBack){
                Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Natrag",tint=Color.White,modifier=Modifier.size(28.dp))
            }
            BrandMark()
            Spacer(Modifier.width(8.dp))
            Text("RASPORED",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.ExtraBold)
        }
    }
}

@Composable private fun BrandMark(){
    Image(
        painter=painterResource(R.drawable.ic_raspored_foreground),
        contentDescription=null,
        modifier=Modifier.size(48.dp)
    )
}

@Composable private fun BottomNav(current:Screen,onSelect:(Screen)->Unit){
    val selected=when(current){
        Screen.Home->Screen.Calendar
        Screen.Payroll->Screen.Stats
        else->current
    }
    NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=6.dp){
        NavItem(selected,Screen.Calendar,"Kalendar",Icons.Outlined.CalendarMonth,onSelect)
        NavItem(selected,Screen.Hours,"Evidencija",Icons.AutoMirrored.Outlined.Notes,onSelect)
        NavItem(selected,Screen.Scan,"Skeniraj",Icons.Outlined.PhotoCamera,onSelect,true)
        NavItem(selected,Screen.Stats,"Statistika",Icons.Outlined.BarChart,onSelect)
        NavItem(selected,Screen.Settings,"Više",Icons.Outlined.MoreHoriz,onSelect)
    }
}
@Composable private fun RowScope.NavItem(current:Screen,target:Screen,label:String,icon:ImageVector,onSelect:(Screen)->Unit,emphasis:Boolean=false){
    NavigationBarItem(modifier=Modifier.testTag("nav-"+target.name.lowercase()),selected=current==target,onClick={onSelect(target)},icon={
        Surface(shape=RoundedCornerShape(if(emphasis)22.dp else 12.dp),color=if(emphasis) Cyan else Color.Transparent){
            Icon(icon,null,modifier=Modifier.padding(if(emphasis)10.dp else 4.dp).size(if(emphasis)28.dp else 24.dp),tint=if(emphasis) Color.White else if(current==target) Cyan else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },label={
        Text(label,fontSize=9.sp,maxLines=1,softWrap=false)
    },colors=NavigationBarItemDefaults.colors(selectedTextColor=Cyan,unselectedTextColor=Slate,indicatorColor=Color.Transparent))
}

private fun appDateTime():LocalDateTime = LocalDateTime.now()
private fun appDate():LocalDate = appDateTime().toLocalDate()

private fun shiftStatusLabel(date:LocalDate,shift:Shift,now:LocalDateTime=appDateTime()):String {
    if(shift.code!="D"&&shift.code!="N")return if(shift.code.isBlank())"Nema smjene" else "Danas"
    val start=when(shift.code){
        "D"->date.atTime(LocalTime.of(7,0))
        else->date.atTime(LocalTime.of(19,0))
    }
    val end=when(shift.code){
        "D"->date.atTime(LocalTime.of(19,0))
        else->date.plusDays(1).atTime(LocalTime.of(7,0))
    }
    return when{
        now.isBefore(start)->{
            val minutes=Duration.between(now,start).toMinutes().coerceAtLeast(0)
            "Za "+(minutes/60)+"h "+(minutes%60).toString().padStart(2,'0')+"min"
        }
        now.isBefore(end)->"U tijeku"
        else->"Završeno"
    }
}

private fun shiftAt(date:LocalDate,codes:Map<String,String>):Shift? =
    shiftFromCode(codes[date.toString()].orEmpty())

private fun currentShiftAt(now:LocalDateTime,codes:Map<String,String>):Pair<LocalDate,Shift>? {
    val today=now.toLocalDate()
    if(now.toLocalTime()<LocalTime.of(7,0)){
        val previous=today.minusDays(1)
        val previousShift=shiftAt(previous,codes)
        if(previousShift?.code=="N")return previous to previousShift
    }
    return shiftAt(today,codes)?.let{today to it}
}

private fun nextWorkShift(after:LocalDate,codes:Map<String,String>):Pair<LocalDate,Shift>? =
    (1L..62L).firstNotNullOfOrNull{offset->
        val date=after.plusDays(offset)
        shiftAt(date,codes)?.takeIf{it.code=="D"||it.code=="N"}?.let{date to it}
    }

private fun nextShiftStatus(today:LocalDate,start:LocalDate,shift:Shift):String {
    val end=if(shift.code=="N") start.plusDays(1) else start
    val prefix=if(start==today.plusDays(1))"Sutra" else start.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.",Locale("hr","HR")))
    return if(shift.code=="N") {
        prefix+"\n"+start.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.",Locale("hr","HR")))+" → "+end.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.",Locale("hr","HR")))
    } else prefix
}
private fun shiftFromCode(code:String):Shift?=when(val normalized=ScheduleStore.normalizeCode(code)){
    "D"->D
    "N"->N
    "GO"->GO
    "BO"->BO
    "PD"->PD
    "SD"->SD
    null->null
    else->Shift(normalized,"Vlastita oznaka "+normalized,"—",0)
}
private fun scheduleFor(month:YearMonth,codes:Map<String,String>):Map<Int,Shift>{
    val persisted=(1..month.lengthOfMonth()).mapNotNull { day ->
        shiftFromCode(codes[month.atDay(day).toString()] ?: "")?.let { day to it }
    }.toMap()
    return persisted
}
private fun codesForMonth(month:YearMonth,data:Map<Int,Shift>):Map<String,String> =
    data.mapKeys { (day,_) -> month.atDay(day).toString() }.mapValues { it.value.code }

private fun minutesLabel(minutes:Long):String {
    val safe=minutes.coerceAtLeast(0L)
    val hours=safe/60L
    val remainder=safe%60L
    return if(remainder==0L) hours.toString()+"h" else hours.toString()+"h "+remainder.toString().padStart(2,'0')+"min"
}

private fun signedMinutesLabel(minutes:Long):String {
    val sign=when{minutes>0L->"+";minutes<0L->"-";else->""}
    return sign+minutesLabel(kotlin.math.abs(minutes))
}

private fun largeMinutesLabel(minutes:Long):String {
    val safe=minutes.coerceAtLeast(0L)
    return (safe/60L).toString()+":"+((safe%60L).toString().padStart(2,'0'))+" h"
}

@Composable private fun HomeScreen(
    scheduleCodes:Map<String,String>,
    evidenceEntries:List<TimeEvidenceEntry>,
    go:(Screen)->Unit
){
    val today=appDate()
    val month=YearMonth.from(today)
    val data=scheduleFor(month,scheduleCodes)
    val analytics=EvidenceAnalytics.summarize(
        month=month,
        entries=evidenceEntries,
        scheduleCodes=codesForMonth(month,data),
        fallbackToPlanned=false
    )
    val now=appDateTime()
    val currentEntry=currentShiftAt(now,scheduleCodes)
    val currentDate=currentEntry?.first ?: today
    val current=currentEntry?.second ?: NONE
    val nextEntry=nextWorkShift(today,scheduleCodes)
    val next=nextEntry?.second ?: NONE
    val formatter=java.time.format.DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy.",Locale("hr","HR"))
    val dateTitle=today.format(formatter).replaceFirstChar{
        if(it.isLowerCase())it.titlecase(Locale("hr","HR")) else it.toString()
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("screen-home").padding(horizontal=16.dp),
        contentPadding=PaddingValues(top=20.dp,bottom=24.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        item{
            Text(dateTitle,fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.onBackground)
            Text("Dobar dan! 👋",fontSize=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item{ShiftCard("Današnja smjena",current,true,statusText=shiftStatusLabel(currentDate,current,now),onOpen={go(Screen.Hours)},onHours={go(Screen.Hours)})}
        item{ShiftCard("Sljedeća smjena",next,false,statusText=nextEntry?.let{nextShiftStatus(today,it.first,it.second)},onOpen={go(Screen.Calendar)},onHours=null)}
        item{
            ShiftLegendGrid()
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
                MetricCard("Ovaj mjesec",minutesLabel(analytics.workedMinutes),"Odrađeno sati",Icons.Outlined.CalendarMonth,Modifier.weight(1f))
                MetricCard("Saldo sati",signedMinutesLabel(analytics.balanceMinutes),"Ukupni saldo",Icons.Outlined.BarChart,Modifier.weight(1f))
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
                MetricCard("Noćni sati",minutesLabel(analytics.nightMinutes),"Ovaj mjesec",Icons.Outlined.DarkMode,Modifier.weight(1f))
                MetricCard("Vikendi i blagdani",minutesLabel(analytics.weekendHolidayMinutes),"Ovaj mjesec",Icons.Outlined.Event,Modifier.weight(1f))
            }
        }
        item{
            OutlinedButton(
                onClick={go(Screen.Payroll)},
                modifier=Modifier.fillMaxWidth().height(54.dp),
                shape=RoundedCornerShape(16.dp)
            ){
                Icon(Icons.Outlined.Balance,null)
                Spacer(Modifier.width(10.dp))
                Text("Izračunaj okvirnu plaću",fontWeight=FontWeight.Bold)
            }
        }
        item{
            Button(
                onClick={go(Screen.Scan)},
                modifier=Modifier.fillMaxWidth().height(58.dp),
                shape=RoundedCornerShape(16.dp)
            ){
                Icon(Icons.Outlined.PhotoCamera,null)
                Spacer(Modifier.width(10.dp))
                Text("Skeniraj raspored",fontWeight=FontWeight.Bold,fontSize=18.sp)
            }
        }
    }
}

@Composable private fun ShiftCard(title:String,shift:Shift,today:Boolean,statusText:String?,onOpen:(()->Unit)?,onHours:(()->Unit)?){
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(18.dp)){
            val headerModifier=if(onOpen!=null) Modifier.fillMaxWidth().clickable(onClick=onOpen) else Modifier.fillMaxWidth()
            Row(headerModifier,verticalAlignment=Alignment.CenterVertically){Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,"Otvori "+title,tint=MaterialTheme.colorScheme.onSurface)}
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment=Alignment.CenterVertically){
                ShiftBadge(shift,72.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)){Text(shift.name,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(shift.time,color=Slate,fontSize=16.sp)}
                if(!statusText.isNullOrBlank()) Surface(
                    shape=RoundedCornerShape(999.dp),
                    color=Color(0xFFE2F4FF)
                ){
                    Text(
                        statusText,
                        color=Color(0xFF087BC9),
                        fontSize=11.sp,
                        lineHeight=13.sp,
                        fontWeight=FontWeight.Bold,
                        modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp)
                    )
                }
            }
            if(today){
                HorizontalDivider(Modifier.padding(vertical=13.dp),color=MaterialTheme.colorScheme.outlineVariant)
                InfoLine(Icons.Outlined.Schedule,"Radno vrijeme",if(shift.hours>0)shift.hours.toString()+"h" else "—")
                InfoLine(Icons.Outlined.Checklist,"Evidentiraj ulaz/izlaz","›",onHours)
                InfoLine(Icons.AutoMirrored.Outlined.Notes,"Bilješka","›",onHours)
            }
        }
    }
}
@Composable private fun InfoLine(icon:ImageVector,label:String,value:String,onClick:(()->Unit)?=null){
    val modifier=if(onClick!=null) Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=7.dp) else Modifier.fillMaxWidth().padding(vertical=5.dp)
    Row(modifier,verticalAlignment=Alignment.CenterVertically){
        Icon(icon,null,tint=MaterialTheme.colorScheme.onSurface,modifier=Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label,modifier=Modifier.weight(1f),color=Slate)
        Text(value,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun MetricCard(label:String,value:String,caption:String,icon:ImageVector,modifier:Modifier){Surface(modifier=modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=1.dp){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Cyan,modifier=Modifier.size(34.dp));Spacer(Modifier.width(10.dp));Column{Text(label,fontSize=13.sp);Text(value,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(caption,fontSize=11.sp,color=Slate)}}}}

@Composable private fun CalendarScreen(
    scheduleCodes: Map<String, String>,
    onShiftChange: (LocalDate, String?) -> Unit
) {
    val today = appDate()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var customCode by remember { mutableStateOf("") }
    var monthPickerDialog by remember { mutableStateOf(false) }
    var pickerYear by remember { mutableIntStateOf(month.year) }

    val data = scheduleFor(month, scheduleCodes)
    val holidays = CroatianHolidays.forYear(month.year)

    fun moveTo(target: YearMonth) {
        month = target
        editingDate = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("screen-calendar")
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .08f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { moveTo(month.minusMonths(1)) }) {
                        Icon(Icons.Outlined.ChevronLeft, "Prethodni mjesec")
                    }
                    TextButton(
                        onClick = {
                            pickerYear = month.year
                            monthPickerDialog = true
                        },
                        modifier = Modifier.weight(1f).testTag("calendar-month-picker"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            month.month
                                .getDisplayName(TextStyle.FULL, Locale("hr", "HR"))
                                .replaceFirstChar { it.titlecase(Locale("hr", "HR")) } +
                                " " + month.year + ".",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.ExpandMore, "Odaberi mjesec")
                    }
                    IconButton(onClick = { moveTo(month.plusMonths(1)) }) {
                        Icon(Icons.Outlined.ChevronRight, "Sljedeći mjesec")
                    }
                }

                CalendarGrid(
                    month = month,
                    data = data,
                    holidays = holidays,
                    selected = editingDate,
                    today = today,
                    multiSelected = emptySet(),
                    onSelect = { date ->
                        if (YearMonth.from(date) != month) {
                            moveTo(YearMonth.from(date))
                        } else {
                            editingDate = date
                            customCode = data[date.dayOfMonth]?.code.orEmpty()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    largeCells = true
                )
            }
        }
    }

    editingDate?.let { date ->
        val currentCode = scheduleCodes[date.toString()].orEmpty()
        AlertDialog(
            onDismissRequest = { editingDate = null },
            title = {
                Column {
                    Text(
                        date.dayOfWeek
                            .getDisplayName(TextStyle.FULL, Locale("hr", "HR"))
                            .replaceFirstChar { it.titlecase(Locale("hr", "HR")) },
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy.")),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Odaberi oznaku za ovaj dan ili upiši vlastitu.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    listOf(
                        listOf(D, N, GO),
                        listOf(BO, PD, SD)
                    ).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { shift ->
                                FilledTonalButton(
                                    onClick = {
                                        onShiftChange(date, shift.code)
                                        editingDate = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = shiftBg(shift),
                                        contentColor = shiftFg(shift)
                                    )
                                ) {
                                    Text(shift.code, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customCode,
                        onValueChange = { value ->
                            customCode = value
                                .uppercase(Locale("hr", "HR"))
                                .filter { it.isLetterOrDigit() }
                                .take(8)
                        },
                        label = { Text("Vlastita oznaka") },
                        placeholder = { Text("npr. J, S, P1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("calendar-custom-code")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = ScheduleStore.normalizeCode(customCode) != null,
                    onClick = {
                        val normalized = ScheduleStore.normalizeCode(customCode) ?: return@Button
                        onShiftChange(date, normalized)
                        editingDate = null
                    }
                ) {
                    Text("Spremi oznaku")
                }
            },
            dismissButton = {
                Row {
                    if (currentCode.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onShiftChange(date, null)
                                editingDate = null
                            }
                        ) {
                            Text("Očisti", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editingDate = null }) {
                        Text("Odustani")
                    }
                }
            }
        )
    }

    if (monthPickerDialog) {
        AlertDialog(
            onDismissRequest = { monthPickerDialog = false },
            title = { Text("Odaberi mjesec") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { pickerYear-- }) {
                            Icon(Icons.Outlined.ChevronLeft, "Prethodna godina")
                        }
                        Text(pickerYear.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { pickerYear++ }) {
                            Icon(Icons.Outlined.ChevronRight, "Sljedeća godina")
                        }
                    }
                    val months = java.time.Month.entries
                    months.chunked(3).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { candidate ->
                                OutlinedButton(
                                    onClick = {
                                        moveTo(YearMonth.of(pickerYear, candidate))
                                        monthPickerDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        candidate.getDisplayName(TextStyle.SHORT, Locale("hr", "HR")),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        moveTo(YearMonth.from(today))
                        monthPickerDialog = false
                    }
                ) {
                    Text("Danas")
                }
            }
        )
    }
}

@Composable private fun CalendarGrid(
    month: YearMonth,
    data: Map<Int, Shift>,
    holidays: Map<LocalDate, String>,
    selected: LocalDate?,
    today: LocalDate,
    multiSelected: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    largeCells: Boolean = false
){
    val firstOffset=month.atDay(1).dayOfWeek.value-1
    val start=month.atDay(1).minusDays(firstOffset.toLong())
    val cells=List(42){start.plusDays(it.toLong())}
    val neutral=MaterialTheme.colorScheme.surfaceVariant
    val weekend=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.66f)

    Row(Modifier.fillMaxWidth()){
        listOf("P","U","S","Č","P","S","N").forEachIndexed{index,label->
            Text(
                label,
                modifier=Modifier.weight(1f).padding(vertical=6.dp),
                fontSize=11.sp,
                fontWeight=FontWeight.Bold,
                color=if(index>=5)Red else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign=TextAlign.Center
            )
        }
    }

    cells.chunked(7).forEach{week->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(3.dp)
        ){
            week.forEach{date->
                val inside=YearMonth.from(date)==month
                val shift=if(inside)data[date.dayOfMonth] else null
                val holiday=holidays[date]
                val isWeekend=date.dayOfWeek.value>=6
                val isMulti=multiSelected.contains(date)
                val bg=when{
                    shift!=null->shiftBg(shift)
                    holiday!=null->BObg.copy(alpha=.72f)
                    isWeekend->weekend
                    else->neutral.copy(alpha=.48f)
                }
                val fg=when{
                    shift!=null->shiftFg(shift)
                    holiday!=null->Red
                    else->MaterialTheme.colorScheme.onSurface
                }

                Surface(
                    onClick={onSelect(date)},
                    shape=RoundedCornerShape(9.dp),
                    color=bg,
                    border=when{
                        isMulti->BorderStroke(2.dp,Teal)
                        date==selected->BorderStroke(2.dp,Cyan)
                        date==today->BorderStroke(1.dp,Cyan.copy(alpha=.55f))
                        else->BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
                    },
                    modifier=Modifier
                        .weight(1f)
                        .aspectRatio(if (largeCells) .72f else .82f)
                        .alpha(if(inside)1f else .30f)
                        .testTag("calendar-day-"+date.toString())
                ){
                    Box(Modifier.fillMaxSize().padding(3.dp)){
                        Text(
                            date.dayOfMonth.toString(),
                            fontSize=if(largeCells)12.sp else 10.sp,
                            fontWeight=if(date==today)FontWeight.ExtraBold else FontWeight.Medium,
                            color=fg,
                            modifier=Modifier.align(Alignment.TopStart)
                        )
                        when{
                            shift!=null->Text(
                                shift.code,
                                fontWeight=FontWeight.ExtraBold,
                                fontSize=if(largeCells) {
                                    if(shift.code.length>2)14.sp else 20.sp
                                } else {
                                    if(shift.code.length>2)10.sp else 13.sp
                                },
                                color=fg,
                                modifier=Modifier.align(Alignment.Center)
                            )
                            holiday!=null->Text(
                                "✣",
                                fontWeight=FontWeight.Bold,
                                fontSize=12.sp,
                                color=Red,
                                modifier=Modifier.align(Alignment.Center)
                            )
                        }
                        if(isMulti){
                            Surface(
                                shape=RoundedCornerShape(99.dp),
                                color=Teal,
                                modifier=Modifier.size(13.dp).align(Alignment.TopEnd)
                            ){
                                Box(contentAlignment=Alignment.Center){
                                    Text("✓",fontSize=8.sp,color=Color.White,fontWeight=FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable private fun StatsScreen(
    scheduleCodes:Map<String,String>,
    evidenceEntries:List<TimeEvidenceEntry>,
    onPayroll:()->Unit
){
    var month by remember { mutableStateOf(YearMonth.from(appDate())) }
    var periodMenu by remember { mutableStateOf(false) }
    val data=scheduleFor(month,scheduleCodes)
    val previousMonth=month.minusMonths(1)
    val previousData=scheduleFor(previousMonth,scheduleCodes)
    val analytics=EvidenceAnalytics.summarize(
        month=month,
        entries=evidenceEntries,
        scheduleCodes=codesForMonth(month,data),
        fallbackToPlanned=false
    )
    val previousAnalytics=EvidenceAnalytics.summarize(
        month=previousMonth,
        entries=evidenceEntries,
        scheduleCodes=codesForMonth(previousMonth,previousData),
        fallbackToPlanned=false
    )
    val holidays=CroatianHolidays.forYear(month.year)
    val saturdayCount=data.count{(day,shift)->
        shift.code in setOf("D","N")&&month.atDay(day).dayOfWeek.value==6
    }
    val sundayCount=data.count{(day,shift)->
        shift.code in setOf("D","N")&&month.atDay(day).dayOfWeek.value==7
    }
    val holidayShiftCount=data.count{(day,shift)->
        shift.code in setOf("D","N")&&holidays.containsKey(month.atDay(day))
    }
    val trend=if(previousAnalytics.workedMinutes>0L){
        ((analytics.workedMinutes-previousAnalytics.workedMinutes)*100L/previousAnalytics.workedMinutes).toInt()
    }else null
    val maxWeek=maxOf(1L,analytics.weekMinutes.maxOrNull()?:1L)
    val monthTitle=month.month.getDisplayName(TextStyle.FULL,Locale("hr","HR"))
        .replaceFirstChar{it.titlecase(Locale("hr","HR"))}+" "+month.year+"."

    LazyColumn(
        Modifier.fillMaxSize().testTag("screen-stats").padding(horizontal=14.dp),
        contentPadding=PaddingValues(top=16.dp,bottom=22.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        item{
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(
                    "Statistika",
                    fontSize=31.sp,
                    fontWeight=FontWeight.ExtraBold,
                    maxLines=1,
                    softWrap=false
                )
                Box{
                    OutlinedButton(
                        onClick={periodMenu=true},
                        modifier=Modifier.fillMaxWidth()
                    ){
                        Icon(Icons.Outlined.CalendarMonth,null)
                        Text(" "+monthTitle+" ",maxLines=1,softWrap=false)
                        Icon(Icons.Outlined.ExpandMore,null)
                    }
                    DropdownMenu(expanded=periodMenu,onDismissRequest={periodMenu=false}){
                        (0..11).map{YearMonth.from(appDate()).minusMonths(it.toLong())}.forEach{option->
                            val label=option.month.getDisplayName(TextStyle.FULL,Locale("hr","HR"))
                                .replaceFirstChar{it.titlecase(Locale("hr","HR"))}+" "+option.year+"."
                            DropdownMenuItem(
                                text={Text(label)},
                                onClick={month=option;periodMenu=false}
                            )
                        }
                    }
                }
                TextButton(
                    onClick=onPayroll,
                    contentPadding=PaddingValues(horizontal=0.dp,vertical=2.dp)
                ){
                    Text(
                        "Izračunaj okvirnu plaću ›",
                        fontWeight=FontWeight.Bold,
                        maxLines=1,
                        softWrap=false
                    )
                }
            }
        }
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column(Modifier.padding(18.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("Ukupno odrađeno sati",fontWeight=FontWeight.Bold)
                            Text(
                                largeMinutesLabel(analytics.workedMinutes),
                                fontSize=48.sp,
                                fontWeight=FontWeight.ExtraBold
                            )
                            Text(
                                when{
                                    trend==null->"Nema podataka za prethodni mjesec"
                                    trend>0->"↗ +"+trend+"% u odnosu na prethodni mjesec"
                                    trend<0->"↘ "+trend+"% u odnosu na prethodni mjesec"
                                    else->"Bez promjene u odnosu na prethodni mjesec"
                                },
                                color=if((trend?:0)>=0)Teal else Red,
                                fontWeight=FontWeight.Bold,
                                fontSize=13.sp
                            )
                        }
                        HoursDonut(
                            dayMinutes=analytics.dayMinutes,
                            nightMinutes=analytics.nightMinutes,
                            otherMinutes=analytics.otherMinutes,
                            totalMinutes=analytics.workedMinutes
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                        StatMini("Dnevni sati",minutesLabel(analytics.dayMinutes),Cyan,Modifier.weight(1f))
                        StatMini("Noćni sati",minutesLabel(analytics.nightMinutes),Nbg,Modifier.weight(1f))
                        StatMini("GO",data.values.count{it.code=="GO"}.toString()+" d",Teal,Modifier.weight(1f))
                        StatMini("BO",data.values.count{it.code=="BO"}.toString()+" d",Red,Modifier.weight(1f))
                    }
                }
            }
        }
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column(Modifier.padding(18.dp)){
                    Text("Raspodjela sati po tjednima",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.height(150.dp).fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceAround,
                        verticalAlignment=Alignment.Bottom
                    ){
                        analytics.weekMinutes.take(4).forEachIndexed{i,minutes->
                            Column(horizontalAlignment=Alignment.CenterHorizontally){
                                Text(minutesLabel(minutes),fontWeight=FontWeight.Bold)
                                Box(
                                    Modifier.width(54.dp)
                                        .height(maxOf(6,((minutes.toFloat()/maxWeek.toFloat())*100f).toInt()).dp)
                                        .background(Cyan,RoundedCornerShape(7.dp,7.dp,0.dp,0.dp))
                                )
                                Text((i+1).toString()+". tjedan",fontSize=10.sp,color=Slate)
                            }
                        }
                    }
                }
            }
        }
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column(Modifier.padding(18.dp)){
                    Text("Detaljna statistika",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    DetailLine(
                        Icons.Outlined.WbSunny,
                        "Dnevni sati",
                        "D raspored · "+data.values.count{it.code=="D"}.toString()+" smjena",
                        minutesLabel(analytics.dayMinutes)
                    )
                    DetailLine(
                        Icons.Outlined.DarkMode,
                        "Noćni sati",
                        "N raspored · "+data.values.count{it.code=="N"}.toString()+" smjena",
                        minutesLabel(analytics.nightMinutes)
                    )
                    DetailLine(
                        Icons.Outlined.CalendarMonth,
                        "Subote",
                        saturdayCount.toString()+" smjena",
                        minutesLabel(analytics.saturdayMinutes)
                    )
                    DetailLine(
                        Icons.Outlined.Event,
                        "Nedjelje",
                        sundayCount.toString()+" smjena",
                        minutesLabel(analytics.sundayMinutes)
                    )
                    DetailLine(
                        Icons.Outlined.Celebration,
                        "Blagdani",
                        holidayShiftCount.toString()+" smjena",
                        minutesLabel(analytics.holidayMinutes)
                    )
                    DetailLine(
                        Icons.Outlined.BeachAccess,
                        "GO",
                        "Godišnji odmor",
                        data.values.count{it.code=="GO"}.toString()+" dana"
                    )
                    DetailLine(
                        Icons.Outlined.MedicalServices,
                        "BO",
                        "Bolovanje",
                        data.values.count{it.code=="BO"}.toString()+" dana"
                    )
                    DetailLine(
                        Icons.Outlined.EventAvailable,
                        "PD",
                        "Plaćeni dopust",
                        data.values.count{it.code=="PD"}.toString()+" dana"
                    )
                    DetailLine(
                        Icons.Outlined.Weekend,
                        "SD",
                        "Slobodan dan",
                        data.values.count{it.code=="SD"}.toString()+" dana"
                    )
                    DetailLine(
                        Icons.Outlined.Balance,
                        "Saldo sati",
                        "Prema evidenciji",
                        signedMinutesLabel(analytics.balanceMinutes)
                    )
                }
            }
        }
    }
}

@Composable private fun HoursDonut(
    dayMinutes:Long,
    nightMinutes:Long,
    otherMinutes:Long,
    totalMinutes:Long
){
    val size=142.dp
    val trackColor=MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(size),contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val stroke=18.dp.toPx()
            drawArc(
                color=trackColor,
                startAngle=-90f,
                sweepAngle=360f,
                useCenter=false,
                style=Stroke(width=stroke)
            )
            if(totalMinutes>0L){
                val daySweep=360f*(dayMinutes.toFloat()/totalMinutes.toFloat())
                val nightSweep=360f*(nightMinutes.toFloat()/totalMinutes.toFloat())
                val otherSweep=360f*(otherMinutes.toFloat()/totalMinutes.toFloat())
                drawArc(
                    color=Cyan,
                    startAngle=-90f,
                    sweepAngle=daySweep,
                    useCenter=false,
                    style=Stroke(width=stroke)
                )
                drawArc(
                    color=Nbg,
                    startAngle=-90f+daySweep,
                    sweepAngle=nightSweep,
                    useCenter=false,
                    style=Stroke(width=stroke)
                )
                if(otherSweep>0f){
                    drawArc(
                        color=Color(0xFFB8D0ED),
                        startAngle=-90f+daySweep+nightSweep,
                        sweepAngle=otherSweep,
                        useCenter=false,
                        style=Stroke(width=stroke)
                    )
                }
            }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Text(minutesLabel(totalMinutes),fontSize=22.sp,fontWeight=FontWeight.ExtraBold)
            Text("ukupno",fontSize=11.sp,color=Slate)
        }
    }
}
@Composable private fun StatMini(label:String,value:String,color:Color,modifier:Modifier){
    Column(modifier.padding(4.dp)){
        Box(Modifier.size(18.dp).background(color,RoundedCornerShape(5.dp)))
        Text(value,fontSize=18.sp,fontWeight=FontWeight.Bold)
        Text(label,fontSize=11.sp,color=Slate)
    }
}
@Composable private fun DetailLine(icon:ImageVector,label:String,caption:String,value:String){
    Row(
        Modifier.fillMaxWidth().padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Icon(icon,null,tint=Cyan)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text(label,fontWeight=FontWeight.Bold)
            Text(caption,fontSize=11.sp,color=Slate)
        }
        Text(value,fontWeight=FontWeight.Bold,fontSize=18.sp)
    }
}

@Composable private fun SettingsScreen(
    darkMode:Boolean,
    reducedMotion:Boolean,
    scheduleCodes:Map<String,String>,
    evidenceEntries:List<TimeEvidenceEntry>,
    onDarkModeChange:(Boolean)->Unit,
    onReducedMotionChange:(Boolean)->Unit
){
    val context=LocalContext.current
    var exportStatus by remember { mutableStateOf("") }
    var exportMonth by remember { mutableStateOf(YearMonth.from(appDate())) }

    fun openExternal(uri:String){
        runCatching{
            context.startActivity(
                Intent(Intent.ACTION_VIEW,Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun exportSelectedMonth(){
        exportStatus=""
        runCatching{
            val uri=ReportExporter.createMonthlyPdf(
                context=context,
                month=exportMonth,
                schedule=scheduleCodes,
                evidence=evidenceEntries,
                profileName=""
            )
            val share=Intent(Intent.ACTION_SEND).apply{
                type="application/pdf"
                putExtra(Intent.EXTRA_STREAM,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share,"Podijeli RASPORED PDF"))
        }.onSuccess{
            exportStatus="PDF je izrađen za "+exportMonth+"."
        }.onFailure{
            exportStatus="PDF trenutačno nije moguće izraditi."
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("screen-settings").padding(16.dp),
        contentPadding=PaddingValues(top=10.dp,bottom=20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        item{Text("Postavke",fontSize=31.sp,fontWeight=FontWeight.ExtraBold)}
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Izvoz",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Text(
                        "Odaberi spremljeni mjesec i izradi PDF s rasporedom, vlastitim oznakama i evidentiranim radom.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize=12.sp
                    )
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        IconButton(onClick={exportMonth=exportMonth.minusMonths(1)}){
                            Icon(Icons.Outlined.ChevronLeft,"Prethodni mjesec")
                        }
                        Text(
                            exportMonth.month.getDisplayName(TextStyle.FULL,Locale("hr","HR"))
                                .replaceFirstChar{it.titlecase(Locale("hr","HR"))}+" "+exportMonth.year+".",
                            modifier=Modifier.weight(1f),
                            textAlign=TextAlign.Center,
                            fontWeight=FontWeight.Bold
                        )
                        IconButton(onClick={exportMonth=exportMonth.plusMonths(1)}){
                            Icon(Icons.Outlined.ChevronRight,"Sljedeći mjesec")
                        }
                    }
                    Button(
                        onClick={exportSelectedMonth()},
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ){
                        Icon(Icons.Outlined.PictureAsPdf,null)
                        Spacer(Modifier.width(8.dp))
                        Text("Izvezi mjesečni PDF")
                    }
                    if(exportStatus.isNotBlank()){
                        Text(exportStatus,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)
                    }
                }
            }
        }
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column(Modifier.padding(18.dp)){
                    Text("Izgled i pristupačnost",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    SettingSwitch(
                        "Tamni način",
                        "Navy/dark surface uz iste statusne boje.",
                        checked=darkMode,
                        onCheckedChange=onDarkModeChange
                    )
                    SettingSwitch(
                        "Smanjene animacije",
                        "Smanjuje prijelaze i motion efekte.",
                        checked=reducedMotion,
                        onCheckedChange=onReducedMotionChange
                    )
                }
            }
        }
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Podrška",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Text(
                        "Za pomoć s aplikacijom, rasporedom ili prijavom greške.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize=12.sp
                    )
                    SupportAction(
                        icon=Icons.Outlined.Chat,
                        title="WhatsApp",
                        value="+385 91 901 0092",
                        onClick={openExternal("https://wa.me/385919010092")}
                    )
                    SupportAction(
                        icon=Icons.Outlined.Email,
                        title="E-mail",
                        value="info@raspored.eu",
                        onClick={openExternal("mailto:info@raspored.eu")}
                    )
                    HorizontalDivider()
                    Text("Developer",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    SupportAction(
                        icon=Icons.Outlined.Language,
                        title="Brendigo Studio",
                        value="brendigo.com",
                        onClick={openExternal("https://brendigo.com")}
                    )
                }
            }
        }
    }



}

@Composable private fun SupportAction(
    icon:ImageVector,
    title:String,
    value:String,
    onClick:()->Unit
){
    Surface(
        modifier=Modifier.fillMaxWidth().clickable(onClick=onClick),
        shape=RoundedCornerShape(14.dp),
        color=MaterialTheme.colorScheme.surfaceVariant
    ){
        Row(
            Modifier.padding(horizontal=14.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            Icon(icon,null,tint=Cyan)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){
                Text(title,fontWeight=FontWeight.Bold)
                Text(value,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SettingSwitch(
    title:String,
    caption:String,
    checked:Boolean,
    onCheckedChange:(Boolean)->Unit
){
    Row(
        Modifier.fillMaxWidth().padding(vertical=13.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Column(Modifier.weight(1f)){
            Text(title,fontWeight=FontWeight.Bold)
            Text(caption,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked=checked,onCheckedChange=onCheckedChange)
    }
}

@Composable private fun ShiftChip(s:Shift,modifier:Modifier){Surface(modifier=modifier.height(40.dp),shape=RoundedCornerShape(11.dp),color=shiftBg(s)){Box(contentAlignment=Alignment.Center){Text(s.code,fontWeight=FontWeight.ExtraBold,color=shiftFg(s),fontSize=13.sp)}}}
@Composable private fun ShiftBadge(s:Shift,size:androidx.compose.ui.unit.Dp){Surface(shape=RoundedCornerShape(14.dp),color=shiftBg(s),modifier=Modifier.size(size)){Box(contentAlignment=Alignment.Center){Text(if(s.code.isBlank())"—" else s.code,fontSize=if(size>50.dp)24.sp else 14.sp,fontWeight=FontWeight.ExtraBold,color=shiftFg(s))}}}
@Composable private fun ShiftLegendGrid(){
    Column(verticalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){
            listOf(D,N,GO).forEach{ShiftChip(it,Modifier.weight(1f))}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){
            listOf(BO,PD,SD).forEach{ShiftChip(it,Modifier.weight(1f))}
        }
    }
}

private fun shiftBg(s:Shift)=when(s.code){"D"->Dbg;"N"->Nbg;"GO"->GObg;"BO"->BObg;"PD"->PDbg;"SD"->SDbg;else->Color(0xFFF1F5F9)}
private fun shiftFg(s:Shift)=when(s.code){"D"->Color(0xFF087BC9);"N"->Color.White;"GO"->Color(0xFF07865F);"BO"->Color(0xFFD22333);"PD"->Color(0xFF9A6500);"SD"->Color(0xFF475569);else->Slate}
