package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.utils.BackgroundUtils
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
    controllerNavigationSubject.onNext(ControllerNavigationChange.Pushed(controller))
  }

  fun onControllerPopped(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    controllerNavigationSubject.onNext(ControllerNavigationChange.Popped(controller))
  }

  fun onControllerPresented(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    controllerNavigationSubject.onNext(ControllerNavigationChange.Presented(controller))
  }

  fun onControllerUnpresented(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    controllerNavigationSubject.onNext(ControllerNavigationChange.Unpresented(controller))
  }

  fun onControllerSwipedTo(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    controllerNavigationSubject.onNext(ControllerNavigationChange.SwipedTo(controller))
  }

  fun onControllerSwipedFrom(controller: Controller) {
    BackgroundUtils.ensureMainThread()
    controllerNavigationSubject.onNext(ControllerNavigationChange.SwipedFrom(controller))
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

}