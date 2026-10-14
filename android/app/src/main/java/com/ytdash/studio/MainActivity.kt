package com.ytdash.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent

private val Bg=Color(0xFF0D0D10)
private val Panel=Color(0xFF18181D)
private val Muted=Color(0xFF9696A3)
private val Accent=Color(0xFFFF555D)
private enum class Tab(val label:String){Dashboard("Dashboard"),Analytics("Analytics"),Content("Content"),Comments("Comments"),Settings("Settings")}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(13,13,16);window.navigationBarColor=android.graphics.Color.rgb(13,13,16);setContent{StudioApp{connect()}}}
 private fun connect(){val uri=Uri.parse("https://ytdash-z-hub.vercel.app/api/youtube/connect?native=1");try{CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this,uri)}catch(_:Exception){startActivity(Intent(Intent.ACTION_VIEW,uri))}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StudioApp(onConnect:()->Unit){
 var tab by remember{mutableStateOf(Tab.Dashboard)}
 var search by remember{mutableStateOf("")}
 var upload by remember{mutableStateOf(false)}
 MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Bg,surface=Panel,onSurface=Color.White,onBackground=Color.White,secondary=Muted)){
 Scaffold(containerColor=Bg,topBar={TopAppBar(title={Column{Text("YTDASH STUDIO",fontSize=10.sp,letterSpacing=2.sp,color=Muted);Text(tab.label,fontSize=21.sp,fontWeight=FontWeight.Bold)}},actions={IconButton(onClick=onConnect){Icon(Icons.Default.AccountCircle,"Connect account",tint=Color.White)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg))},
 bottomBar={NavigationBar(containerColor=Panel){Tab.entries.forEach{t->val icon=when(t){Tab.Dashboard->Icons.Default.Home;Tab.Analytics->Icons.Default.Analytics;Tab.Content->Icons.Default.VideoLibrary;Tab.Comments->Icons.Default.ChatBubbleOutline;Tab.Settings->Icons.Default.Settings};NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Icon(icon,t.label)},label={Text(t.label,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Accent,indicatorColor=Accent.copy(alpha=.14f),unselectedIconColor=Muted,selectedTextColor=Color.White,unselectedTextColor=Muted))}}},
 ){padding->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
 item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(CircleShape).background(Accent));Spacer(Modifier.width(8.dp));Text("CREATOR WORKSPACE",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted);Spacer(Modifier.weight(1f));Text("NATIVE ANDROID",fontSize=9.sp,color=Muted)}}
 when(tab){
 Tab.Dashboard->{item{Text("Your channel,\nat a glance.",fontSize=32.sp,lineHeight=37.sp,fontWeight=FontWeight.Bold);Text("A native workspace for your creator workflow.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))};item{Surface(color=Panel,shape=RoundedCornerShape(20.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha=.15f)),contentAlignment=Alignment.Center){Text("▶",color=Accent,fontSize=20.sp)};Spacer(Modifier.width(12.dp));Column{Text("Connect your channel",fontWeight=FontWeight.SemiBold);Text("Sign in to load real data",fontSize=11.sp,color=Muted)}};HorizontalDivider(color=Color.White.copy(alpha=.08f));Text("Channel metrics are not fabricated. Connect YouTube to request authorized data.",fontSize=12.sp,lineHeight=18.sp,color=Muted);Button(onClick=onConnect,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Connect YouTube",fontWeight=FontWeight.Bold)}}}};item{Text("CHANNEL OVERVIEW",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted,fontWeight=FontWeight.Bold)};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Views","—","Last 28 days",Modifier.weight(1f));Metric("Watch time","—","Hours",Modifier.weight(1f))}};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Subscribers","—","Current total",Modifier.weight(1f));Metric("Videos","—","Published uploads",Modifier.weight(1f))}};item{Text("QUICK ACTIONS",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted,fontWeight=FontWeight.Bold)};item{Action("Manage videos","Search, review, and edit uploads",Icons.Default.VideoLibrary){tab=Tab.Content}};item{Action("Community inbox","Read and respond to comments",Icons.Default.ChatBubbleOutline){tab=Tab.Comments}};item{Action("Performance","Review channel analytics",Icons.Default.Analytics){tab=Tab.Analytics}}}
 Tab.Analytics->{item{Intro("Analytics","Channel performance from YouTube Analytics API.")};item{Metric("Views · 28 days","—","Waiting for authorized data",Modifier.fillMaxWidth())};item{Metric("Watch time","—","Hours · last 28 days",Modifier.fillMaxWidth())};item{Metric("Subscribers gained","—","Last 28 days",Modifier.fillMaxWidth())};item{Empty("No analytics loaded","Connect your channel to request real reporting data.",onConnect)}}
 Tab.Content->{item{Intro("Your content","Native content workspace for video management.")};item{OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search your videos")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(13.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=Panel,focusedTextColor=Color.White,unfocusedTextColor=Color.White))};item{Button(onClick={upload=true},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Icon(Icons.Default.CloudUpload,null);Spacer(Modifier.width(8.dp));Text("Upload video")}};item{Empty(if(search.isBlank())"Your uploads will appear here" else "No matching videos","Connect YouTube to load and edit your real uploads.",onConnect)}}
 Tab.Comments->{item{Intro("Community inbox","Manage conversations with your viewers.")};item{Empty("No comments loaded","Connect YouTube to load comment threads and available moderation actions.",onConnect)}}
 Tab.Settings->{item{Intro("Settings","Account, permissions, and privacy.")};item{Surface(color=Panel,shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("YouTube account",fontWeight=FontWeight.SemiBold);Text("Account authorization opens in your system browser, not an embedded WebView.",fontSize=12.sp,color=Muted,lineHeight=18.sp);Button(onClick=onConnect,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Connect / reconnect")}}}};item{Text("Ytdash Studio · Native Android\nIndependent app, not affiliated with Google.",fontSize=10.sp,color=Muted,lineHeight=16.sp)}}
 }
 }}
 if(upload)AlertDialog(onDismissRequest={upload=false},title={Text("Upload video")},text={Text("Video upload requires a configured YouTube OAuth connection. Connect your account first.")},confirmButton={TextButton(onClick={upload=false;onConnect()}){Text("Connect account")}},dismissButton={TextButton(onClick={upload=false}){Text("Cancel")}},containerColor=Panel)
 }}
@Composable private fun Metric(label:String,value:String,detail:String,modifier:Modifier=Modifier){Surface(modifier=modifier,color=Panel,shape=RoundedCornerShape(17.dp)){Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(label,fontSize=11.sp,color=Muted);Text(value,fontSize=26.sp,fontWeight=FontWeight.Bold);Text(detail,fontSize=9.sp,color=Muted)}}}
@Composable private fun Intro(title:String,subtitle:String){Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontSize=27.sp,fontWeight=FontWeight.Bold);Text(subtitle,fontSize=12.sp,lineHeight=18.sp,color=Muted)}}
@Composable private fun Empty(title:String,description:String,onConnect:()->Unit){Surface(color=Panel,shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Default.CloudDone,null,tint=Muted,modifier=Modifier.size(28.dp));Text(title,fontWeight=FontWeight.SemiBold);Text(description,fontSize=11.sp,color=Muted,lineHeight=17.sp);Button(onClick=onConnect,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF292930))){Text("Connect YouTube",color=Color.White)}}}}
@Composable private fun Action(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),color=Panel,shape=RoundedCornerShape(14.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Muted,modifier=Modifier.size(22.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=10.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}
