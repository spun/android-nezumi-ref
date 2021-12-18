package com.spundev.nezumi.service

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.spundev.nezumi.R
import com.spundev.nezumi.util.MimeType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.text.CharacterIterator
import java.text.StringCharacterIterator


class DownloadWorker(
    val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {

        // get download url from the input data
        val downloadUrl = inputData.getString(DOWNLOAD_URL)
        val downloadFilename =
            inputData.getString(DOWNLOAD_FILENAME) ?: downloadUrl.hashCode().toString()
        val downloadMimeType =
            inputData.getString(DOWNLOAD_FILE_MIMETYPE) ?: "video/mp4"

        return try {
            // Set this work as a foreground service with a notification
            val notificationId = downloadUrl.hashCode()
            val notificationBuilder = createNotificationBuilder()
            // Use unique notification ids for each url
            val foregroundInfo = ForegroundInfo(notificationId, notificationBuilder.build())
            setForeground(foregroundInfo)

            if (downloadUrl == null || TextUtils.isEmpty(downloadUrl)) {
                throw IllegalArgumentException("Invalid input url")
            }

            // Start download
            val okHttpClient = OkHttpClient.Builder().build()

            // Note: This won't return until the given block and all its children coroutines
            // are completed
            coroutineScope {
                // Start file download
                try {
                    var percentage = 0
                    var lastPercentageUpdate = -1
                    // startDownload gives us a flow with download progress updates
                    startDownload(okHttpClient, downloadUrl, downloadFilename, downloadMimeType)
                        .conflate() // Ignore stale values
                        .collect { (bytesDownloaded, bytesTotal) ->
                            // If we know the final size of the file
                            if (bytesTotal != -1L) {
                                // Calculate the progress percentage
                                percentage = (100 * bytesDownloaded / bytesTotal).toInt()
                                // Only update if the value is different from the last update
                                if (percentage != lastPercentageUpdate) {
                                    lastPercentageUpdate = percentage
                                    // Update notification progress
                                    notificationBuilder.setProgress(100, percentage, false)
                                    val progressText =
                                        StringBuilder(humanReadableByteCountSI(bytesDownloaded))
                                            .append(" / ")
                                            // TODO: Avoid calculation, bytesTotal won't change
                                            .append(humanReadableByteCountSI(bytesTotal))
                                    notificationBuilder.setContentText(progressText)
                                    notificationManager.notify(
                                        notificationId,
                                        notificationBuilder.build()
                                    )
                                }
                            } else {
                                // If we don't know the progress percentage, we can show the downloaded
                                // bytes in the notification
                                notificationBuilder.setContentText(
                                    humanReadableByteCountSI(bytesDownloaded)
                                )
                                notificationManager.notify(
                                    notificationId,
                                    notificationBuilder.build()
                                )
                            }

                            // We shouldn't update the notification everytime we have new info.
                            // Android has a limit for notification updates and in Nougat it was
                            // reduced to 10 updates every second per package.
                            // More info: https://saket.me/android-7-nougat-rate-limiting-notifications/
                            // Update notification every second
                            delay(1000)
                        }
                } catch (socketException: SocketException) {
                    // This exception can be thrown when calling cancel() in a okHttp call. Since we
                    // use cancel() to stop a running download, this exception is expected in some
                    // situations. To check if the exception was caused due to a cancellation, we
                    // check if the scope is still active
                    if (isActive) {
                        // Unexpected SocketException while scope is still active, notify
                        Log.e(TAG, "Scope isActive() value is $isActive", socketException)
                    }
                    // Else, expected. Ignore and allow the cancellation exception to be caught
                }
            }
            // At this point, the download has been completed successfully
            Log.d(TAG, "Success!")
            Result.success()

        } catch (throwable: Throwable) {
            // This catch block is different from the one catching "CancellationException", we
            // should have an active scope that we can use to clean any partial download.
            Log.e(TAG, "Throwable catch", throwable)
            Result.failure()
        }
    }

    private suspend fun startDownload(
        client: OkHttpClient,
        downloadUri: String,
        filename: String,
        downloadMimeType: String,
    ) = flow<Pair<Long, Long>> {

        // Add a specific media item.
        val resolver = applicationContext.contentResolver

        // Find all video files on the primary external storage device.
        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            // On API <= 28, use VOLUME_EXTERNAL instead.
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        // Publish a new video.
        val newVideoDetails = ContentValues().apply {
            put(MediaStore.Video.Media.TITLE, filename)
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, downloadMimeType)
            // date values should be in seconds
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                // IMPORTANT: In devices < 29, the video created by the MediaStore doesn't use
                //  the filename from TITLE or DISPLAY_NAME, it uses the current time in millis
                //  as filename and 3gp as extension (¿?).
                //  This is not avoidable, some answers (https://stackoverflow.com/a/65627697)
                //  suggest Updating the DISPLAY_NAME after the insert call. This could fix the
                //  problem with file apps that use the DISPLAY_NAME, but other apps use the
                //  filename directly and that value would still be wrong.
                //  To avoid this, we are going to create the file manually with the filename value
                //  that we want and then insert the data to MediaStore with the path of our file.
                val directory =
                    "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}${Environment.DIRECTORY_MOVIES}"
                val fullFilename =
                    MimeType.getExtensionFromMimeType(downloadMimeType).let { extension ->
                        if (extension.isNotEmpty()) "$filename.$extension" else filename
                    }
                val newVideo = File(directory, fullFilename)
                put(MediaStore.Video.Media.DATA, newVideo.absolutePath)
            }
        }

        // Keeps a handle to the new video's URI in case we need to modify it later.
        val newVideoUri = resolver.insert(videoCollection, newVideoDetails)
        val outputStream = resolver.openOutputStream(newVideoUri!!, "w")

        val request = Request.Builder()
            .url(downloadUri)
            .build()

        client.newCall(request).execute().use { response ->
            // https://stackoverflow.com/a/29012988
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            response.body?.let { responseBody ->

                // Get inputStream
                val inputStream = responseBody.byteStream()

                // Get total file size from header
                val contentLengthHeader = response.header("Content-Length")
                val contentLength = contentLengthHeader?.toLongOrNull() ?: -1L

                // Although we might not know the final final size, it's useful to use the copy with
                // progress to deal with cancellations
                outputStream?.let {
                    inputStream.copyToWithProgressFlow(it).collect { bytesCopied ->
                        emit(bytesCopied to contentLength)
                    }
                }

                // We are manually closing instead of using "use { }" on AutoCloseables to avoid
                // chaining multiple "use" blocks
                responseBody.close()
                outputStream?.close()

                // Now that the download is completed, release the "pending" status, and allow
                // other apps to play the video.
                newVideoDetails.apply {
                    clear()
                    put(MediaStore.Video.Media.SIZE, response.body!!.contentLength()) // bytes
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                }.also { details ->
                    resolver.update(newVideoUri, details, null, null)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Create the notification and required channel (O+) for running work
     * in a foreground service.
     */
    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val context = applicationContext
        val channelId = context.getString(R.string.file_download_notification_channel_id)
        val title = context.getString(R.string.file_download_notification_title)
        val cancel = context.getString(R.string.file_download_notification_cancel_action)
        val name = context.getString(R.string.file_download_notification_channel_name)
        val description = context.getString(R.string.file_download_notification_channel_description)
        // This PendingIntent can be used to cancel the Worker.
        val intent = WorkManager.getInstance(context).createCancelPendingIntent(id)

        val builder = NotificationCompat.Builder(context, channelId).apply {
            setContentTitle(title)
            setTicker(title)
            setSmallIcon(R.drawable.ic_notification_download_animation)
            setOngoing(true)
            addAction(R.drawable.ic_cancel_download, cancel, intent)
            setProgress(100, 0, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(channelId, name, description).also {
                builder.setChannelId(it.id)
            }
        }
        return builder
    }

    /**
     * Create the required notification channel for O+ devices.
     */
    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(
        channelId: String,
        name: String,
        description: String
    ): NotificationChannel {
        return NotificationChannel(
            channelId, name, NotificationManager.IMPORTANCE_LOW
        ).also { channel ->
            // Optional description
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val DOWNLOAD_URL = "DOWNLOAD_URL"
        private const val DOWNLOAD_FILENAME = "DOWNLOAD_FILENAME"
        private const val DOWNLOAD_FILE_MIMETYPE = "DOWNLOAD_FILE_MIMETYPE"
        const val WORK_NAME: String = "com.spundev.nezumi.service.DownloadWorker"

        fun enqueueDownload(context: Context, url: String, filename: String?, mimeType: String?) {

            val downloadData = workDataOf(
                DOWNLOAD_URL to url,
                DOWNLOAD_FILENAME to filename,
                DOWNLOAD_FILE_MIMETYPE to mimeType
            )

            val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(downloadData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(downloadData)
                .build()

            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(downloadRequest)
        }
    }
}

// Extracted from InputStream "copyTo", modified to expose a progress percentage
fun InputStream.copyToWithProgressFlow(
    out: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
) = flow {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0 && currentCoroutineContext().isActive) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer)

        emit(bytesCopied)
    }
}.flowOn(Dispatchers.IO)

/**
 * Convert a byte size number to a human readable string
 * From "The most copied Stack Overflow snippet" !
 * https://stackoverflow.com/a/3758880
 */
fun humanReadableByteCountSI(bytesValue: Long): String {
    var bytes = bytesValue
    if (-1000 < bytes && bytes < 1000) {
        return "$bytes B"
    }
    val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
    while (bytes <= -999_950 || bytes >= 999_950) {
        bytes /= 1000
        ci.next()
    }
    return String.format("%.1f %cB", bytes / 1000.0, ci.current())
}

private const val TAG = "DownloadWorker"