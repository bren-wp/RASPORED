package hr.raspored.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.BuildConfig
import hr.raspored.app.R
import hr.raspored.app.data.ScheduleStore
import hr.raspored.app.data.CroatianHolidays
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

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

private enum class Screen { Home, Calendar, Scan, Stats, Settings }
private data class Shift(val code:String,val name:String,val time:String,val hours:Int)
private val D=Shift("D","Dnevna smjena","07:00 – 19:00 (12h)",12)
private val N=Shift("N","Noćna smjena","19:00 – 07:00 (12h)",12)
private val GO=Shift("GO","Slobodan dan","—",0)
private val BO=Shift("BO","Bolovanje","—",0)

@Composable fun RasporedApp(){
    var screen by remember { mutableStateOf(Screen.Home) }
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ScheduleStore(context) }
    val scheduleCodes = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(store) {
        scheduleCodes.clear()
        scheduleCodes.putAll(store.load())
    }
    MaterialTheme(
        colorScheme=lightColorScheme(primary=Cyan,secondary=Teal,background=Bg,surface=Color.White,onSurface=Navy),
        typography=Typography()
    ){
        Scaffold(
            containerColor=Bg,
            topBar={ BrandHeader() },
            bottomBar={ BottomNav(screen){screen=it} }
        ){ padding ->
            Box(Modifier.padding(padding).fillMaxSize()){
                when(screen){
                    Screen.Home->HomeScreen(scheduleCodes){screen=it}
                    Screen.Calendar->CalendarScreen(scheduleCodes)
                    Screen.Scan->OcrScanScreen(YearMonth.from(appDate())) { month, shifts ->
                        store.saveMonth(month, shifts)
                        (1..month.lengthOfMonth()).forEach { scheduleCodes.remove(month.atDay(it).toString()) }
                        shifts.forEach { (day, code) -> scheduleCodes[month.atDay(day).toString()] = code }
                        screen = Screen.Calendar
                    }
                    Screen.Stats->StatsScreen(scheduleCodes)
                    Screen.Settings->SettingsScreen()
                }
            }
        }
    }
}

