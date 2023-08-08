package com.github.k1rakishou.common

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.system.ErrnoException
import android.system.OsConstants
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.lang.Thread.currentThread
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import javax.net.ssl.SSLException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val ELLIPSIZE_SYMBOL: CharSequence = "…"

suspend fun OkHttpClient.suspendCall(request: Request): Response {
  return suspendCancellableCoroutine { continuation ->
    val call = newCall(request)

    continuation.invokeOnCancellation { throwable ->
      if (throwable != null) {
        Try {
          if (!call.isCanceled()) {
            call.cancel()
          }
        }.ignore()
      }
    }

    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        continuation.resumeErrorSafe(e)
      }

      override fun onResponse(call: Call, response: Response) {
        continuation.resumeValueSafe(response)
      }
    })
  }
}

fun <T> CancellableContinuation<T>.resumeValueSafe(value: T) {
  if (isActive) {
    resume(value)
  }
}

fun CancellableContinuation<*>.resumeErrorSafe(error: Throwable) {
  if (isActive) {
    resumeWithException(error)
  }
}

inline fun <T : Any?> ResponseBody.useBufferedSource(useFunc: (BufferedSource) -> T): T {
  return byteStream().use { inputStream ->
    return@use inputStream.useBufferedSource(useFunc)
  }
}

inline fun <T : Any?> InputStream.useBufferedSource(useFunc: (BufferedSource) -> T): T {
  return source().use { source ->
    return@use source.buffer().use { buffer ->
      return@use useFunc(buffer)
    }
  }
}

inline fun <T : Any?> ResponseBody.useJsonReader(useFunc: (JsonReader) -> T): T {
  return byteStream().use { inputStream ->
    return@use InputStreamReader(inputStream).use { isr ->
      return@use JsonReader(isr).use { jsonReader ->
        return@use useFunc(jsonReader)
      }
    }
  }
}

inline fun <T : Any?> ResponseBody.useHtmlReader(requestUrl: String, useFunc: (Document) -> T): T {
  return byteStream().use { inputStream ->
    val document = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), requestUrl)
    return@use useFunc(document)
  }
}

suspend inline fun <reified T> OkHttpClient.suspendConvertIntoJsonObject(
  request: Request,
  gson: Gson
): JsonConversionResult<out T> {
  return withContext(Dispatchers.IO) {
    try {
      Logger.d("suspendConvertIntoJsonObject", "url='${request.url}'")
      val response = suspendCall(request)

      if (!response.isSuccessful) {
        return@withContext JsonConversionResult.HttpError(response.code)
      }

      val body = response.body
      if (body == null) {
        return@withContext JsonConversionResult.UnknownError(EmptyBodyResponseException())
      }

      val result = body.useJsonReader { jsonReader -> gson.fromJson<T>(jsonReader, T::class.java) }

      return@withContext JsonConversionResult.Success(result)
    } catch (error: Throwable) {
      return@withContext JsonConversionResult.UnknownError(error)
    }
  }
}

suspend inline fun OkHttpClient.suspendConvertIntoJsoupDocument(
  request: Request,
): ModularResult<Document> {
  return withContext(Dispatchers.IO) {
    return@withContext Try {
      Logger.d("suspendConvertIntoJsoupDocument", "url='${request.url}'")
      val response = suspendCall(request)

      if (!response.isSuccessful) {
        throw BadStatusResponseException(response.code)
      }

      if (response.body == null) {
        throw EmptyBodyResponseException()
      }

      return@Try response.body!!.use { body ->
        return@use body.byteStream().use { inputStream ->
          val url = request.url.toString()

          return@use Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), url)
        }
      }
    }
  }
}

suspend inline fun <reified T : Any?> OkHttpClient.suspendConvertIntoJsonObjectWithAdapter(
  request: Request,
  adapter: JsonAdapter<T>
): ModularResult<out T?> {
  return withContext(Dispatchers.IO) {
    return@withContext Try {
      Logger.d("suspendConvertIntoJsonObjectWithAdapter", "url='${request.url}'")
      val response = suspendCall(request)

      if (!response.isSuccessful) {
        throw BadStatusResponseException(response.code)
      }

      val body = response.body
      if (body == null) {
        throw EmptyBodyResponseException()
      }

      return@Try body.useBufferedSource { bufferedSource -> adapter.fromJson(bufferedSource) as T }
    }
  }
}

suspend inline fun <reified T> OkHttpClient.suspendConvertIntoJsonObjectWithType(
  request: Request,
  gson: Gson,
  type: Type
): JsonConversionResult<out T> {
  return withContext(Dispatchers.IO) {
    try {
      Logger.d("suspendConvertIntoJsonObjectWithType", "url='${request.url}'")
      val response = suspendCall(request)

      if (!response.isSuccessful) {
        return@withContext JsonConversionResult.HttpError(response.code)
      }

      val body = response.body
      if (body == null) {
        return@withContext JsonConversionResult.UnknownError(EmptyBodyResponseException())
      }

      val result = body.useJsonReader { jsonReader -> gson.fromJson<T>(jsonReader, type) }
      return@withContext JsonConversionResult.Success(result)
    } catch (error: Throwable) {
      return@withContext JsonConversionResult.UnknownError(error)
    }
  }
}

