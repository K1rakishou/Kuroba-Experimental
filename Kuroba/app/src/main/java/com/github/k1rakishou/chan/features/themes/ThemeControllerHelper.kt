package com.github.k1rakishou.chan.features.themes

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Spannable
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.findChild
import com.github.k1rakishou.core_spannable.BackgroundColorIdSpan
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.ThemeEditorPostLinkable
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import com.github.k1rakishou.model.data.post.PostIndexed
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.*
import java.util.concurrent.TimeUnit

class ThemeControllerHelper(
  private val themeEngine: ThemeEngine,
  private val postFilterManager: PostFilterManager,
  private val archivesManager: ArchivesManager
) {

  private val dummyBoardDescriptor =
    BoardDescriptor.create("test_site", "test_board")
  private val dummyThreadDescriptor =
    ChanDescriptor.ThreadDescriptor.create("test_site", "test_board", 1234567890L)

  private val dummyPostCallback: PostCellInterface.PostCellCallback = object : PostCellInterface.PostCellCallback {
    override fun onPostBind(postCellData: PostCellData) {}
    override fun onPostUnbind(postCellData: PostCellData, isActuallyRecycling: Boolean) {}
    override fun onPostClicked(postDescriptor: PostDescriptor) {}
    override fun onGoToPostButtonClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode) {}
    override fun onGoToPostButtonLongClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode) {}
    override fun onThumbnailClicked(postCellData: PostCellData, postImage: ChanPostImage) {}
    override fun onThumbnailLongClicked(chanDescriptor: ChanDescriptor, postImage: ChanPostImage) {}
    override fun onThumbnailOmittedFilesClicked(postCellData: PostCellData, postImage: ChanPostImage) {}
    override fun onShowPostReplies(post: ChanPost) {}
    override fun onPreviewThreadPostsClicked(post: ChanPost) {}
    override fun onPopulatePostOptions(post: ChanPost, menu: MutableList<FloatingListMenuItem>, inPopup: Boolean) {}
    override fun onPostOptionClicked(post: ChanPost, item: FloatingListMenuItem, inPopup: Boolean) {}
    override fun onPostLinkableClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean) {}
    override fun onPostLinkableLongClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean) {}
    override fun onPostNoClicked(post: ChanPost) {}
    override fun onPostPosterIdClicked(post: ChanPost) {}
    override fun onPostPosterNameClicked(post: ChanPost) {}
    override fun onPostPosterTripcodeClicked(post: ChanPost) {}
    override fun onPostSelectionQuoted(postDescriptor: PostDescriptor, selection: CharSequence) {}
    override fun onPostSelectionFilter(postDescriptor: PostDescriptor, selection: CharSequence) {}
    override fun showPostOptions(post: ChanPost, inPopup: Boolean, items: List<FloatingListMenuItem>) {}
    override fun onUnhidePostClick(post: ChanPost, inPopup: Boolean) {}
    override fun currentSpanCount(): Int = 1

    override val currentChanDescriptor: ChanDescriptor?
      get() = dummyThreadDescriptor

    override fun getBoardPages(boardDescriptor: BoardDescriptor): BoardPages? {
      return null
    }
  }

  private val parserCallback: PostParser.Callback = object : PostParser.Callback {
    override fun isSaved(threadNo: Long, postNo: Long, postSubNo: Long): Boolean {
      return false
    }

    override fun isHiddenOrRemoved(threadNo: Long, postNo: Long, postSubNo: Long): Int {
      return PostParser.NORMAL_POST
    }

    override fun isInternal(postNo: Long): Boolean {
      return true
    }

    override fun isParsingCatalogPosts(): Boolean {
      return false
    }
  }

  suspend fun createSimpleThreadView(
    context: Context,
    theme: ChanTheme,
    navigationItem: NavigationItem,
    navigationController: NavigationController,
    options: Options,
    postCellDataWidthNoPaddings: Int
  ): CoordinatorLayout {
    val parser = CommentParser()
      .addDefaultRules()

    val postParser = DefaultPostParser(parser, archivesManager)
    val builder1 = ChanPostBuilder()
      .boardDescriptor(dummyBoardDescriptor)
      .id(123456789)
      .opId(123456789)
      .op(true)
      .replies(1)
      .setUnixTimestampSeconds(
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30))
      )
      .subject("Lorem ipsum")
      .name("OP")
      .comment(
        "<span class=\"deadlink\">&gt;&gt;987654321</span><br>" + "http://example.com/<br>"
          + "Phasellus consequat semper sodales. Donec dolor lectus, aliquet nec mollis vel, rutrum vel enim.<br>"
          + "<span class=\"quote\">&gt;Nam non hendrerit justo, venenatis bibendum arcu.</span>"
      )

    val post1 = postParser.parseFull(builder1, parserCallback)
    post1.repliesFrom.add(PostDescriptor.create(dummyThreadDescriptor, 234567890L))

    val pd2 = PostDescriptor.create(dummyBoardDescriptor, 234567890L, 123456789L)
    val builder2 = ChanPostBuilder()
      .boardDescriptor(dummyBoardDescriptor)
      .id(234567890)
      .opId(123456789)
      .setUnixTimestampSeconds(
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15))
      )
      .comment(
        "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a><br>"
          + "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
      )
      .name("Test name")
      .tripcode("!N4EoL/Xuog")
      .postImages(
        listOf(
          ChanPostImageBuilder()
            .serverFilename("123123123123.jpg")
            .imageUrl((AppConstants.RESOURCES_ENDPOINT + "ic_launcher_release_round.png").toHttpUrl())
            .thumbnailUrl((AppConstants.RESOURCES_ENDPOINT + "ic_launcher_release_round.png").toHttpUrl())
            .filename("icon")
            .extension("png")
            .build()
        ),
        pd2
      )

    val post2 = postParser.parseFull(builder2, parserCallback)

    val posts: MutableList<ChanPost> = ArrayList()
    posts.add(post1)
    posts.add(post2)

    val linearLayout = LinearLayout(context)
    linearLayout.layoutParams = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    )

    linearLayout.orientation = LinearLayout.VERTICAL
    linearLayout.setBackgroundColor(theme.backColor)

    val postsView = RecyclerView(context)
    postsView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
      override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
        return EdgeEffect(view.context).apply { color = theme.accentColor }
      }
    }

    val layoutManager = LinearLayoutManager(context)
    layoutManager.orientation = RecyclerView.VERTICAL
    postsView.layoutManager = layoutManager

    val adapter = PostAdapter(
      postsView,
      object : PostAdapter.PostAdapterCallback {
        override val currentChanDescriptor: ChanDescriptor?
          get() = dummyThreadDescriptor
        override val endOfCatalogReached: Boolean
          get() = false
        override val unlimitedOrCompositeCatalogEndReached: Boolean
          get() = true

        override fun loadCatalogPage(overridePage: Int?) {

        }

        override val isUnlimitedOrCompositeCatalog: Boolean
          get() = false

        override fun getNextPage(): Int? {
          return null
        }

        override fun onPostCellBound(postCell: GenericPostCell) {
          val postCellView = postCell.findChild { it is PostCell } as? PostCell
            ?: return

          (postCellView.findChild { it.id == R.id.title } as? TextView)?.let { title ->
            hackSpanColors(title.text, theme)
          }

          (postCellView.findChild { it.id == R.id.comment } as? TextView)?.let { comment ->
            hackSpanColors(comment.text, theme)
          }
        }
      },
      dummyPostCallback,
      object : ThreadStatusCell.Callback {
        override suspend fun timeUntilLoadMoreMs(): Long {
          return 0
        }

        override val currentChanDescriptor: ChanDescriptor?
          get() = null

        override fun getPage(originalPostDescriptor: PostDescriptor): BoardPage? {
          return null
        }

        override fun isWatching(): Boolean {
          return false
        }

        override fun onListStatusClicked() {}
      }
    )

    adapter.setThread(dummyThreadDescriptor, theme, indexPosts(posts), postCellDataWidthNoPaddings)
    adapter.setBoardPostViewMode(ChanSettings.BoardPostViewMode.LIST)
    postsView.adapter = adapter

    val bottomNavView = BottomNavigationView(context)
    bottomNavView.menu.clear()
    bottomNavView.inflateMenu(R.menu.bottom_navigation_menu)
    bottomNavView.selectedItemId = R.id.action_browse
    bottomNavView.setBackgroundColor(theme.primaryColor)

    val uncheckedColor = if (ThemeEngine.isNearToFullyBlackColor(theme.primaryColor)) {
      Color.DKGRAY
    } else {
      ThemeEngine.manipulateColor(theme.primaryColor, .7f)
    }

    bottomNavView.itemIconTintList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(Color.WHITE, uncheckedColor)
    )

    bottomNavView.itemTextColor = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(Color.WHITE, uncheckedColor)
    )

    val toolbar = Toolbar(context)
    toolbar.setIgnoreThemeChanges()
    toolbar.setBackgroundColor(theme.primaryColor)
    toolbar.setNavigationItem(false, true, navigationItem, theme)

    val fab = FloatingActionButton(context)
    fab.id = R.id.theme_view_fab_id
    fab.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_create_white_24dp))
    fab.imageTintList = ColorStateList.valueOf(Color.WHITE)
    fab.updateColors(theme)
    fab.backgroundTintList = ColorStateList.valueOf(theme.accentColor)

    val showMoreThemesButton = AppCompatButton(context)
    showMoreThemesButton.setBackgroundColor(theme.accentColor)
    showMoreThemesButton.setTextColor(Color.WHITE)
    showMoreThemesButton.setText(getString(R.string.theme_settings_controller_more_themes))
    showMoreThemesButton.setOnClickListener {
      val themeGalleryController = ThemeGalleryController(context, theme.isLightTheme, options.refreshThemesControllerFunc)
      navigationController.pushController(themeGalleryController)
    }

    if (options.showMoreThemesButton) {
      showMoreThemesButton.setVisibilityFast(View.VISIBLE)
    } else {
      showMoreThemesButton.setVisibilityFast(View.GONE)
    }

    linearLayout.addView(
      toolbar,
      LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        AppModuleAndroidUtils.getDimen(R.dimen.toolbar_height)
      )
    )
    linearLayout.addView(
      postsView,
      LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f
      )
    )
    linearLayout.addView(
      bottomNavView,
      LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        AppModuleAndroidUtils.getDimen(R.dimen.navigation_view_size)
      )
    )

    val coordinatorLayout = CoordinatorLayout(context)
    coordinatorLayout.addView(linearLayout)

    coordinatorLayout.addView(
      fab,
      CoordinatorLayout.LayoutParams(
        CoordinatorLayout.LayoutParams.WRAP_CONTENT,
        CoordinatorLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.END or Gravity.BOTTOM
        bottomMargin = AppModuleAndroidUtils.dp(16f) + AppModuleAndroidUtils.getDimen(R.dimen.navigation_view_size)
        marginEnd = AppModuleAndroidUtils.dp(16f)
      }
    )

    coordinatorLayout.addView(
      showMoreThemesButton,
      CoordinatorLayout.LayoutParams(
        CoordinatorLayout.LayoutParams.WRAP_CONTENT,
        CoordinatorLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.START or Gravity.BOTTOM
        bottomMargin = AppModuleAndroidUtils.dp(16f) + AppModuleAndroidUtils.getDimen(R.dimen.navigation_view_size)
        marginStart = AppModuleAndroidUtils.dp(16f)
      }
    )

    return coordinatorLayout
  }

  private fun indexPosts(posts: List<ChanPost>): List<PostIndexed> {
    return posts.mapIndexed { index, post ->
      PostIndexed(post, index)
    }
  }

  private fun hackSpanColors(input: CharSequence?, theme: ChanTheme) {
    if (input !is Spannable) {
      return
    }

    val spans = input.getSpans<CharacterStyle>()

    for (span in spans) {
      if (span is BackgroundColorIdSpan || span is ForegroundColorIdSpan) {
        val start = input.getSpanStart(span)
        val end = input.getSpanEnd(span)
        val flags = input.getSpanFlags(span)

        val newColor = when (span) {
          is ForegroundColorIdSpan -> span.chanThemeColorId
          is BackgroundColorIdSpan -> span.chanThemeColorId
          else -> throw IllegalStateException("Unknown span: ${span::class.java.simpleName}")
        }

        input.removeSpan(span)
        input.setSpan(
          ForegroundColorSpan(theme.getColorByColorId(newColor)),
          start,
          end,
          flags,
        )

        continue
      }

      if (span is PostLinkable) {
        val start = input.getSpanStart(span)
        val end = input.getSpanEnd(span)
        val flags = input.getSpanFlags(span)

        input.removeSpan(span)
        input.setSpan(
          ThemeEditorPostLinkable(themeEngine, theme, span.key, span.linkableValue, span.type),
          start,
          end,
          flags,
        )
      }
    }
  }

  private fun FloatingActionButton.updateColors(theme: ChanTheme) {
    backgroundTintList = ColorStateList.valueOf(theme.accentColor)

    val isDarkColor = ThemeEngine.isDarkColor(theme.accentColor)
    if (isDarkColor) {
      drawable.setTint(Color.WHITE)
    } else {
      drawable.setTint(Color.BLACK)
    }
  }

  data class Options(
    val showMoreThemesButton: Boolean = false,
    val refreshThemesControllerFunc: (() -> Unit)? = null
  )

}