@Composable private fun BrandHeader(){
    Surface(color=Navy,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.statusBarsPadding().height(76.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
            BrandMark()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){Text("RASPORED",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.ExtraBold);Text("Shift planner & evidencija sati",color=Color(0xFFC6D4EA),fontSize=10.sp)}
            IconButton(onClick={}){Icon(Icons.Outlined.Notifications,null,tint=Color.White)}
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
    NavigationBar(containerColor=Color.White,tonalElevation=6.dp){
        NavItem(current,Screen.Home,"Početna",Icons.Outlined.Home,onSelect)
        NavItem(current,Screen.Calendar,"Kalendar",Icons.Outlined.CalendarMonth,onSelect)
        NavItem(current,Screen.Scan,"Skeniraj",Icons.Outlined.PhotoCamera,onSelect,true)
        NavItem(current,Screen.Stats,"Statistika",Icons.Outlined.BarChart,onSelect)
        NavItem(current,Screen.Settings,"Postavke",Icons.Outlined.Settings,onSelect)
    }
}
@Composable private fun RowScope.NavItem(current:Screen,target:Screen,label:String,icon:ImageVector,onSelect:(Screen)->Unit,emphasis:Boolean=false){
    NavigationBarItem(selected=current==target,onClick={onSelect(target)},icon={
        Surface(shape=RoundedCornerShape(if(emphasis)22.dp else 12.dp),color=if(emphasis) Cyan else Color.Transparent){
            Icon(icon,null,modifier=Modifier.padding(if(emphasis)10.dp else 4.dp).size(if(emphasis)28.dp else 24.dp),tint=if(emphasis) Color.White else if(current==target) Cyan else Navy)
        }
    },label={Text(label,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedTextColor=Cyan,unselectedTextColor=Slate,indicatorColor=Color.Transparent))
}

private fun sampleSchedule(month:YearMonth):Map<Int,Shift>{
    val p=listOf(D,N,D,null,null,D,D,GO,D,N,null,N,D,N,D,D,N,GO,D,D,N,D,D,BO,null,D,BO,D,D,N,D)
    return (1..month.lengthOfMonth()).mapNotNull{day->p[(day-1)%p.size]?.let{day to it}}.toMap().toMutableMap().apply {
        if(month==YearMonth.of(2026,10)){this[16]=D;this[17]=N}
    }
}
private fun appDate():LocalDate = if(BuildConfig.DEBUG) LocalDate.of(2026,10,16) else LocalDate.now()
private fun shiftFromCode(code:String):Shift?=when(code){"D"->D;"N"->N;"GO"->GO;"BO"->BO;else->null}
private fun scheduleFor(month:YearMonth,codes:Map<String,String>):Map<Int,Shift>{
    val persisted=(1..month.lengthOfMonth()).mapNotNull { day ->
        shiftFromCode(codes[month.atDay(day).toString()] ?: "")?.let { day to it }
    }.toMap()
    return if(persisted.isNotEmpty()) persisted else if(BuildConfig.DEBUG) sampleSchedule(month) else emptyMap()
}
private fun weeklyHours(month:YearMonth,data:Map<Int,Shift>):List<Int> =
    (0..4).map { week ->
        val first=week*7+1
        val last=minOf(month.lengthOfMonth(),first+6)
        if(first>month.lengthOfMonth()) 0 else (first..last).sumOf { data[it]?.hours ?: 0 }
    }

@Composable private fun HomeScreen(scheduleCodes:Map<String,String>,go:(Screen)->Unit){
    val today=appDate()
    val month=YearMonth.from(today)
    val data=scheduleFor(month,scheduleCodes)
    val worked=data.values.sumOf{it.hours}
    val night=data.values.filter{it.code=="N"}.sumOf{it.hours}
    val weekendHours=data.entries.sumOf{(day,shift)->val dow=month.atDay(day).dayOfWeek.value;if(dow>=6) shift.hours else 0}
    val current=data[today.dayOfMonth] ?: GO
    val nextEntry=(today.dayOfMonth+1..month.lengthOfMonth()).firstNotNullOfOrNull{day->data[day]?.takeIf{it.code=="D"||it.code=="N"}?.let{day to it}}
    val next=nextEntry?.second ?: GO
    val formatter=java.time.format.DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy.",Locale("hr","HR"))
    val dateTitle=today.format(formatter).replaceFirstChar{if(it.isLowerCase())it.titlecase(Locale("hr","HR")) else it.toString()}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(top=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text(dateTitle,fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=Navy);Text("Dobar dan! 👋",fontSize=20.sp,color=Slate)}
        item{ShiftCard("Današnja smjena",current,true)}
        item{ShiftCard("Sljedeća smjena",next,false)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){listOf(D,N,GO,BO).forEach{ShiftChip(it,Modifier.weight(1f))}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){MetricCard("Ovaj mjesec",worked.toString()+"h","Odrađeno sati",Icons.Outlined.CalendarMonth,Modifier.weight(1f));MetricCard("Saldo sati","0h","Ukupni saldo",Icons.Outlined.BarChart,Modifier.weight(1f))}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){MetricCard("Noćni sati",night.toString()+"h","Ovaj mjesec",Icons.Outlined.DarkMode,Modifier.weight(1f));MetricCard("Vikendi i blagdani",weekendHours.toString()+"h","Ovaj mjesec",Icons.Outlined.Event,Modifier.weight(1f))}}
        item{Button(onClick={go(Screen.Scan)},modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(16.dp)){Icon(Icons.Outlined.PhotoCamera,null);Spacer(Modifier.width(10.dp));Text("Skeniraj raspored",fontWeight=FontWeight.Bold,fontSize=18.sp)}}
    }
}