sealed class JsonConversionResult<T> {
  data class HttpError(val status: Int) : JsonConversionResult<Nothing>() {
    val isNotFound: Boolean
      get() = status == 404
  }

  data class UnknownError(val error: Throwable) : JsonConversionResult<Nothing>()
  data class Success<T>(val obj: T) : JsonConversionResult<T>()
}

fun JsonReader.nextStringOrNull(): String? {
  if (peek() != JsonToken.STRING) {
    skipValue()
    return null
  }

  val value = nextString()
  if (value.isNullOrEmpty()) {
    return null
  }

  return value
}

fun JsonReader.nextIntOrNull(): Int? {
  if (peek() != JsonToken.NUMBER) {
    skipValue()
    return null
  }

  return nextInt()
}

fun JsonReader.nextBooleanOrNull(): Boolean? {
  if (peek() != JsonToken.BOOLEAN) {
    skipValue()
    return null
  }

  return nextBoolean()
}

inline fun <T : Any?> JsonReader.jsonObject(func: JsonReader.() -> T): T {
  beginObject()

  try {
    return func(this)
  } finally {
    var token = peek()

    while (token != JsonToken.END_OBJECT) {
      skipValue()
      token = peek()
    }

    endObject()
  }
}

inline fun <T> JsonReader.jsonArray(next: JsonReader.() -> T): T {
  beginArray()

  try {
    return next(this)
  } finally {
    var token = peek()

    while (token != JsonToken.END_ARRAY) {
      skipValue()
      token = peek()
    }

    endArray()
  }
}

inline fun <T, R> Iterable<T>.flatMapNotNull(transform: (T) -> Iterable<R>?): List<R> {
  return flatMapNotNullTo(ArrayList<R>(), transform)
}

inline fun <T, R, C : MutableCollection<in R>> Iterable<T>.flatMapNotNullTo(
  destination: C,
  transform: (T) -> Iterable<R>?
): C {
  this
    .mapNotNull { transform(it) }
    .forEach { destination.addAll(it) }
  return destination
}

inline fun <T, R> Collection<T>.flatMapIndexed(transform: (Int, T) -> Collection<R>): List<R> {
  val destination = mutableListOf<R>()

  for ((index, element) in this.withIndex()) {
    val list = transform(index, element)

    destination.addAll(list)
  }

  return destination
}

inline fun <T> MutableCollection<T>.removeIfKt(filter: (T) -> Boolean): Boolean {
  var removed = false
  val mutableIterator = iterator()

  while (mutableIterator.hasNext()) {
    if (filter.invoke(mutableIterator.next())) {
      mutableIterator.remove()
      removed = true
    }
  }

  return removed
}

inline fun <E> Collection<E>.indexedIteration(func: (Int, E) -> Boolean) {
  val iterator = this.iterator()
  var index = 0

  while (iterator.hasNext()) {
    if (!func(index, iterator.next())) {
      return
    }

    ++index
  }
}

inline fun <E> Collection<E>.iteration(func: (Iterator<E>, E) -> Boolean) {
  val iterator = this.iterator()

  while (iterator.hasNext()) {
    if (!func(iterator, iterator.next())) {
      return
    }
  }
}

inline fun <E> MutableCollection<E>.mutableIteration(func: (MutableIterator<E>, E) -> Boolean) {
  val iterator = this.iterator()

  while (iterator.hasNext()) {
    if (!func(iterator, iterator.next())) {
      return
    }
  }
}

inline fun <K, V> MutableMap<K, V>.mutableIteration(func: (MutableIterator<Map.Entry<K, V>>, Map.Entry<K, V>) -> Boolean) {
  val iterator = this.iterator()

  while (iterator.hasNext()) {
    if (!func(iterator, iterator.next())) {
      return
    }
  }
}

val <T> List<T>.lastIndexOrNull: Int?
  get() {
    if (this.isEmpty()) {
      return null
    }

    return this.size - 1
  }

inline fun <T> Collection<T>.forEachReverseIndexed(action: (index: Int, T) -> Unit): Unit {
  if (this.isEmpty()) {
    return
  }

  var index = this.size - 1

  for (item in this) {
    action(index--, item)
  }
}

public inline fun <T> Sequence<T>.firstOrNullIndexed(predicate: (Int, T) -> Boolean): T? {
  for ((index, element) in this.withIndex()) if (predicate(index, element)) return element
  return null
}

inline fun <T, R> List<T>.bidirectionalMap(
  startPosition: Int = size / 2,
  crossinline mapper: (T) -> R
): List<R> {
  return this.bidirectionalSequence(startPosition)
    .map { element -> mapper(element) }
    .toList()
}


