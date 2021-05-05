package com.github.k1rakishou.common

import android.graphics.Bitmap
import android.system.ErrnoException
import android.system.OsConstants
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.core.view.children
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val ELLIPSIZE_SYMBOL: CharSequence = "…"

suspend fun OkHttpClient.suspendCall(request: Request): Response {
  return suspendCancellableCoroutine { continuation ->
    val call = newCall(request)

    continuation.invokeOnCancellation {
      Try { call.cancel() }.ignore()
    }

    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        continuation.resumeWithException(e)
      }

      override fun onResponse(call: Call, response: Response) {
        continuation.resume(response)
      }
    })
  }
}

suspend inline fun <reified T> OkHttpClient.suspendConvertIntoJsonObject(
  request: Request,
  gson: Gson
): JsonConversionResult<out T> {
  return withContext(Dispatchers.IO) {
    try {
      val response = suspendCall(request)

      if (!response.isSuccessful) {
        return@withContext JsonConversionResult.HttpError(response.code)
      }

      val body = response.body
      if (body == null) {
        return@withContext JsonConversionResult.UnknownError(IOException("Response has no body"))
      }

      val result = body.byteStream().use { inputStream ->
        gson.fromJson<T>(JsonReader(InputStreamReader(inputStream)), T::class.java)
      }

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

inline fun <E> MutableList<E>.mutableIteration(func: (MutableIterator<E>, E) -> Boolean) {
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

inline fun <T, R> List<T>.highLowMap(mapper: (T) -> R): List<R> {
  if (isEmpty()) {
    return emptyList()
  }

  if (size == 1) {
    return listOf(mapper(first()))
  }

  var position = size / 2
  var index = 0
  var increment = true

  val resultList = mutableListWithCap<R>(size)

  var reachedLeftSide = false
  var reachedRightSize = false

  while (true) {
    val element = getOrNull(position)
    if (element == null) {
      if (reachedLeftSide && reachedRightSize) {
        break
      }

      if (position <= 0) {
        reachedLeftSide = true
      }

      if (position >= lastIndex) {
        reachedRightSize = true
      }
    }

    if (element != null) {
      resultList += mapper(element)
    }

    ++index

    if (increment) {
      position += index
    } else {
      position -= index
    }

    increment = increment.not()
  }

  return resultList
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
    is CancellationException -> false
    is InterruptedIOException -> false
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

  setPadding(newLeft, newTop, newRight, newBottom)
}

fun View.updatePaddings(
  left: Int = paddingLeft,
  right: Int = paddingRight,
  top: Int = paddingTop,
  bottom: Int = paddingBottom
) {
  setPadding(left, top, right, bottom)
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

  return TextUtils.concat(this.take(maxLength - ELLIPSIZE_SYMBOL.length), ELLIPSIZE_SYMBOL)
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

fun SpannableStringBuilder.setSpanSafe(span: CharacterStyle, start: Int, end: Int, flags: Int) {
  setSpan(
    span,
    start.coerceAtLeast(0),
    end.coerceAtMost(this.length),
    flags
  )
}

fun SpannableString.setSpanSafe(span: CharacterStyle, start: Int, end: Int, flags: Int) {
  setSpan(
    span,
    start.coerceAtLeast(0),
    end.coerceAtMost(this.length),
    flags
  )
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

fun IOException.isOutOfDiskSpaceError(): Boolean {
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
      throw IOException("Bad response code: ${response.code}")
    }

    val responseBody = response.body
      ?: throw IOException("Response has no body")

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