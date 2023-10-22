package com.rakibcse99.videodownloder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rakibcse99.videodownloder.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.File

class MainActivity : AppCompatActivity() {

    private var downloadJob: Job? = null
    private lateinit var binding: ActivityMainBinding
    // Declare the launcher at the top of your Activity/Fragment:
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {

        } else {
        //  showExplanationDialog()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        askNotificationPermission()
        startDownloadInternal()

    }

    fun startDownload(view: View) {
        binding.startDownloadButton.isEnabled = false
        binding.downloadProgressBar.visibility = View.VISIBLE
        startDownloadInternal()
    }

    private fun startDownloadInternal() {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Download in progress")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val notificationManager = NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileUrl =
                    "https://file-examples.com/storage/fe1207564e65327fe9c8723/2017/04/file_example_MP4_480_1_5MG.mp4"
                val client = OkHttpClient()
                val request = Request.Builder().url(fileUrl).build()
                val response: Response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val contentLength = response.body?.contentLength() ?: 0
                    val file = File(filesDir, "sample_video.mp4")
                    val sink = file.sink().buffer()

                    response.body?.source()?.use { source ->
                        var totalBytesRead: Long = 0
                        var bytesRead: Long

                        while (source.read(sink.buffer, 8192).also { bytesRead = it } != -1L) {
                            sink.emit()
                            totalBytesRead += bytesRead
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            withContext(Dispatchers.Main) {
                                updateProgressBar(progress)
                                updateNotificationProgress(progress, notificationManager, builder)
                            }
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
                    notificationManager.cancel(NOTIFICATION_ID)
                }
            }
        }
    }

    private fun updateProgressBar(progress: Int) {
        binding.downloadProgressBar.progress = progress
    }

    private fun updateNotificationProgress(
        progress: Int,
        notificationManager: NotificationManagerCompat,
        builder: NotificationCompat.Builder
    ) {
        builder.setProgress(100, progress, false)
        builder.setContentText("$progress%")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
               // showExplanationDialog()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showExplanationDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)

        alertDialogBuilder.setTitle("Permission Explanation")
        alertDialogBuilder.setMessage("Granting this permission will allow you to receive notifications about important updates.")

        alertDialogBuilder.setPositiveButton("OK") { _, _ ->
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        alertDialogBuilder.setNegativeButton("No thanks") { _, _ ->
        }

        val dialog = alertDialogBuilder.create()

        dialog.show()
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        super.onDestroy()
    }
    companion object{
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1
    }
}
