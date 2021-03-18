/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.controller

import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.activity.StartActivityCallbacks
import com.github.k1rakishou.chan.controller.transition.FadeInTransition
import com.github.k1rakishou.chan.controller.transition.FadeOutTransition
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_logger.Logger
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.util.*
import javax.inject.Inject

@Suppress("LeakingThis")
@DoNotStrip
abstract class Controller(@JvmField var context: Context) {

  lateinit var view: ViewGroup

  @Inject
  lateinit var controllerNavigationManager: ControllerNavigationManager

  @JvmField
  var navigation = NavigationItem()
  @JvmField
  var parentController: Controller? = null
  @JvmField
  var childControllers: MutableList<Controller> = ArrayList()

  // NavigationControllers members
  @JvmField
  var previousSiblingController: Controller? = null
  @JvmField
  var navigationController: NavigationController? = null
  @JvmField
  var doubleNavigationController: DoubleNavigationController? = null

  /**
   * Controller that this controller is presented by.
   */
  @JvmField
  var presentedByController: Controller? = null

  /**
   * Controller that this controller is presenting.
   */
  @JvmField
  var presentingThisController: Controller? = null

  val top: Controller?
    get() = if (childControllers.size > 0) {
      childControllers[childControllers.size - 1]
    } else {
      null
    }

  open val toolbar: Toolbar?
    get() = null

  @JvmField
  var alive = false

  protected var compositeDisposable = CompositeDisposable()
    @JvmName("compositeDisposable") get
    private set

  private val job = SupervisorJob()
  protected var mainScope = CoroutineScope(job + Dispatchers.Main + CoroutineName("Controller_${this::class.java.simpleName}"))

  var shown = false
    @JvmName("shown") get
    private set

  protected val cancellableToast = CancellableToast()

  fun requireToolbar(): Toolbar = requireNotNull(toolbar) {
    "Toolbar was not set"
  }

  fun requireNavController(): NavigationController = requireNotNull(navigationController) {
    "navigationController was not set"
  }

  fun requireStartActivity(): StartActivityCallbacks {
    return context as? StartActivityCallbacks
      ?: throw IllegalStateException("Wrong context! Must be StartActivity")
  }

  fun isViewInitialized(): Boolean = ::view.isInitialized

  init {
    injectDependencies(AppModuleAndroidUtils.extractActivityComponent(context))
  }

  protected abstract fun injectDependencies(component: ActivityComponent)

  @CallSuper
  open fun onCreate() {
    alive = true

    if (LOG_STATES) {
      Logger.e("LOG_STATES", javaClass.simpleName + " onCreate")
    }
  }

  @CallSuper
  open fun onShow() {
    shown = true

    if (LOG_STATES) {
      Logger.e("LOG_STATES", javaClass.simpleName + " onShow")
    }

    view.visibility = View.VISIBLE

    for (controller in childControllers) {
      if (!controller.shown) {
        controller.onShow()
      }
    }
  }

  @CallSuper
  open fun onHide() {
    shown = false

    if (LOG_STATES) {
      Logger.e("LOG_STATES", javaClass.simpleName + " onHide")
    }

    view.visibility = View.GONE

    for (controller in childControllers) {
      if (controller.shown) {
        controller.onHide()
      }
    }
  }

  @CallSuper
  open fun onDestroy() {
    alive = false
    compositeDisposable.clear()
    job.cancelChildren()

    if (LOG_STATES) {
      Logger.e("LOG_STATES", javaClass.simpleName + " onDestroy")
    }

    while (childControllers.size > 0) {
      removeChildController(childControllers[0])
    }

    if (AndroidUtils.removeFromParentView(view)) {
      if (LOG_STATES) {
        Logger.e("LOG_STATES", javaClass.simpleName + " view removed onDestroy")
      }
    }
  }

  fun addChildController(controller: Controller) {
    childControllers.add(controller)
    controller.parentController = this

    if (doubleNavigationController != null) {
      controller.doubleNavigationController = doubleNavigationController
    }

    if (navigationController != null) {
      controller.navigationController = navigationController
    }

    controller.onCreate()
  }

