package uk.woozy.fit

import android.content.Intent
import uk.woozy.fit.data.db.DbGate

/** Release builds: no demo mode. The debug copy of this file fills sample data for CI screenshots. */
object Demo {
    fun wanted(i: Intent?) = false
    suspend fun start(c: AppContainer): DbGate = c.database.start()
}
