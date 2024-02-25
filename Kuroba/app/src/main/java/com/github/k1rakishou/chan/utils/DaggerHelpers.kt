package com.github.k1rakishou.chan.utils

import android.content.Context
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.di.component.application.ApplicationComponent
import com.github.k1rakishou.chan.core.di.module.activity.IHasActivityComponent

fun Context.applicationComponent(): ApplicationComponent {
  return Chan.getComponent()
}

fun Context.activityComponent(): ActivityComponent {
  return (this as IHasActivityComponent).activityComponent
}