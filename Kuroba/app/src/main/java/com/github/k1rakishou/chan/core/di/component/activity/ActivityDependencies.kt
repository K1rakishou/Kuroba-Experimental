package com.github.k1rakishou.chan.core.di.component.activity

import com.github.k1rakishou.chan.core.di.module.activity.ActivityScopedViewModelFactory
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager

interface ActivityDependencies {
  val globalWindowInsetsManager: GlobalWindowInsetsManager
  val injectedViewModelFactory: ActivityScopedViewModelFactory
  val applicationVisibilityManager: ApplicationVisibilityManager
  val dialogFactory: DialogFactory
}