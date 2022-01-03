package comment

import com.github.k1rakishou.core_parser.comment.HtmlParser
import junit.framework.Assert.assertEquals
import org.junit.Test

class HtmlParserTest {

  @Test
  fun html_parser_test_1() {
    val html = "Test<a href=\"#p333650561\" class=\"quotelink\">&gt;&gt;33365<wbr>0561</a><br><span class=\"quote\">&gt;what&#039;s the<wbr>best alternative</span><br>Reps";

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """Test
<a, href=#p333650561, class=quotelink>
>>33365
<wbr>
0561
<br>
<span, class=quote>
>what's the
<wbr>
best alternative
<br>
Reps
""".lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_2() {
    val html = "<s><a class=\"linkify twitter\" rel=\"noreferrer noopener\" target=\"_blank\" href=\"https://twitter.com/denonbu_eng/status/1388107521022468102\">https://twitter.com/denonbu_eng/sta<wbr>tus/1388107521022468102</a><a class=\"embedder\" href=\"javascript:;\" data-key=\"Twitter\" data-uid=\"denonbu_eng/status/1388107521022468102\" data-options=\"undefined\" data-href=\"https://twitter.com/denonbu_eng/status/1388107521022468102\">(<span>un</span>embed)</a></s>";

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """<s>
<a, class=linkify twitter, rel=noreferrer noopener, target=_blank, href=https://twitter.com/denonbu_eng/status/1388107521022468102>
https://twitter.com/denonbu_eng/sta
<wbr>
tus/1388107521022468102
<a, class=embedder, href=javascript:;, data-key=Twitter, data-uid=denonbu_eng/status/1388107521022468102, data-options=undefined, data-href=https://twitter.com/denonbu_eng/status/1388107521022468102>
(
<span>
un
embed)
""".lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_3() {
    val html = "<a href=\"/a/res/7272693.html#7272700\" class=\"post-reply-link\" data-thread=\"7272693\" data-num=\"7272700\">>>7272700</a><br>Ах ты пидор!!!!1<br>Хуй я тебе что посоветую теперь."

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """
<a, href=/a/res/7272693.html#7272700, class=post-reply-link, data-thread=7272693, data-num=7272700>
>>7272700
<br>
Ах ты пидор!!!!1
<br>
Хуй я тебе что посоветую теперь.

    """.trimIndent().lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_equals_symbols_inside_tag() {
    val html = "<a href=\"//boards.4channel.org/g/catalog#s=fglt\" class=\"quotelink\">&gt;&gt;&gt;/g/fglt</a>";

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes
    val expected = "<a, href=//boards.4channel.org/g/catalog#s=fglt, class=quotelink>\n>>>/g/fglt\n".lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_space_symbols_inside_tag() {
    val html = "<a href=\"//boards.4channel.org/  g/catalog#s=fglt\" class=\"quotelink\">&gt;&gt;&gt;/g/fglt</a>";

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes
    val expected = "<a, href=//boards.4channel.org/  g/catalog#s=fglt, class=quotelink>\n>>>/g/fglt\n".lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_test_dvach_empty_span_value_should_not_crash() {
    val html = "Тред обсуждения Oneplus, дочерней компании BBK.<br style=\"\"><br style=\"\">Особенности бренда:&nbsp;<br style=\"\"><br style=\"\">1) " +
      "смартфоны имеют одну из лучших оболочек на рынке — OxygenOS, тем не менее есть все возможности для её замены, " +
      "очень много кастомов обычно;<br style=\"\">2) Разницы между \"китайскими\" и \"некитайскими\" версиями смартфонов нет, " +
      "можно спокойно покупать смартфон на али. Китайские версии из коробки имеют прошивку HydrogenOS, но она очень легко меняется, " +
      "нужно просто скачать OxygenOS с официального сайта и установить его через меню телефона."

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """
Тред обсуждения Oneplus, дочерней компании BBK.
<br, style>
<br, style>
Особенности бренда: 
<br, style>
<br, style>
1) смартфоны имеют одну из лучших оболочек на рынке — OxygenOS, тем не менее есть все возможности для её замены, очень много кастомов обычно;
<br, style>
2) Разницы между "китайскими" и "некитайскими" версиями смартфонов нет, можно спокойно покупать смартфон на али. Китайские версии из коробки имеют прошивку HydrogenOS, но она очень легко меняется, нужно просто скачать OxygenOS с официального сайта и установить его через меню телефона.

    """.trimIndent().lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_foolfuuka_tags_with_whitespaces() {
    val html = "What are you working on, /g/?<br />\n" +
      "<br />\n" +
      "Previous thread: <a href=\"https://archive.wakarimasen.moe/g/post/82765615/\" class=\"backlink\" " +
      "data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"82765615\">&gt;&gt;82765615</a>"

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """
What are you working on, /g/?
<br>
<br>
Previous thread: 
<a, href=https://archive.wakarimasen.moe/g/post/82765615/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=82765615>
>>82765615

    """.trimIndent().lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }

  }

  @Test
  fun html_parser_foolfuuka_newlines_between_tags() {
    val html = "<span class=\"greentext\">&gt;Read the sticky: <a href=\"https://archive.wakarimasen.moe/g/post/76759434/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"76759434\">&gt;&gt;76759434</a></span><br />\n" +
      " <br />\n" +
      "<span class=\"greentext\">&gt;GNU/Linux questions <a href=\"https://archive.wakarimasen.moe/g/post/fglt/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"fglt\">&gt;&gt;&gt;/g/fglt</a></span><br />\n" +
      " <br />\n" +
      "<span class=\"greentext\">&gt;Windows questions <a href=\"https://archive.wakarimasen.moe/g/post/fwt/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"fwt\">&gt;&gt;&gt;/g/fwt</a></span><br />\n" +
      " <br />\n" +
      "<span class=\"greentext\">&gt;PC building? <a href=\"https://archive.wakarimasen.moe/g/post/pcbg/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"pcbg\">&gt;&gt;&gt;/g/pcbg</a></span><br />\n" +
      " <br />\n" +
      "<span class=\"greentext\">&gt;Programming questions <a href=\"https://archive.wakarimasen.moe/g/post/dpt/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"dpt\">&gt;&gt;&gt;/g/dpt</a></span><br />\n" +
      " <br />\n" +
      "<span class=\"greentext\">&gt;Good, cheap, laptops <a href=\"https://archive.wakarimasen.moe/g/post/tpg/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"tpg\">&gt;&gt;&gt;/g/tpg</a></span><br />\n" +
      " <br />\n" +
      "<span class=\"greentext\">&gt;Cheap electronics <a href=\"https://archive.wakarimasen.moe/g/post/csg/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"csg\">&gt;&gt;&gt;/g/csg</a></span><br />\n" +
      " <br />\n" +
      "<span class=\"greentext\">&gt;Server questions <a href=\"https://archive.wakarimasen.moe/g/post/hsg/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"hsg\">&gt;&gt;&gt;/g/hsg</a></span><br />\n" +
      " <br />\n" +
      "<span class=\"greentext\">&gt;Buying headphones <a href=\"https://archive.wakarimasen.moe/g/post/hpg/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"hpg\">&gt;&gt;&gt;/g/hpg</a></span><br />\n" +
      " <br />\n" +
      "How to find/activate any version of Windows?<br />\n" +
      "<a href=\"https://rentry.org/installwindows\" target=\"_blank\" rel=\"nofollow\">https://rentry.org/installwindows</a><br />\n" +
      " <br />\n" +
      "Previous Thread <a href=\"https://archive.wakarimasen.moe/g/post/82766675/\" class=\"backlink\" data-function=\"highlight\" data-backlink=\"true\" data-board=\"g\" data-post=\"82766675\">&gt;&gt;82766675</a>"

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """
<span, class=greentext>
>Read the sticky: 
<a, href=https://archive.wakarimasen.moe/g/post/76759434/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=76759434>
>>76759434
<br>
<br>
<span, class=greentext>
>GNU/Linux questions 
<a, href=https://archive.wakarimasen.moe/g/post/fglt/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=fglt>
>>>/g/fglt
<br>
<br>
<span, class=greentext>
>Windows questions 
<a, href=https://archive.wakarimasen.moe/g/post/fwt/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=fwt>
>>>/g/fwt
<br>
<br>
<span, class=greentext>
>PC building? 
<a, href=https://archive.wakarimasen.moe/g/post/pcbg/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=pcbg>
>>>/g/pcbg
<br>
<br>
<span, class=greentext>
>Programming questions 
<a, href=https://archive.wakarimasen.moe/g/post/dpt/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=dpt>
>>>/g/dpt
<br>
<br>
<span, class=greentext>
>Good, cheap, laptops 
<a, href=https://archive.wakarimasen.moe/g/post/tpg/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=tpg>
>>>/g/tpg
<br>
<br>
<span, class=greentext>
>Cheap electronics 
<a, href=https://archive.wakarimasen.moe/g/post/csg/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=csg>
>>>/g/csg
<br>
<br>
<span, class=greentext>
>Server questions 
<a, href=https://archive.wakarimasen.moe/g/post/hsg/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=hsg>
>>>/g/hsg
<br>
<br>
<span, class=greentext>
>Buying headphones 
<a, href=https://archive.wakarimasen.moe/g/post/hpg/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=hpg>
>>>/g/hpg
<br>
<br>
How to find/activate any version of Windows?
<br>
<a, href=https://rentry.org/installwindows, target=_blank, rel=nofollow>
https://rentry.org/installwindows
<br>
<br>
Previous Thread 
<a, href=https://archive.wakarimasen.moe/g/post/82766675/, class=backlink, data-function=highlight, data-backlink=true, data-board=g, data-post=82766675>
>>82766675

    """.trimIndent().lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_lainchan_incorrectly_handling_br_tags_parsing_them_as_b_tags() {
    val html = """
      This is the Beginner's General for beginners' questions.<br/><br />
      If with a simple question, and a suitable thread doesn't already exist, just post it here and someone will probably try to answer it.<br/><br/>
      Remember to do some research before asking a question. No one wants to answer a question that a simple search can already resolve.
    """.trimIndent()

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """
This is the Beginner's General for beginners' questions.
<br>
<br>
If with a simple question, and a suitable thread doesn't already exist, just post it here and someone will probably try to answer it.
<br>
<br>
Remember to do some research before asking a question. No one wants to answer a question that a simple search can already resolve.

    """.trimIndent().lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_incorrect_iframe_tag_case() {
    val html = """
      <iframe width=\"560\" height=\"315\" src=\"https://www.youtube.com/embed/GjUrSjjUbVk?&amp;autoplay=1\" title=\"YouTube video player\" frameborder=\"0\" allowfullscreen></iframe>
    """.trimIndent()

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val expected = """
      <iframe, width=560, height=315, src=https://www.youtube.com/embed/GjUrSjjUbVk?&amp;autoplay=1, title=YouTube video player, frameborder=0, allowfullscreen>

    """.trimIndent().lines()

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

  @Test
  fun html_parser_2chhk_weird_attribute_that_crashes_the_parser() {
    val html = """
      <p><a href="https://2ch.hk/media/res/127593.html" style="color:#CD6EFF;class=" s13"="">Webm-тред</a><br><br></p><table class="table table-bordered"><tbody><tr><td>test</td><td><br></td><td><br></td><td><br></td><td><br></td><td><br></td></tr><tr><td><br></td><td><br></td><td><br></td><td><br></td><td><br></td><td><br></td></tr><tr><td><br></td><td><br></td><td><br></td><td><br></td><td><br></td><td><br></td></tr></tbody></table>
    """.trimIndent()

    val expected = """
      <p>
      <a, href=https://2ch.hk/media/res/127593.html, style=color:#CD6EFF;class=>
      Webm-тред
      <br>
      <br>
      <table, class=table table-bordered>
      <tbody>
      <tr>
      <td>
      test
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <tr>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <tr>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      <td>
      <br>
      
    """.trimIndent().lines()

    val htmlParser = HtmlParser()
    val nodes = htmlParser.parse(html).nodes

    val actual = htmlParser.debugConcatIntoString(nodes).lines()
    assertEquals(expected.size, actual.size)

    actual.forEachIndexed { index, actualLine ->
      val expectedLine = expected[index]
      assertEquals(expectedLine, actualLine)
    }
  }

}