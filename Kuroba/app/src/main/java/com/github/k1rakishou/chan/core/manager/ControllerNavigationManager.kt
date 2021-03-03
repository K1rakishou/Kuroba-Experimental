package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor

class ControllerNavigationManager {
  private val controllerNavigationSubject = PublishProcessor.create<ControllerNavigationChange>()

  fun listenForControllerNavigationChanges(): Flowable<ControllerNavigationChange> {
    BackgroundUtils.ensureMainThread()

    return controllerNavigationSubject
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  fun onControllerPushed(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onControllerPushed(${controller.javaClass.simpleName})")

    controllerNavigationSubject.onNext(ControllerNavigationChange.Pushed(controller))
  }

  fun onControllerPopped(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onControllerPopped(${controller.javaClass.simpleName})")

    controllerNavigationSubject.onNext(ControllerNavigationChange.Popped(controller))
  }

  fun onControllerPresented(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onControllerPresented(${controller.javaClass.simpleName})")

    controllerNavigationSubject.onNext(ControllerNavigationChange.Presented(controller))
  }

  fun onControllerUnpresented(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onControllerUnpresented(${controller.javaClass.simpleName})")

    controllerNavigationSubject.onNext(ControllerNavigationChange.Unpresented(controller))
  }

  fun onControllerSwipedTo(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onControllerSwipedTo(${controller.javaClass.simpleName})")

    controllerNavigationSubject.onNext(ControllerNavigationChange.SwipedTo(controller))
  }

  fun onControllerSwipedFrom(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onControllerSwipedFrom(${controller.javaClass.simpleName})")

    controllerNavigationSubject.onNext(ControllerNavigationChange.SwipedFrom(controller))
  }

  fun onCloseAllNonMainControllers() {
    BackgroundUtils.ensureMainThread()
    Logger.d(TAG, "onCloseAllNonMainControllers()")

    // Do nothing here, other than logging
  }

  sealed class ControllerNavigationChange(val controller: Controller) {
    class Pushed(controller: Controller) : ControllerNavigationChange(controller)
    class Popped(controller: Controller) : ControllerNavigationChange(controller)
    class Presented(controller: Controller) : ControllerNavigationChange(controller)
    class Unpresented(controller: Controller) : ControllerNavigationChange(controller)
    class SwipedTo(controller: Controller) : ControllerNavigationChange(controller)
    class SwipedFrom(controller: Controller) : ControllerNavigationChange(controller)


    override fun toString(): String {
      return "CNC{${javaClass.simpleName}, controller=${controller.javaClass.simpleName}}"
    }
  }

  companion object {
    private const val TAG = "ControllerNavigationManager"
  }

}