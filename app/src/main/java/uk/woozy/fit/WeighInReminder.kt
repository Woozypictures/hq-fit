package uk.woozy.fit

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * A weekly nudge to step on the scale. One inexact alarm is always set for the next occurrence; when it fires the
 * notification is posted and the next one is set. The same is done after a restart. Needs a phone check.
 */
object WeighInReminder {
    private const val CHANNEL = "weigh_in"
    private const val ID = 4201

    private fun prefs(c: Context) = c.getSharedPreferences("weighin", Context.MODE_PRIVATE)

    data class Setting(val enabled: Boolean, val day: DayOfWeek, val hour: Int)

    fun get(c: Context): Setting = prefs(c).let {
        Setting(it.getBoolean("on", false), DayOfWeek.of(it.getInt("dow", 7)), it.getInt("hour", 8))
    }

    fun set(c: Context, s: Setting) {
        prefs(c).edit().putBoolean("on", s.enabled).putInt("dow", s.day.value).putInt("hour", s.hour).apply()
        schedule(c)
    }

    fun nextMs(s: Setting, now: LocalDateTime = LocalDateTime.now()): Long {
        var t = now.with(TemporalAdjusters.nextOrSame(s.day)).withHour(s.hour).withMinute(0).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusWeeks(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val s = get(c)
        if (!s.enabled) { am.cancel(intent(c)); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMs(s), intent(c))
    }

    internal fun fire(c: Context) {
        schedule(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weigh-in reminder", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Weigh-in")
            .setContentText("Step on the scale, then open HQ Fit.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(ID, n) }
    }

    private fun intent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(c, 3, Intent(c, WeighInReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class WeighInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) WeighInReminder.schedule(context) else WeighInReminder.fire(context)
    }
}