fun <T> List<T>.bidirectionalSequenceIndexed(startPosition: Int = size / 2): Sequence<IndexedValue<T>> {
  return sequence<IndexedValue<T>> {
    if (isEmpty()) {
      return@sequence
    }

    if (size == 1) {
      yield(IndexedValue(index = 0, value = first()))
      return@sequence
    }

    var position = startPosition
    var index = 0
    var increment = true

    var reachedLeftSide = false
    var reachedRightSide = false

    while (true) {
      val element = getOrNull(position)
      if (element == null) {
        if (reachedLeftSide && reachedRightSide) {
          break
        }

        if (position <= 0) {
          reachedLeftSide = true
        }

        if (position >= lastIndex) {
          reachedRightSide = true
        }
      }

      if (element != null) {
        yield(IndexedValue(index = position, value = element))
      }

      ++index

      if (increment) {
        position += index
      } else {
        position -= index
      }

      increment = increment.not()
    }
  }
}


fun <T> List<T>.bidirectionalSequence(startPosition: Int = size / 2): Sequence<T> {
  return sequence<T> {
    if (isEmpty()) {
      return@sequence
    }

    if (size == 1) {
      yield(first())
      return@sequence
    }

    var position = startPosition
    var index = 0
    var increment = true

    var reachedLeftSide = false
    var reachedRightSide = false

    while (true) {
      val element = getOrNull(position)
      if (element == null) {
        if (reachedLeftSide && reachedRightSide) {
          break
        }

        if (position <= 0) {
          reachedLeftSide = true
        }

        if (position >= lastIndex) {
          reachedRightSide = true
        }
      }

      if (element != null) {
        yield(element)
      }

      ++index

      if (increment) {
        position += index
      } else {
        position -= index
      }

      increment = increment.not()
    }
  }
}


inline fun <T, R : Any, C : MutableCollection<in R>> Collection<T>.mapReverseIndexedNotNullTo(
  destination: C,
  transform: (index: Int, T) -> R?
): C {
  forEachReverseIndexed { index, element -> transform(index, element)?.let { destination.add(it) } }
  return destination
}

inline fun <T, R : Any> Collection<T>.mapReverseIndexedNotNull(transform: (index: Int, T) -> R?): List<R> {
  return mapReverseIndexedNotNullTo(ArrayList<R>(), transform)
}

public inline fun <T, K> Iterable<T>.toHashSetBy(capacity: Int = 16, keySelector: (T) -> K): HashSet<K> {
  val hashSet = hashSetWithCap<K>(capacity)

  for (element in this) {
    hashSet.add(keySelector(element))
  }

  return hashSet
}

public inline fun <T, K, V> Iterable<T>.toHashMapBy(
  capacity: Int = 16,
  keySelector: (T) -> K,
  valueSelector: (T) -> V
): MutableMap<K, V> {
  val hashMap = mutableMapWithCap<K, V>(capacity)

  for (element in this) {
    val key = keySelector(element)
    val value = valueSelector(element)

    hashMap[key] = value
  }

  return hashMap
}

/**
 * Forces the kotlin compiler to require handling of all branches in the "when" operator
 * */
val <T : Any?> T.exhaustive: T
  get() = this

fun Matcher.groupOrNull(group: Int): String? {
  return try {
    if (group < 0 || group > groupCount()) {
      return null
    }

    this.group(group)
  } catch (error: Throwable) {
    null
  }
}

fun <K, V> MutableMap<K, V>.putIfNotContains(key: K, value: V) {
  if (!this.containsKey(key)) {
    this[key] = value
  }
}

inline fun <K, V> MutableMap<K, V>.putIfNotContainsLazy(key: K, crossinline valueFunc: () -> V) {
  if (!this.containsKey(key)) {
    this[key] = valueFunc()
  }
}

fun <K, V> HashMap<K, V>.putIfNotContains(key: K, value: V) {
  if (!this.containsKey(key)) {
    this[key] = value
  }
}

inline fun <K, V> HashMap<K, V>.putIfNotContainsLazy(key: K, crossinline valueFunc: () -> V) {
  if (!this.containsKey(key)) {
    this[key] = valueFunc()
  }
}

fun <K, V> TreeMap<K, V>.firstKeyOrNull(): K? {
  if (isEmpty()) {
    return null
  }

  return firstKey()
}

fun Throwable.errorMessageOrClassName(): String {
  if (!isExceptionImportant()) {
    return this::class.java.name
  }

  val actualMessage = if (cause?.message?.isNotNullNorBlank() == true) {
    cause!!.message
  } else {
    message
  }

  if (!actualMessage.isNullOrBlank()) {
    return actualMessage
  }

  return this::class.java.name
}

fun Throwable.isExceptionImportant(): Boolean {
  return when (this) {
    is CancellationException,
    is InterruptedIOException,
    is InterruptedException,
    is SSLException -> false
    else -> true
  }
}

