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
package com.github.k1rakishou.chan.ui.controller.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostPreloadedInfoHolder
import com.github.k1rakishou.chan.core.model.ChanThread
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.PostImage
import com.github.k1rakishou.chan.core.model.PostIndexed
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4PagesRequest.BoardPage
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.text.span.PostLinkable
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.toolbar.Toolbar.ToolbarCallback
import com.github.k1rakishou.chan.ui.view.FloatingMenu
import com.github.k1rakishou.chan.ui.view.FloatingMenu.FloatingMenuCallback
import com.github.k1rakishou.chan.ui.view.FloatingMenuItem
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.ViewPagerAdapter
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ThemeSettingsController(context: Context) : Controller(context), View.OnClickListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var mockReplyManager: MockReplyManager

  private val dummyBoardDescriptor =
    BoardDescriptor.create("test_site", "test_board")
  private val dummyThreadDescriptor =
    ChanDescriptor.ThreadDescriptor.create("test_site", "test_board", 1234567890L)

  private val dummyPostCallback: PostCellCallback = object : PostCellCallback {
    override fun onPostBind(post: Post) {}
    override fun onPostUnbind(post: Post, isActuallyRecycling: Boolean) {}
    override fun onPostClicked(post: Post) {}
    override fun onPostDoubleClicked(post: Post) {}
    override fun onThumbnailClicked(postImage: PostImage, thumbnail: ThumbnailView) {}
    override fun onThumbnailLongClicked(postImage: PostImage, thumbnail: ThumbnailView) {}
    override fun onShowPostReplies(post: Post) {}
    override fun onPopulatePostOptions(post: Post, menu: MutableList<FloatingListMenuItem>) {}
    override fun onPostOptionClicked(post: Post, id: Any, inPopup: Boolean) {}
    override fun onPostLinkableClicked(post: Post, linkable: PostLinkable) {}
    override fun onPostNoClicked(post: Post) {}
    override fun onPostSelectionQuoted(post: Post, quoted: CharSequence) {}
    override fun showPostOptions(post: Post, inPopup: Boolean, items: List<FloatingListMenuItem>) {}

    override fun getChanDescriptor(): ChanDescriptor? {
      return dummyThreadDescriptor
    }

    override fun getPage(op: Post): BoardPage? {
      return null
    }

    override fun hasAlreadySeenPost(post: Post): Boolean {
      return false
    }
  }

  private val parserCallback: PostParser.Callback = object : PostParser.Callback {
    override fun isSaved(postNo: Long, postSubNo: Long): Boolean {
      return false
    }

    override fun isInternal(postNo: Long): Boolean {
      return true
    }

    override fun isValidBoard(boardDescriptor: BoardDescriptor): Boolean {
      return true
    }
  }

  private lateinit var pager: ViewPager
  private lateinit var done: ColorizableFloatingActionButton
  private lateinit var textView: TextView
  private var selectedAccentColor: Int = 0

  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    navigation.setTitle(R.string.settings_screen_theme)
    navigation.swipeable = false
    view = AndroidUtils.inflate(context, R.layout.controller_theme)
    pager = view.findViewById(R.id.pager)
    done = view.findViewById(R.id.add)
    done.setOnClickListener(this)
    textView = view.findViewById(R.id.text)

    val changeAccentColor = SpannableString(AndroidUtils.getString(R.string.setting_theme_accent))

    changeAccentColor.setSpan(object : ClickableSpan() {
      override fun onClick(widget: View) {
        showAccentColorPicker()
      }
    }, 0, changeAccentColor.length, 0)

    textView.text = TextUtils.concat(
      AndroidUtils.getString(R.string.setting_theme_explanation),
      "\n",
      changeAccentColor
    )

    textView.movementMethod = LinkMovementMethod.getInstance()

    val adapter = Adapter()
    pager.adapter = adapter

    val currentSettingsTheme = ChanSettings.getThemeAndColor()

