package com.github.k1rakishou.chan.core.usecase

import android.content.Context
import android.net.Uri
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingDelegate
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.Segment
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.io.File
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportDownloadedThreadAsHtmlUseCase(
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val fileManager: FileManager,
  private val chanPostRepository: ChanPostRepository
) : ISuspendUseCase<ExportDownloadedThreadAsHtmlUseCase.Params, ModularResult<Unit>> {

  override suspend fun execute(parameter: Params): ModularResult<Unit> {
    return ModularResult.Try {
      val outputDirUri = parameter.outputDirUri
      val threadDescriptors = parameter.threadDescriptors
      val onUpdate = parameter.onUpdate

      withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onUpdate(0, threadDescriptors.size) }

        threadDescriptors.forEachIndexed { index, threadDescriptor ->
          ensureActive()

          val outputDir = fileManager.fromUri(outputDirUri)
            ?: throw ThreadExportException("Failed to get output file for directory: \'$outputDirUri\'")

          val fileName = "${threadDescriptor.siteName()}_${threadDescriptor.boardCode()}_${threadDescriptor.threadNo}.zip"
          val outputFile = fileManager.createFile(outputDir, fileName)
            ?: throw ThreadExportException("Failed to create output file \'$fileName\' in directory \'${outputDir}\'")

          try {
            exportThreadAsHtml(outputFile, threadDescriptor)
          } catch (error: Throwable) {
            fileManager.fromUri(outputDirUri)?.let { file ->
              if (fileManager.isFile(file)) {
                fileManager.delete(file)
              }
            }

            throw error
          }

          withContext(Dispatchers.Main) { onUpdate(index + 1, threadDescriptors.size) }
        }

        withContext(Dispatchers.Main) { onUpdate(threadDescriptors.size, threadDescriptors.size) }
      }
    }
  }

  private suspend fun exportThreadAsHtml(
    outputFile: AbstractFile,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    val postsLoadResult = chanPostRepository.getThreadPostsFromDatabase(threadDescriptor)

    val chanPosts = if (postsLoadResult is ModularResult.Error) {
      throw postsLoadResult.error
    } else {
      postsLoadResult as ModularResult.Value
      postsLoadResult.value.sortedBy { post -> post.postNo() }
    }

    if (chanPosts.isEmpty()) {
      throw ThreadExportException("Failed to load posts to export")
    }

    if (chanPosts.first() !is ChanOriginalPost) {
      throw ThreadExportException("First post is not OP")
    }

    val outputFileUri = outputFile.getFullPath()
    Logger.d(TAG, "exportThreadAsHtml exporting ${chanPosts.size} posts into file '$outputFileUri'")

    val outputStream = fileManager.getOutputStream(outputFile)
    if (outputStream == null) {
      throw ThreadExportException("Failed to open output stream for file '${outputFileUri}'")
    }

    runInterruptible {
      outputStream.use { os ->
        ZipOutputStream(os).use { zos ->
          appContext.resources.openRawResource(R.raw.tomorrow).use { cssFileInputStream ->
            zos.putNextEntry(ZipEntry("tomorrow.css"))
            cssFileInputStream.copyTo(zos)
          }

          kotlin.run {
            zos.putNextEntry(ZipEntry("thread_data.html"))
            HTML_TEMPLATE_START.byteInputStream().use { templateStartStream ->
              templateStartStream.copyTo(zos)
            }

            chanPosts.forEach { chanPost ->
              formatPost(chanPost).byteInputStream().use { formattedPostStream ->
                formattedPostStream.copyTo(zos)
              }
            }

            HTML_TEMPLATE_END.byteInputStream().use { templateEndStream ->
              templateEndStream.copyTo(zos)
            }
          }

          val threadMediaDirName = ThreadDownloadingDelegate.formatDirectoryName(threadDescriptor)
          val threadMediaDir = File(appConstants.threadDownloaderCacheDir, threadMediaDirName)

          threadMediaDir.listFiles()?.forEach { mediaFile ->
            zos.putNextEntry(ZipEntry(mediaFile.name))

            mediaFile.inputStream().use { mediaFileSteam ->
              mediaFileSteam.copyTo(zos)
            }
          }
        }
      }
    }

    Logger.d(TAG, "exportThreadAsHtml done")
  }

  private fun formatPost(chanPost: ChanPost): String {
    val template = if (chanPost is ChanOriginalPost) {
      OP_POST_TEMPLATE
    } else {
      REGULAR_POST_TEMPLATE
    }

    val templateBuilder = StringBuilder(template.length)
    val matcher = TEMPLATE_PARAMETER_PATTERN.matcher(template)

    var offset = 0

    while (matcher.find()) {
      val startIndex = matcher.start(0)
      val endIndex = matcher.end(0)

      templateBuilder.append(template.substring(offset, startIndex))

      val templateParam = template.substring(startIndex, endIndex)
        .removePrefix("{{")
        .removeSuffix("}}")

      val templateValue = when (templateParam) {
        "POST_NO" -> chanPost.postDescriptor.postNo.toString()
        "ORIGINAL_POST_FILES",
        "REGULAR_POST_FILES" -> formatPostFiles(chanPost)
        "THREAD_SUBJECT" -> {
          chanPost.subject ?: ""
        }
        "POSTER_NAME" -> {
          chanPost.tripcode ?: ""
        }
        "DATE_TIME_FORMATTED" -> {
          DATE_TIME_PRINTER.print(chanPost.timestamp * 1000L)
        }
        "POST_COMMENT" -> {
          chanPost.postComment.originalUnparsedComment ?: ""
        }
        else -> error("Unknown template parameter: ${templateParam}")
      }

      templateBuilder
        .append(templateValue)

      offset = endIndex
    }

    templateBuilder.append(template.substring(offset, template.length))

    return templateBuilder.toString()
  }

  private fun formatPostFiles(chanPost: ChanPost): String {
    if (chanPost.postImages.isEmpty()) {
      return ""
    }

    val templateBuilder = StringBuilder(128)
    templateBuilder
      .append("<div class=\"files_container\">")

    chanPost.iteratePostImages { chanPostImage ->
      val template = if (chanPost is ChanOriginalPost) {
        ORIGINAL_POST_FILE_TEMPLATE
      } else {
        REGULAR_POST_FILE_TEMPLATE
      }

      val matcher = TEMPLATE_PARAMETER_PATTERN.matcher(template)

      var offset = 0

      while (matcher.find()) {
        val startIndex = matcher.start(0)
        val endIndex = matcher.end(0)

        templateBuilder.append(template.substring(offset, startIndex))

        val templateParam = template.substring(startIndex, endIndex)
          .removePrefix("{{")
          .removeSuffix("}}")

        val templateValue = when (templateParam) {
          "POST_NO" -> chanPost.postDescriptor.postNo.toString()
          "FILE_NAME_WEIGHT_DIMENS" -> {
            val fileName = chanPostImage.formatFullOriginalFileName() ?: ""
            val weight = ChanPostUtils.getReadableFileSize(chanPostImage.size)
            val dimens = "${chanPostImage.imageWidth}x${chanPostImage.imageHeight}"

            "${fileName}, $weight, $dimens"
          }
          "FULL_IMAGE_NAME" -> chanPostImage.imageUrl?.extractFileName() ?: ""
          "THUMBNAIL_NAME" -> chanPostImage.actualThumbnailUrl?.extractFileName() ?: ""
          "FILE_WEIGHT" -> ChanPostUtils.getReadableFileSize(chanPostImage.size)
          else -> error("Unknown template parameter: ${templateParam}")
        }

        templateBuilder
          .append(templateValue)

        offset = endIndex
      }

      templateBuilder
        .append(template.substring(offset, template.length))
    }

    templateBuilder
      .append("</div>")

    return templateBuilder.toString()
  }

  class ThreadExportException(message: String) : Exception(message)

  data class Params(
    val outputDirUri: Uri,
    val threadDescriptors: List<ChanDescriptor.ThreadDescriptor>,
    val onUpdate: (Int, Int) -> Unit
  )

  companion object {
    private const val TAG = "ExportDownloadedThreadAsHtmlUseCase"

    private val TEMPLATE_PARAMETER_PATTERN = Pattern.compile("\\{\\{\\w+\\}\\}")

    private val DATE_TIME_PRINTER = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .appendLiteral(' ')
      .append(ISODateTimeFormat.hourMinuteSecond())
      .toFormatter()
      .withZone(DateTimeZone.forTimeZone(TimeZone.getDefault()))

    private const val HTML_TEMPLATE_START = """
<!DOCTYPE html>
<head>
   <link rel="stylesheet" title="switch" href="tomorrow.css">
   <meta charset="utf-8">
<body class="is_thread">
   <form name="delform" id="delform">
      <div class="board">
         <div class="thread">
    """

    private const val HTML_TEMPLATE_END = """
         </div>
         <hr>
      </div>
   </form>
</body>
</head>
    """

    private const val OP_POST_TEMPLATE = """
            <div class="postContainer opContainer" id="pc{{POST_NO}}">
              <div id="p{{POST_NO}}" class="post op">
                {{ORIGINAL_POST_FILES}}
                  <div class="postInfo desktop" id="pi{{POST_NO}}">
                    <span class="subject">{{THREAD_SUBJECT}}</span> 
                    <span class="nameBlock">
                      <span class="name">{{POSTER_NAME}}</span> 
                    </span> 
                    <span class="dateTime">{{DATE_TIME_FORMATTED}} No. {{POST_NO}}</span> 
                  </div>
                  <blockquote class="postMessage" id="m{{POST_NO}}">{{POST_COMMENT}}</blockquote>
               </div>
            </div>
    """

    private const val REGULAR_POST_TEMPLATE = """
            <div class="postContainer replyContainer" id="pc{{POST_NO}}">
               <div id="p{{POST_NO}}" class="post reply">
                  <div class="postInfo desktop" id="pi{{POST_NO}}">
                    <span class="nameBlock">
                      <span class="name">{{POSTER_NAME}}</span>
                    </span> 
                    <span class="dateTime">{{DATE_TIME_FORMATTED}} No. {{POST_NO}}</span> 
                  </div>
                  {{REGULAR_POST_FILES}}
                  <blockquote class="postMessage" id="m{{POST_NO}}">{{POST_COMMENT}}</blockquote>
               </div>
            </div>
    """

    private const val ORIGINAL_POST_FILE_TEMPLATE = """
                <div class="files_container">
                  <div class="file" id="f{{POST_NO}}">
                     <div class="fileText" id="fT{{POST_NO}}">File: 
                      <a href="{{FULL_IMAGE_NAME}}" target="_blank">{{FILE_NAME_WEIGHT_DIMENS}}</a>
                  </div>
                     <a class="fileThumb" href="{{FULL_IMAGE_NAME}}" target="_blank">
                        <img src="{{THUMBNAIL_NAME}}" alt="{{FILE_WEIGHT}}" style="height: 200px; width: 250px;" loading="lazy">
                     </a>
                  </div>
                </div>
    """

    private const val REGULAR_POST_FILE_TEMPLATE = """
                <div class="file" id="f{{POST_NO}}">
                  <div class="fileText" id="fT{{POST_NO}}">File:
                    <a href="{{FULL_IMAGE_NAME}}" target="_blank">{{FILE_NAME_WEIGHT_DIMENS}}</a>
                  </div>
                  <a class="fileThumb" href="{{FULL_IMAGE_NAME}}" target="_blank">
                    <img src="{{THUMBNAIL_NAME}}" alt="{{FILE_WEIGHT}}" style="height: 120px; width: 125px;" loading="lazy">
                  </a>
                </div>
    """

  }
}