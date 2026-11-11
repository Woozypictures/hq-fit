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

/** A Sunday 18:00 nudge for the check-in (mood, metrics, photo). Re-armed every time the app opens and after a restart. */
object CheckInReminder {
    private const val CHANNEL = "check_in"
    private const val ID = 4301

    fun nextMs(now: LocalDateTime = LocalDateTime.now()): Long {
        var t = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).withHour(18).withMinute(0).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusWeeks(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("checkin", Context.MODE_PRIVATE)

    /** On unless switched off in Settings > Notifications (you asked for this reminder). */
    fun enabled(c: Context) = prefs(c).getBoolean("on", true)
    fun setEnabled(c: Context, on: Boolean) { prefs(c).edit().putBoolean("on", on).apply(); schedule(c) }

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!enabled(c)) { am.cancel(intent(c)); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMs(), intent(c))
    }

    internal fun fire(c: Context) {
        schedule(c)
        if (!enabled(c)) return
        // At most one a day, even if the alarm fires twice (a restart near 18:00, a clock change).
        val day = java.time.LocalDate.now().toString()
        if (prefs(c).getString("last", null) == day) return
        prefs(c).edit().putString("last", day).apply()
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Sunday check-in", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            c, 4, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Sunday check-in")
            .setContentText("Mood, body metrics and a progress photo.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(ID, n) }
    }

    private fun intent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(c, 6, Intent(c, CheckInReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) CheckInReminder.schedule(context) else CheckInReminder.fire(context)
    }
}
