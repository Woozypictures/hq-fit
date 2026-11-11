package uk.woozy.fit

import android.app.Application
import android.content.Context
import uk.woozy.fit.data.ThemeSettings
import uk.woozy.fit.data.db.DatabaseProvider

/** Manual dependency container. Later milestones add Health Connect and food sources here. */
class AppContainer(context: Context) {
    val themeSettings = ThemeSettings(context)
    val database = DatabaseProvider(context)
    val off = uk.woozy.fit.data.off.OffClient()
}

class HqFitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
