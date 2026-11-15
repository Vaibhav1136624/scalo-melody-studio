package dev.scalo

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*

class PitchTest {
    @Test fun followsTonesAndRejectsSilence(){
        for(n in listOf(45,60,64,69,76,81)){
            val hz=440*2.0.pow((n-69)/12.0)
            val frame=FloatArray(FRAME){i->sin(2*PI*i*hz/RATE).toFloat()}
            assertEquals(n.toFloat(),pitch(frame),0.12f)
        }
        assertTrue(pitch(FloatArray(FRAME)).isNaN())
    }
    @Test fun detectsTransposedMajorAndMinor(){
        val major=listOf(0,0,0,0,2,4,4,5,7,7,7,9,11)
        val minor=listOf(0,0,0,0,2,3,3,5,7,7,7,8,10)
        for(root in 0..11){
            assertEquals(Key(root,false),estimate(major.map{(60+root+it).toFloat()}))
            assertEquals(Key(root,true),estimate(minor.map{(60+root+it).toFloat()}))
        }
        assertNull(estimate(listOf(60f,Float.NaN)))
    }
}
