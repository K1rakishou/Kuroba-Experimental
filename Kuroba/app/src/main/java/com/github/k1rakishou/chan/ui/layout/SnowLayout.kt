package com.github.k1rakishou.chan.ui.layout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.TimeUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import kotlin.random.Random

class SnowLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle) {
  private val fps = ((1f / 16f) * 1000f).toLong()
  private val random = Random(System.currentTimeMillis())
  private val snowflakes = Array<Snowflake>(45) { Snowflake(random) }
  private val fireworks = Array<Firework>(3) { Firework(random = random) }
  private var prevDt = 0L

  private var shown = true
  private var focused = true
  private var resumed = true

  private val isNewYearToday = TimeUtils.isNewYearToday()
  private val isChristmasToday = TimeUtils.isChristmasToday()

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

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    if (isNewYearToday || isChristmasToday) {
      setVisibilityFast(View.VISIBLE)

      setWillNotDraw(false)
      invalidate()

      (context as? LifecycleOwner)?.lifecycle?.addObserver(observer)
    } else {
      setVisibilityFast(View.GONE)
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
    if (!isNewYearToday && !isChristmasToday) {
      return
    }

    if (canDraw) {
      for (snowflake in snowflakes) {
        snowflake.draw(canvas)
      }

      if (isNewYearToday) {
        for (firework in fireworks) {
          firework.draw(canvas)
        }
      }
    }

    postOnAnimationDelayed(
      {
        update()
        invalidate()
      },
      fps
    )
  }

