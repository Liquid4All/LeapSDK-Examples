package com.tldw.app.data.repository

import ai.liquid.leap.ModelRunner
import ai.liquid.leap.downloader.LeapModelDownloader
import ai.liquid.leap.downloader.LeapModelDownloaderNotificationConfig
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.MessageResponse
import android.content.Context
import com.tldw.app.Consts
import com.tldw.app.domain.model.DownloadProgress
import com.tldw.app.domain.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext

class ModelRepositoryImpl(private val context: Context) : ModelRepository {

  private var downloader: LeapModelDownloader? = null

  @Volatile private var modelRunner: ModelRunner? = null

  private fun getOrCreateDownloader(): LeapModelDownloader =
    downloader
      ?: LeapModelDownloader(
          context,
          notificationConfig =
            LeapModelDownloaderNotificationConfig.build {
              notificationTitleDownloading = "Downloading Model"
              notificationTitleDownloaded = "Model Ready!"
            },
        )
        .also { downloader = it }

  override suspend fun isModelDownloaded(): Boolean =
    withContext(Dispatchers.IO) {
      val dl = getOrCreateDownloader()
      val status = dl.queryStatus(Consts.MODEL_NAME, Consts.QUANTIZATION_SLUG)
      status is LeapModelDownloader.ModelDownloadStatus.Downloaded
    }

  override fun downloadModel(): Flow<DownloadProgress> =
    flow {
        val dl = getOrCreateDownloader()
        if (
          dl.queryStatus(Consts.MODEL_NAME, Consts.QUANTIZATION_SLUG)
            is LeapModelDownloader.ModelDownloadStatus.Downloaded
        )
          return@flow

        val progressFlow = dl.observeDownloadProgress(Consts.MODEL_NAME, Consts.QUANTIZATION_SLUG)
        dl.requestDownloadModel(Consts.MODEL_NAME, Consts.QUANTIZATION_SLUG)

        progressFlow
          .takeWhile { progress ->
            val status = dl.queryStatus(Consts.MODEL_NAME, Consts.QUANTIZATION_SLUG)
            progress != null || status is LeapModelDownloader.ModelDownloadStatus.DownloadInProgress
          }
          .collect { progress ->
            if (progress != null) {
              val pct =
                if (progress.totalSizeInBytes > 0)
                  (progress.downloadedSizeInBytes * 100.0 / progress.totalSizeInBytes).toInt()
                else 0
              emit(DownloadProgress(progress.downloadedSizeInBytes, progress.totalSizeInBytes, pct))
            }
          }
      }
      .flowOn(Dispatchers.IO)

  override suspend fun stopDownload() =
    withContext(Dispatchers.IO) {
      val dl = getOrCreateDownloader()
      dl.requestStopDownload(
        modelName = Consts.MODEL_NAME,
        quantizationSlug = Consts.QUANTIZATION_SLUG,
      )
      dl
        .getModelResourceFolder(
          modelName = Consts.MODEL_NAME,
          quantizationSlug = Consts.QUANTIZATION_SLUG,
        )
        .deleteRecursively()
      Unit
    }

  override suspend fun loadModel(): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val dl = getOrCreateDownloader()
        modelRunner =
          dl.loadModel(modelName = Consts.MODEL_NAME, quantizationType = Consts.QUANTIZATION_SLUG)
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun unloadModel() =
    withContext(Dispatchers.IO) {
      if (modelRunner is AutoCloseable) {
        (modelRunner as? AutoCloseable)?.close()
      }
      modelRunner = null
    }

  override fun generateTldr(transcript: String): Flow<String> =
    flow {
        val runner = modelRunner ?: error("Model not loaded. Download and load the model first.")
        val conversation = runner.createConversation(Consts.TLDR_SYSTEM_PROMPT)
        val prompt =
          "Summarize this YouTube video transcript in a concise TL;DR:\n\n" +
            transcript.take(Consts.MAX_TRANSCRIPT_CHARS)
        val message = ChatMessage(role = ChatMessage.Role.USER, textContent = prompt)
        val buffer = StringBuilder()
        conversation.generateResponse(message).collect { response ->
          if (response is MessageResponse.Chunk) {
            buffer.append(response.text)
            emit(buffer.toString())
          }
        }
      }
      .flowOn(Dispatchers.IO)
}
