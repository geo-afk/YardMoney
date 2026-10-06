package jm.yardmoney.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import jm.yardmoney.Prefs
import jm.yardmoney.R
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.appearancePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class BillReminder(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.appearancePrefs()
        if (!prefs.getBoolean(Prefs.REMINDERS, false)) return Result.success()
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return Result.success()
        return try {
            val app = applicationContext as YardMoneyApplication
            app.repository.materializeBills()
            val due =
                app.repository.dao.commitments().first().any {
                    it.remainingMinor > 0 &&
                        it.commitment.dueDate?.let(LocalDate::parse)?.let { date ->
                            date <= app.repository.today.plusDays(1)
                        } == true
                }
            val day = app.repository.today.toString()
            if (due && prefs.getString(Prefs.REMINDER_DAY, null) != day) {
                applicationContext
                    .getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(
                        NotificationChannel(
                            "bills",
                            applicationContext.getString(R.string.reminder_channel),
                            NotificationManager.IMPORTANCE_DEFAULT,
                        )
                    )
                val intent =
                    android.app.PendingIntent.getActivity(
                        applicationContext,
                        0,
                        android.content.Intent(
                            applicationContext,
                            jm.yardmoney.MainActivity::class.java,
                        ),
                        android.app.PendingIntent.FLAG_IMMUTABLE or
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                NotificationManagerCompat.from(applicationContext)
                    .notify(
                        2001,
                        NotificationCompat.Builder(applicationContext, "bills")
                            .setSmallIcon(R.drawable.ic_stat_bills)
                            .setContentTitle("Check your payday plan")
                            .setContentText("You have upcoming or overdue bills to review.")
                            .setContentIntent(intent)
                            .setAutoCancel(true)
                            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                            .build(),
                    )
                prefs.edit().putString(Prefs.REMINDER_DAY, day).apply()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Transient problems (a locked database) get a few retries; after that, wait for the
            // next daily run instead of retrying forever.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            context.appearancePrefs().edit().putBoolean(Prefs.REMINDERS, enabled).apply()
            val manager = WorkManager.getInstance(context)
            if (enabled)
                manager.enqueueUniquePeriodicWork(
                    "bill-reminders",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<BillReminder>(24, TimeUnit.HOURS).build(),
                )
            else {
                manager.cancelUniqueWork("bill-reminders")
                NotificationManagerCompat.from(context).cancel(2001)
            }
        }
    }
}
