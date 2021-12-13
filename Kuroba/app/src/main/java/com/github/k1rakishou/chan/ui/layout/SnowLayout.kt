package com.github.k1rakishou.chan.ui.layout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.k1rakishou.chan.utils.TimeUtils
import kotlin.random.Random

class SnowLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle) {
  private val delay = ((1f / 12f) * 1000f).toLong()
  private val random = Random(System.currentTimeMillis())
  private val snowflakes = Array<Snowflake>(32) { Snowflake(random) }
  private var prevDt = 0L

  private var shown = true
  private var focused = true
  private var resumed = true

  private val observer = object : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
      resumed = true
    }

    override fun onPause(owner: LifecycleOwner) {
      resumed = false
    }
  }

  private val canDraw: Boolean
    get() = shown && focused && resumed

  init {
    setWillNotDraw(false)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    if (TimeUtils.isSnowTimeToday()) {
      invalidate()

      (context as? LifecycleOwner)?.lifecycle?.addObserver(observer)
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    (context as? LifecycleOwner)?.lifecycle?.removeObserver(observer)
  }

  fun lostFocus() {
    focused = false
  }

  fun gainedFocus() {
    focused = true
  }

  fun onShown() {
    shown = true
  }

  fun onHidden() {
    shown = false
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    return false
  }

  override fun onDraw(canvas: Canvas) {
    if (!TimeUtils.isSnowTimeToday()) {
      return
    }

    if (canDraw) {
      for (snowflake in snowflakes) {
        snowflake.draw(canvas)
      }
    }

    postOnAnimationDelayed(
      {
        update()
        invalidate()
      },
      delay
    )
  }

  private fun update() {
    val viewWidth = width
    val viewHeight = height

    if (viewWidth <= 0 || viewHeight <= 0) {
      return
    }

    val dt = if (prevDt == 0L) {
      prevDt = SystemClock.elapsedRealtime()
      delay
    } else {
      val dt = SystemClock.elapsedRealtime() - prevDt
      prevDt = SystemClock.elapsedRealtime()
      dt
    }

    for (snowflake in snowflakes) {
      snowflake.update(dt.toInt(), viewWidth, viewHeight)
    }
  }

  private class Snowflake(
    private val random: Random,
    private var alive: Boolean = false,
    private var x: Int = 0,
    private var y: Int = 0,
    private var size: Int = 1,
    private var speed: Float = 1f,
    private var delayTillNextSpawnMs: Int = 0
  ) {

    init {
      this.delayTillNextSpawnMs = random.nextInt(3000, 8000)
    }

    fun update(dt: Int, viewWidth: Int, viewHeight: Int) {
      if (!alive) {
        if (this.delayTillNextSpawnMs > 0) {
          this.delayTillNextSpawnMs -= dt
        } else {
          refresh(viewWidth, viewHeight)
        }
      }

      if (this.y > viewHeight + size && this.alive) {
        this.alive = false
        this.delayTillNextSpawnMs = random.nextInt(500, 3000)
      } else if (alive) {
        this.y += (speed * dt.toFloat()).toInt()
      }
    }

    fun draw(canvas: Canvas) {
      if (!alive) {
        return
      }

      canvas.drawCircle(x.toFloat(), y.toFloat(), size.toFloat(), PAINT)
    }

    private fun refresh(viewWidth: Int, viewHeight: Int) {
      this.size = random.nextInt(3, 15)
      this.x = random.nextInt(size, viewWidth - size)
      this.y = -size
      this.speed = (random.nextFloat() + 0.5f) / 10f
      this.delayTillNextSpawnMs = 0
      this.alive = true
    }

    companion object {
      private val PAINT = Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 160)
        paint.style = Paint.Style.FILL
      }
    }
  }
}