  fun addChildControllerOrMoveToTop(container: ViewGroup, newController: Controller) {
    fun restoreCallbacks(newController: Controller) {
      if (doubleNavigationController != null) {
        newController.doubleNavigationController = doubleNavigationController
      }

      if (navigationController != null) {
        newController.navigationController = navigationController
      }
    }

    fun hideOtherControllers(newController: Controller) {
      childControllers.forEach { controller ->
        if (controller === newController) {
          return@forEach
        }

        if (controller.shown) {
          controller.onHide()
        }
      }
    }

    val controllerIndex = childControllers.indexOfFirst { controller -> controller === newController }
    if (controllerIndex >= 0) {
      hideOtherControllers(newController)
      restoreCallbacks(newController)

      if (childControllers.size >= 2) {
        // Move this controller to the end so that it's considered to be the "top" controller
        childControllers.add(childControllers.removeAt(controllerIndex))
      }

      newController.attachToParentView(container)

      if (!newController.shown) {
        newController.onShow()
      }

      return
    }

    childControllers.add(newController)

    newController.parentController = this

    hideOtherControllers(newController)
    restoreCallbacks(newController)

    newController.onCreate()
    newController.attachToParentView(container)
    newController.onShow()
  }

  fun removeChildController(controller: Controller) {
    controller.onDestroy()
    childControllers.remove(controller)
  }

  fun attachToParentView(parentView: ViewGroup?) {
    if (view.parent != null) {
      if (LOG_STATES) {
        Logger.e("LOG_STATES", javaClass.simpleName + " view removed")
      }

      AndroidUtils.removeFromParentView(view)
    }

    if (parentView != null) {
      if (LOG_STATES) {
        Logger.e("LOG_STATES", javaClass.simpleName + " view attached")
      }

      attachToView(parentView)
    }
  }

  open fun onConfigurationChanged(newConfig: Configuration) {
    for (controller in childControllers) {
      controller.onConfigurationChanged(newConfig)
    }
  }

  open fun dispatchKeyEvent(event: KeyEvent): Boolean {
    for (i in childControllers.indices.reversed()) {
      val controller = childControllers[i]
      if (controller.dispatchKeyEvent(event)) {
        return true
      }
    }

    return false
  }

  open fun onBack(): Boolean {
    for (index in childControllers.indices.reversed()) {
      val controller = childControllers[index]
      if (controller.onBack()) {
        return true
      }
    }
    return false
  }

  @JvmOverloads
  open fun presentController(controller: Controller, animated: Boolean = true) {
    val contentView = (context as StartActivity).contentView
    presentingThisController = controller

    controller.presentedByController = this
    controller.onCreate()
    controller.attachToView(contentView)
    controller.onShow()
    (context as StartActivity).pushController(controller)

    if (animated) {
      val transition = FadeInTransition()
      transition.to = controller
      transition.setCallback {
        controllerNavigationManager.onControllerPresented(controller)
      }
      transition.perform()

      return
    }

    controllerNavigationManager.onControllerPresented(controller)
  }

  fun isAlreadyPresenting(predicate: (Controller) -> Boolean): Boolean {
    return (context as StartActivity).isControllerAdded(predicate)
  }

  fun getControllerOrNull(predicate: (Controller) -> Boolean): Controller? {
    return (context as StartActivity).getControllerOrNull(predicate)
  }

  open fun stopPresenting() {
    stopPresenting(true)
  }

  open fun stopPresenting(animated: Boolean) {
    val startActivity = (context as StartActivity)
    if (!startActivity.containsController(this)) {
      return
    }

    if (animated) {
      val transition = FadeOutTransition()
      transition.from = this
      transition.setCallback { finishPresenting() }
      transition.perform()
    } else {
      finishPresenting()
    }

    startActivity.popController(this)
    presentedByController?.presentingThisController = null
    controllerNavigationManager.onControllerUnpresented(this)
  }

  @JvmOverloads
  protected fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    cancellableToast.showToast(context, message, duration)
  }

  @JvmOverloads
  protected fun showToast(@StringRes messageId: Int, duration: Int = Toast.LENGTH_SHORT) {
    cancellableToast.showToast(context, messageId, duration)
  }

  private fun finishPresenting() {
    onHide()
    onDestroy()
  }

  private fun attachToView(parentView: ViewGroup) {
    var params = view.layoutParams
    if (params == null) {
      params = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    } else {
      params.width = ViewGroup.LayoutParams.MATCH_PARENT
      params.height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    view.layoutParams = params
    parentView.addView(view, view.layoutParams)
  }

  companion object {
    private const val LOG_STATES = false
  }

}