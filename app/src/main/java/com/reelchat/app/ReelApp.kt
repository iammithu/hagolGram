package com.reelchat.app

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

class ReelApp : Application() {
    companion object { lateinit var instance: ReelApp }
    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifier.init(this)
    }
}

object Notifier {
    private const val CH = "reelchat"
    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CH, "Activity", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    fun show(ctx: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body)
            .setContentIntent(pi).setAutoCancel(true).build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
