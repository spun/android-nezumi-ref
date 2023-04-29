package com.spundev.nezumi.ui.details

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spundev.nezumi.model.Format
import com.spundev.nezumi.model.PlayerResponse
import com.spundev.nezumi.model.VideoDetails
import com.spundev.nezumi.service.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody


class DetailsViewModel(val app: Application) : AndroidViewModel(app) {
    // TODO: Add DI
    private val client = OkHttpClient.Builder().build()

    private val _currentVideoId = MutableStateFlow("")
    private val currentVideoId: StateFlow<String> = _currentVideoId

    private val playerResponse: StateFlow<PlayerResponse?> = currentVideoId.map { videoId ->
        getPlayerResponse(videoId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val videoDetails: StateFlow<VideoDetails?> =
        playerResponse.filterNotNull().map { it.videoDetails }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    val videoFormats: StateFlow<List<Format>?> = playerResponse.filterNotNull().map {
        // Return base formats + adaptive formats
        Log.d(TAG, "Mapping again: ")
        it.streamingData.formats + it.streamingData.adaptiveFormats
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private var _currentFormatSelectionPosition: MutableLiveData<Int> = MutableLiveData(-1)

    // Set / change the video id for which we will get the data.
    fun setVideoId(newVideoId: String) {
        _currentVideoId.value = newVideoId
    }

    private suspend fun getPlayerResponse(videoId: String): PlayerResponse? {
        val body: RequestBody = """
            {
                context: {
                  client: {
                    hl: "en",
                    clientName: "WEB",
                    clientVersion: "2.20230427.04.00",
                    mainAppWebInfo: { graftUrl: "/watch?v=${videoId}" },
                  },
                },
                videoId: "$videoId",
          }
        """.trimIndent().toRequestBody("application/json".toMediaTypeOrNull())

        // Api request url
        val requestUrl = Uri.Builder()
            .scheme("https")
            .authority("youtubei.googleapis.com")
            .appendPath("youtubei")
            .appendPath("v1")
            .appendPath("player")
            .appendQueryParameter("key", "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
            .build()
            .toString()

        // Create a new request
        val request: Request = Request.Builder()
            .url(requestUrl)
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            // Fetch the data
            client.newCall(request).execute().use { response ->
                response.body?.string()
            }?.let { playerResponse ->
                // Parse JSON allowing unknown keys
                val format = Json { ignoreUnknownKeys = true }
                format.decodeFromString(PlayerResponse.serializer(), playerResponse)
            }
        }
    }

    fun downloadFormat(format: Format?) {
        format?.let {
            DownloadWorker.enqueueDownload(app, it.url, videoDetails.value?.title, it.mimeType)
        }
    }


//    // Note: DownloadManager deletes the files downloaded by the app after this one is
//    // uninstalled. If we want to change this behaviour we'll have to move or rename the file
//    // after the download ends.
//    var downloadID: Long = 0
//    fun downloadWithDownloadManager(url: Uri) {
//        // File destination
//        val file = File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Dummy")
//        // DownloadManager request
//        val request = DownloadManager.Request(url)
//                .setTitle("File") // Title of the Download Notification
//                .setDescription("Downloading file") // Description of the Download Notification
//                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE) // Visibility of the download Notification
//                //.setDestinationInExternalPublicDir(file)
//                .setDestinationUri(Uri.fromFile(file)) // Uri of the destination file
//                .setAllowedOverMetered(true) // Set if download is allowed on Mobile network
//                .setAllowedOverRoaming(true) // Set if download is allowed on roaming network
//
//        request.allowScanningByMediaScanner()
//
//
//        val downloadManager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
//        downloadID = downloadManager.enqueue(request) // enqueue puts the download request in the queue.
//    }
//
//    // Note: We could do this in a better way instead of just showing a toast from the viewModel and
//    // all with the app context, but since we are no going to use DownloadManager, we are leaving this
//    // as reference of how to use it.
//    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            //Fetching the download id received with the broadcast
//            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
//            //Checking if the received broadcast is for our enqueued download by matching download id
//            if (downloadID == id) {
//                Toast.makeText(app, "Download Completed", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    init {
//        app.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)); }
//
//    override fun onCleared() {
//        app.unregisterReceiver(onDownloadComplete)
//    }
}


private const val TAG = "DetailsViewModel"