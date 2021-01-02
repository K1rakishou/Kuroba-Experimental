package com.github.k1rakishou.chan.utils

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import com.airbnb.epoxy.AsyncEpoxyController
import com.airbnb.epoxy.DiffResult
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.epoxy.OnModelBuildFinishedListener
import com.github.k1rakishou.chan.activity.SharingActivity
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.controller.Controller
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
  this.add(disposable)
}

fun Throwable.errorMessageOrClassName(): String {
  if (message != null) {
    return message!!
  }

  return this::class.java.name
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

fun Context.getLifecycleFromContext(): Lifecycle? {
  return when (this) {
    is StartActivity -> this.lifecycle
    is SharingActivity -> this.lifecycle
    is ContextWrapper -> (this.baseContext as? AppCompatActivity)?.getLifecycleFromContext()
    else -> null
  }
}

suspend fun View.awaitUntilPreDraw() {
  suspendCancellableCoroutine<Unit> { cancellableContinuation ->
    doOnPreDraw { cancellableContinuation.resume(Unit) }
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

fun View.setAlphaFast(newAlpha: Float) {
  if (alpha != newAlpha) {
    alpha = newAlpha
  }
}

fun View.setVisibilityFast(newVisibility: Int) {
  if (visibility != newVisibility) {
    visibility = newVisibility
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