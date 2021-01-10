package com.spundev.nezumi.service

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spundev.nezumi.R
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Source
import okio.buffer
import okio.sink


class DownloadWorker(
    context: Context, workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {

        val downloadUrl = inputData.getString(DOWNLOAD_URL)

        return try {
            setForeground(createForegroundInfo())
            if (downloadUrl == null || TextUtils.isEmpty(downloadUrl)) {
                throw IllegalArgumentException("Invalid input url")
            }
            //Start download
            val okhttpClient = createOkHttpClient()
            prepareDownload(okhttpClient, downloadUrl)

            Result.success()
        } catch (throwable: Throwable) {
            Log.e(TAG, "Download error", throwable)
            Result.failure()
        }
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    private fun prepareDownload(client: OkHttpClient, downloadUri: String) {

        // https://developer.android.com/training/data-storage/shared/media#add-item

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
            put(MediaStore.Video.Media.DISPLAY_NAME, "video")
            put(
                MediaStore.Video.Media.DATE_ADDED,
                System.currentTimeMillis() / 1000
            ) // should be in unit of seconds
            put(
                MediaStore.Video.Media.DATE_MODIFIED,
                System.currentTimeMillis() / 1000
            ) // should be in unit of seconds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        // Keeps a handle to the new song's URI in case we need to modify it later.
        val newVideoUri = resolver.insert(videoCollection, newVideoDetails)
        val outputStream = resolver.openOutputStream(newVideoUri!!, "w")

        val request = Request.Builder()
            .url(downloadUri)
            .build()

        client.newCall(request).execute().use { response ->
            // https://stackoverflow.com/a/29012988
            val sink = outputStream!!.sink().buffer()
            sink.writeAll(response.body?.source() as Source)
            sink.close()

            // Now that we're finished, release the "pending" status, and allow other apps
            // to play the video.
            newVideoDetails.apply {
                clear()
                put(
                    MediaStore.Video.Media.SIZE,
                    response.body!!.contentLength()
                ) // should be in unit of bytes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
            }.also { details ->
                resolver.update(newVideoUri, details, null, null)
            }
        }
    }


    /**
     * Create ForegroundInfo required to run a Worker in a foreground service.
     */
    private fun createForegroundInfo(): ForegroundInfo {
        // Use a different id for each Notification.
        val notificationId = 1
        return ForegroundInfo(notificationId, createNotificationBuilder())
    }

    /**
     * Create the notification and required channel (O+) for running work
     * in a foreground service.
     */
    private fun createNotificationBuilder(): Notification {
        val context = applicationContext
        val channelId = context.getString(R.string.download_notification_channel_id)
        val title = context.getString(R.string.download_notification_title)
        val cancel = context.getString(R.string.download_notification_cancel_action)
        val name = context.getString(R.string.download_notification_channel_name)
        val description = context.getString(R.string.download_notification_channel_description)
        // This PendingIntent can be used to cancel the Worker.
        val intent = WorkManager.getInstance(context).createCancelPendingIntent(id)

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(R.drawable.ic_download_notification_24)
            .setOngoing(true)
            .addAction(R.drawable.ic_cancel_download, cancel, intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(channelId, name, description).also {
                builder.setChannelId(it.id)
            }
        }
        return builder.build()
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
        const val DOWNLOAD_URL = "DOWNLOAD_URL"
    }
}

private const val TAG = "DownloadWorker"