@Composable private fun ShiftCard(title:String,shift:Shift,today:Boolean){
    Surface(shape=RoundedCornerShape(20.dp),color=Color.White,shadowElevation=2.dp,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(18.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null,tint=Navy)}
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment=Alignment.CenterVertically){
                ShiftBadge(shift,72.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)){Text(shift.name,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(shift.time,color=Slate,fontSize=16.sp)}
                if(today) AssistChip(onClick={},label={Text("Za 2h 20min")})
            }
            if(today){Divider(Modifier.padding(vertical=13.dp),color=Color(0xFFE6EDF5));InfoLine(Icons.Outlined.Schedule,"Radno vrijeme","12h");InfoLine(Icons.Outlined.Checklist,"Evidentiraj ulaz/izlaz","›");InfoLine(Icons.Outlined.Notes,"Bilješka","›")}
        }
    }
}
@Composable private fun InfoLine(icon:ImageVector,label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Navy,modifier=Modifier.size(22.dp));Spacer(Modifier.width(12.dp));Text(label,modifier=Modifier.weight(1f),color=Slate);Text(value,fontWeight=FontWeight.Bold)}}
@Composable private fun MetricCard(label:String,value:String,caption:String,icon:ImageVector,modifier:Modifier){Surface(modifier=modifier,shape=RoundedCornerShape(18.dp),color=Color.White,shadowElevation=1.dp){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Cyan,modifier=Modifier.size(34.dp));Spacer(Modifier.width(10.dp));Column{Text(label,fontSize=13.sp);Text(value,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(caption,fontSize=11.sp,color=Slate)}}}}

@Composable private fun CalendarScreen(scheduleCodes:Map<String,String>){
    val today=appDate()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val data=scheduleFor(month,scheduleCodes)
    val holidays=CroatianHolidays.forYear(month.year)
    val worked=data.values.sumOf{it.hours}
    val night=data.values.filter{it.code=="N"}.sumOf{it.hours}
    val selectedShift=if(YearMonth.from(selected)==month) data[selected.dayOfMonth] else null
    val selectedHoliday=holidays[selected]
    val formatter=java.time.format.DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy.",Locale("hr","HR"))
    val selectedTitle=if(selected==today)"Danas" else selected.dayOfWeek.getDisplayName(TextStyle.FULL,Locale("hr","HR")).replaceFirstChar{it.titlecase(Locale("hr","HR"))}

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal=14.dp),
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
                    Divider(Modifier.padding(vertical=12.dp))
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
                            "Planirano" to worked.toString()+"h",
                            "Odrađeno" to worked.toString()+"h",
                            "Saldo" to "0h",
                            "Noćni sati" to night.toString()+"h"
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

@Composable private fun ScanScreen(){
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(top=16.dp,bottom=22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Skeniraj raspored",fontSize=30.sp,fontWeight=FontWeight.ExtraBold);Text("Slikaj raspored s papira ili učitaj fotografiju.\nMi ćemo automatski prepoznati podatke.",color=Slate)}
        item{Surface(shape=RoundedCornerShape(20.dp),color=Color(0xFF755E49)){Column(Modifier.padding(16.dp)){Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFFE7EAEE),modifier=Modifier.fillMaxWidth().height(280.dp)){Column(Modifier.padding(18.dp)){Text("LISTOPAD 2026.",fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.End));Spacer(Modifier.height(30.dp));repeat(5){Text((it+1).toString()+"   PREPOZNATI REDAK     D   N   GO   D",fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp))}}};Row(Modifier.fillMaxWidth()){TextButton(onClick={},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.PhotoCamera,null,tint=Color.White);Text(" Ponovno skeniraj",color=Color.White)};TextButton(onClick={},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Image,null,tint=Color.White);Text(" Odaberi iz galerije",color=Color.White)}}}}}
        item{Surface(shape=RoundedCornerShape(18.dp),color=Color.White){Column(Modifier.padding(16.dp)){Text("Odaberi moj redak",fontSize=22.sp,fontWeight=FontWeight.Bold);Text("Provjeri je li ispravno prepoznat tvoj redak.",color=Slate);OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Icon(Icons.Outlined.Person,null);Spacer(Modifier.width(8.dp));Text("6. MARIO EGIMOVIĆ",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold);Icon(Icons.Outlined.ExpandMore,null)}}}}
        item{Surface(shape=RoundedCornerShape(18.dp),color=Color.White){Column(Modifier.padding(16.dp)){Row{Column(Modifier.weight(1f)){Text("Provjera rasporeda",fontSize=22.sp,fontWeight=FontWeight.Bold);Text("Pregledaj prepoznate smjene i po potrebi ih ispravi.",fontSize=12.sp,color=Slate)};AssistChip(onClick={},label={Text("✓ Prepoznato 31 dan")})};Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf(D,N,GO,Shift("—","slobodno","—",0),D).forEachIndexed{i,s->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text("0"+(i+1)+".10.",fontSize=11.sp);Spacer(Modifier.height(6.dp));ShiftBadge(s,44.dp)}}};Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={},modifier=Modifier.weight(1f)){Text("✎ Uredi")};OutlinedButton(onClick={},modifier=Modifier.weight(1f)){Text("⌗ Ponovno skeniraj")}}}}}
        item{Button(onClick={},modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(16.dp)){Text("✓  Spremi raspored",fontSize=18.sp,fontWeight=FontWeight.Bold)}}
    }
}

