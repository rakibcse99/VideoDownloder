package com.rakibcse99.videodownloder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rakibcse99.videodownloder.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var notificationManager: NotificationManagerCompat
    private var downloadJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        binding.startDownloadButton.setOnClickListener {
            startDownload()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Download Channel"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startDownload() {
        binding.startDownloadButton.isEnabled = false
        binding.downloadProgressBar.visibility = View.VISIBLE

        val url =
            "https://v1.cdnpk.net/videvo_files/video/premium/video0036/large_watermarked/counter100%204_preview.mp4"
        val request = Request.Builder().url(url).build()

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val response: Response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val contentLength = response.body?.contentLength() ?: 0
                    val file = File(filesDir, "downloaded_video.mp4")
                    val sink = file.sink().buffer()

                    response.body?.source()?.use { source ->
                        var totalBytesRead: Long = 0
                        var bytesRead: Long

                        while (source.read(sink.buffer, 8192).also { bytesRead = it } != -1L) {
                            sink.emit()
                            totalBytesRead += bytesRead
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            updateProgressBar(progress)
                            updateNotification(progress)
                        }

                        sink.close()
                    }

                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    downloadJob = null
                    binding.startDownloadButton.isEnabled = true
                    binding.downloadProgressBar.visibility = View.GONE
                    notificationManager.cancel(notificationId)
                }
            }
        }
    }

    private fun updateProgressBar(progress: Int) {
        runOnUiThread {
            binding.downloadProgressBar.progress = progress
        }
    }

    private fun updateNotification(progress: Int) {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Download in progress")
            .setContentText("$progress%")
            .setSmallIcon(R.drawable.ic_notifications)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(notificationId, builder.build())
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        super.onDestroy()
    }
    companion object {

        private const val channelId = "download_channel"
        private const val notificationId = 1
    }
}
