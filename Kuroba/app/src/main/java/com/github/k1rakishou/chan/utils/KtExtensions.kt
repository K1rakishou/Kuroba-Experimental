package com.github.k1rakishou.chan.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.text.TextWatcher
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import com.airbnb.epoxy.AsyncEpoxyController
import com.airbnb.epoxy.DiffResult
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.OnModelBuildFinishedListener
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.di.component.activity.ActivityDependencies
import com.github.k1rakishou.chan.core.di.component.application.ApplicationDependencies
import com.github.k1rakishou.chan.core.di.module.activity.IHasActivityComponent
import com.github.k1rakishou.chan.features.view.media.MediaViewerActivity
import com.github.k1rakishou.chan.ui.activity.SharingActivity
import com.github.k1rakishou.chan.ui.activity.StartActivity
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.absoluteValue
import kotlin.math.log10


private val TAG = "KotlinExts"

fun Int.countDigits(): Int {
  if (this == 0) {
    return 1
  }

  return (log10(this.absoluteValue.toDouble()).toInt() + 1)
}

fun removeExtensionIfPresent(filename: String): String {
  val index = filename.lastIndexOf('.')
  if (index < 0) {
    return filename
  }

  return filename.substring(0, index)
}

fun extractFileNameExtension(filename: String): String? {
  val index = filename.lastIndexOf('.')
  return if (index == -1) {
    null
  } else {
    filename.substring(index + 1)
  }
}


fun EpoxyRecyclerView.withModelsAsync(buildModels: EpoxyController.() -> Unit) {
  val controller = object : AsyncEpoxyController(true) {
    override fun buildModels() {
      buildModels(this)
    }
  }

  setController(controller)
  controller.requestModelBuild()
}

fun EpoxyController.addOneshotModelBuildListener(callback: () -> Unit) {
  addModelBuildListener(object : OnModelBuildFinishedListener {
    override fun onModelBuildFinished(result: DiffResult) {
      callback()

      removeModelBuildListener(this)
    }
  })
}

fun Context.lifecycleFromContextOrNull(): Lifecycle? {
  return when (this) {
    is StartActivity -> this.lifecycle
    is SharingActivity -> this.lifecycle
    is MediaViewerActivity -> this.lifecycle
    is ContextWrapper -> (this.baseContext as? AppCompatActivity)?.lifecycleFromContextOrNull()
    else -> null
  }
}

fun Context.lifecycleFromContent(): Lifecycle {
  return requireNotNull(lifecycleFromContextOrNull()) {
    "Lifecycle is null! Probably bad context: ${this::class.java.simpleName}"
  }
}