@Composable private fun StatsScreen(scheduleCodes:Map<String,String>){
    var month by remember { mutableStateOf(YearMonth.from(appDate())) }
    var periodMenu by remember { mutableStateOf(false) }
    val data=scheduleFor(month,scheduleCodes)
    val previous=scheduleFor(month.minusMonths(1),scheduleCodes)
    val worked=data.values.sumOf{it.hours}
    val previousWorked=previous.values.sumOf{it.hours}
    val dayHours=data.values.filter{it.code=="D"}.sumOf{it.hours}
    val night=data.values.filter{it.code=="N"}.sumOf{it.hours}
    val weeks=weeklyHours(month,data)
    val maxWeek=maxOf(1,weeks.maxOrNull()?:1)
    val holidays=CroatianHolidays.forYear(month.year)
    val saturdayCount=data.keys.count{month.atDay(it).dayOfWeek.value==6}
    val sundayCount=data.keys.count{month.atDay(it).dayOfWeek.value==7}
    val holidayShiftCount=data.keys.count{holidays.containsKey(month.atDay(it))}
    val trend=if(previousWorked>0)((worked-previousWorked)*100/previousWorked) else null
    val monthTitle=month.month.getDisplayName(TextStyle.FULL,Locale("hr","HR")).replaceFirstChar{it.titlecase(Locale("hr","HR"))}+" "+month.year+"."

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal=14.dp),
        contentPadding=PaddingValues(top=16.dp,bottom=22.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Statistika",fontSize=31.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.weight(1f))
                Box{
                    OutlinedButton(onClick={periodMenu=true}){
                        Icon(Icons.Outlined.CalendarMonth,null)
                        Text(" "+monthTitle+" ")
                        Icon(Icons.Outlined.ExpandMore,null)
                    }
                    DropdownMenu(expanded=periodMenu,onDismissRequest={periodMenu=false}){
                        (0..11).map{YearMonth.from(appDate()).minusMonths(it.toLong())}.forEach{option->
                            val label=option.month.getDisplayName(TextStyle.FULL,Locale("hr","HR")).replaceFirstChar{it.titlecase(Locale("hr","HR"))}+" "+option.year+"."
                            DropdownMenuItem(text={Text(label)},onClick={month=option;periodMenu=false})
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
                            Text(worked.toString()+":00 h",fontSize=48.sp,fontWeight=FontWeight.ExtraBold)
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
                        HoursDonut(dayHours=dayHours,nightHours=night,total=worked)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                        StatMini("Dnevne",dayHours.toString()+"h",Cyan,Modifier.weight(1f))
                        StatMini("Noćne",night.toString()+"h",Nbg,Modifier.weight(1f))
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
                        weeks.take(4).forEachIndexed{i,h->
                            Column(horizontalAlignment=Alignment.CenterHorizontally){
                                Text(h.toString()+"h",fontWeight=FontWeight.Bold)
                                Box(
                                    Modifier.width(54.dp)
                                        .height(maxOf(6,((h.toFloat()/maxWeek)*100).toInt()).dp)
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
                    DetailLine(Icons.Outlined.WbSunny,"Dnevne smjene",data.values.count{it.code=="D"}.toString()+" smjena",dayHours.toString()+"h")
                    DetailLine(Icons.Outlined.DarkMode,"Noćne smjene",data.values.count{it.code=="N"}.toString()+" smjena",night.toString()+"h")
                    DetailLine(Icons.Outlined.CalendarMonth,"Subote",saturdayCount.toString()+" smjena",(saturdayCount*12).toString()+"h")
                    DetailLine(Icons.Outlined.Event,"Nedjelje",sundayCount.toString()+" smjena",(sundayCount*12).toString()+"h")
                    DetailLine(Icons.Outlined.Celebration,"Blagdani",holidayShiftCount.toString()+" smjena",(holidayShiftCount*12).toString()+"h")
                    DetailLine(Icons.Outlined.BeachAccess,"GO",data.values.count{it.code=="GO"}.toString()+" dana",(data.values.count{it.code=="GO"}*8).toString()+"h")
                    DetailLine(Icons.Outlined.MedicalServices,"BO",data.values.count{it.code=="BO"}.toString()+" dana",(data.values.count{it.code=="BO"}*8).toString()+"h")
                }
            }
        }
    }
}

@Composable private fun HoursDonut(dayHours:Int,nightHours:Int,total:Int){
    val size=142.dp
    Box(Modifier.size(size),contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val stroke=18.dp.toPx()
            drawArc(
                color=Color(0xFFE6EDF5),
                startAngle=-90f,
                sweepAngle=360f,
                useCenter=false,
                style=Stroke(width=stroke)
            )
            if(total>0){
                val daySweep=360f*(dayHours.toFloat()/total.toFloat())
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
                    sweepAngle=360f-daySweep,
                    useCenter=false,
                    style=Stroke(width=stroke)
                )
            }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Text(total.toString()+"h",fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
            Text("ukupno",fontSize=11.sp,color=Slate)
        }
    }
}
@Composable private fun StatMini(label:String,value:String,color:Color,modifier:Modifier){Column(modifier.padding(4.dp)){Box(Modifier.size(18.dp).background(color,RoundedCornerShape(5.dp)));Text(value,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(label,fontSize=11.sp,color=Slate)}}
@Composable private fun DetailLine(icon:ImageVector,label:String,caption:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Cyan);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(label,fontWeight=FontWeight.Bold);Text(caption,fontSize=11.sp,color=Slate)};Text(value,fontWeight=FontWeight.Bold,fontSize=18.sp)}}

