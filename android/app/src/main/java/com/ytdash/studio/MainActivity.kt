package com.ytdash.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
 private var connectionStatus by mutableStateOf<String?>(null)
 private var dashboardData by mutableStateOf<JSONObject?>(null)
 private var videoItems by mutableStateOf<List<JSONObject>>(emptyList())
 private var dataLoading by mutableStateOf(false)
 private var dataError by mutableStateOf<String?>(null)
 private var nativeSession: String? = null

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  nativeSession = getSharedPreferences("ytdash", MODE_PRIVATE).getString("session", null)
  handleOAuthIntent(intent)
  if (nativeSession != null) loadYouTubeData()
  window.statusBarColor=android.graphics.Color.rgb(13,13,16)
  window.navigationBarColor=android.graphics.Color.rgb(13,13,16)
  setContent{StudioApp(onConnect={connect()},connectionStatus=connectionStatus,dashboardData=dashboardData,videoItems=videoItems,dataLoading=dataLoading,dataError=dataError,onRefresh={loadYouTubeData()})}
 }

 override fun onNewIntent(intent:Intent){
  super.onNewIntent(intent)
  setIntent(intent)
  handleOAuthIntent(intent)
 }

 private fun handleOAuthIntent(intent:Intent?){
  val data=intent?.data ?: return
  if(data.scheme!="ytdash" || data.host!="oauth" || data.path!="/callback") return
  val receivedSession = data.getQueryParameter("session")
  if (!receivedSession.isNullOrBlank()) {
   nativeSession = receivedSession
   getSharedPreferences("ytdash", MODE_PRIVATE).edit().putString("session", receivedSession).apply()
  }
  connectionStatus=when{
   data.getQueryParameter("connected")=="1" -> "YouTube connected. Loading your channel data…"
   data.getQueryParameter("error")!=null -> "Connection failed: ${data.getQueryParameter("error")?.replace('_',' ')}"
   else -> "Returned from YouTube authorization."
  }
  if (!receivedSession.isNullOrBlank()) loadYouTubeData()
 }

 private fun loadYouTubeData() {
  val session = nativeSession ?: return
  dataLoading = true
  dataError = null
  Thread {
   try {
    val dashboard = apiGet("/api/youtube/data", session)
    val videos = apiGet("/api/youtube/videos", session)
    val items = videos.optJSONArray("items")
    val parsedVideos = mutableListOf<JSONObject>()
    if (items != null) for (i in 0 until items.length()) {
     val item = items.optJSONObject(i)
     if (item != null) parsedVideos.add(item)
    }
    runOnUiThread {
     dashboardData = dashboard
     videoItems = parsedVideos
     dataLoading = false
     connectionStatus = "YouTube connected. Your channel data is loaded."
    }
   } catch (e: Exception) {
    runOnUiThread {
     dataLoading = false
     dataError = e.message ?: "Could not load YouTube data."
     connectionStatus = "Connected, but data could not load: ${e.message ?: "request failed"}"
    }
   }
  }.start()
 }

 private fun apiGet(path: String, session: String): JSONObject {
  val connection = (URL("https://ytdash-ashy.vercel.app" + path).openConnection() as HttpURLConnection)
  connection.requestMethod = "GET"
  connection.setRequestProperty("Authorization", "Bearer " + session)
  connection.setRequestProperty("Accept", "application/json")
  connection.connectTimeout = 15000
  connection.readTimeout = 20000
  try {
   val code = connection.responseCode
   val stream = if (code in 200..299) connection.inputStream else connection.errorStream
   val body = stream.bufferedReader().use { it.readText() }
   val json = JSONObject(body)
   if (code !in 200..299) throw IllegalStateException(json.optString("error", "API request failed ($code)"))
   if (json.has("connected") && !json.optBoolean("connected")) throw IllegalStateException("Session not accepted. Reconnect YouTube.")
   if (json.has("error") && json.optString("error").isNotBlank()) throw IllegalStateException(json.optString("error"))
   return json
  } finally { connection.disconnect() }
 }

 private fun connect(){
  val uri=Uri.parse("https://ytdash-ashy.vercel.app/api/youtube/connect?native=1")
  try{CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this,uri)}
  catch(_:Exception){startActivity(Intent(Intent.ACTION_VIEW,uri))}
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StudioApp(onConnect:()->Unit,connectionStatus:String?,dashboardData:JSONObject?,videoItems:List<JSONObject>,dataLoading:Boolean,dataError:String?,onRefresh:()->Unit){
 var tab by remember{mutableStateOf(Tab.Dashboard)}
 var search by remember{mutableStateOf("")}
 var upload by remember{mutableStateOf(false)}
 MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Bg,surface=Panel,onSurface=Color.White,onBackground=Color.White,secondary=Muted)){
 Scaffold(containerColor=Bg,topBar={TopAppBar(title={Column{Text("YTDASH STUDIO",fontSize=10.sp,letterSpacing=2.sp,color=Muted);Text(tab.label,fontSize=21.sp,fontWeight=FontWeight.Bold)}},actions={IconButton(onClick=onConnect){Icon(Icons.Default.AccountCircle,"Connect account",tint=Color.White)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg))},
 bottomBar={NavigationBar(containerColor=Panel){Tab.entries.forEach{t->val icon=when(t){Tab.Dashboard->Icons.Default.Home;Tab.Analytics->Icons.Default.Analytics;Tab.Content->Icons.Default.VideoLibrary;Tab.Comments->Icons.Default.ChatBubbleOutline;Tab.Settings->Icons.Default.Settings};NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Icon(icon,t.label)},label={Text(t.label,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Accent,indicatorColor=Accent.copy(alpha=.14f),unselectedIconColor=Muted,selectedTextColor=Color.White,unselectedTextColor=Muted))}}},
 ){padding->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
 item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(CircleShape).background(Accent));Spacer(Modifier.width(8.dp));Text("CREATOR WORKSPACE",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted);Spacer(Modifier.weight(1f));Text("NATIVE ANDROID",fontSize=9.sp,color=Muted)}}
 if(connectionStatus!=null){item{Surface(color=Panel,shape=RoundedCornerShape(14.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(connectionStatus.startsWith("YouTube connected"))Icons.Default.CheckCircle else Icons.Default.Info,null,tint=if(connectionStatus.startsWith("YouTube connected"))Color(0xFF65D6A0) else Accent);Spacer(Modifier.width(10.dp));Text(connectionStatus,fontSize=12.sp,color=Color.White,lineHeight=17.sp)}}}}
 when(tab){
 Tab.Dashboard->{item{Text(dashboardData?.optJSONObject("channel")?.optString("title")?.takeIf{it.isNotBlank()} ?: "Your channel,\nat a glance.",fontSize=32.sp,lineHeight=37.sp,fontWeight=FontWeight.Bold);Text("Live data from your connected YouTube channel.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))};item{Surface(color=Panel,shape=RoundedCornerShape(20.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha=.15f)),contentAlignment=Alignment.Center){Text("▶",color=Accent,fontSize=20.sp)};Spacer(Modifier.width(12.dp));Column{Text("Connect your channel",fontWeight=FontWeight.SemiBold);Text("Sign in to load real data",fontSize=11.sp,color=Muted)}};HorizontalDivider(color=Color.White.copy(alpha=.08f));Text("Channel metrics are not fabricated. Connect YouTube to request authorized data.",fontSize=12.sp,lineHeight=18.sp,color=Muted);Button(onClick=onConnect,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Connect YouTube",fontWeight=FontWeight.Bold)}}}};item{Text("CHANNEL OVERVIEW",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted,fontWeight=FontWeight.Bold)};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Views",dashboardData?.optJSONObject("analytics")?.optString("views") ?: "—","Last 28 days",Modifier.weight(1f));Metric("Watch time",dashboardData?.optJSONObject("analytics")?.optString("watchTime") ?: "—","Hours · last 28 days",Modifier.weight(1f))}};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Subscribers",dashboardData?.optJSONObject("channel")?.optString("subscribers") ?: "—","Current total",Modifier.weight(1f));Metric("Videos",dashboardData?.optJSONObject("channel")?.optString("videos") ?: "—","Published uploads",Modifier.weight(1f))}};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){if(dataLoading) CircularProgressIndicator(modifier=Modifier.size(18.dp),strokeWidth=2.dp,color=Accent);Text(dataError ?: "Refresh channel data",fontSize=11.sp,color=Muted,modifier=Modifier.weight(1f));TextButton(onClick=onRefresh){Text("Refresh",color=Accent)}}};item{Text("QUICK ACTIONS",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted,fontWeight=FontWeight.Bold)};item{Action("Manage videos","Search, review, and edit uploads",Icons.Default.VideoLibrary){tab=Tab.Content}};item{Action("Community inbox","Read and respond to comments",Icons.Default.ChatBubbleOutline){tab=Tab.Comments}};item{Action("Performance","Review channel analytics",Icons.Default.Analytics){tab=Tab.Analytics}}}
 Tab.Analytics->{item{Intro("Analytics","Channel performance from YouTube Analytics API.")};item{Metric("Views · 28 days",dashboardData?.optJSONObject("analytics")?.optString("views") ?: "—","Last 28 days",Modifier.fillMaxWidth())};item{Metric("Watch time",dashboardData?.optJSONObject("analytics")?.optString("watchTime") ?: "—","Hours · last 28 days",Modifier.fillMaxWidth())};item{Metric("Subscribers gained",dashboardData?.optJSONObject("analytics")?.optString("subscribers") ?: "—","Last 28 days",Modifier.fillMaxWidth())};item{if(dataLoading) CircularProgressIndicator(color=Accent) else if(dataError!=null) Text(dataError,color=Accent,fontSize=12.sp); Button(onClick=onRefresh,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Refresh analytics")}}}
 Tab.Content->{item{Intro("Your content","Native content workspace for video management.")};item{OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search your videos")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(13.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=Panel,focusedTextColor=Color.White,unfocusedTextColor=Color.White))};item{Button(onClick={upload=true},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Icon(Icons.Default.CloudUpload,null);Spacer(Modifier.width(8.dp));Text("Upload video")}};items(videoItems.filter { v -> val sn=v.optJSONObject("snippet"); search.isBlank() || (sn?.optString("title") ?: "").contains(search,true) }.size){ index -> val filtered=videoItems.filter { item -> val sn=item.optJSONObject("snippet"); search.isBlank() || (sn?.optString("title") ?: "").contains(search,true) }; val v=filtered[index]; val sn=v.optJSONObject("snippet"); Surface(color=Panel,shape=RoundedCornerShape(14.dp)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(sn?.optString("title") ?: "Untitled video",fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text("Published · "+(sn?.optString("publishedAt")?.take(10) ?: "date unavailable"),fontSize=10.sp,color=Muted);Text("Views: "+(v.optJSONObject("statistics")?.optString("viewCount") ?: "—"),fontSize=10.sp,color=Muted)}}}};if(videoItems.isEmpty()){item{Empty(if(dataLoading)"Loading your uploads…" else "No uploads found",dataError ?: "Your channel has no accessible uploads yet, or the API request failed.",onConnect)}}}
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
