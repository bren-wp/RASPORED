package hr.raspored.app.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.R
import hr.raspored.app.data.ScheduleStore
import hr.raspored.app.data.CroatianHolidays
import hr.raspored.app.data.UiSettingsStore
import hr.raspored.app.data.EvidenceAnalytics
import hr.raspored.app.data.TimeEvidenceEntry
import hr.raspored.app.data.TimeEvidenceStore
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

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

private enum class Screen { Home, Calendar, Scan, Stats, Payroll, Hours, Settings }
private data class Shift(val code:String,val name:String,val time:String,val hours:Int)
private val D=Shift("D","Dnevna smjena","07:00 – 19:00 (12h)",12)
private val N=Shift("N","Noćna smjena","19:00 – 07:00 (12h)",12)
private val GO=Shift("GO","Slobodan dan","—",0)
private val BO=Shift("BO","Bolovanje","—",0)
private val NONE=Shift("","Nema planirane smjene","—",0)

@Composable fun RasporedApp(){
    var screen by remember { mutableStateOf(Screen.Home) }
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ScheduleStore(context) }
    val uiSettings = remember(context) { UiSettingsStore(context) }
    val evidenceStore = remember(context) { TimeEvidenceStore(context) }
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
                if(screen==Screen.Scan) ScanHeader(onBack={screen=Screen.Home})
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
                    Screen.Calendar->CalendarScreen(scheduleCodes,evidenceEntries)
                    Screen.Scan->OcrScanScreen(YearMonth.from(appDate())) { month, shifts ->
                        store.saveMonth(month, shifts)
                        (1..month.lengthOfMonth()).forEach { scheduleCodes.remove(month.atDay(it).toString()) }
                        shifts.forEach { (day, code) -> scheduleCodes[month.atDay(day).toString()] = code }
                        screen = Screen.Calendar
                    }
                    Screen.Stats->StatsScreen(scheduleCodes,evidenceEntries,onPayroll={screen=Screen.Payroll})
                    Screen.Payroll->PayrollScreen(evidenceEntries,onBack={screen=Screen.Stats})
                    Screen.Hours->{
                        val shift=currentShiftAt(appDateTime(),scheduleCodes)?.second
                        TimeEvidenceScreen(
                            plannedShiftCode=shift?.code,
                            plannedShiftLabel=shift?.let{it.name+" · "+it.time} ?: "—",
                            onBack={screen=Screen.Home},
                            onEvidenceChanged={evidenceRevision++}
                        )
                    }
                    Screen.Settings->SettingsScreen(
                        darkMode=darkMode,
                        reducedMotion=reducedMotion,
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
                    Icon(Icons.Outlined.CloudSync,"Osvježi podatke",tint=Color.White)
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
        Screen.Hours->Screen.Home
        Screen.Payroll->Screen.Stats
        else->current
    }
    NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=6.dp){
        NavItem(selected,Screen.Home,"Početna",Icons.Outlined.Home,onSelect)
        NavItem(selected,Screen.Calendar,"Kalendar",Icons.Outlined.CalendarMonth,onSelect)
        NavItem(selected,Screen.Scan,"Skeniraj",Icons.Outlined.PhotoCamera,onSelect,true)
        NavItem(selected,Screen.Stats,"Statistika",Icons.Outlined.BarChart,onSelect)
        NavItem(selected,Screen.Settings,"Postavke",Icons.Outlined.Settings,onSelect)
    }
}
@Composable private fun RowScope.NavItem(current:Screen,target:Screen,label:String,icon:ImageVector,onSelect:(Screen)->Unit,emphasis:Boolean=false){
    NavigationBarItem(modifier=Modifier.testTag("nav-"+target.name.lowercase()),selected=current==target,onClick={onSelect(target)},icon={
        Surface(shape=RoundedCornerShape(if(emphasis)22.dp else 12.dp),color=if(emphasis) Cyan else Color.Transparent){
            Icon(icon,null,modifier=Modifier.padding(if(emphasis)10.dp else 4.dp).size(if(emphasis)28.dp else 24.dp),tint=if(emphasis) Color.White else if(current==target) Cyan else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },label={Text(label,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedTextColor=Cyan,unselectedTextColor=Slate,indicatorColor=Color.Transparent))
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
private fun shiftFromCode(code:String):Shift?=when(code){"D"->D;"N"->N;"GO"->GO;"BO"->BO;else->null}
private fun scheduleFor(month:YearMonth,codes:Map<String,String>):Map<Int,Shift>{
    val persisted=(1..month.lengthOfMonth()).mapNotNull { day ->
        shiftFromCode(codes[month.atDay(day).toString()] ?: "")?.let { day to it }
    }.toMap()
    return persisted
}
private fun weeklyHours(month:YearMonth,data:Map<Int,Shift>):List<Int> =
    (0..4).map { week ->
        val first=week*7+1
        val last=minOf(month.lengthOfMonth(),first+6)
        if(first>month.lengthOfMonth()) 0 else (first..last).sumOf { data[it]?.hours ?: 0 }
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
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                listOf(D,N,GO,BO).forEach{ShiftChip(it,Modifier.weight(1f))}
            }
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

@Composable private fun CalendarScreen(scheduleCodes:Map<String,String>,evidenceEntries:List<TimeEvidenceEntry>){
    val today=appDate()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val data=scheduleFor(month,scheduleCodes)
    val holidays=CroatianHolidays.forYear(month.year)
    val analytics=EvidenceAnalytics.summarize(
        month=month,
        entries=evidenceEntries,
        scheduleCodes=codesForMonth(month,data),
        fallbackToPlanned=false
    )
    val selectedShift=if(YearMonth.from(selected)==month) data[selected.dayOfMonth] else null
    val selectedHoliday=holidays[selected]
    val formatter=java.time.format.DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy.",Locale("hr","HR"))
    val selectedTitle=if(selected==today)"Danas" else selected.dayOfWeek.getDisplayName(TextStyle.FULL,Locale("hr","HR")).replaceFirstChar{it.titlecase(Locale("hr","HR"))}

    LazyColumn(
        Modifier.fillMaxSize().testTag("screen-calendar").padding(horizontal=14.dp),
        contentPadding=PaddingValues(top=16.dp,bottom=22.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        item{
            Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp){
                Column(Modifier.padding(14.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        IconButton(onClick={
                            month=month.minusMonths(1)
                            selected=month.atDay(1)
                        }){Icon(Icons.Outlined.ChevronLeft,"Prethodni mjesec")}
                        Text(
                            month.month.getDisplayName(TextStyle.FULL,Locale("hr","HR")).replaceFirstChar{it.titlecase(Locale("hr","HR"))}+" "+month.year+".",
                            fontSize=25.sp,
                            fontWeight=FontWeight.Bold,
                            modifier=Modifier.weight(1f),
                            textAlign=androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(onClick={
                            month=month.plusMonths(1)
                            selected=month.atDay(1)
                        }){Icon(Icons.Outlined.ChevronRight,"Sljedeći mjesec")}
                    }
                    CalendarGrid(
                        month=month,
                        data=data,
                        holidays=holidays,
                        selected=selected,
                        today=today,
                        onSelect={ date ->
                            if(YearMonth.from(date)!=month) month=YearMonth.from(date)
                            selected=date
                        }
                    )
                }
            }
        }
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column(Modifier.padding(16.dp)){
                    Row{
                        Column(Modifier.weight(1f)){
                            Text(selectedTitle,fontSize=24.sp,fontWeight=FontWeight.Bold)
                            Text(
                                selected.format(formatter).replaceFirstChar{it.titlecase(Locale("hr","HR"))},
                                color=Slate
                            )
                        }
                        if(selectedHoliday!=null){
                            Text("▦ Blagdan\n"+selectedHoliday,color=Red,fontWeight=FontWeight.Bold,fontSize=12.sp)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical=12.dp))
                    Row(verticalAlignment=Alignment.CenterVertically){
                        if(selectedShift!=null){
                            ShiftBadge(selectedShift,62.dp)
                        }else{
                            Surface(
                                shape=RoundedCornerShape(14.dp),
                                color=if(selectedHoliday!=null) BObg else Color(0xFFF1F5F9),
                                modifier=Modifier.size(62.dp)
                            ){Box(contentAlignment=Alignment.Center){Text(if(selectedHoliday!=null)"BO" else "—",fontWeight=FontWeight.Bold,color=if(selectedHoliday!=null)Red else Slate)}}
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)){
                            Text(
                                selectedShift?.name ?: if(selectedHoliday!=null)"Blagdan (neradni dan)" else "Nema planirane smjene",
                                fontWeight=FontWeight.Bold,
                                fontSize=18.sp
                            )
                            Text(selectedShift?.time ?: "—",color=Slate)
                        }
                        Icon(Icons.Outlined.ChevronRight,null)
                    }
                }
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){
                listOf(D,N,GO,BO).forEach{ShiftChip(it,Modifier.weight(1f))}
            }
        }
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){
                Column(Modifier.padding(16.dp)){
                    Text("Sažetak za mjesec",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row{
                        listOf(
                            "Planirano" to minutesLabel(analytics.plannedMinutes),
                            "Odrađeno" to minutesLabel(analytics.workedMinutes),
                            "Saldo" to signedMinutesLabel(analytics.balanceMinutes),
                            "Noćni sati" to minutesLabel(analytics.nightMinutes)
                        ).forEach{
                            Column(Modifier.weight(1f)){
                                Text(it.first,fontSize=11.sp,color=Slate)
                                Text(it.second,fontWeight=FontWeight.Bold,fontSize=18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun CalendarGrid(
    month:YearMonth,
    data:Map<Int,Shift>,
    holidays:Map<LocalDate,String>,
    selected:LocalDate,
    today:LocalDate,
    onSelect:(LocalDate)->Unit
){
    val firstOffset=month.atDay(1).dayOfWeek.value-1
    val start=month.atDay(1).minusDays(firstOffset.toLong())
    val cells=List(42){start.plusDays(it.toLong())}
    Row(Modifier.fillMaxWidth()){
        listOf("Pon","Uto","Sri","Čet","Pet","Sub","Ned").forEach{
            Text(
                it,
                modifier=Modifier.weight(1f).padding(vertical=8.dp),
                fontSize=12.sp,
                color=Slate,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
    cells.chunked(7).forEach{week->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
            week.forEach{date->
                val inside=YearMonth.from(date)==month
                val shift=if(inside)data[date.dayOfMonth] else null
                val holiday=holidays[date]
                val isWeekend=date.dayOfWeek.value>=6
                val bg=when{
                    shift!=null->shiftBg(shift)
                    holiday!=null->BObg
                    isWeekend->Color(0xFFF6F8FB)
                    else->Color(0xFFF1F5F9)
                }
                val fg=when{
                    shift!=null->shiftFg(shift)
                    holiday!=null->Red
                    else->Navy
                }
                Surface(
                    onClick={onSelect(date)},
                    shape=RoundedCornerShape(10.dp),
                    color=bg,
                    border=when{
                        date==selected->BorderStroke(2.dp,Cyan)
                        date==today->BorderStroke(1.dp,Color(0x6600C2FF))
                        else->null
                    },
                    modifier=Modifier.weight(1f).aspectRatio(.9f).alpha(if(inside)1f else .38f)
                ){
                    Box(contentAlignment=Alignment.Center){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Text(date.dayOfMonth.toString(),fontWeight=FontWeight.Bold,color=fg)
                            when{
                                shift!=null->Text(shift.code,fontWeight=FontWeight.Bold,color=fg)
                                holiday!=null->Text("✣",fontWeight=FontWeight.Bold,color=Red)
                            }
                        }
                    }
                }
            }
        }
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
    val saturdayCount=data.keys.count{month.atDay(it).dayOfWeek.value==6}
    val sundayCount=data.keys.count{month.atDay(it).dayOfWeek.value==7}
    val holidayShiftCount=data.keys.count{holidays.containsKey(month.atDay(it))}
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
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Statistika",fontSize=31.sp,fontWeight=FontWeight.ExtraBold)
                    TextButton(onClick=onPayroll,contentPadding=PaddingValues(0.dp)){
                        Text("Izračunaj okvirnu plaću ›",fontWeight=FontWeight.Bold)
                    }
                }
                Box{
                    OutlinedButton(onClick={periodMenu=true}){
                        Icon(Icons.Outlined.CalendarMonth,null)
                        Text(" "+monthTitle+" ")
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
                        StatMini("Dnevne",minutesLabel(analytics.dayMinutes),Cyan,Modifier.weight(1f))
                        StatMini("Noćne",minutesLabel(analytics.nightMinutes),Nbg,Modifier.weight(1f))
                        StatMini("GO",(data.values.count{it.code=="GO"}*8).toString()+"h",Teal,Modifier.weight(1f))
                        StatMini("BO",(data.values.count{it.code=="BO"}*8).toString()+"h",Red,Modifier.weight(1f))
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
                        "Dnevne smjene",
                        data.values.count{it.code=="D"}.toString()+" smjena",
                        minutesLabel(analytics.dayMinutes)
                    )
                    DetailLine(
                        Icons.Outlined.DarkMode,
                        "Noćne smjene",
                        data.values.count{it.code=="N"}.toString()+" smjena",
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
                        data.values.count{it.code=="GO"}.toString()+" dana",
                        (data.values.count{it.code=="GO"}*8).toString()+"h"
                    )
                    DetailLine(
                        Icons.Outlined.MedicalServices,
                        "BO",
                        data.values.count{it.code=="BO"}.toString()+" dana",
                        (data.values.count{it.code=="BO"}*8).toString()+"h"
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
    onDarkModeChange:(Boolean)->Unit,
    onReducedMotionChange:(Boolean)->Unit
){
    LazyColumn(
        Modifier.fillMaxSize().testTag("screen-settings").padding(16.dp),
        contentPadding=PaddingValues(top=10.dp,bottom=20.dp)
    ){
        item{
            Text("Postavke",fontSize=31.sp,fontWeight=FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
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
private fun shiftBg(s:Shift)=when(s.code){"D"->Dbg;"N"->Nbg;"GO"->GObg;"BO"->BObg;else->Color(0xFFF1F5F9)}
private fun shiftFg(s:Shift)=when(s.code){"D"->Color(0xFF087BC9);"N"->Color.White;"GO"->Color(0xFF07865F);"BO"->Color(0xFFD22333);else->Slate}
