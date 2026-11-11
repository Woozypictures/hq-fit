package uk.woozy.fit

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The rest timer outside the app. Ticking a set posts a notification with a live countdown and sets an alarm; when
 * the alarm fires the phone vibrates and a "Rest over" notification appears. The end time is also kept in
 * preferences so the screen can pick the countdown up again after the app was closed.
 *
 * This uses a notification and an alarm rather than a foreground service: a rest is at most a few minutes, and
 * nothing needs to run in between. If the phone's battery saver delays the alarm, the countdown notification still
 * shows the right time. Needs a phone check (see HANDOVER.md).
 */
object RestTimer {
    private const val CHANNEL_COUNT = "rest_countdown"
    private const val CHANNEL_DONE = "rest_done"
    private const val ID_COUNT = 4101
    private const val ID_DONE = 4102

    private fun prefs(c: Context) = c.getSharedPreferences("rest", Context.MODE_PRIVATE)

    /** Epoch ms when the current rest ends, or 0. */
    fun endMs(c: Context): Long = prefs(c).getLong("end", 0L)

    fun start(c: Context, endMs: Long, exercise: String) {
        prefs(c).edit().putLong("end", endMs).apply()
        ensureChannels(c)
        val nm = NotificationManagerCompat.from(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CHANNEL_COUNT)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Rest")
            .setContentText(exercise)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endMs)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setTimeoutAfter((endMs - System.currentTimeMillis()).coerceAtLeast(1000))
            .build()
        runCatching { nm.notify(ID_COUNT, n) } // no permission means no notification; the in-app timer still works
        nm.cancel(ID_DONE)
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs, alarmIntent(c))
    }

    fun cancel(c: Context) {
        prefs(c).edit().putLong("end", 0L).apply()
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(c))
        val nm = NotificationManagerCompat.from(c)
        nm.cancel(ID_COUNT)
        nm.cancel(ID_DONE)
    }

    internal fun fire(c: Context) {
        prefs(c).edit().putLong("end", 0L).apply()
        val nm = NotificationManagerCompat.from(c)
        nm.cancel(ID_COUNT)
        ensureChannels(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Rest over")
            .setContentText("Next set")
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(ID_DONE, n) }
        @Suppress("DEPRECATION")
        (c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
    }

    private fun alarmIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 1, Intent(c, RestAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNT, "Rest countdown", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shows the rest time left between sets" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Rest over", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Tells you the rest is finished" },
        )
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestTimer.fire(context)
}