fun Throwable.isCoroutineCancellationException(): Boolean {
  return when (this) {
    is CancellationException -> true
    is InterruptedIOException -> true
    else -> false
  }
}

fun View.updateMargins(all: Int) {
  updateMargins(start = all, end = all, top = all, bottom = all)
}

fun View.updateMargins(
  left: Int? = null,
  right: Int? = null,
  start: Int? = null,
  end: Int? = null,
  top: Int? = null,
  bottom: Int? = null
) {
  val layoutParams = layoutParams as? ViewGroup.MarginLayoutParams
    ?: return

  val newLeft = left ?: layoutParams.leftMargin
  val newRight = right ?: layoutParams.rightMargin
  val newStart = start ?: layoutParams.marginStart
  val newEnd = end ?: layoutParams.marginEnd
  val newTop = top ?: layoutParams.topMargin
  val newBottom = bottom ?: layoutParams.bottomMargin

  layoutParams.setMargins(
    newLeft,
    newTop,
    newRight,
    newBottom
  )

  layoutParams.marginStart = newStart
  layoutParams.marginEnd = newEnd
}

fun View.updatePaddings(
  left: Int? = null,
  right: Int? = null,
  top: Int? = null,
  bottom: Int? = null
) {
  val newLeft = left ?: paddingLeft
  val newRight = right ?: paddingRight
  val newTop = top ?: paddingTop
  val newBottom = bottom ?: paddingBottom

  try {
    setPadding(newLeft, newTop, newRight, newBottom)
  } catch (ignored: NullPointerException) {
    // Some weird Xiaomi bug.
    // java.lang.NullPointerException: Attempt to invoke interface method
    // 'void android.view.ActionMode$Callback.onDestroyActionMode(android.view.ActionMode)' on a null object reference
  }
}

fun View.updatePaddings(
  left: Int = paddingLeft,
  right: Int = paddingRight,
  top: Int = paddingTop,
  bottom: Int = paddingBottom
) {
  try {
    setPadding(left, top, right, bottom)
  } catch (ignored: NullPointerException) {
    // Some weird Xiaomi bug.
    // java.lang.NullPointerException: Attempt to invoke interface method
    // 'void android.view.ActionMode$Callback.onDestroyActionMode(android.view.ActionMode)' on a null object reference
  }
}

fun View.findParent(predicate: (ViewParent) -> Boolean): ViewParent? {
  var currentParent = this.parent

  while (currentParent != null) {
    if (predicate(currentParent)) {
      return currentParent
    }

    currentParent = currentParent.parent
  }

  return null
}

fun View.findChild(predicate: (View) -> Boolean): View? {
  if (predicate(this)) {
    return this
  }

  if (this !is ViewGroup) {
    return null
  }

  return findChildRecursively(this, predicate)
}

private fun findChildRecursively(viewGroup: ViewGroup, predicate: (View) -> Boolean): View? {
  for (index in 0 until viewGroup.childCount) {
    val child = viewGroup.getChildAt(index)
    if (predicate(child)) {
      return child
    }

    if (child is ViewGroup) {
      val result = findChildRecursively(child, predicate)
      if (result != null) {
        return result
      }
    }
  }

  return null
}

fun <T : View> View.findAllChildren(): Set<T> {
  val children = hashSetOf<View>()
  children += this

  iterateChildrenRecursivelyDeepFirst(children, this) { true }
  return children as Set<T>
}

fun <T : View> View.findChildren(predicate: (View) -> Boolean): Set<T> {
  val children = hashSetOf<View>()

  if (predicate(this)) {
    children += this
  }

  iterateChildrenRecursivelyDeepFirst(children, this, predicate)
  return children as Set<T>
}


fun iterateChildrenRecursivelyDeepFirst(children: HashSet<View>, view: View, predicate: (View) -> Boolean) {
  if (view !is ViewGroup) {
    return
  }

  for (index in 0 until view.childCount) {
    val child = view.getChildAt(index)
    if (predicate(child)) {
      children += child
    }

    if (child is ViewGroup) {
      iterateChildrenRecursivelyDeepFirst(children, child, predicate)
    }
  }
}

fun View.iterateAllChildrenBreadthFirstWhile(iterator: (View) -> ViewIterationResult) {
  val queue = LinkedList<View>()
  queue.add(this)

  while (queue.isNotEmpty()) {
    val child = queue.pop()

    val result = iterator(child)
    if (result == ViewIterationResult.Exit) {
      return
    }

    if (result == ViewIterationResult.SkipChildren) {
      continue
    }

    if (child is ViewGroup) {
      queue.addAll(child.children.toList())
    }
  }
}

enum class ViewIterationResult {
  Continue,
  SkipChildren,
  Exit
}

fun View.updateHeight(newHeight: Int) {
  val updatedLayoutParams = layoutParams
  updatedLayoutParams.height = newHeight
  layoutParams = updatedLayoutParams
}

