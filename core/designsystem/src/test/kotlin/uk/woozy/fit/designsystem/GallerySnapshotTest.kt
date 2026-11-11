package uk.woozy.fit.designsystem

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

/** Renders the full component gallery in light and dark. CI records the images and attaches them to the release. */
class GallerySnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
    )

    @Test
    fun galleryLight() {
        paparazzi.snapshot { HqFitTheme(darkTheme = false) { ComponentGallery() } }
    }

    @Test
    fun galleryDark() {
        paparazzi.snapshot { HqFitTheme(darkTheme = true) { ComponentGallery() } }
    }
}