suspend fun View.awaitUntilGloballyLaidOutAndGetSize(
  waitForWidth: Boolean = false,
  waitForHeight: Boolean = false,
  attempts: Int = 5
) : Pair<Int, Int> {
  val viewTag = "${this::class.java.name}_${this.viewIdForLogs()}"

  if (!waitForWidth && !waitForHeight) {
    error("awaitUntilGloballyLaidOutAndGetSize($viewTag) At least one of the parameters must be set to true!")
  }

  val widthOk = (!waitForWidth || width > 0)
  val heightOk = (!waitForHeight || height > 0)

  if (attempts <= 0) {
    val additionalStatusLog = buildString {
      if (waitForWidth) {
        append("widthOk: $widthOk, width: $width")
      }

      if (waitForHeight) {
        if (isNotEmpty()) {
          append("; ")
        }

        append("heightOk: $heightOk, height: $height")
      }
    }

    Logger.e(TAG, "awaitUntilGloballyLaidOutAndGetSize($viewTag) exhausted all attempts exiting (${additionalStatusLog})")
    return width to height
  }

  if (widthOk && heightOk) {
    Logger.d(TAG, "awaitUntilGloballyLaidOutAndGetSize($viewTag) both width and height are OK. width: $width, height: $height")
    return width to height
  }

  if (!ViewCompat.isLaidOut(this) || attempts < 5) {
    Logger.d(TAG, "awaitUntilGloballyLaidOutAndGetSize($viewTag) requesting layout " +
            "(viewLaidOut: ${ViewCompat.isLaidOut(this)}, attempts: ${attempts})...")
    requestLayout()
  }

  Logger.d(TAG, "awaitUntilGloballyLaidOutAndGetSize($viewTag) before OnGlobalLayoutListener (attempts: $attempts)")

  suspendCancellableCoroutine<Unit> { cancellableContinuation ->
    val listener = object : OnGlobalLayoutListener {
      override fun onGlobalLayout() {
        val view = this@awaitUntilGloballyLaidOutAndGetSize

        Logger.d(TAG, "awaitUntilGloballyLaidOutAndGetSize($viewTag) onGlobalLayout called " +
                "(width: ${view.width}, ${view.measuredWidth}, " +
                "height: ${view.height}, ${view.measuredHeight})")

        viewTreeObserver.removeOnGlobalLayoutListener(this)
        cancellableContinuation.resumeValueSafe(Unit)
      }
    }

    viewTreeObserver.addOnGlobalLayoutListener(listener)

    cancellableContinuation.invokeOnCancellation { cause ->
      Logger.d(TAG, "awaitUntilGloballyLaidOutAndGetSize($viewTag) onCancel called, reason: '${cause}'")

      viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }
  }

  Logger.d(TAG, "awaitUntilGloballyLaidOutAndGetSize($viewTag) after OnGlobalLayoutListener")
  return awaitUntilGloballyLaidOutAndGetSize(waitForWidth, waitForHeight, attempts - 1)
}

fun View.viewIdForLogs(): String {
  val id: Int = id
  if (id == View.NO_ID) {
    return "<no id>"
  }

  return buildString {
    val resources = context.resources
    if (id <= 0 || resources == null) {
      return "<resources is null>"
    }

    try {
      val pkgname = when (id and -0x1000000) {
        0x7f000000 -> "app"
        0x01000000 -> "android"
        else -> resources.getResourcePackageName(id)
      }

      val typename = resources.getResourceTypeName(id)
      val entryname = resources.getResourceEntryName(id)

      append(pkgname)
      append(":")
      append(typename)
      append("/")
      append(entryname)
    } catch (e: Resources.NotFoundException) {
      return "<error>"
    }
  }
}

fun Controller.findControllerOrNull(predicate: (Controller) -> Boolean): Controller? {
  if (predicate(this)) {
    return this
  }

  for (childController in childControllers) {
    val result = childController.findControllerOrNull(predicate)
    if (result != null) {
      return result
    }
  }

  return null
}

fun Controller.findControllers(predicate: (Controller) -> Boolean): Set<Controller> {
  val foundControllers = mutableSetOf<Controller>()

  if (predicate(this)) {
    foundControllers += this
  }

  val stack = mutableListOf<Controller>()
  stack += childControllers

  while (true) {
    val controller = stack.removeFirstOrNull()
      ?: break

    if (predicate(controller)) {
      foundControllers += controller
    }

    stack += controller.childControllers
  }

  return foundControllers
}

fun View.setAlphaFast(newAlpha: Float) {
  if (alpha != newAlpha) {
    alpha = newAlpha
  }
}

fun View.setEnabledFast(newEnabled: Boolean) {
  if (isEnabled != newEnabled) {
    isEnabled = newEnabled
  }

  if (this is ImageView) {
    if (!newEnabled) {
      setAlphaFast(0.6f)
    } else {
      setAlphaFast(1f)
    }
  }
}

fun View.setClickableFast(newClickable: Boolean) {
  if (isClickable != newClickable) {
    isClickable = newClickable
  }
}

fun View.setVisibilityFast(newVisibility: Int) {
  if (visibility != newVisibility) {
    visibility = newVisibility
  }
}

fun View.setScaleFastXY(newScale: Float) {
  if (scaleX != newScale) {
    scaleX = newScale
  }

  if (scaleY != newScale) {
    scaleY = newScale
  }
}

