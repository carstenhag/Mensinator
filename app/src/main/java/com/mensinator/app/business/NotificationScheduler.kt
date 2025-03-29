package com.mensinator.app.business


import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mensinator.app.NotificationReceiver
import com.mensinator.app.settings.IntSetting
import com.mensinator.app.settings.StringSetting
import com.mensinator.app.ui.ResourceMapper
import com.mensinator.app.utils.IDispatcherProvider
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class NotificationScheduler(
    private val context: Context,
    private val dbHelper: IPeriodDatabaseHelper,
    private val periodPrediction: IPeriodPrediction,
    private val dispatcherProvider: IDispatcherProvider,
    private val alarmManager: AlarmManager
) : INotificationScheduler {

    // Schedule notification for reminder
    // Check that reminders should be scheduled (reminder>0)
    // and that it's more then reminderDays left (do not schedule notifications where there's too few reminderDays left until period)
    override suspend fun schedulePeriodNotification() {
        withContext(dispatcherProvider.IO) {
            val periodReminderDays =
                dbHelper.getSettingByKey(IntSetting.REMINDER_DAYS.settingDbKey)?.value?.toIntOrNull() ?: 2
            val nextPeriodDate = periodPrediction.getPredictedPeriodDate()
            val initPeriodKeyOrCustomMessage =
                dbHelper.getStringSettingByKey(StringSetting.PERIOD_NOTIFICATION_MESSAGE.settingDbKey)
            val periodMessageText =
                ResourceMapper.getPeriodReminderMessage(initPeriodKeyOrCustomMessage, context)

            val notificationDate = getNotificationScheduleDate(periodReminderDays, nextPeriodDate)


            withContext(dispatcherProvider.Main) {
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = NotificationReceiver.ACTION_NOTIFICATION
                    putExtra(NotificationReceiver.MESSAGE_TEXT_KEY, periodMessageText)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (notificationDate != null) {
                    val notificationTimeMillis = notificationDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        notificationTimeMillis,
                        pendingIntent
                    )
                    Log.d("NotificationScheduler", "Notification scheduled for $notificationDate")
                } else {
                    // Make sure the scheduled notification is cancelled, if the user data/conditions become invalid.
                    alarmManager.cancel(pendingIntent)
                    Log.d("NotificationScheduler", "Notification cancelled")
                }
            }
        }
    }

    // If the date checks pass, return the notification schedule date.
    private fun getNotificationScheduleDate(
        periodReminderDays: Int,
        nextPeriodDate: LocalDate?
    ): LocalDate? {
        if (periodReminderDays <= 0 || nextPeriodDate == null) return null

        val notificationDate = nextPeriodDate.minusDays(periodReminderDays.toLong())
        if (notificationDate.isBefore(LocalDate.now())) {
            Log.d(
                "CalendarScreen",
                "Notification not scheduled because the reminder date is in the past"
            )
            return null
        }

        return nextPeriodDate.minusDays(periodReminderDays.toLong())
    }
}
