package hr.raspored.app.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.raspored.app.BuildConfig
import hr.raspored.app.R
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
                    Screen.Home->HomeScreen{screen=it}
                    Screen.Calendar->CalendarScreen()
                    Screen.Scan->ScanScreen()
                    Screen.Stats->StatsScreen()
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
private fun scheduleFor(month:YearMonth):Map<Int,Shift> = if(BuildConfig.DEBUG) sampleSchedule(month) else emptyMap()
private fun weeklyHours(month:YearMonth,data:Map<Int,Shift>):List<Int> =
    (0..4).map { week ->
        val first=week*7+1
        val last=minOf(month.lengthOfMonth(),first+6)
        if(first>month.lengthOfMonth()) 0 else (first..last).sumOf { data[it]?.hours ?: 0 }
    }

@Composable private fun HomeScreen(go:(Screen)->Unit){
    val today=appDate()
    val month=YearMonth.from(today)
    val data=scheduleFor(month)
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

@Composable private fun CalendarScreen(){
    val today=appDate()
    val month=YearMonth.from(today)
    val data=scheduleFor(month)
    val worked=data.values.sumOf{it.hours}
    val night=data.values.filter{it.code=="N"}.sumOf{it.hours}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(top=16.dp,bottom=22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Surface(shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=2.dp){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={}){Icon(Icons.Outlined.ChevronLeft,null)};Text(month.month.getDisplayName(TextStyle.FULL,Locale("hr","HR")).replaceFirstChar{it.titlecase(Locale("hr","HR"))}+" "+month.year+".",fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center);IconButton(onClick={}){Icon(Icons.Outlined.ChevronRight,null)}};CalendarGrid(month,data)}}}
        item{Surface(shape=RoundedCornerShape(20.dp),color=Color.White){Column(Modifier.padding(16.dp)){Row{Column(Modifier.weight(1f)){Text("Danas",fontSize=24.sp,fontWeight=FontWeight.Bold);Text(today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy.",Locale("hr","HR"))).replaceFirstChar{it.titlecase(Locale("hr","HR"))},color=Slate)};Text("▦ Blagdan",color=Red,fontWeight=FontWeight.Bold)};Divider(Modifier.padding(vertical=12.dp));Row(verticalAlignment=Alignment.CenterVertically){ShiftBadge(BO,62.dp);Spacer(Modifier.width(14.dp));Text("Blagdan (neradni dan)",fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)}}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){listOf(D,N,GO,BO).forEach{ShiftChip(it,Modifier.weight(1f))}}}
        item{Surface(shape=RoundedCornerShape(20.dp),color=Color.White){Column(Modifier.padding(16.dp)){Text("Sažetak za mjesec",fontSize=20.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Row{listOf("Planirano" to worked.toString()+"h","Odrađeno" to worked.toString()+"h","Saldo" to "0h","Noćni sati" to night.toString()+"h").forEach{Column(Modifier.weight(1f)){Text(it.first,fontSize=11.sp,color=Slate);Text(it.second,fontWeight=FontWeight.Bold,fontSize=18.sp)}}}}}}
    }
}
@Composable private fun CalendarGrid(month:YearMonth,data:Map<Int,Shift>){
    val firstOffset=(month.atDay(1).dayOfWeek.value-1)
    val cells=List(42){idx->idx-firstOffset+1}
    Row(Modifier.fillMaxWidth()){listOf("Pon","Uto","Sri","Čet","Pet","Sub","Ned").forEach{Text(it,modifier=Modifier.weight(1f).padding(vertical=8.dp),fontSize=12.sp,color=Slate,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
    cells.chunked(7).forEach{week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){week.forEach{day->val valid=day in 1..month.lengthOfMonth();val shift=if(valid)data[day] else null;Surface(shape=RoundedCornerShape(10.dp),color=if(shift!=null) shiftBg(shift) else Color(0xFFF1F5F9),modifier=Modifier.weight(1f).aspectRatio(.9f)){Box(contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(valid) day.toString() else "",fontWeight=FontWeight.Bold,color=if(shift?.code=="N") Color.White else Navy);if(shift!=null)Text(shift.code,fontWeight=FontWeight.Bold,color=shiftFg(shift))}}}}}}
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

@Composable private fun StatsScreen(){
    val month=YearMonth.from(appDate());val data=scheduleFor(month);val worked=data.values.sumOf{it.hours};val night=data.values.filter{it.code=="N"}.sumOf{it.hours};val weeks=weeklyHours(month,data)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(top=16.dp,bottom=22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Statistika",fontSize=31.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.weight(1f));OutlinedButton(onClick={}){Icon(Icons.Outlined.CalendarMonth,null);Text(" "+month.month.getDisplayName(TextStyle.FULL,Locale("hr","HR")).replaceFirstChar{it.titlecase(Locale("hr","HR"))}+" "+month.year+". ")}}}
        item{Surface(shape=RoundedCornerShape(20.dp),color=Color.White){Column(Modifier.padding(18.dp)){Text("Ukupno odrađeno sati",fontWeight=FontWeight.Bold);Text(worked.toString()+":00 h",fontSize=48.sp,fontWeight=FontWeight.ExtraBold);Text("↗ +8%  u odnosu na rujan",color=Teal,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){StatMini("Dnevne",(data.values.count{it.code=="D"}*12).toString()+"h",Cyan,Modifier.weight(1f));StatMini("Noćne",night.toString()+"h",Nbg,Modifier.weight(1f));StatMini("GO",(data.values.count{it.code=="GO"}*8).toString()+"h",Teal,Modifier.weight(1f));StatMini("BO",(data.values.count{it.code=="BO"}*8).toString()+"h",Red,Modifier.weight(1f))}}}}
        item{Surface(shape=RoundedCornerShape(20.dp),color=Color.White){Column(Modifier.padding(18.dp)){Text("Raspodjela sati po tjednima",fontSize=20.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(16.dp));Row(Modifier.height(150.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround,verticalAlignment=Alignment.Bottom){weeks.take(4).forEachIndexed{i,h->Column(horizontalAlignment=Alignment.CenterHorizontally){Text(h.toString()+"h",fontWeight=FontWeight.Bold);Box(Modifier.width(54.dp).height((h*2).dp).background(Cyan,RoundedCornerShape(7.dp,7.dp,0.dp,0.dp)));Text((i+1).toString()+". tjedan",fontSize=10.sp,color=Slate)}}}}}}
        item{Surface(shape=RoundedCornerShape(20.dp),color=Color.White){Column(Modifier.padding(18.dp)){Text("Detaljna statistika",fontSize=20.sp,fontWeight=FontWeight.Bold);DetailLine(Icons.Outlined.WbSunny,"Dnevne smjene",data.values.count{it.code=="D"}.toString()+" smjena",(data.values.count{it.code=="D"}*12).toString()+"h");DetailLine(Icons.Outlined.DarkMode,"Noćne smjene",data.values.count{it.code=="N"}.toString()+" smjena",night.toString()+"h");DetailLine(Icons.Outlined.CalendarMonth,"GO",data.values.count{it.code=="GO"}.toString()+" dana",(data.values.count{it.code=="GO"}*8).toString()+"h");DetailLine(Icons.Outlined.Event,"BO",data.values.count{it.code=="BO"}.toString()+" dana",(data.values.count{it.code=="BO"}*8).toString()+"h")}}}
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
