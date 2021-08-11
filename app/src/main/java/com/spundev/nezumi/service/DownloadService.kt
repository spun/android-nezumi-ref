package com.spundev.nezumi.service

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.spundev.nezumi.R
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Source
import okio.buffer
import okio.sink


class DownloadService : IntentService("DownloadService") {

    companion object {
        const val DOWNLOAD_URL = "DOWNLOAD_URI"
        const val DOWNLOADING_CHANNEL = "download_Channel"
    }

    private lateinit var client: OkHttpClient

    override fun onCreate() {
        super.onCreate()

        client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(ProgressResponseBody(originalResponse.body!!, progressListener))
                    .build()
            }.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                DOWNLOADING_CHANNEL,
                getString(R.string.file_download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
                .apply {
                    description = getString(R.string.file_download_notification_channel_description)
                }


            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)

            val notification = NotificationCompat.Builder(this, DOWNLOADING_CHANNEL)
                .setContentTitle("Example IntentService")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_download_notification_24)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            startForeground(1, notification)
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        // Gets data from the incoming Intent
        val downloadUrl = intent?.getStringExtra(DOWNLOAD_URL)
        if (downloadUrl != null && downloadUrl != "")
            prepareDownload(downloadUrl)
    }


    private fun prepareDownload(downloadUri: String) {

        // Add a specific media item.
        val resolver = applicationContext.contentResolver

        // Find all audio files on the primary external storage device.
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        val newVideoUri = resolver.insert(audioCollection, newVideoDetails)
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
                put(MediaStore.Video.Media.SIZE, response.body!!.contentLength()) // should be in unit of bytes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
            }.also { details ->
                resolver.update(newVideoUri, details, null, null)
            }
        }
    }


    private val progressListener = object : ProgressResponseBody.ProgressListener {
        override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
            if (done) {
                println("completed")

            } else {
                if (contentLength != -1L) {
                    println("completed" + ((100 * bytesRead) / contentLength))
                }
            }
        }

    }

}