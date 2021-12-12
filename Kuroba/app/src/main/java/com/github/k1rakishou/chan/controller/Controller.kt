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
import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivityCallbacks
import com.github.k1rakishou.chan.controller.transition.FadeInTransition
import com.github.k1rakishou.chan.controller.transition.FadeOutTransition
import com.github.k1rakishou.chan.core.base.ControllerHostActivity
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.navigation.ControllerWithNavigation
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar
import com.github.k1rakishou.chan.features.drawer.MainController
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.ui.controller.PopupController
import com.github.k1rakishou.chan.ui.controller.ThreadController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.navigation.BottomNavBarAwareNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.pxToDp
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
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

  fun controllerViewOrNull(): ViewGroup? {
    if (::view.isInitialized) {
      return view
    }

    return null
  }

  fun getCurrentControllerWithNavigation(): ControllerWithNavigation {
    if (doubleNavigationController != null) {
      return doubleNavigationController!!
    } else {
      return navigationController!!
    }
  }

  fun requireToolbarNavController(): ToolbarNavigationController {
    val navController = requireNavController()
    check(navController is ToolbarNavigationController) {
      "navigationController is not ToolbarNavigationController"
    }

    return navController
  }

  fun toolbarNavControllerOrNull(): ToolbarNavigationController? {
    return navigationController as? ToolbarNavigationController
  }

  fun requireToolbar(): Toolbar = requireNotNull(toolbar) {
    "Toolbar was not set"
  }

  fun requireNavController(): NavigationController = requireNotNull(navigationController) {
    "navigationController was not set"
  }

  fun requireStartActivity(): StartActivityCallbacks {
    return (context as? StartActivityCallbacks)
      ?: throw IllegalStateException("Wrong context! Must be StartActivity")
  }

  fun requireComponentActivity(): ComponentActivity {
    return (context as? ComponentActivity)
      ?: throw IllegalStateException("Wrong context! Must be ComponentActivity")
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

    if (::view.isInitialized && AndroidUtils.removeFromParentView(view)) {
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

    if (controller.navigationController is StyledToolbarNavigationController) {
      (controller.navigationController as StyledToolbarNavigationController).onChildControllerPushed(controller)
    }
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

    if (controller.navigationController is StyledToolbarNavigationController) {
      (controller.navigationController as StyledToolbarNavigationController).onChildControllerPopped(controller)
    }
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
    val contentView = (context as ControllerHostActivity).contentView
    presentingThisController = controller

    controller.presentedByController = this
    (context as ControllerHostActivity).pushController(controller)

    controller.onCreate()
    controller.attachToView(contentView)
    controller.onShow()

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
    return (context as ControllerHostActivity).isControllerAdded(predicate)
  }

  fun getControllerOrNull(predicate: (Controller) -> Boolean): Controller? {
    return (context as ControllerHostActivity).getControllerOrNull(predicate)
  }

  open fun stopPresenting() {
    stopPresenting(true)
  }

  open fun stopPresenting(animated: Boolean) {
    val startActivity = (context as ControllerHostActivity)
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

  fun popFromNavController(chanDescriptor: ChanDescriptor) {
    popFromNavControllerWithAction(chanDescriptor = chanDescriptor, action = {  })
  }

  fun popFromNavControllerWithAction(chanDescriptor: ChanDescriptor, action: (ThreadController) -> Unit) {
    var threadController: ThreadController? = null

    if (previousSiblingController is ThreadController) {
      // phone mode
      threadController = previousSiblingController as ThreadController?
    } else if (previousSiblingController is DoubleNavigationController) {
      // slide mode
      val doubleNav = previousSiblingController as DoubleNavigationController
      if (doubleNav is ThreadSlideController) {
        if (doubleNav.leftOpen()) {
          threadController = doubleNav.getLeftController() as ThreadController
        } else {
          threadController = doubleNav.getRightController() as ThreadController
        }
      } else if (doubleNav.getRightController() is ThreadController) {
        threadController = doubleNav.getRightController() as ThreadController
      }
    } else if (previousSiblingController == null) {
      // split nav has no "sibling" to look at, so we go WAY back to find the view thread controller
      val splitNav = parentController?.parentController?.presentedByController as SplitNavigationController?

      threadController = when (chanDescriptor) {
        is ChanDescriptor.ICatalogDescriptor -> {
          splitNav?.leftController?.childControllers?.get(0) as ThreadController?
        }
        is ChanDescriptor.ThreadDescriptor -> {
          splitNav?.rightController?.childControllers?.get(0) as ThreadController?
        }
      }

      // clear the popup here because split nav is weirdly laid out in the stack
      splitNav?.popController()
    }

    if (threadController != null) {
      action(threadController)
      navigationController!!.popController(false)
    }
  }

  protected fun <T> ModularResult<T>.toastOnError(
    longToast: Boolean = false,
    formatErrorMessage: ((Throwable) -> String)? = null
  ): ModularResult<T> {
    when (this) {
      is ModularResult.Error -> {
        val message = formatErrorMessage?.invoke(this.error)
          ?: this.error.errorMessageOrClassName()

        val duration = if (longToast) {
          Toast.LENGTH_LONG
        } else {
          Toast.LENGTH_SHORT
        }

        showToast(message, duration)
      }
      is ModularResult.Value -> {
        // no-op
      }
    }

    return this
  }

  protected fun <T> ModularResult<T>.toastOnSuccess(
    message: () -> String,
    longToast: Boolean = false
  ): ModularResult<T> {
    when (this) {
      is ModularResult.Error -> {
        // no-op
      }
      is ModularResult.Value -> {
        val duration = if (longToast) {
          Toast.LENGTH_LONG
        } else {
          Toast.LENGTH_SHORT
        }

        showToast(message(), duration)
      }
    }

    return this
  }

  /**
   * Very complex method that checks a lot of stuff. Basically it calculates the bottom padding that
   * is then used as a content padding of a RecyclerView.
   * */
  protected fun calculateBottomPaddingForRecyclerInDp(
    globalWindowInsetsManager: GlobalWindowInsetsManager,
    mainControllerCallbacks: MainControllerCallbacks?
  ): Int {
    val isInsideBottomNavAwareController = isInsideBottomNavAwareController()
    val isInsidePopupOrFloatingController = isInsidePopupOrFloatingController()
    val isInsideRequiresNoBottomNavBarController = isInsideRequiresNoBottomNavBarController()
    val isSplitLayoutMode = ChanSettings.isSplitLayoutMode()
    val bottomNavigationViewEnabled = ChanSettings.bottomNavigationViewEnabled.get()
      && !isInsideRequiresNoBottomNavBarController
    val isMainController = this is MainController
    val isKeyboardOpened = globalWindowInsetsManager.isKeyboardOpened

    // Main controller is a special case (it may or may not have the bottomNavView) so we handle
    // it separately
    if (isKeyboardOpened && !isMainController) {
      if (ChanSettings.isBottomNavigationPresent()) {
        return 0
      }

      return pxToDp(globalWindowInsetsManager.keyboardHeight)
    }

    // BottomPanel (the one with options like in the BookmarksController) must push the RecyclerView
    // upward when the bottomNavView is not present (SPLIT layout or it's disabled in the settings)
    if (mainControllerCallbacks?.isBottomPanelShown == true) {
      return when {
        isSplitLayoutMode || !bottomNavigationViewEnabled -> pxToDp(mainControllerCallbacks.bottomPanelHeight)
        bottomNavigationViewEnabled -> 0
        else -> pxToDp(globalWindowInsetsManager.bottom())
      }
    }

    if (isSplitLayoutMode) {
      if (isInsidePopupOrFloatingController) {
        return 0
      }

      return pxToDp(globalWindowInsetsManager.bottom())
    }

    if (isInsideBottomNavAwareController && !isSplitLayoutMode && !bottomNavigationViewEnabled) {
      // Controllers is inside the BottomNavAwareController (when the bottomNavigationView is disabled)
      // have 0 padding so we need to account for the system insets to avoid the recycler being drawn
      // below the nav bar.
      return pxToDp(globalWindowInsetsManager.bottom())
    }

    if (isInsideBottomNavAwareController || isInsidePopupOrFloatingController) {
      // Floating controllers handle the bottom inset inside the BaseFloatingController
      return 0
    }

    if (bottomNavigationViewEnabled) {
      if (isMainController) {
        return pxToDp(globalWindowInsetsManager.bottom())
      }

      return pxToDp(getDimen(R.dimen.navigation_view_size) + globalWindowInsetsManager.bottom())
    }

    if (isMainController) {
      return 0
    }

    return pxToDp(globalWindowInsetsManager.bottom())
  }

  private fun isInsideRequiresNoBottomNavBarController(): Boolean {
    var controller: Controller? = this

    while (true) {
      if (controller == null) {
        break
      }

      if (controller is RequiresNoBottomNavBar) {
        return true
      }

      controller = controller.parentController
    }

    return false
  }

  private fun isInsidePopupOrFloatingController(): Boolean {
    var controller: Controller? = this

    while (true) {
      if (controller == null) {
        break
      }

      if (controller is BaseFloatingComposeController || controller is PopupController) {
        return true
      }

      controller = controller.parentController
    }

    return false
  }

  private fun isInsideBottomNavAwareController(): Boolean {
    var controller: Controller? = this

    while (true) {
      if (controller == null) {
        break
      }

      if (controller is BottomNavBarAwareNavigationController) {
        return true
      }

      controller = controller.parentController
    }

    return false
  }

  companion object {
    private const val LOG_STATES = false
  }

}