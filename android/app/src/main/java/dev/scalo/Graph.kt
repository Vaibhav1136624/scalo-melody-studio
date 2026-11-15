package dev.scalo

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

internal val Night=Color(0xFF0D1015)
internal val Panel=Color(0xFF151A21)
internal val Pink=Color(0xFFFF5489)
internal val Paper=Color(0xFFF3EFEB)
internal val Muted=Color(0xFFA3ABB9)
internal fun frameAt(ms:Int)=((ms-32+STEP_MS/2)/STEP_MS).coerceAtLeast(0)

@Composable
internal fun Graph(melody:Melody?,ms:Int,root:Int,modifier:Modifier){
    Canvas(modifier){
        val left=32.dp.toPx();val right=size.width-36.dp.toPx()
        val top=12.dp.toPx();val bottom=size.height-14.dp.toPx()
        val notes=melody?.notes.orEmpty()
        val head=frameAt(ms)
        val current=notes.getOrNull(head)?.takeIf { it.isFinite() }
        val center=melody?.center ?: 66
        var base=root+12*floor((center-root-3)/12.0).toInt()
        if(current!=null && current<base-3) base-=12*ceil((base-3-current)/12).toInt()
        if(current!=null && current>base+16) base+=12*ceil((current-base-16)/12).toInt()
        fun y(note:Float)=bottom-(note-base+3)/19*(bottom-top)
        val end=max(125,head+22)
        val start=end-125
        fun x(i:Int)=left+(right-left)*(i-start)/125
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            textSize=11.sp.toPx()
        }
        for(i in start..end){
            if(i%25==0) drawLine(Color(0xFF252B33),Offset(x(i),top),Offset(x(i),bottom),1f)
        }
        for(n in base-3..base+16){
            val d=pc(n-root)
            val active=current?.roundToInt()==n
            if(d !in listOf(0,2,4,5,7,9,11) && !active) continue
            val row=y(n.toFloat())
            drawLine(if(d==0) Color(0xFF565460) else Color(0xFF2A3039),Offset(left,row),Offset(right,row),1f)
            paint.color=(if(active) Pink else if(d==0) Paper else Muted).toArgb()
            paint.textAlign=Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(devanagari[d],left-6.dp.toPx(),row-paint.ascent()/3,paint)
            paint.textAlign=Paint.Align.LEFT
            drawContext.canvas.nativeCanvas.drawText(roman[d]+if(n>=base+12) "′" else "",right+5.dp.toPx(),row-paint.ascent()/3,paint)
        }
        fun trace(from:Int,to:Int,color:Color,width:Float){
            val path=Path();var connected=false
            for(i in from..min(to,notes.lastIndex)){
                val n=notes[i]
                if(!n.isFinite() || n<base-3 || n>base+16){connected=false;continue}
                if(connected) path.lineTo(x(i),y(n)) else path.moveTo(x(i),y(n))
                connected=true
            }
            drawPath(path,color,style=Stroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
        trace(max(start,head),end,Color(0xFF49303E),1.dp.toPx())
        trace(start,min(head,end),Pink,2.2.dp.toPx())
        if(current!=null) drawCircle(Pink,3.4.dp.toPx(),Offset(x(head),y(current)))
    }
}
