package com.kakario.householdbook.backup

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kakario.householdbook.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 매일 정한 시각(기본 밤 12시)에 FTP 서버로 백업 (구글 드라이브는 준비 중) */
object BackupScheduler {

    private fun alarmIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7101, Intent(ctx, BackupAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun nextTime(hour: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance(); c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= now + 60_000) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /** 다음 알람 예약 (백업 꺼져 있으면 취소) */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx)
        if (!FtpBackup.enabled(ctx)) { am.cancel(pi); return }
        // 정확한 알람 권한 없이도 되는 방식 (절전 중에도 울림, 몇 분 늦을 수 있음)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime(DriveBackup.hour(ctx)), pi)
    }

    /** 인터넷 연결되면 바로 백업 */
    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("drive-backup", ExistingWorkPolicy.REPLACE, req)
    }

    /** 폰이 꺼져 있어서 놓친 백업이 있으면 (마지막 성공이 하루 넘게 지났으면) 지금 */
    fun catchUp(ctx: Context) {
        if (!FtpBackup.enabled(ctx)) return
        schedule(ctx)
        val last = DriveBackup.lastOkTime(ctx)
        if (last > 0 && System.currentTimeMillis() - last > 26L * 3600 * 1000 && !DriveBackup.needsReconnect(ctx)) runNow(ctx)
    }

    fun timeText(t: Long): String = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(t))
}

class BackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BackupScheduler.runNow(context)
        BackupScheduler.schedule(context)
    }
}

/** 폰을 껐다 켜면 알람이 지워지므로 다시 예약 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BackupScheduler.catchUp(context)
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!FtpBackup.enabled(ctx)) return Result.success()
        return try {
            val name = FtpBackup.backupNow(ctx)
            DriveBackup.saveStatus(ctx, true, "${BackupScheduler.timeText(System.currentTimeMillis())} 백업 완료 ($name)")
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) return Result.retry()
            DriveBackup.saveStatus(ctx, false, "백업 실패: ${e.message ?: e.javaClass.simpleName}")
            notify(ctx, "가계부 백업을 못 했어요", e.message ?: "잠시 후 다시 시도해 주세요")
            Result.failure()
        }
    }

    private fun notify(ctx: Context, title: String, text: String) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("backup", "드라이브 백업", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, "backup")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(7102, n)
        } catch (_: SecurityException) { }
    }
}
