package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.base.TestModule
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.core.site.sites.search.Chan4SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_themes.DefaultDarkTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@Suppress("BlockingMethodInNonBlockingContext")
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Chan4SearchRequestTest {
  private val testModule = TestModule()
  private lateinit var globalSearchUseCase: GlobalSearchUseCase
  private lateinit var siteManager: SiteManager
  private lateinit var themeEngine: ThemeEngine
  private lateinit var simpleCommentParser: SimpleCommentParser

  @Before
  fun setup() {
    AndroidUtils.init(testModule.provideApplication())
    ShadowLog.stream = System.out

    siteManager = Mockito.mock(SiteManager::class.java)
    themeEngine = Mockito.mock(ThemeEngine::class.java)
    simpleCommentParser = Mockito.mock(SimpleCommentParser::class.java)
    globalSearchUseCase = GlobalSearchUseCase(siteManager, themeEngine, simpleCommentParser)
  }

  @Test
  fun `test normal search response`() = runBlocking {
    val mockWebServer = MockWebServer()

    mockWebServer.enqueue(createResponse())
    mockWebServer.start()

    val mockUrl = mockWebServer.url("/?q=test&o=0")

    val siteDescriptor = SiteDescriptor.create("4chan")
    val chan4 = Mockito.mock(Site::class.java)
    val testSiteActions = TestSiteActions(testModule.provideProxiedOkHttpClient(), mockUrl)

    val theme = DefaultDarkTheme()

    whenever(siteManager.bySiteDescriptor(siteDescriptor)).thenReturn(chan4)
    whenever(siteManager.awaitUntilInitialized()).thenReturn(Unit)
    whenever(chan4.actions()).thenReturn(testSiteActions)
    whenever(themeEngine.chanTheme).thenReturn(theme)
    whenever(simpleCommentParser.parseComment(any()))
      .then { answer -> answer.getArgument(1, CharSequence::class.java) }

    val searchResult = globalSearchUseCase.execute(Chan4SearchParams(null, siteDescriptor, "test", null))
    assertThat(searchResult, instanceOf(SearchResult.Success::class.java))

    searchResult as SearchResult.Success
    assertEquals(5, searchResult.searchEntries.size)

    assertEquals(siteDescriptor, searchResult.searchEntries[0].posts.first().postDescriptor.threadDescriptor().siteDescriptor())
    assertEquals(siteDescriptor, searchResult.searchEntries[1].posts.first().postDescriptor.threadDescriptor().siteDescriptor())
    assertEquals(siteDescriptor, searchResult.searchEntries[2].posts.first().postDescriptor.threadDescriptor().siteDescriptor())
    assertEquals(siteDescriptor, searchResult.searchEntries[3].posts.first().postDescriptor.threadDescriptor().siteDescriptor())
    assertEquals(siteDescriptor, searchResult.searchEntries[4].posts.first().postDescriptor.threadDescriptor().siteDescriptor())

    assertEquals("int", searchResult.searchEntries[0].posts.first().postDescriptor.threadDescriptor().boardDescriptor.boardCode)
    assertEquals("int", searchResult.searchEntries[1].posts.first().postDescriptor.threadDescriptor().boardDescriptor.boardCode)
    assertEquals("int", searchResult.searchEntries[2].posts.first().postDescriptor.threadDescriptor().boardDescriptor.boardCode)
    assertEquals("int", searchResult.searchEntries[3].posts.first().postDescriptor.threadDescriptor().boardDescriptor.boardCode)
    assertEquals("pol", searchResult.searchEntries[4].posts.first().postDescriptor.threadDescriptor().boardDescriptor.boardCode)

    assertEquals(130661539, searchResult.searchEntries[0].posts.first().postDescriptor.threadDescriptor().threadNo)
    assertEquals(130667221, searchResult.searchEntries[1].posts.first().postDescriptor.threadDescriptor().threadNo)
    assertEquals(130667737, searchResult.searchEntries[2].posts.first().postDescriptor.threadDescriptor().threadNo)
    assertEquals(130639481, searchResult.searchEntries[3].posts.first().postDescriptor.threadDescriptor().threadNo)
    assertEquals(277880206, searchResult.searchEntries[4].posts.first().postDescriptor.threadDescriptor().threadNo)

    assertTrue(searchResult.searchEntries[0].posts.first().isOp)
    assertTrue(searchResult.searchEntries[1].posts.first().isOp)
    assertTrue(searchResult.searchEntries[2].posts.first().isOp)
    assertTrue(searchResult.searchEntries[3].posts.first().isOp)
    assertTrue(searchResult.searchEntries[4].posts.first().isOp)

    assertEquals(3, searchResult.searchEntries[0].posts.size)
    assertEquals(5, searchResult.searchEntries[1].posts.size)
    assertEquals(3, searchResult.searchEntries[2].posts.size)
    assertEquals(2, searchResult.searchEntries[3].posts.size)
    assertEquals(2, searchResult.searchEntries[4].posts.size)

    assertThat(searchResult.nextPageCursor, instanceOf(PageCursor.Page::class.java))
    assertEquals(10, (searchResult.nextPageCursor as PageCursor.Page).value)
    assertEquals(12108, searchResult.totalFoundEntries)

    mockWebServer.shutdown()
  }

  private fun createResponse(): MockResponse {
    val fileBytes = javaClass.classLoader!!.getResourceAsStream("search/test_search_response.html").readBytes()
    return MockResponse().setBody(String(fileBytes)).setResponseCode(200)
  }

  open class TestSiteActions(
    private val proxiedOkHttpClient: ProxiedOkHttpClient,
    private val mockUrl: HttpUrl
  ) : SiteActions {
    override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<SiteBoards>
      = JsonReaderRequest.JsonReaderResponse.UnknownServerError(NotImplementedError())

    override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<BoardPages>
      = JsonReaderRequest.JsonReaderResponse.UnknownServerError(NotImplementedError())

    override suspend fun post(replyChanDescriptor: ChanDescriptor, replyMode: ReplyMode): Flow<SiteActions.PostResult> {
      return flowOf(SiteActions.PostResult.PostError(NotImplementedError()))
    }

    override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult
      = SiteActions.DeleteResult.DeleteError(NotImplementedError())

    override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult
      = SiteActions.LoginResult.LoginError("NotImplementedError")

    override fun postRequiresAuthentication(): Boolean = false
    override fun postAuthenticate(): SiteAuthentication = SiteAuthentication.fromNone()
    override fun logout() = Unit
    override fun isLoggedIn(): Boolean = false
    override fun loginDetails(): AbstractLoginRequest? = null

    override suspend fun <T : SearchParams> search(searchParams: T): SearchResult {
      searchParams as Chan4SearchParams

      val request = Request.Builder()
        .url(mockUrl)
        .get()
        .build()

      return Chan4SearchRequest(
        request,
        proxiedOkHttpClient,
        searchParams
      ).execute()
    }
  }

}