fun String.removeAllAfterFirstInclusive(
  delimiter: Char,
  ignoreCase: Boolean = false
): String {
  var index = indexOfFirst { ch -> ch.equals(other = delimiter, ignoreCase = ignoreCase) }
  if (index < 0) {
    return this
  }

  if (index <= 0) {
    return ""
  }

  return substring(startIndex = 0, endIndex = index)
}

fun String.ellipsizeMiddle(maxLength: Int): String {
  val minStringLength = 5

  if (maxLength < minStringLength) {
    return this
  }

  if (this.length <= maxLength) {
    return this
  }

  val resultLength = maxLength - ELLIPSIZE_SYMBOL.length

  return this.take(resultLength / 2) + ELLIPSIZE_SYMBOL + this.takeLast(resultLength / 2)
}

fun String.ellipsizeEnd(maxLength: Int): String {
  val minStringLength = 5

  if (maxLength < minStringLength) {
    return this
  }

  if (this.length <= maxLength) {
    return this
  }

  return this.take(maxLength - ELLIPSIZE_SYMBOL.length) + ELLIPSIZE_SYMBOL
}

fun CharSequence.ellipsizeEnd(maxLength: Int): CharSequence {
  val minStringLength = 5

  if (maxLength < minStringLength) {
    return this
  }

  if (this.length <= maxLength) {
    return this
  }

  val spannableString = SpannableString.valueOf(this)
  val cutString = subSequence(0, (maxLength - ELLIPSIZE_SYMBOL.length).coerceAtMost(spannableString.length))

  return TextUtils.concat(cutString, ELLIPSIZE_SYMBOL)
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
inline fun CharSequence?.isNotNullNorEmpty(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorEmpty != null)
  }

  return this != null && this.length > 0
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
inline fun CharSequence?.isNotNullNorBlank(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorBlank != null)
  }

  return this != null && this.isNotBlank()
}

suspend fun <T> CompletableDeferred<T>.awaitSilently(defaultValue: T): T {
  return try {
    await()
  } catch (ignored: CancellationException) {
    defaultValue
  }
}

suspend fun Deferred<*>.awaitSilently() {
  try {
    await()
  } catch (ignored: CancellationException) {
    // no-op
  }
}

suspend fun CompletableDeferred<*>.awaitSilently() {
  try {
    await()
  } catch (ignored: CancellationException) {
    // no-op
  }
}

suspend fun <T> CompletableDeferred<T>.awaitCatching(): ModularResult<T> {
  return ModularResult.Try { await() }
}

fun View.resetClickListener() {
  setOnClickListener(null)

  // setOnClickListener sets isClickable to true even when the callback is null
  // (which is absolutely not obvious)
  isClickable = false
}

fun View.resetLongClickListener() {
  setOnLongClickListener(null)

  // setOnLongClickListener sets isClickable to true even when the callback is null
  // (which is absolutely not obvious)
  isClickable = false
}

fun safeCapacity(initialCapacity: Int): Int {
  return if (initialCapacity < 16) {
    16
  } else {
    initialCapacity
  }
}

inline fun <T> mutableListWithCap(initialCapacity: Int): MutableList<T> {
  return ArrayList(safeCapacity(initialCapacity))
}

inline fun <T> mutableListWithCap(collection: Collection<*>): MutableList<T> {
  return ArrayList(safeCapacity(collection.size))
}

inline fun <K, V> mutableMapWithCap(initialCapacity: Int): MutableMap<K, V> {
  return HashMap(safeCapacity(initialCapacity))
}

inline fun <K, V> mutableMapWithCap(collection: Collection<*>): MutableMap<K, V> {
  return HashMap(safeCapacity(collection.size))
}

inline fun <K, V> linkedMapWithCap(initialCapacity: Int): LinkedHashMap<K, V> {
  return LinkedHashMap(safeCapacity(initialCapacity))
}

inline fun <K, V> linkedMapWithCap(collection: Collection<*>): LinkedHashMap<K, V> {
  return LinkedHashMap(safeCapacity(collection.size))
}

inline fun <T> hashSetWithCap(initialCapacity: Int): HashSet<T> {
  return HashSet(safeCapacity(initialCapacity))
}

inline fun <T> hashSetWithCap(collection: Collection<*>): HashSet<T> {
  return HashSet(safeCapacity(collection.size))
}

inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long {
  var sum = 0L
  for (element in this) {
    sum += selector(element)
  }
  return sum
}

inline fun <T, R> Iterable<T>.chunkedMap(chunkSize: Int, mapper: (List<T>) -> List<R>): List<R> {
  require(chunkSize > 0) { "Bad chunkSize: $chunkSize" }

  return this
    .chunked(chunkSize)
    .flatMap { element -> mapper(element) }
}

