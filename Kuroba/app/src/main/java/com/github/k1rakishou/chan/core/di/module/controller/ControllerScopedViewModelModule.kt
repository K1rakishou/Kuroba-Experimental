package com.github.k1rakishou.chan.core.di.module.controller

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.chan.core.di.key.ViewModelKey
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.di.scope.PerController
import com.github.k1rakishou.chan.features.album.AlbumViewControllerViewModel
import com.github.k1rakishou.chan.features.setup.boards.add.AddBoardsControllerViewModel
import com.github.k1rakishou.chan.features.setup.boards.reorder.BoardsReorderControllerViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module
abstract class ControllerScopedViewModelModule {

  @IntoMap
  @ViewModelKey(AlbumViewControllerViewModel::class)
  @Binds
  @PerController
  abstract fun bindAlbumViewControllerViewModel(
    impl: AlbumViewControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(AddBoardsControllerViewModel::class)
  @Binds
  @PerController
  abstract fun bindAddBoardsControllerViewModel(
    impl: AddBoardsControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(BoardsReorderControllerViewModel::class)
  @Binds
  @PerController
  abstract fun bindBoardsConfigureControllerViewModel(
    impl: BoardsReorderControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

}