package com.github.k1rakishou.chan.ui.controller.base

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.github.k1rakishou.chan.core.base.ControllerHostActivity
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.core.di.module.controller.ControllerModule
import com.github.k1rakishou.chan.core.di.module.shared.IHasViewModelProviderFactory
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarStateManager
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.SnackbarManagerFactory
import com.github.k1rakishou.chan.ui.controller.base.transition.FadeTransition
import com.github.k1rakishou.chan.ui.controller.base.transition.TransitionMode
import com.github.k1rakishou.chan.ui.controller.navigation.BottomPanelContract
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleControllerType
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.IHasViewModelScope
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.chan.utils.activityDependencies
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class Controller(
  @JvmField var context: Context
) : IHasViewModelScope, IHasViewModelProviderFactory, SavedStateRegistryOwner {

  private lateinit var injectedViewModelFactory: ViewModelProvider.Factory

  override val viewModelFactory: ViewModelProvider.Factory
    get() = injectedViewModelFactory

  val kurobaSettings: KurobaSettings by lazy { appDependencies().kurobaSettings }
  val kurobaToolbarStateManager: KurobaToolbarStateManager by lazy { appDependencies().kurobaToolbarStateManager }
  val globalUiStateHolder: GlobalUiStateHolder by lazy { appDependencies().globalUiStateHolder }
  val appResources: AppResources by lazy { appDependencies().appResources }
  val snackbarManagerFactory: SnackbarManagerFactory by lazy { appDependencies().snackbarManagerFactory }
  val dialogFactory: DialogFactory by lazy { activityDependencies(context).dialogFactory }

  private val _lifecycleRegistry by lazy(LazyThreadSafetyMode.NONE) { LifecycleRegistry(this) }
  private val _savedStateRegistryController by lazy(LazyThreadSafetyMode.NONE) { SavedStateRegistryController.create(this) }
  // TODO: scoped viewmodels. Move this thing into an activity scoped ViewModel so that it can outlive configuration changes.
  val viewModelStore by lazy(LazyThreadSafetyMode.NONE) { ViewModelStore() }

  open val controllerKey: ControllerKey
    get() = ControllerKey(this::class.java.name)
  open val toolbarState: KurobaToolbarState
    get() = kurobaToolbarStateManager.getOrCreate(this, controllerKey)
  open var containerToolbarState: KurobaToolbarState
    get() = requireToolbarNavController().containerToolbarState
    set(value) { requireToolbarNavController().containerToolbarState = value }

  open val snackbarScope: SnackbarScope = SnackbarScope.Global()

  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ActivityScope(context.requireComponentActivity())

  override val lifecycle: Lifecycle
    get() = _lifecycleRegistry
  override val savedStateRegistry: SavedStateRegistry
    get() = _savedStateRegistryController.savedStateRegistry

  lateinit var view: ViewGroup

  private val _controllerResult = CompletableDeferred<ControllerResultInternal>()

  @JvmField
  var parentController: Controller? = null
  @JvmField
  val childControllers = ArrayList<Controller>()

  // NavigationControllers members
  @JvmField
  var previousSiblingController: Controller? = null
  @JvmField
  var navigationController: NavigationController? = null
  @JvmField
  var doubleNavigationController: DoubleNavigationController? = null
  @JvmField
  var doubleControllerType: DoubleControllerType? = null

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

  val topController: Controller?
    get() = if (childControllers.isNotEmpty()) {
      childControllers[childControllers.size - 1]
    } else {
      null
    }

  private val _topControllerState = MutableStateFlow<Controller?>(null)
  val topControllerState: StateFlow<Controller?>
    get() = _topControllerState.asStateFlow()

  private var _alive = false
  val alive: Boolean
    get() = _alive
  private var _shown = false
  val shown: Boolean
    get() = _shown

  protected val snackbarManager by lazy(LazyThreadSafetyMode.NONE) {
    snackbarManagerFactory.snackbarManager(snackbarScope)
  }

  private val job = SupervisorJob()
  protected val controllerScope by lazy(LazyThreadSafetyMode.NONE) {
    CoroutineScope(job + Dispatchers.Main + CoroutineName(controllerKey.key))
  }

  private val _navigationFlags = mutableStateOf<DeprecatedNavigationFlags>(DeprecatedNavigationFlags())
  val hasDrawer: Boolean
    get() = _navigationFlags.value.hasDrawer == true
  val hasBack: Boolean
    get() = _navigationFlags.value.hasBack == true
  val swipeable: Boolean
    get() = _navigationFlags.value.swipeable == true

  open val isFloating: Boolean = false

  init {
    initDependencies()
  }

  fun updateNavigationFlags(newNavigationFlags: DeprecatedNavigationFlags) {
    _navigationFlags.value = newNavigationFlags
  }

  fun controllerViewOrNull(): ViewGroup? {
    if (::view.isInitialized) {
      return view
    }

    return null
  }

  fun <T : Controller> lastChildControllerOrNull(predicate: (Controller) -> Boolean): T? {
    for (controller in childControllers.asReversed()) {
      if (predicate(controller)) {
        return controller as? T
      }
    }

    return null
  }

  fun requireToolbarNavController(): ToolbarNavigationController {
    if (this is ToolbarNavigationController) {
      return this
    }

    val navController = requireNavController()
    check(navController is ToolbarNavigationController) {
      "Expected navController to be 'ToolbarNavigationController' but got '${navController::class.java.name}'"
    }

    return navController
  }

  fun requireBottomPanelContract(): BottomPanelContract {
    return requireToolbarNavController() as BottomPanelContract
  }

  fun requireNavController(): NavigationController {
    if (this is NavigationController) {
      return this
    }

    return requireNotNull(navigationController) { "navigationController was not set" }
  }

  fun requireControllerHostActivity(): ControllerHostActivity {
    return (context as? ControllerHostActivity)
      ?: error("Wrong context! Expected ControllerHostActivity but got '${context::class.java.name}'")
  }

  fun isViewInitialized(): Boolean = ::view.isInitialized

  @Deprecated("All controllers should use controller scope! Switch this controller to injectControllerDependencies")
  protected open fun injectActivityDependencies(component: ActivityComponent) {
    error("Must be overridden!")
  }

  protected open fun injectControllerDependencies(component: ControllerComponent) {
    error("Must be overridden!")
  }

  @CallSuper
  open fun onCreate() {
    _alive = true
    Logger.verbose(TAG) { "${controllerKey} onCreate" }

    // TODO: scoped viewmodels. Pass a Bundle here
    Logger.verbose(TAG) { "${controllerKey} savedStateRegistryController.performRestore() start" }
    _savedStateRegistryController.performRestore(null)
    Logger.verbose(TAG) { "${controllerKey} savedStateRegistryController.performRestore() done" }
  }

  @CallSuper
  open fun onShow() {
    _shown = true
    view.visibility = View.VISIBLE

    Logger.verbose(TAG) { "${controllerKey} onShow" }

    for (controller in childControllers) {
      if (!controller.shown) {
        controller.onShow()
      }
    }
  }

  @CallSuper
  open fun onHide() {
    _shown = false
    view.visibility = View.GONE

    for (controller in childControllers) {
      if (controller.shown) {
        controller.onHide()
      }
    }

    Logger.verbose(TAG) { "${controllerKey} onHide" }
  }

  @CallSuper
  open fun onDestroy() {
    _alive = false
    compositeDisposable.clear()
    job.cancelChildren()

    Logger.verbose(TAG) { "${controllerKey} onDestroy" }

    while (childControllers.size > 0) {
      removeChildController(childControllers[0])
    }

    if (::view.isInitialized && AndroidUtils.removeFromParentView(view)) {
      Logger.verbose(TAG) { "${controllerKey} view removed onDestroy" }
    }

    // TODO: scoped viewmodels.
    //  Add 'isBeingDestroyed' flag to onDestroy() which should be set to false when:
    //  1. Controller is being removed by some user action (like popController).
    //  2. Owner activity is being destroyed (isChangingOrientations == false)
    //  In all different cases it should be false.
    //  Call `viewModelStore.clear()` only when isBeingDestroyed == true
    viewModelStore.clear()
    setControllerNoResult()
  }

  @CallSuper
  open fun saveInstanceState(outBundle: Bundle) {
    Logger.verbose(TAG) { "${controllerKey} savedStateRegistryController.performSave() start" }
    _savedStateRegistryController.performSave(outBundle)
    Logger.verbose(TAG) { "${controllerKey} savedStateRegistryController.performSave() done" }
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

    if (controller is DoubleNavigationController) {
      controller.leftControllerToolbarState?.init()
      controller.rightControllerToolbarState?.init()
    } else {
      controller.toolbarState.init()
    }

    if (controller.navigationController is StyledToolbarNavigationController) {
      (controller.navigationController as StyledToolbarNavigationController).onChildControllerPushed(controller)
    }

    snackbarManager.onControllerCreated(controllerKey)
    _topControllerState.value = topController
  }

  fun removeChildController(controller: Controller?) {
    if (controller == null) {
      return
    }

    controller.onDestroy()
    childControllers.remove(controller)

    if (controller.navigationController is StyledToolbarNavigationController) {
      (controller.navigationController as StyledToolbarNavigationController).onChildControllerPopped(controller)
    }

    if (controller is DoubleNavigationController) {
      controller.leftControllerToolbarState?.destroy()
      controller.rightControllerToolbarState?.destroy()
    } else {
      controller.toolbarState.destroy()
    }

    kurobaToolbarStateManager.remove(controller.controllerKey)
    snackbarManager.onControllerDestroyed(controllerKey)
    _topControllerState.value = topController
  }

  fun attachToParentView(parentView: ViewGroup?) {
    if (view.parent != null) {
      AndroidUtils.removeFromParentView(view)
      Logger.verbose(TAG) { "${controllerKey} view removed" }
    }

    if (parentView != null) {
      attachToView(parentView)
      Logger.verbose(TAG) { "${controllerKey} view attached" }
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

  open fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (!isTouchInsideView(event)) {
      return false
    }

    for (i in childControllers.indices.reversed()) {
      val controller = childControllers[i]
      if (controller.dispatchTouchEvent(event)) {
        return true
      }
    }

    return false
  }

  open fun onTouchEvent(event: MotionEvent): Boolean {
    if (!isTouchInsideView(event)) {
      return false
    }

    for (i in childControllers.indices.reversed()) {
      val controller = childControllers[i]
      if (controller.onTouchEvent(event)) {
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
    require(controller.isFloating) {
      "presentController() controller ${controller.controllerKey.key} must be floating!"
    }

    val contentView = requireControllerHostActivity().contentView
    presentingThisController = controller

    controller.presentedByController = this
    requireControllerHostActivity().pushControllerIntoStack(controller)

    controller.onCreate()
    controller.attachToView(contentView)
    controller.onShow()

    if (animated) {
      val transition = FadeTransition(transitionMode = TransitionMode.In)
      transition.to = controller
      transition.perform()

      return
    }
  }

  fun isAlreadyPresenting(predicate: (Controller) -> Boolean): Boolean {
    return requireControllerHostActivity().isControllerAdded(predicate)
  }

  fun getControllerOrNull(predicate: (Controller) -> Boolean): Controller? {
    return requireControllerHostActivity().getControllerOrNull(predicate)
  }

  open fun stopPresenting() {
    stopPresenting(true)
  }

  open fun stopPresenting(animated: Boolean) {
    require(this.isFloating) { "stopPresenting() controller ${this.controllerKey.key} must be floating!" }

    val startActivity = requireControllerHostActivity()
    if (!startActivity.containsController(this)) {
      return
    }

    if (animated) {
      val transition = FadeTransition(transitionMode = TransitionMode.Out)
      transition.from = this
      transition.onTransitionFinished { finishPresenting() }
      transition.perform()
    } else {
      finishPresenting()
    }

    startActivity.popController(this)
    presentedByController?.presentingThisController = null
  }

  @JvmOverloads
  protected fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    snackbarManager.globalToast(message, duration)
  }

  @JvmOverloads
  protected fun showToast(@StringRes messageId: Int, duration: Int = Toast.LENGTH_SHORT) {
    snackbarManager.globalToast(appResources.string(messageId), duration)
  }

  @JvmOverloads
  protected fun showErrorToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    snackbarManager.globalErrorToast(message, duration)
  }

  @JvmOverloads
  protected fun showErrorToast(@StringRes messageId: Int, duration: Int = Toast.LENGTH_SHORT) {
    snackbarManager.globalErrorToast(appResources.string(messageId), duration)
  }

  fun withLayoutMode(
    phone: (() -> Unit)? = null,
    tablet: (() -> Unit)? = null
  ) {
    if (kurobaSettings.application.isSplitLayoutModeBlocking()) {
      tablet?.invoke()
    } else {
      phone?.invoke()
    }
  }

  protected fun <T> ModularResult<T>.toastOnError(
    longToast: Boolean = false,
    message: ((Throwable) -> String)? = null
  ): ModularResult<T> {
    when (this) {
      is ModularResult.Error -> {
        error.toastOnError(longToast, message)
      }
      is ModularResult.Value -> {
        // no-op
      }
    }

    return this
  }

  protected fun Throwable.toastOnError(
    longToast: Boolean = false,
    message: ((Throwable) -> String)? = null
  ) {
    val message = message?.invoke(this)
      ?: this.errorMessageOrClassName()

    val duration = if (longToast) {
      Toast.LENGTH_LONG
    } else {
      Toast.LENGTH_SHORT
    }

    showToast(message, duration)
  }

  protected fun <T> ModularResult<T>.toastOnSuccess(
    longToast: Boolean = false,
    message: () -> String
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

  private fun initDependencies() {
    when (viewModelScope) {
      is ViewModelScope.ControllerScope -> {
        val controllerComponent = AppModuleAndroidUtils.extractActivityComponent(context)
          .controllerComponentBuilder()
          .controller(this)
          .controllerModule(ControllerModule())
          .build()

        injectedViewModelFactory = controllerComponent.controllerScopedViewModelFactory
        injectControllerDependencies(controllerComponent)
      }
      is ViewModelScope.ActivityScope -> {
        val activityComponent = AppModuleAndroidUtils.extractActivityComponent(context)

        injectedViewModelFactory = activityComponent.injectedViewModelFactory
        injectActivityDependencies(activityComponent)
      }
    }
  }

  protected fun isTouchInsideView(event: MotionEvent): Boolean {
    if (!::view.isInitialized) {
      return false
    }

    var x = event.x
    var y = event.y

    x += view.left.toFloat()
    y += view.top.toFloat()

    val left = view.left
    val top = view.top
    val right = view.right
    val bottom = view.bottom

    return (x >= left && x <= right && y >= top && y <= bottom)
  }

  protected fun setControllerResult(result: Any?) {
    if (!_controllerResult.isCompleted) {
      _controllerResult.complete(ControllerResultInternal.Result(result))
    }
  }

  protected fun setControllerNoResult() {
    if (!_controllerResult.isCompleted) {
      _controllerResult.complete(ControllerResultInternal.NoResult)
    }
  }

  suspend fun <T> awaitForResult(): ControllerResult<T> {
    return when (val result = _controllerResult.awaitSilently(null)) {
      is ControllerResultInternal.Result -> ControllerResult.Result(result.value as T)
      ControllerResultInternal.NoResult,
      null -> ControllerResult.NoResult
    }
  }

  private sealed interface ControllerResultInternal {
    data class Result(val value: Any?) : ControllerResultInternal
    data object NoResult : ControllerResultInternal
  }

  sealed interface ControllerResult<out T> {
    data class Result<T>(val value: T) : ControllerResult<T>
    data object NoResult : ControllerResult<Nothing>
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Controller

    return controllerKey == other.controllerKey
  }

  override fun hashCode(): Int {
    return javaClass.hashCode()
  }

  override fun toString(): String {
    return "Controller(controllerKey=$controllerKey)"
  }

  companion object {
    private const val TAG = "Controller"
  }

}