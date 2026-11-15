package dev.scalo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import kotlin.math.*

/** A finger pluck resolves into the opening Sa–Re–Ga phrase. */
internal class LaunchPhrase(context: Context) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val pink = Color.rgb(255, 103, 147)
    private val wordmark = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val notes = listOf("Sa", "Re", "Ga")
    private var beat = 0f
    private val animation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1650
        interpolator = LinearInterpolator()
        addUpdateListener {
            beat = it.animatedValue as Float
            alpha = 1f - ease((beat - 0.83f) / 0.17f)
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                (parent as? ViewGroup)?.removeView(this@LaunchPhrase)
            }
        })
    }

    init {
        setBackgroundColor(Color.rgb(14, 16, 19))
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun start() = animation.start()

    override fun onDetachedFromWindow() {
        animation.removeAllListeners()
        animation.cancel()
        super.onDetachedFromWindow()
    }

    private fun ease(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x*x*(3f-2f*x)
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val span = min(width * 0.78f, 340f * density)
        val left = (width - span) / 2
        val base = height * 0.47f
        val step = 18f * density
        val head = ease(beat / 0.77f)
        val morph = ease((beat - 0.15f) / 0.34f)
        fun pitch(u: Float): Float {
            val vibration = sin(PI*u) * cos(beat*26 - u*5) * exp(-beat*7.0)
            val level = ease((u-.32f)/.08f) + ease((u-.62f)/.08f)
            return base + (step * vibration * (1f-morph)).toFloat() - step * level * morph
        }
        ink.style = Paint.Style.STROKE
        ink.strokeCap = Paint.Cap.ROUND
        ink.strokeJoin = Paint.Join.ROUND
        ink.strokeWidth = density
        ink.color = Color.rgb(55, 47, 58)
        for (i in 0..2) canvas.drawLine(left,base-i*step,left+span,base-i*step,ink)
        path.reset()
        for (i in 0..100) {
            val u = head*i/100
            if (i==0) path.moveTo(left,pitch(u)) else path.lineTo(left+span*u,pitch(u))
        }
        ink.color = Color.argb(34,255,103,147)
        ink.strokeWidth = 7*density
        canvas.drawPath(path,ink)
        ink.strokeWidth = 2.4f*density
        ink.color = pink
        canvas.drawPath(path,ink)
        ink.style = Paint.Style.FILL
        canvas.drawCircle(left+span*head,pitch(head),3.6f*density,ink)
        ink.typeface = Typeface.MONOSPACE
        ink.textSize = 12*density
        ink.textAlign = Paint.Align.CENTER
        notes.forEachIndexed { i, note ->
            val u = .14f+i*.30f
            ink.color = pink
            ink.alpha = (255*ease((head-u)/.10f)).toInt()
            canvas.drawText(note,left+span*u,base+25*density,ink)
        }
        ink.typeface = wordmark
        ink.textSize = 42*density
        ink.color = Color.rgb(245,241,238)
        canvas.drawText("Scalo",width/2f,base+88*density,ink)
    }
}