fun View.setBackgroundColorFast(newBackgroundColor: Int) {
  val prevColor = (background as? ColorDrawable)?.color
  if (prevColor != newBackgroundColor) {
    setBackgroundColor(newBackgroundColor)
  }
}

fun AppCompatEditText.doIgnoringTextWatcher(textWatcher: TextWatcher, func: AppCompatEditText.() -> Unit) {
  removeTextChangedListener(textWatcher)
  func(this)
  addTextChangedListener(textWatcher)
}

fun ByteArray.containsPattern(startFrom: Int, pattern: ByteArray): Boolean {
  if (pattern.size > this.size) {
    return false
  }

  for (offset in startFrom until this.size) {
    if (pattern[0] == this[offset]) {
      if (checkPattern(this, offset, pattern)) {
        return true
      }
    }
  }

  return false
}

private fun checkPattern(input: ByteArray, offset: Int, pattern: ByteArray): Boolean {
  for (index in pattern.indices) {
    if (pattern[index] != input[offset + index]) {
      return false
    }
  }

  return true
}

fun fixImageUrlIfNecessary(requestUrl: String, imageUrl: String?): String? {
  if (imageUrl == null) {
    return imageUrl
  }

  // arch.b4k.co was caught red-handed sending broken links (without http/https schema but
  // with both forward slashes, e.g. "//arch.b4k.co/..."  instead of "https://arch.b4k.co/...".
  // We gotta fix this by ourselves for now.
  // https://arch.b4k.co/meta/thread/357/
  //
  // UPD: it was fixed, but let's still leave this hack in case it happens again
  // UPD: the same thing happens on warosu.org and apparently it's normal
  if (imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) {
    return imageUrl
  }

  if (imageUrl.startsWith("//")) {
    return "https:$imageUrl"
  }

  if (imageUrl.startsWith("/")) {
    val requestHttpUrl = requestUrl.toHttpUrlOrNull()
    if (requestHttpUrl == null) {
      Logger.e(TAG, "Failed to convert requestUrl \'${requestUrl}\' to HttpUrl")
      return null
    }

    val scheme = if (requestHttpUrl.isHttps) {
      requestHttpUrl.scheme
    } else {
      "https"
    }

    return HttpUrl.Builder()
      .scheme(scheme)
      .host(requestHttpUrl.host)
      .encodedPath(imageUrl)
      .build()
      .toString()
  }

  Logger.e(TAG, "Unknown kind of broken image url: \"$imageUrl\". If you see this report it to devs!")
  return null
}

fun appDependencies(): ApplicationDependencies {
  return Chan.getComponent()
}

fun activityDependencies(context: Context): ActivityDependencies {
  return (context as IHasActivityComponent).activityComponent
}

@Composable
fun activityDependencies(): ActivityDependencies {
  val context = LocalContext.current
  return activityDependencies(context)
}

@Composable
fun rememberComposableLambda(vararg keys: Any, block: @Composable () -> Unit): @Composable () -> Unit {
  return remember(keys) { { block() } }
}

@Composable
fun <T> rememberComposableLambda(vararg keys: Any, block: @Composable (T) -> Unit): @Composable (T) -> Unit {
  return remember(keys) {
    val func: @Composable (T) -> Unit = { value: T -> block(value) }
    return@remember func
  }
}

fun Context.startActivitySafe(intent: Intent) {
  val activityName = intent.component?.className ?: "???"

  try {
    startActivity(intent)
  } catch (error: Throwable) {
    Logger.e("startActivitySafe", "error", error)

    AppModuleAndroidUtils.showToast(
      this,
      "Failed to start activity (${activityName}) because of an unknown error. " +
        "Error: '${error.errorMessageOrClassName()}', intent: '${intent}'", Toast.LENGTH_LONG
    )
  } catch (error: RuntimeException) {
    Logger.e(TAG, "startActivitySafe() error", error)

    AppModuleAndroidUtils.showToast(
      this,
      "Failed to start activity (${activityName}) because of an unknown error. " +
        "Error: '${error.errorMessageOrClassName()}', intent: '${intent}'", Toast.LENGTH_LONG
    )
  }
}