package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.epoxy.epoxyPostLink
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPost
import javax.inject.Inject

class PostLinksController(
  private val post: ChanPost,
  private val onPostLinkClicked: (PostLinkable) -> Unit,
  context: Context
) : BaseFloatingController(context), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var recyclerView: EpoxyRecyclerView
  private lateinit var clickableArea: ConstraintLayout

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_post_links

  override fun onCreate() {
    super.onCreate()

    recyclerView = view.findViewById(R.id.epoxy_recycler_view)

    clickableArea = view.findViewById(R.id.clickable_area)
    clickableArea.setOnClickListener { pop() }

    val postLinks = collectLinks()
    if (postLinks.isEmpty()) {
      pop()
      return
    }

    themeEngine.addListener(this)
    onThemeChanged()

    renderPostLinks(postLinks)
  }

  override fun onDestroy() {
    super.onDestroy()

    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    recyclerView.setBackgroundColor(themeEngine.chanTheme.backColor)
  }

  private fun collectLinks(): Set<PostLink> {
    val linkables = post.postComment.linkables
      .filter { postLinkable -> postLinkable.type == PostLinkable.Type.LINK }

    val links = hashSetWithCap<PostLink>(linkables.size)

    for (index in linkables.indices) {
      val link = linkables[index].key.toString()
      if (link.isEmpty()) {
        continue
      }

      val postLinkable = linkables[index]
      val linkText = SpannableString(link)

      linkText.setSpan(
        PostLinkable(
          key = link,
          linkableValue = PostLinkable.Value.StringValue(link),
          type = PostLinkable.Type.LINK
        ),
        0,
        linkText.length,
        (250 shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY
      )

      links.add(PostLink(linkText, postLinkable))
    }

    return links
  }

  private fun renderPostLinks(postLinks: Set<PostLink>) {
    recyclerView.withModels {
      postLinks.forEach { postLink ->
        epoxyPostLink {
          id("epoxy_post_link_${postLink.hashCode()}")
          linkText(postLink.linkText)
          onOpenLinkClicked {
            onPostLinkClicked(postLink.postLinkable)
          }
        }
      }
    }
  }

  private data class PostLink(
    val linkText: CharSequence,
    val postLinkable: PostLinkable
  )
}