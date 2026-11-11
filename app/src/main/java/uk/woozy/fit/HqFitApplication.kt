package uk.woozy.fit

import android.app.Application
import android.content.Context
import uk.woozy.fit.data.ThemeSettings

/** Manual dependency container. Later milestones add the database, Health Connect and food sources here. */
class AppContainer(context: Context) {
    val themeSettings = ThemeSettings(context)
}

class HqFitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
