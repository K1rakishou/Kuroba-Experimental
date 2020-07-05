package com.github.adamantcheese.chan.utils

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import com.airbnb.epoxy.*
import com.github.adamantcheese.chan.StartActivity
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
  this.add(disposable)
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
    is ContextWrapper -> (this.baseContext as? StartActivity)?.lifecycle
    else -> null
  }
}