package com.github.adamantcheese.chan.utils

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import com.airbnb.epoxy.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.util.regex.Matcher

/**
 * Forces the kotlin compiler to require handling of all branches in the "when" operator
 * */
val <T : Any?> T.exhaustive: T
    get() = this

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    this.add(disposable)
}

fun Matcher.groupOrNull(group: Int): String? {
    return try {
        if (group < 0 || group > groupCount()) {
            return null
        }

        this.group(group)
    } catch (error: Throwable) {
        null
    }
}

/**
 * Not thread-safe!
 * */
fun <K, V> MutableMap<K, V>.putIfNotContains(key: K, value: V) {
    if (!this.containsKey(key)) {
        this[key] = value
    }
}

/**
 * Not thread-safe!
 * */
fun <K, V> HashMap<K, V>.putIfNotContains(key: K, value: V) {
    if (!this.containsKey(key)) {
        this[key] = value
    }
}

fun Throwable.errorMessageOrClassName(): String {
    if (message != null) {
        return message!!
    }

    return this::class.java.name
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

fun View.updateMargins(
  left: Int? = null,
  right: Int? = null,
  start: Int? = null,
  end: Int? = null,
  top: Int? = null,
  bottom: Int? = null
) {
    val layoutParams = layoutParams as? MarginLayoutParams
      ?: return

    val newLeft = left ?: layoutParams.leftMargin
    val newRight = right ?: layoutParams.rightMargin
    val newStart = start ?: layoutParams.marginStart
    val newEnd = end ?: layoutParams.marginEnd
    val newTop = top ?: layoutParams.topMargin
    val newBottom = bottom ?: layoutParams.bottomMargin

    layoutParams.setMargins(
      newLeft,
      newTop,
      newRight,
      newBottom
    )

    layoutParams.marginStart = newStart
    layoutParams.marginEnd = newEnd
}

fun View.updatePaddings(
  left: Int = paddingLeft,
  right: Int = paddingRight,
  top: Int = paddingTop,
  bottom: Int = paddingBottom
) {
    setPadding(left, top, right, bottom)
}

fun ViewGroup.findChild(predicate: (View) -> Boolean): View? {
    if (predicate(this)) {
        return this
    }

    return findChildRecursively(this, predicate)
}

private fun findChildRecursively(viewGroup: ViewGroup, predicate: (View) -> Boolean): View? {
    for (index in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(index)
        if (predicate(child)) {
            return child
        }

        if (child is ViewGroup) {
            val result = findChildRecursively(child, predicate)
            if (result != null) {
                return result
            }
        }
    }

    return null
}

fun View.updateHeight(newHeight: Int) {
    val updatedLayoutParams = layoutParams
    updatedLayoutParams.height = newHeight
    layoutParams = updatedLayoutParams
}