  private fun update() {
    if (!isNewYearToday && !isChristmasToday) {
      return
    }

    val viewWidth = width
    val viewHeight = height

    if (viewWidth <= 0 || viewHeight <= 0) {
      return
    }

    val dt = if (prevDt == 0L) {
      prevDt = SystemClock.elapsedRealtime()
      fps
    } else {
      val dt = SystemClock.elapsedRealtime() - prevDt
      prevDt = SystemClock.elapsedRealtime()
      dt
    }

    for (snowflake in snowflakes) {
      snowflake.update(dt.toInt(), viewWidth, viewHeight)
    }

    if (isNewYearToday) {
      for (firework in fireworks) {
        firework.update(dt.toInt(), viewWidth, viewHeight)
      }
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
      this.delayTillNextSpawnMs = random.nextInt(1000, 10000)
    }

    fun update(dt: Int, viewWidth: Int, viewHeight: Int) {
      if (!alive) {
        if (this.delayTillNextSpawnMs > 0) {
          this.delayTillNextSpawnMs -= dt
        } else {
          refresh(viewWidth, viewHeight)
        }

        return
      }

      if (this.y > viewHeight + size) {
        this.alive = false
        this.delayTillNextSpawnMs = random.nextInt(500, 3000)
      } else {
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

  private class Firework(
    private val random: Random,
    private var x: Int = 0,
    private var y: Int = 0,
    private var explosionHeight: Int = 0,
    private var state: State = State.Dead,
    private var delayTillNextSpawnMs: Int = 0
  ) {
    private val speed = .5f

    private val launchTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
      paint.color = ColorUtils.setAlphaComponent(Color.TRANSPARENT, 255)
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = dp(4f).toFloat()
    }

    private val fireworkExplosionAnimation = FireworkExplosionAnimation(random)

    init {
      this.delayTillNextSpawnMs = nextRespawnTime()
    }

    private fun nextRespawnTime(): Int {
      if (isDevBuild()) {
        return random.nextInt(1000, 4500)
      }

      return random.nextInt(15000, 60000)
    }

    fun update(dt: Int, viewWidth: Int, viewHeight: Int) {
      when (state) {
        State.Dead -> {
          if (this.delayTillNextSpawnMs > 0) {
            this.delayTillNextSpawnMs -= dt
          } else {
            refresh(viewWidth, viewHeight)
          }
        }
        State.LaunchAnimation -> {
          if (this.y < (viewHeight - explosionHeight)) {
            this.state = State.ExplosionAnimationProgress
          } else {
            this.y -= (speed * dt.toFloat()).toInt()
          }
        }
        State.ExplosionAnimationProgress -> {
          fireworkExplosionAnimation.update(
            dt = dt,
            startX = x.toFloat(),
            startY = y.toFloat() - launchTrailHeight,
            minRadius = explosionMinRadius,
            maxRadius = explosionMaxRadius,
            trailColor = this.launchTrailPaint.color
          )

          if (fireworkExplosionAnimation.allDead()) {
            this.state = State.ExplosionAnimationEnd
          }
        }
        State.ExplosionAnimationEnd -> {
          this.state = State.Dead
          this.delayTillNextSpawnMs = nextRespawnTime()
          fireworkExplosionAnimation.reset()
        }
      }
    }

    fun draw(canvas: Canvas) {
      when (state) {
        State.Dead -> {
          // no-op
        }
        State.LaunchAnimation -> {
          val endY = y.toFloat()
          val startY = endY - launchTrailHeight

          canvas.drawLine(x.toFloat(), startY, x.toFloat(), endY, launchTrailPaint)
        }
        State.ExplosionAnimationProgress -> {
          fireworkExplosionAnimation.draw(canvas)
        }
        State.ExplosionAnimationEnd -> {
          // no-op
        }
      }
    }

    private fun refresh(viewWidth: Int, viewHeight: Int) {
      this.x = random.nextInt(horizPadding, viewWidth - horizPadding)
      this.y = viewHeight
      this.delayTillNextSpawnMs = 0
      this.launchTrailPaint.color = colors.random(random)

      this.explosionHeight = if ((viewHeight - explosionMaxRadius) > minHeight) {
        viewHeight - random.nextInt(minHeight, viewHeight - explosionMaxRadius)
      } else {
        minHeight
      }

      this.state = if (random.nextInt() % 3 == 0) {
        State.LaunchAnimation
      } else {
        State.ExplosionAnimationEnd
      }
    }

    private enum class State {
      Dead,
      LaunchAnimation,
      ExplosionAnimationProgress,
      ExplosionAnimationEnd
    }

    companion object {
      private val horizPadding = dp(32f)
      private val explosionMinRadius = dp(25f)
      private val explosionMaxRadius = dp(50f)
      private val minHeight = dp(100f)
      private val launchTrailHeight = dp(48f)
    }

  }

  private class FireworkExplosionAnimation(
    private val random: Random
  ) {
    private var explosionFlashLifetime = 0
    private var explosionRadius = 0
    private var startX = 0f
    private var startY = 0f
    private var particles: Array<FireworkExplosionParticle> = emptyArray()
    private var initialized = false

    private val explosionFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
      paint.color = ColorUtils.setAlphaComponent(Color.TRANSPARENT, 255)
      paint.style = Paint.Style.FILL
    }

    fun allDead(): Boolean {
      for (particle in particles) {
        if (!particle.isDead()) {
          return false
        }
      }

      return true
    }

    fun reset() {
      for (particle in particles) {
        particle.end()
      }
      
      initialized = false
    }

    fun update(dt: Int, startX: Float, startY: Float, minRadius: Int, maxRadius: Int, trailColor: Int) {
      if (!initialized) {
        this.startX = startX
        this.startY = startY
        this.explosionRadius = random.nextInt(minRadius, maxRadius)
        this.explosionFlashLifetime = 255
        this.explosionFlashPaint.color = trailColor
        this.explosionFlashPaint.alpha = 255
        this.particles = Array(random.nextInt(10, 40)) { FireworkExplosionParticle(random) }

        this.initialized = true
      }

      explosionFlashLifetime -= dt

      if (explosionFlashPaint.alpha > 0) {
        explosionFlashPaint.alpha = explosionFlashPaint.alpha - dt
      }

      for (particle in particles) {
        particle.start(startX, startY)
        particle.update(dt)
      }
    }

    fun draw(canvas: Canvas) {
      for (particle in particles) {
        particle.draw(canvas)
      }

      if (explosionFlashLifetime > 0) {
        canvas.drawCircle(startX, startY, EXPLOSION_FLASH_RADIUS, explosionFlashPaint)
      }
    }

    companion object {
      private val EXPLOSION_FLASH_RADIUS = dp(32f).toFloat()
    }
  }

  private class FireworkExplosionParticle(
    private val random: Random,
    private var particleTrailHeight: Int = 0,
    private var lifetimeMs: Int = 0,
    private var x: Float = 0f,
    private var y: Float = 0f,
    private var dirX: Float = 0f,
    private var dirY: Float = 0f,
    private var speed: Float = 0f,
    private var time: Int = 0
  ) {
    private var started = false

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).also { paint ->
      paint.color = ColorUtils.setAlphaComponent(Color.TRANSPARENT, 255)
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = dp(2f).toFloat()
    }

    fun isDead(): Boolean {
      return lifetimeMs <= 0
    }

    fun start(startX: Float, startY: Float) {
      if (started) {
        return
      }

      this.lifetimeMs = random.nextInt(500, 2500)
      this.x = startX
      this.y = startY
      this.speed = random.nextFloat() + 0.1f
      this.particlePaint.color = colors.random(random)
      this.particleTrailHeight = random.nextInt(5, 20)

      val angle = random.nextInt(0, 360)
      dirX = Math.sin(angle * Math.PI / 180.0f).toFloat()
      dirY = Math.cos(angle * Math.PI / 180.0f).toFloat()

      started = true
    }

    fun end() {
      started = false
    }

    fun update(dt: Int) {
      if (lifetimeMs < 0) {
        return
      }

      time += dt
      lifetimeMs -= dt

      x += dirX * speed * dt
      y += dirY * speed * dt
    }

    fun draw(canvas: Canvas) {
      if (lifetimeMs < 0) {
        return
      }

      val startX = x - (dirX * particleTrailHeight)
      val startY = y - (dirY * particleTrailHeight)

      canvas.drawLine(startX, startY, x, y, particlePaint)
    }

  }

  companion object {
    private val colors = arrayOf(
      Color.RED,
      Color.BLUE,
      Color.GREEN,
      Color.YELLOW,
      Color.MAGENTA,
      Color.CYAN,
      Color.parseColor("#FFFF4500"), // orange
      Color.parseColor("#FF8F00FF"), // violet
      Color.parseColor("#FF4B0082"), // indigo
    )
  }

}