@Composable private fun SettingsScreen(){
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),contentPadding=PaddingValues(top=10.dp,bottom=20.dp)){
        item{Text("Postavke",fontSize=31.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.height(14.dp));Surface(shape=RoundedCornerShape(20.dp),color=Color.White){Column(Modifier.padding(18.dp)){Text("Izgled i pristupačnost",fontSize=20.sp,fontWeight=FontWeight.Bold);SettingSwitch("Tamni način","Navy/dark surface uz iste statusne boje.");SettingSwitch("Smanjene animacije","Smanjuje prijelaze i motion efekte.")}}}
    }
}
@Composable private fun SettingSwitch(title:String,caption:String){var checked by remember{mutableStateOf(false)};Row(Modifier.fillMaxWidth().padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(caption,fontSize=12.sp,color=Slate)};Switch(checked=checked,onCheckedChange={checked=it})}}

@Composable private fun ShiftChip(s:Shift,modifier:Modifier){Surface(modifier=modifier.height(40.dp),shape=RoundedCornerShape(11.dp),color=shiftBg(s)){Box(contentAlignment=Alignment.Center){Text(s.code,fontWeight=FontWeight.ExtraBold,color=shiftFg(s),fontSize=13.sp)}}}
@Composable private fun ShiftBadge(s:Shift,size:androidx.compose.ui.unit.Dp){Surface(shape=RoundedCornerShape(14.dp),color=shiftBg(s),modifier=Modifier.size(size)){Box(contentAlignment=Alignment.Center){Text(s.code,fontSize=if(size>50.dp)24.sp else 14.sp,fontWeight=FontWeight.ExtraBold,color=shiftFg(s))}}}
private fun shiftBg(s:Shift)=when(s.code){"D"->Dbg;"N"->Nbg;"GO"->GObg;"BO"->BObg;else->Color(0xFFF1F5F9)}
private fun shiftFg(s:Shift)=when(s.code){"D"->Color(0xFF087BC9);"N"->Color.White;"GO"->Color(0xFF07865F);"BO"->Color(0xFFD22333);else->Slate}
