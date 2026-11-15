package dev.scalo

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlin.math.*

class MainActivity:ComponentActivity(){
    private var onBackground:()->Unit={}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(13,16,21)
        window.navigationBarColor=window.statusBarColor
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Pink,onPrimary=Night,surface=Panel,onSurface=Paper,background=Night)){
                SongScreen { onBackground=it }
            }
        }
    }
    override fun onStop(){onBackground();super.onStop()}
}

private fun clock(ms:Int)="%d:%02d".format(ms/60000,ms/1000%60)
private fun midiName(n:Int)=keys[pc(n)]+(n/12-1)
private val mono=FontFamily.Monospace

@Composable
private fun SongScreen(bindPause:((()->Unit))->Unit){
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("songs",0) }
    var source by remember { mutableStateOf(prefs.getString("source",null)) }
    var title by remember { mutableStateOf(prefs.getString("title",null) ?: "Daybreak") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var melody by remember { mutableStateOf<Melody?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var tuning by rememberSaveable(source) { mutableIntStateOf(-1) }
    var menu by remember { mutableStateOf(false) }
    fun choose(path:String?,name:String){
        source=path;title=name
        prefs.edit().putString("source",path).putString("title",name).apply()
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){
            runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name=runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst()) it.getString(0) else null
            } }.getOrNull() ?: "Selected audio"
            choose(uri.toString(),name)
        }
    }
    LaunchedEffect(source){
        val uri=source?.let(Uri::parse)
        val mp=MediaPlayer()
        player=null;melody=null;position=0;duration=0;playing=false;progress=0;failure=null
        try {
            val analysis=withContext(Dispatchers.IO){
                val result=analyze(context,uri){progress=it}
                if(uri!=null) mp.setDataSource(context,uri)
                else context.resources.openRawResourceFd(R.raw.daybreak).use {
                    mp.setDataSource(it.fileDescriptor,it.startOffset,it.length)
                }
                mp.prepare()
                result
            }
            duration=mp.duration
            mp.setOnCompletionListener { playing=false;position=duration }
            mp.setOnErrorListener { _,_,_->failure="Playback failed. Try another audio file.";playing=false;true }
            melody=analysis;player=mp
            awaitCancellation()
        } catch(e:CancellationException){throw e}
        catch(e:Exception){failure=e.localizedMessage ?: "Unable to read this audio file."}
        finally {mp.release();player=null}
    }
    SideEffect { bindPause { if(playing){player?.pause();playing=false} } }
    LaunchedEffect(playing){
        while(playing){position=player?.currentPosition ?: 0;delay(32)}
    }
    val detected=melody?.key
    val root=if(tuning>=0) tuning else detected?.root ?: 0
    val notes=melody?.notes.orEmpty()
    val frame=frameAt(position)
    val note=notes.getOrNull(frame)?.takeIf {it.isFinite()}?.roundToInt()
    val changes=remember(notes){
        buildList<Pair<Int,Int>> {
            var last:Int?=null
            var streak=0
            var candidate:Int?=null
            for((index,f) in notes.withIndex()){
                val n=if(f.isFinite()) f.roundToInt() else null
                if(n==candidate) streak++ else {candidate=n;streak=1}
                if(streak==2 && n!=last){if(n!=null) add(index-1 to n);last=n}
            }
        }
    }
    val at=changes.binarySearchBy(frame){it.first}.let { if(it>=0) it else -it-2 }
    val scroll=rememberScrollState()
    Column(Modifier.fillMaxSize().background(Night).safeDrawingPadding().verticalScroll(scroll).padding(horizontal=18.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)){
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("Scalo",color=Paper,fontSize=26.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
            Row(verticalAlignment=Alignment.CenterVertically){
                if(source!=null) TextButton({choose(null,"Daybreak")}){Text("Demo")}
                OutlinedButton({picker.launch(arrayOf("audio/*"))}){Text("Open song",color=Paper)}
            }
        }
        Text(title,color=Paper,fontSize=32.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text(if(detected==null) "Key undetermined" else "Estimated key  ·  "+keys[detected.root]+if(detected.minor) " minor" else " major",
                color=Muted,fontSize=13.sp)
            Box {
                TextButton({menu=true}){Text("Sa = "+keys[root]+" ▾")}
                DropdownMenu(menu,{menu=false}){
                    DropdownMenuItem({Text("Automatic")},{tuning=-1;menu=false})
                    keys.forEachIndexed { i,k->DropdownMenuItem({Text("Sa = "+k)},{tuning=i;menu=false}) }
                }
            }
        }
        Graph(melody,position,root,Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp - 490).coerceIn(125,380).dp))
        HorizontalDivider(color=Panel)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("SWARA",fontFamily=mono,color=Muted,fontSize=11.sp,letterSpacing=2.sp)
            Text(if(note==null) "—" else midiName(note)+"  ·  "+roman[pc(note-root)],fontFamily=mono,color=Pink,fontSize=13.sp)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(13.dp)){
            val start=(at-2).coerceAtLeast(0)
            changes.drop(start).take(6).forEachIndexed { i,entry->
                val tone=pc(entry.second-root)
                val active=start+i==at && note!=null
                Column(horizontalAlignment=Alignment.CenterHorizontally){
                    Text(devanagari[tone],color=if(active) Pink else Paper,fontSize=26.sp,fontWeight=FontWeight.Bold)
                    Text(roman[tone],fontFamily=mono,color=if(active) Pink else Muted,fontSize=11.sp)
                }
            }
        }
        if(failure!=null) Text(failure!!,color=Pink)
        else if(melody==null) Text("Finding pitches… "+progress+"%",color=Muted)
        else if(changes.isEmpty()) Text("No clear melody detected.",color=Muted)
        Slider(position.toFloat().coerceIn(0f,max(1,duration).toFloat()),{v->
            val p=v.toInt();position=p;player?.seekTo(p.toLong(),MediaPlayer.SEEK_CLOSEST)
        },enabled=player!=null && failure==null,valueRange=0f..max(1,duration).toFloat())
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text(clock(position)+" / "+clock(duration),color=Muted,fontFamily=mono,fontSize=13.sp)
            Button({
                val mp=player ?: return@Button
                if(playing){mp.pause();position=mp.currentPosition;playing=false}
                else {if(position>=duration){mp.seekTo(0);position=0};mp.start();playing=true}
            },enabled=player!=null && failure==null){Text(if(playing) "Pause" else if(position>=duration && duration>0) "Replay" else "Play")}
        }
        Spacer(Modifier.height(12.dp))
    }
}


