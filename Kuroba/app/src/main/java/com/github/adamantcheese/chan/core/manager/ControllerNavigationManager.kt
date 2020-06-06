package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.controller.Controller
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor

class ControllerNavigationManager {
  private val controllerNavigationSubject = PublishProcessor.create<ControllerNavigationChange>()

  fun listenForControllerNavigationChanges(): Flowable<ControllerNavigationChange> {
    return controllerNavigationSubject
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  fun onControllerPushed(controller: Controller) {
    controllerNavigationSubject.onNext(ControllerNavigationChange.Pushed(controller))
  }

  fun onControllerPopped(controller: Controller) {
    controllerNavigationSubject.onNext(ControllerNavigationChange.Popped(controller))
  }

  fun onControllerPresented(controller: Controller) {
    controllerNavigationSubject.onNext(ControllerNavigationChange.Presented(controller))
  }

  fun onControllerUnpresented(controller: Controller) {
    controllerNavigationSubject.onNext(ControllerNavigationChange.Unpresented(controller))
  }

  fun onControllerSwipedTo(controller: Controller) {
    controllerNavigationSubject.onNext(ControllerNavigationChange.SwipedTo(controller))
  }

  fun onControllerSwipedFrom(controller: Controller) {
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