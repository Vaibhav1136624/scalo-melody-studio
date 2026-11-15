package dev.scalo

import android.content.Context
import android.media.*
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder
import kotlin.math.*

internal data class Melody(val notes:List<Float>,val key:Key?,val center:Int)

internal suspend fun analyze(context:Context, uri:Uri?, progress:(Int)->Unit):Melody {
    val extractor=MediaExtractor()
    try {
        if(uri!=null) extractor.setDataSource(context,uri,null)
        else context.resources.openRawResourceFd(R.raw.daybreak).use {
            extractor.setDataSource(it.fileDescriptor,it.startOffset,it.length)
        }
        val track=(0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
        } ?: error("This file contains no audio track.")
        extractor.selectTrack(track)
        val format=extractor.getTrackFormat(track)
        val duration=if(format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        fun checkLength(us:Long){require(us<=720_000_000L){"Choose a song up to 12 minutes long."}}
        checkLength(duration)
        val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        try {
            codec.configure(format,null,null,0)
            codec.start()
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floating=false
            var pending=0
            var phase=0
            var sum=0f
            var summed=0
            var inputDone=false
            var outputDone=false
            val info=MediaCodec.BufferInfo()
            val frame=FloatArray(FRAME)
            val notes=ArrayList<Float>()
            var lastProgress=-1
            while(!outputDone) {
                currentCoroutineContext().ensureActive()
                if(!inputDone) {
                    val id=codec.dequeueInputBuffer(5000)
                    if(id>=0) {
                        val count=extractor.readSampleData(codec.getInputBuffer(id)!!,0)
                        if(count<0){codec.queueInputBuffer(id,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                        else {codec.queueInputBuffer(id,0,count,extractor.sampleTime,0);extractor.advance()}
                    }
                }
                val id=codec.dequeueOutputBuffer(info,5000)
                if(id==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    val out=codec.outputFormat
                    rate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val encoding=if(out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(encoding in listOf(AudioFormat.ENCODING_PCM_16BIT,AudioFormat.ENCODING_PCM_FLOAT)){"Unsupported PCM sample format."}
                    floating=encoding==AudioFormat.ENCODING_PCM_FLOAT
                    require(rate>=RATE){"Choose audio sampled at 8 kHz or higher."}
                }
                if(id<0) continue
                checkLength(info.presentationTimeUs)
                if(duration>0){
                    val pct=(info.presentationTimeUs*100/duration).toInt().coerceIn(0,99)
                    if(pct!=lastProgress){progress(pct);lastProgress=pct}
                }
                if(info.size>0){
                    val buf=codec.getOutputBuffer(id)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset);buf.limit(info.offset+info.size)
                    val bytes=if(floating) 4 else 2
                    while(buf.remaining()>=bytes*channels){
                        var mono=0f
                        repeat(channels){mono+=if(floating) buf.float else buf.short/32768f}
                        sum+=mono/channels;summed++;phase+=RATE
                        if(phase>=rate){
                            phase-=rate;frame[pending++]=sum/summed;sum=0f;summed=0
                            if(pending==FRAME){
                                notes.add(pitch(frame))
                                frame.copyInto(frame,0,HOP,FRAME)
                                pending=FRAME-HOP
                            }
                        }
                    }
                }
                outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                codec.releaseOutputBuffer(id,false)
            }
            require(notes.isNotEmpty()){"This clip is too short to analyze."}
            val voiced=notes.filter { it.isFinite() }.sorted()
            return Melody(notes,estimate(notes),voiced.getOrNull(voiced.size/2)?.roundToInt() ?: 64)
        } finally {codec.release()}
    } finally {extractor.release()}
}
