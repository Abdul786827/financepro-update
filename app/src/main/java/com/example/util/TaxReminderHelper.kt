package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.model.TaxYearEntity

/**
 * Local notification helper for Pakistan income tax filing deadline reminders.
 */
object TaxReminderHelper {

    private const val CHANNEL_ID = "tax_deadline_reminders_channel"
    private const val CHANNEL_NAME = "Tax Filing Deadline Reminders"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Local reminders before your configured income tax return filing deadline"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun checkAndNotifyFilingDeadlines(
        context: Context,
        taxYears: List<TaxYearEntity>,
        todayStr: String = DateUtils.getCurrentDate()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        for (ty in taxYears) {
            if (ty.isLocked) continue
            if (ty.filingDeadline.isBlank() || ty.reminderDaysBefore <= 0) continue

            val daysLeft = PakistanTaxYearUtils.daysUntilDeadline(ty.filingDeadline, todayStr) ?: continue
            if (daysLeft in 0..ty.reminderDaysBefore.toLong()) {
                val whenText = when (daysLeft) {
                    0L -> "today (${ty.filingDeadline})"
                    1L -> "tomorrow (${ty.filingDeadline})"
                    else -> "in $daysLeft days (${ty.filingDeadline})"
                }
                val title = "Tax Filing Reminder: ${PakistanTaxYearUtils.formatShortTaxYearLabel(ty.taxYear)}"
                val body = "${ty.label} filing deadline is $whenText. Review your summary and wealth statement."

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

                manager.notify(50_000 + ty.taxYear, notification)
            }
        }
    }
}