//    val primaryColor = theme.primaryColor
//    if (theme.name == currentSettingsTheme.theme) {
//      // Current theme
//      pager.setCurrentItem(i, false)
//    }
//
//    selectedPrimaryColors.add(primaryColor)

    selectedAccentColor = themeEngine.chanTheme.accentColor
    done.backgroundTintList = ColorStateList.valueOf(selectedAccentColor)
  }

  override fun onClick(v: View) {
    if (v === done) {
      saveTheme()
    }
  }

  private fun saveTheme() {
//    val currentItem = pager.currentItem
//    val selectedTheme = themeEngine.chanTheme
//    val selectedColor = selectedPrimaryColors[currentItem]

    // TODO(KurobaEx):
//    themeHelper.changeTheme(selectedTheme, selectedColor, selectedAccentColor)
    (context as StartActivity).restartApp()
  }

  private fun showAccentColorPicker() {
    val items: MutableList<FloatingMenuItem> = ArrayList()
    var selected: FloatingMenuItem? = null

//    for (color in themeHelper.colors) {
//      val floatingMenuItem = FloatingMenuItem(
//        ColorsAdapterItem(color, color.color),
//        color.displayName
//      )
//
//      items.add(floatingMenuItem)
//      if (color == selectedAccentColor) {
//        selected = floatingMenuItem
//      }
//    }

    val menu = getColorsMenu(items, selected, textView)
    menu.setCallback(object : FloatingMenuCallback {
      override fun onFloatingMenuItemClicked(menu: FloatingMenu, item: FloatingMenuItem) {
        // TODO(KurobaEx):
//        val colorItem = item.id as ColorsAdapterItem
//        selectedAccentColor = colorItem.color
//        done.backgroundTintList = ColorStateList.valueOf(selectedAccentColor.color)
      }

      override fun onFloatingMenuDismissed(menu: FloatingMenu) {}
    })

    menu.setPopupHeight(AndroidUtils.dp(300f))
    menu.show()
  }

  private fun getColorsMenu(
    items: List<FloatingMenuItem>,
    selected: FloatingMenuItem?,
    anchor: View?
  ): FloatingMenu {
    val menu = FloatingMenu(context)
    menu.setItems(items)
    menu.setAdapter(ColorsAdapter(items))
    menu.setSelectedItem(selected)
    menu.setAnchor(anchor, Gravity.CENTER, 0, AndroidUtils.dp(5f))
    return menu
  }

  private inner class Adapter : ViewPagerAdapter() {

    override fun getPageTitle(position: Int): CharSequence? {
      return super.getPageTitle(position)
    }

    override fun getView(position: Int, parent: ViewGroup): View {
      val theme = themeEngine.chanTheme
      val parser = CommentParser(mockReplyManager).addDefaultRules()

      val postParser = DefaultPostParser(parser, postFilterManager)
      val builder1 = Post.Builder()
        .boardDescriptor(dummyBoardDescriptor)
        .id(123456789)
        .opId(123456789)
        .op(true)
        .replies(1)
        .setUnixTimestampSeconds(
          TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30))
        )
        .subject("Lorem ipsum")
        .comment("<span class=\"deadlink\">&gt;&gt;987654321</span><br>" + "http://example.com/<br>"
          + "Phasellus consequat semper sodales. Donec dolor lectus, aliquet nec mollis vel, rutrum vel enim.<br>"
          + "<span class=\"quote\">&gt;Nam non hendrerit justo, venenatis bibendum arcu.</span>")

      val post1 = postParser.parse(theme, builder1, parserCallback)
      post1.repliesFrom.add(234567890L)

      val builder2 = Post.Builder()
        .boardDescriptor(dummyBoardDescriptor)
        .id(234567890)
        .opId(123456789)
        .setUnixTimestampSeconds(
          TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15))
        )
        .comment("<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a><br>"
          + "Lorem ipsum dolor sit amet, consectetur adipiscing elit.")
        .postImages(listOf(
          PostImage.Builder()
            .serverFilename("123123123123.jpg")
            .imageUrl((BuildConfig.RESOURCES_ENDPOINT + "release_icon_512.png").toHttpUrl())
            .thumbnailUrl((BuildConfig.RESOURCES_ENDPOINT + "release_icon_512.png").toHttpUrl())
            .filename("icon")
            .extension("png")
            .build()))

      val post2 = postParser.parse(theme, builder2, parserCallback)

      val posts: MutableList<Post> = ArrayList()
      posts.add(post1)
      posts.add(post2)

      val linearLayout = LinearLayout(context)
      linearLayout.orientation = LinearLayout.VERTICAL
      linearLayout.setBackgroundColor(theme.primaryColor)

      val postsView = RecyclerView(context)

      val layoutManager = LinearLayoutManager(context)
      layoutManager.orientation = RecyclerView.VERTICAL
      postsView.layoutManager = layoutManager

      val adapter = PostAdapter(
        postFilterManager,
        postsView,
        object : PostAdapterCallback {
          override fun getChanDescriptor(): ChanDescriptor? {
            return dummyThreadDescriptor
          }

          override fun onUnhidePostClick(post: Post) {}
        },
        dummyPostCallback,
        object : ThreadStatusCell.Callback {
          override fun getTimeUntilLoadMore(): Long {
            return 0
          }

          override fun isWatching(): Boolean {
            return false
          }

          override fun getChanThread(): ChanThread? {
            return null
          }

          override fun getPage(op: Post): BoardPage? {
            return null
          }

          override fun onListStatusClicked() {}
        }
      )

      val postPreloadedInfoHolder = PostPreloadedInfoHolder()
      postPreloadedInfoHolder.preloadPostsInfo(posts)
      adapter.setThread(dummyThreadDescriptor, postPreloadedInfoHolder, indexPosts(posts))
      adapter.setPostViewMode(ChanSettings.PostViewMode.LIST)
      postsView.adapter = adapter

      val toolbar = Toolbar(context)

      val colorClick = View.OnClickListener {
        val items: MutableList<FloatingMenuItem> = ArrayList()
        var selected: FloatingMenuItem? = null

        // TODO(KurobaEx):
//        for (color in themeHelper.colors) {
//          val floatingMenuItem = FloatingMenuItem(ColorsAdapterItem(color, color.color500), color.displayName)
//          items.add(floatingMenuItem)
//          if (color == selectedPrimaryColors[position]) {
//            selected = floatingMenuItem
//          }
//        }

        val menu = getColorsMenu(items, selected, toolbar)

        menu.setCallback(object : FloatingMenuCallback {
          override fun onFloatingMenuItemClicked(menu: FloatingMenu, item: FloatingMenuItem) {
//            val colorItem = item.id as ColorsAdapterItem
//            selectedPrimaryColors[position] = colorItem.color
//            toolbar.setBackgroundColor(colorItem.color.color)
          }

          override fun onFloatingMenuDismissed(menu: FloatingMenu) {}
        })
        menu.show()
      }

      toolbar.setCallback(object : ToolbarCallback {
        override fun onMenuOrBackClicked(isArrow: Boolean) {
          colorClick.onClick(toolbar)
        }

        override fun onSearchVisibilityChanged(item: NavigationItem, visible: Boolean) {}
        override fun onSearchEntered(item: NavigationItem, entered: String) {}
      })

      toolbar.setBackgroundColor(theme.primaryColor)

      val item = NavigationItem()
      item.title = theme.name
      item.hasBack = false

      toolbar.setNavigationItem(false, true, item, theme)
      toolbar.setOnClickListener(colorClick)

      linearLayout.addView(
        toolbar,
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          AndroidUtils.getDimen(R.dimen.toolbar_height)
        )
      )
      linearLayout.addView(
        postsView,
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      )

      return linearLayout
    }

    private fun indexPosts(posts: List<Post>): List<PostIndexed> {
      return posts.mapIndexed { index, post -> PostIndexed(post, index, index) }
    }

    override fun getCount(): Int {
      return 1
    }
  }

  private inner class ColorsAdapter(private val items: List<FloatingMenuItem>) : BaseAdapter() {

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val textView = AndroidUtils.inflate(
        parent.context,
        R.layout.toolbar_menu_item,
        parent,
        false
      ) as TextView

      textView.text = getItem(position)
      textView.typeface = themeEngine.chanTheme.mainFont

//      val color = items[position].id as ColorsAdapterItem
//      textView.setBackgroundColor(color.bg)
//
//      val lightColor =
//        (Color.red(color.bg) * 0.299f + Color.green(color.bg) * 0.587f + Color.blue(color.bg) * 0.114f > 125f)
//
//      textView.setTextColor(if (lightColor) Color.BLACK else Color.WHITE)
      return textView
    }

    override fun getCount(): Int {
      return items.size
    }

    override fun getItem(position: Int): String {
      return items[position].text
    }

    override fun getItemId(position: Int): Long {
      return position.toLong()
    }

  }

//  private class ColorsAdapterItem(var color: PrimaryColor, var bg: Int)

}