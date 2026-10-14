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
 private var commentItems by mutableStateOf<List<JSONObject>>(emptyList())
 private var analyticsDays by mutableStateOf("28")
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
  setContent{StudioApp(onConnect={connect()},connectionStatus=connectionStatus,dashboardData=dashboardData,videoItems=videoItems,commentItems=commentItems,analyticsDays=analyticsDays,dataLoading=dataLoading,dataError=dataError,onRefresh={loadYouTubeData()},onPeriodChange={loadYouTubeData(it)},onReply={id,text->replyToComment(id,text)},onOpenUrl={openUrl(it)})}
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

 private fun loadYouTubeData(days: String = analyticsDays) {
  val session = nativeSession ?: return
  analyticsDays = if (days in listOf("7","28","90","365")) days else "28"
  dataLoading = true
  dataError = null
  Thread {
   try {
    val dashboard = apiGet("/api/youtube/data?days=" + analyticsDays, session)
    val videos = apiGet("/api/youtube/videos", session)
    val comments = try { apiGet("/api/youtube/comments", session) } catch (_: Exception) { JSONObject() }
    val commentArray = comments.optJSONArray("items")
    val parsedComments = mutableListOf<JSONObject>()
    if (commentArray != null) for (i in 0 until commentArray.length()) {
     val item = commentArray.optJSONObject(i)
     if (item != null) parsedComments.add(item)
    }
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


 private fun replyToComment(commentId: String, text: String) {
  val session = nativeSession ?: return
  if (text.isBlank()) return
  dataLoading = true
  Thread {
   var message = "Reply posted to YouTube."
   try {
    val connection = (URL("https://ytdash-ashy.vercel.app/api/youtube/comments").openConnection() as HttpURLConnection)
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.setRequestProperty("Authorization", "Bearer " + session)
    connection.setRequestProperty("Content-Type", "application/json")
    connection.connectTimeout = 15000
    connection.readTimeout = 20000
    try {
     val payload = JSONObject().put("action","reply").put("commentId",commentId).put("text",text)
     connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
     val code = connection.responseCode
     val stream = if (code in 200..299) connection.inputStream else connection.errorStream
     val body = stream.bufferedReader().use { it.readText() }
     val result = JSONObject(body)
     if (code !in 200..299) throw IllegalStateException(result.optString("error","Could not reply to comment."))
    } finally { connection.disconnect() }
   } catch (e: Exception) { message = e.message ?: "Could not reply to comment." }
   runOnUiThread {
    connectionStatus = message
    dataLoading = false
    if (message == "Reply posted to YouTube.") loadYouTubeData()
   }
  }.start()
 }

 private fun openUrl(url: String) {
  try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {
   connectionStatus = "Could not open this link."
  }
 }

 private fun connect(){
  val uri=Uri.parse("https://ytdash-ashy.vercel.app/api/youtube/connect?native=1")
  try{CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this,uri)}
  catch(_:Exception){startActivity(Intent(Intent.ACTION_VIEW,uri))}
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StudioApp(onConnect:()->Unit,connectionStatus:String?,dashboardData:JSONObject?,videoItems:List<JSONObject>,commentItems:List<JSONObject>,analyticsDays:String,dataLoading:Boolean,dataError:String?,onRefresh:()->Unit,onPeriodChange:(String)->Unit,onReply:(String,String)->Unit,onOpenUrl:(String)->Unit){
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
 Tab.Dashboard->{item{Text(dashboardData?.optJSONObject("channel")?.optString("title")?.takeIf{it.isNotBlank()} ?: "Your channel,\nat a glance.",fontSize=32.sp,lineHeight=37.sp,fontWeight=FontWeight.Bold);Text("Live data from your connected YouTube channel.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))};item{Surface(color=Panel,shape=RoundedCornerShape(20.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha=.15f)),contentAlignment=Alignment.Center){Text("▶",color=Accent,fontSize=20.sp)};Spacer(Modifier.width(12.dp));Column{Text(dashboardData?.optJSONObject("channel")?.optString("title")?.takeIf{it.isNotBlank()} ?: "Connect your channel",fontWeight=FontWeight.SemiBold);Text(if(dashboardData!=null) "Connected · live data enabled" else "Sign in to load real data",fontSize=11.sp,color=Muted)}};HorizontalDivider(color=Color.White.copy(alpha=.08f));Text(if(dashboardData!=null) "Your channel data is loaded from YouTube. Refresh any time to get the latest numbers." else "Channel metrics are not fabricated. Connect YouTube to request authorized data.",fontSize=12.sp,lineHeight=18.sp,color=Muted);Button(onClick={if(dashboardData!=null) onRefresh() else onConnect()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text(if(dashboardData!=null) "Refresh YouTube data" else "Connect YouTube",fontWeight=FontWeight.Bold)}}}};item{Text("CHANNEL OVERVIEW",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted,fontWeight=FontWeight.Bold)};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Channel views",dashboardData?.optJSONObject("channel")?.optString("views") ?: "—","Lifetime total",Modifier.weight(1f));Metric("Watch time",dashboardData?.optJSONObject("analytics")?.optString("watchTime") ?: "—","Hours · last 28 days",Modifier.weight(1f))}};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Subscribers",dashboardData?.optJSONObject("channel")?.optString("subscribers") ?: "—","Current total",Modifier.weight(1f));Metric("Videos",dashboardData?.optJSONObject("channel")?.optString("videos") ?: "—","Published uploads",Modifier.weight(1f))}};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){if(dataLoading) CircularProgressIndicator(modifier=Modifier.size(18.dp),strokeWidth=2.dp,color=Accent);Text(dataError ?: "Refresh channel data",fontSize=11.sp,color=Muted,modifier=Modifier.weight(1f));TextButton(onClick=onRefresh){Text("Refresh",color=Accent)}}};item{Text("QUICK ACTIONS",fontSize=10.sp,letterSpacing=1.5.sp,color=Muted,fontWeight=FontWeight.Bold)};item{Action("Manage videos","Search, review, and edit uploads",Icons.Default.VideoLibrary){tab=Tab.Content}};item{Action("Community inbox","Read and respond to comments",Icons.Default.ChatBubbleOutline){tab=Tab.Comments}};item{Action("Performance","Review channel analytics",Icons.Default.Analytics){tab=Tab.Analytics}}}
  Tab.Analytics->{item{Intro("Analytics","Performance metrics from YouTube Analytics. Select a reporting window.")};item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("7","28","90","365").forEach{days->FilterChip(selected=analyticsDays==days,onClick={onPeriodChange(days)},label={Text("$days days",fontSize=10.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Accent.copy(alpha=.18f),selectedLabelColor=Color.White))}}};item{Metric("Views · $analyticsDays days",dashboardData?.optJSONObject("analytics")?.optString("views") ?: "—","Reported by YouTube Analytics",Modifier.fillMaxWidth())};item{Metric("Watch time",dashboardData?.optJSONObject("analytics")?.optString("watchTime") ?: "—","Hours · selected period",Modifier.fillMaxWidth())};item{Metric("Subscribers gained",dashboardData?.optJSONObject("analytics")?.optString("subscribers") ?: "—","Selected period",Modifier.fillMaxWidth())};item{Metric("Likes",dashboardData?.optJSONObject("analytics")?.optString("likes") ?: "—","Selected period",Modifier.fillMaxWidth())};item{Metric("Comments",dashboardData?.optJSONObject("analytics")?.optString("comments") ?: "—","Selected period",Modifier.fillMaxWidth())};item{Metric("Shares",dashboardData?.optJSONObject("analytics")?.optString("shares") ?: "—","Selected period",Modifier.fillMaxWidth())};item{if(dataLoading) CircularProgressIndicator(color=Accent) else if(dataError!=null) Text(dataError,color=Accent,fontSize=12.sp); Button(onClick=onRefresh,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Refresh analytics")}}}
 Tab.Content->{item{Intro("Your content","Native content workspace for video management.")};item{OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search your videos")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(13.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=Panel,focusedTextColor=Color.White,unfocusedTextColor=Color.White))};item{Button(onClick={upload=true},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Icon(Icons.Default.CloudUpload,null);Spacer(Modifier.width(8.dp));Text("Upload video")}};items(videoItems.filter { v -> val sn=v.optJSONObject("snippet"); search.isBlank() || (sn?.optString("title") ?: "").contains(search,true) }.size){ index -> val filtered=videoItems.filter { item -> val sn=item.optJSONObject("snippet"); search.isBlank() || (sn?.optString("title") ?: "").contains(search,true) }; val v=filtered[index]; val sn=v.optJSONObject("snippet"); Surface(color=Panel,shape=RoundedCornerShape(14.dp)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(sn?.optString("title") ?: "Untitled video",fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text("Published · "+(sn?.optString("publishedAt")?.take(10) ?: "date unavailable"),fontSize=10.sp,color=Muted);Text("Views: "+(v.optJSONObject("statistics")?.optString("viewCount") ?: "—"),fontSize=10.sp,color=Muted)}}}};if(videoItems.isEmpty()){item{Empty(if(dataLoading)"Loading your uploads…" else "No uploads found",dataError ?: "Your channel has no accessible uploads yet, or the API request failed.",onConnect)}}}
  Tab.Comments->{item{Intro("Community inbox","Read real comment threads and reply from the app.")};if(commentItems.isEmpty()){item{Empty(if(dataLoading)"Loading comments…" else "No comments found","Check your connection or open the web workspace for more moderation tools.",onConnect)}};items(commentItems.size){index->val thread=commentItems[index];val top=thread.optJSONObject("snippet")?.optJSONObject("topLevelComment");val sn=top?.optJSONObject("snippet");val commentId=top?.optString("id") ?: thread.optString("id");Surface(color=Panel,shape=RoundedCornerShape(15.dp)){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(sn?.optString("authorDisplayName") ?: "YouTube viewer",fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text(sn?.optString("textDisplay") ?: "Comment unavailable",fontSize=12.sp,color=Color(0xFFE1E1E8),lineHeight=18.sp);Text((sn?.optString("publishedAt")?.take(10) ?: "")+" · "+thread.optJSONObject("snippet")?.optInt("totalReplyCount",0)+" replies",fontSize=10.sp,color=Muted);if(replyFor==commentId){OutlinedTextField(value=replyText,onValueChange={replyText=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Write a helpful reply")},shape=RoundedCornerShape(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={replyFor="";replyText=""}){Text("Cancel")};Button(onClick={onReply(commentId,replyText);replyFor="";replyText=""},enabled=replyText.isNotBlank()&&!dataLoading,colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Post reply")}}}else{TextButton(onClick={replyFor=commentId;replyText=""}){Icon(Icons.Default.Reply,null);Spacer(Modifier.width(6.dp));Text("Reply to comment")}}}}}}
  Tab.Settings->{item{Intro("Settings","Account, permissions, and creator platform shortcuts.")};item{Surface(color=Panel,shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("YouTube account",fontWeight=FontWeight.SemiBold);Text("Account authorization opens in your system browser, not an embedded WebView.",fontSize=12.sp,color=Muted,lineHeight=18.sp);Button(onClick=onConnect,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Connect / reconnect")};OutlinedButton(onClick={onOpenUrl("https://studio.youtube.com/")},modifier=Modifier.fillMaxWidth()){Text("Open YouTube Studio")};OutlinedButton(onClick={onOpenUrl("https://www.tiktok.com/tiktokstudio")},modifier=Modifier.fillMaxWidth()){Text("Open TikTok Studio")};Text("TikTok analytics are not connected; a separate approved integration is required.",fontSize=10.sp,color=Muted,lineHeight=16.sp)}}};item{Text("Ytdash Studio · Native Android\nIndependent app, not affiliated with Google.",fontSize=10.sp,color=Muted,lineHeight=16.sp)}}
 }
 }}
 if(upload)AlertDialog(onDismissRequest={upload=false},title={Text("Upload video")},text={Text("Video upload requires a configured YouTube OAuth connection. Connect your account first.")},confirmButton={TextButton(onClick={upload=false;onConnect()}){Text("Connect account")}},dismissButton={TextButton(onClick={upload=false}){Text("Cancel")}},containerColor=Panel)
 }}
@Composable private fun Metric(label:String,value:String,detail:String,modifier:Modifier=Modifier){Surface(modifier=modifier,color=Panel,shape=RoundedCornerShape(17.dp)){Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(label,fontSize=11.sp,color=Muted);Text(value,fontSize=26.sp,fontWeight=FontWeight.Bold);Text(detail,fontSize=9.sp,color=Muted)}}}
@Composable private fun Intro(title:String,subtitle:String){Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontSize=27.sp,fontWeight=FontWeight.Bold);Text(subtitle,fontSize=12.sp,lineHeight=18.sp,color=Muted)}}
@Composable private fun Empty(title:String,description:String,onConnect:()->Unit){Surface(color=Panel,shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Default.CloudDone,null,tint=Muted,modifier=Modifier.size(28.dp));Text(title,fontWeight=FontWeight.SemiBold);Text(description,fontSize=11.sp,color=Muted,lineHeight=17.sp);Button(onClick=onConnect,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF292930))){Text("Connect YouTube",color=Color.White)}}}}
@Composable private fun Action(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),color=Panel,shape=RoundedCornerShape(14.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Muted,modifier=Modifier.size(22.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=10.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}
