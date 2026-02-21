package com.github.k1rakishou.chan.core.di.component.controller

import com.github.k1rakishou.chan.core.di.module.controller.ControllerModule
import com.github.k1rakishou.chan.core.di.module.controller.ControllerScopedViewModelFactoryModule
import com.github.k1rakishou.chan.core.di.module.controller.ControllerScopedViewModelModule
import com.github.k1rakishou.chan.core.di.scope.PerController
import com.github.k1rakishou.chan.features.album.AlbumViewController
import com.github.k1rakishou.chan.features.setup.boards.add.AddBoardsController
import com.github.k1rakishou.chan.features.setup.boards.reorder.BoardsReorderController
import com.github.k1rakishou.chan.features.setup.boards.selection.BoardSelectionController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import dagger.BindsInstance
import dagger.Subcomponent

@PerController
@Subcomponent(
  modules = [
    ControllerModule::class,
    ControllerScopedViewModelFactoryModule::class,
    ControllerScopedViewModelModule::class
  ]
)
interface ControllerComponent : ControllerDependencies {
  fun inject(controller: Controller)
  fun inject(albumViewController: AlbumViewController)
  fun inject(addBoardsController: AddBoardsController)
  fun inject(boardsReorderController: BoardsReorderController)
  fun inject(boardSelectionController: BoardSelectionController)

  @Subcomponent.Builder
  interface Builder {
    @BindsInstance
    fun controller(controller: Controller): Builder
    @BindsInstance
    fun controllerModule(module: ControllerModule): Builder

    fun build(): ControllerComponent
  }
}