fun SpannableStringBuilder.setSpanSafe(span: CharacterStyle, start: Int, end: Int, flags: Int) {
  if (this.length <= 0) {
    return
  }

  if (start >= end) {
    return
  }

  val len = this.length.coerceAtLeast(0)
  setSpan(span, start.coerceIn(0, len), end.coerceIn(0, len), flags)
}

fun SpannableString.setSpanSafe(span: CharacterStyle, start: Int, end: Int, flags: Int) {
  if (this.length <= 0) {
    return
  }

  if (start >= end) {
    return
  }

  val len = this.length.coerceAtLeast(0)
  setSpan(span, start.coerceIn(0, len), end.coerceIn(0, len), flags)
}

fun Spannable.setSpanSafe(span: CharacterStyle, start: Int, end: Int, flags: Int) {
  if (this.length <= 0) {
    return
  }

  if (start >= end) {
    return
  }

  val len = this.length.coerceAtLeast(0)
  setSpan(span, start.coerceIn(0, len), end.coerceIn(0, len), flags)
}

fun Int.mbytesToBytes(): Long {
  return this * (1024L * 1024L)
}

fun Long.mbytesToBytes(): Long {
  return this * (1024L * 1024L)
}

fun StringBuilder.appendIfNotEmpty(text: String): StringBuilder {
  if (isNotEmpty()) {
    append(text)
  }

  return this
}

@Suppress("UnnecessaryVariable")
suspend fun <T : Any> doIoTaskWithAttempts(attempts: Int, task: suspend (Int) -> T): T {
  require(attempts > 0) { "Bad attempts count: $attempts" }
  val retries = AtomicInteger(0)

  return coroutineScope {
    while (true) {
      ensureActive()

      try {
        // Try to execute a task
        val result = task(retries.incrementAndGet())

        // If no exceptions were thrown then just exit
        return@coroutineScope result
      } catch (error: IOException) {
        // If any kind of IOException was thrown then retry until we either succeed or exhaust all
        // attempts
        if (retries.get() >= attempts) {
          throw error
        }
      }
    }

    throw RuntimeException("Shouldn't be called")
  }
}

fun Throwable.isOutOfDiskSpaceError(): Boolean {
  if (cause is ErrnoException) {
    val errorNumber: Int = (cause as ErrnoException).errno
    if (errorNumber == OsConstants.ENOSPC) {
      return true
    }
  }

  return false
}

fun Bitmap.recycleSafe() {
  if (!isRecycled) {
    recycle()
  }
}

// This is absolutely ridiculous but kinda makes sense. Basically Mutex.withLock won't be executed
// and will throw CancellationException if the parent job is canceled. This may lead to very nasty
// bugs. So to avoid them here is a function that guarantees that withLock { ... } will be executed.
suspend fun <T> Mutex.withLockNonCancellable(owner: Any? = null, action: suspend () -> T): T {
  return withContext(NonCancellable) { withLock(owner) { action.invoke() } }
}

suspend fun OkHttpClient.readResponseAsString(request: Request): ModularResult<String> {
  return Try {
    val response = suspendCall(request)
    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val responseBody = if (response.body == null) {
      throw EmptyBodyResponseException()
    } else {
      response.body!!
    }

    return@Try responseBody.string()
  }
}

fun MotionEvent.isActionUpOrCancel(): Boolean {
  return actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL
}

fun CharSequence?.copy(): CharSequence? {
  if (this == null) {
    return null
  }

  return SpannableString(this)
}

fun View.isPointInsideView(x: Float, y: Float): Boolean {
  val location = IntArray(2)
  this.getLocationOnScreen(location)

  val viewX = location[0]
  val viewY = location[1]

  return x > viewX && x < viewX + this.width && y > viewY && y < viewY + this.height
}

inline fun <T, reified R> List<T>.mapArray(mapper: (T) -> R): Array<R> {
  val array = arrayOfNulls<R>(size)

  forEachIndexed { index, element ->
    array[index] = mapper(element)
  }

  return array as Array<R>
}

suspend fun <T, R> processDataCollectionConcurrently(
  dataList: Collection<T>,
  batchCount: Int = Runtime.getRuntime().availableProcessors(),
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
  processFunc: suspend (T) -> R?
): List<R> {
  if (dataList.isEmpty()) {
    return emptyList()
  }

  return supervisorScope {
    return@supervisorScope dataList
      .chunked(batchCount)
      .flatMap { dataChunk ->
        return@flatMap dataChunk
          .map { data ->
            return@map async(dispatcher) {
              try {
                ensureActive()
                return@async processFunc(data)
              } catch (error: Throwable) {
                return@async null
              }
            }
          }
          .awaitAll()
          .filterNotNull()
      }
  }
}

/**
 * @note: indexed doesn't mean ordered!
 * */
