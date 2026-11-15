package dev.scalo

import kotlin.math.*

internal const val RATE = 8000
internal const val FRAME = 512
internal const val HOP = 320
internal const val STEP_MS = HOP * 1000 / RATE
internal val keys = listOf("C","C♯","D","E♭","E","F","F♯","G","A♭","A","B♭","B")
internal val roman = listOf("Sa","re","Re","ga","Ga","Ma","Ma♯","Pa","dha","Dha","ni","Ni")
internal val devanagari = listOf("सा","रे॒","रे","ग॒","ग","म","म॑","प","ध॒","ध","नि॒","नि")
internal fun pc(n: Int) = Math.floorMod(n,12)

internal fun pitch(samples: FloatArray): Float {
    // Normalized autocorrelation. Prefer the first strong local maximum to avoid octave errors.
    var energy = 0.0
    for (v in samples) energy += v*v
    if (energy / FRAME < 0.000025) return Float.NaN
    val scores = DoubleArray(103)
    for (lag in 6 until scores.size) {
        var cross = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in 0 until FRAME-103) {
            val a=samples[i]; val b=samples[i+lag]
            cross += a*b; aa += a*a; bb += b*b
        }
        scores[lag]=cross/sqrt(aa*bb+1e-16)
    }
    val threshold=max(0.80,scores.max()*0.94)
    for(i in 7 until scores.lastIndex) {
        val c=scores[i]; val l=scores[i-1]; val r=scores[i+1]
        if(c>=threshold && c>l && c>=r) {
            val lag=i+((l-r)/(2*(l-2*c+r))).coerceIn(-0.5,0.5)
            return (69+12*log2(RATE/(lag*440))).toFloat()
        }
    }
    return Float.NaN
}

internal data class Key(val root:Int,val minor:Boolean)
internal fun estimate(notes:List<Float>):Key? {
    val h=DoubleArray(12)
    notes.filter { it.isFinite() }.forEach { h[pc(it.roundToInt())]++ }
    if(h.count { it>0 } < 3) return null
    val profiles=listOf(
        doubleArrayOf(6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88),
        doubleArrayOf(6.33,2.68,3.52,5.38,2.60,3.53,2.54,4.75,3.98,2.69,3.34,3.17))
    var best=Double.NEGATIVE_INFINITY
    var result=Key(0,false)
    for ((mode,p) in profiles.withIndex()) {
        val mean=p.average()
        val norm=sqrt(p.sumOf { (it-mean).pow(2) })
        for(root in 0..11) {
            val score=h.indices.sumOf { h[it]*(p[pc(it-root)]-mean) }/norm
            if(score>best){best=score;result=Key(root,mode==1)}
        }
    }
    return result
}