suspend fun <T, R> processDataCollectionConcurrentlyIndexed(
  dataList: Collection<T>,
  batchCount: Int = Runtime.getRuntime().availableProcessors(),
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
  processFunc: suspend (Int, T) -> R?
): List<R> {
  if (dataList.isEmpty()) {
    return emptyList()
  }

  val batchIndex = AtomicInteger(0)

  return supervisorScope {
    return@supervisorScope dataList
      .chunked(batchCount)
      .flatMap { dataChunk ->
        val results = dataChunk
          .mapIndexed { index, data ->
            return@mapIndexed async(dispatcher) {
              try {
                ensureActive()
                return@async processFunc(batchIndex.get() + index, data)
              } catch (error: Throwable) {
                return@async null
              }
            }
          }
          .awaitAll()
          .filterNotNull()

        batchIndex.addAndGet(results.size)
        return@flatMap results
      }
  }
}

private const val COOKIE_HEADER_NAME = "Cookie"

fun Request.Builder.appendCookieHeader(value: String): Request.Builder {
  val request = build()

  val cookies = request.header(COOKIE_HEADER_NAME)
  if (cookies == null) {
    return addHeader(COOKIE_HEADER_NAME, value)
  }

  // Absolute retardiation but OkHttp doesn't allow doing it differently (or maybe I just don't know?)
  val fullCookieValue = request.newBuilder()
    .removeHeader(COOKIE_HEADER_NAME)
    .addHeader(COOKIE_HEADER_NAME, "${cookies}; ${value}")
    .build()
    .header(COOKIE_HEADER_NAME)!!

  return header(COOKIE_HEADER_NAME, fullCookieValue)
}

fun HttpUrl.extractFileName(): String? {
  return this.pathSegments.lastOrNull()?.substringAfterLast("/")
}

fun CharSequence.countLines(): Int {
  var offset = 0
  var linesCount = 0

  while (offset < length) {
    val currentCh = getOrNull(offset)
      ?: break

    val nextCh = getOrNull(offset + 1)

    if (currentCh == '\n') {
      ++linesCount
    } else if (currentCh == '\r') {
      ++linesCount

      if (nextCh != null && nextCh == '\n') {
        ++offset
      }
    }

    ++offset
  }

  return linesCount
}

data class TextBounds(
  val textWidth: Int,
  val textHeight: Int,
  val lineBounds: List<RectF>
) {

  fun mergeWith(other: TextBounds): TextBounds {
    return TextBounds(
      textWidth = Math.max(this.textWidth, other.textWidth),
      textHeight = this.textHeight + other.textHeight,
      lineBounds = this.lineBounds + other.lineBounds
    )
  }

  companion object {
    val EMPTY = TextBounds(0, 0, emptyList())
  }

}

fun TextView.getTextBounds(text: CharSequence, availableWidth: Int): TextBounds {
  if (paint == null) {
    return TextBounds.EMPTY
  }

  val staticLayout = if (AndroidUtils.isAndroidM()) {
    StaticLayout.Builder
      .obtain(text, 0, text.length, paint, availableWidth)
      .setBreakStrategy(breakStrategy)
      .justificationModeTextView(this)
      .setHyphenationFrequency(hyphenationFrequency)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setMaxLines(maxLines)
      .setIncludePad(true)
      .setLineSpacing(0f, 1f)
      .build()
  } else {
    StaticLayout(text, paint, availableWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, true)
  }

  val lineBounds = (0 until staticLayout.lineCount)
    .map { line ->
      return@map RectF(
        staticLayout.getLineLeft(line),
        staticLayout.getLineTop(line).toFloat(),
        staticLayout.getLineRight(line),
        staticLayout.getLineBottom(line).toFloat()
      )
    }

  return TextBounds(
    staticLayout.width,
    staticLayout.height,
    lineBounds
  )
}

private fun StaticLayout.Builder.justificationModeTextView(textView: TextView): StaticLayout.Builder {
  if (AndroidUtils.isAndroid10()) {
    return setJustificationMode(textView.justificationMode)
  }

  return this
}

fun <K, V> LruCache<K, V>.contains(key: K): Boolean {
  return get(key) != null
}

fun <K, V> LruCache<K, V>.putIfNotContains(key: K, value: V) {
  if (!contains(key)) {
    put(key, value)
  }
}

fun TextView.selectionStartSafe(): Int {
  val len = length()
  if (len == 0) {
    return 0
  }

  return selectionStart.coerceIn(0, len)
}

fun TextView.selectionEndSafe(): Int {
  val len = length()
  if (len == 0) {
    return 0
  }

  return selectionEnd.coerceIn(0, len)
}

fun Element.getFirstElementByClassWithAnyValue(vararg values: String): Element? {
  for (value in values) {
    val element = getFirstElementByClassWithValue(value)
    if (element != null) {
      return element
    }
  }

  return null
}

fun Element.getFirstElementByClassWithValue(value: String): Element? {
  return getElementsByAttributeValue("class", value).firstOrNull()
}

data class FullSpanInfo(
  val span: CharacterStyle,
  val start: Int,
  val end: Int,
  val flags: Int
)

inline fun <reified T : CharacterStyle> Spannable.getAllSpans(
  start: Int = 0,
  end: Int = length
): List<FullSpanInfo> {
  return getSpans<T>(start, end, T::class.java).map { span ->
    return@map FullSpanInfo(span, getSpanStart(span), getSpanEnd(span), getSpanFlags(span))
  }
}

fun Spannable.removeSpans(
  start: Int,
  end: Int
) {
  getSpans(start, end, CharacterStyle::class.java).forEach { span ->
    val spanStart = getSpanStart(span)
    val spanEnd = getSpanEnd(span)

    if (spanStart in start..end || spanEnd in start..end) {
      removeSpan(span)
    }
  }
}

fun Int.modifyCurrentAlpha(modifier: Float): Int {
  val alpha = (this shr 24) and 0xff
  val newAlpha = (alpha.toFloat() * modifier.coerceIn(0f, 1f)).toInt()
  return ColorUtils.setAlphaComponent(this, newAlpha)
}

fun <T> MutableList<T>.move(fromIdx: Int, toIdx: Int): Boolean {
  if (fromIdx == toIdx) {
    return false
  }

  if (fromIdx < 0 || fromIdx >= size) {
    return false
  }

  if (toIdx < 0 || toIdx >= size) {
    return false
  }

  if (toIdx > fromIdx) {
    for (i in fromIdx until toIdx) {
      this[i] = this[i + 1].also { this[i + 1] = this[i] }
    }
  } else {
    for (i in fromIdx downTo toIdx + 1) {
      this[i] = this[i - 1].also { this[i - 1] = this[i] }
    }
  }

  return true
}

@JvmOverloads
fun Thread.callStack(tag: String = ""): String {
  val resultString = java.lang.StringBuilder(256)
  var index = 0

  for (ste in currentThread().stackTrace) {
    val className = ste?.className ?: continue
    val fileName = ste?.fileName ?: continue
    val methodName = ste?.methodName ?: continue
    val lineNumber = ste?.lineNumber ?: continue

    if (!className.startsWith("com.github.k1rakishou")) {
      continue
    }

    if (fileName.contains("KotlinExtensions.kt") && methodName.contains("callStack")) {
      continue
    }

    if (index > 0) {
      resultString.appendLine()
    }

    resultString.append("${tag} ${index}-[${fileName}:${lineNumber}]")
    resultString.append(" ")
    resultString.append(className)
    resultString.append("#")
    resultString.append(methodName)

    ++index
  }

  return resultString.toString()
}

public inline fun buildSpannableString(
  builderAction: SpannableStringBuilder.() -> Unit
): SpannableString {
  val builder = SpannableStringBuilder()
  builder.builderAction()
  return SpannableString.valueOf(builder)
}

fun Parcelable.marshall(): ByteArray {
  val parcel = Parcel.obtain()

  try {
    this.writeToParcel(parcel, 0)
    return parcel.marshall()
  } finally {
    parcel.recycle()
  }
}

fun <T : Parcelable> ByteArray.unmarshall(creator: Parcelable.Creator<T>): ModularResult<T> {
  return Try {
    val parcel = unmarshall(this)
    return@Try creator.createFromParcel(parcel)
  }
}

private fun unmarshall(bytes: ByteArray): Parcel {
  val parcel = Parcel.obtain()
  parcel.unmarshall(bytes, 0, bytes.size)
  parcel.setDataPosition(0)
  return parcel
}

fun MediaType.isJson(): Boolean {
  return type == "application" && subtype == "json"
}

private const val HTTP = "http://"
private const val HTTPS = "https://"
private const val WWW = "www."

fun fixUrlOrNull(inputUrlRaw: String?): String? {
  if (inputUrlRaw == null) {
    return null
  }

  return fixUrl(inputUrlRaw)
}

fun fixUrl(inputUrlRaw: String): String {
  var url = inputUrlRaw

  if (url.startsWith("//")) {
    url = url.removePrefix("//")
  }

  if (url.startsWith(WWW)) {
    url = url.removePrefix(WWW)
  }

  if (url.startsWith(HTTPS)) {
    return url
  }

  if (url.startsWith(HTTP)) {
    return HTTPS + url.removePrefix(HTTP)
  }

  return HTTPS + url
}

fun HttpUrl.domain(): String? {
  val host = host.removePrefix("www.")
  if (host.isEmpty()) {
    return null
  }

  var topDomainSeparatorFound = false
  var indexOfDomainSeparator = -1

  for (index in host.lastIndex downTo 0) {
    if (host[index] == '.') {
      if (!topDomainSeparatorFound) {
        topDomainSeparatorFound = true
        continue
      }

      indexOfDomainSeparator = index
      break
    }
  }

  if (indexOfDomainSeparator < 0) {
    return host
  }

  return host.substring(indexOfDomainSeparator + 1, host.length)
}

fun unreachable(message: String? = null): Nothing = error(message ?: "Unreachable!")