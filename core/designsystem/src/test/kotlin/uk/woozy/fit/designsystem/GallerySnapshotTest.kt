package uk.woozy.fit.designsystem

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

/**
 * Renders each group of components in light and dark, one image per group so each stays full size.
 * CI records the images and attaches them to the release.
 */
class GallerySnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
    )

    @Test
    fun light() = render(dark = false)

    @Test
    fun dark() = render(dark = true)

    private fun render(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        GalleryBlock.entries.forEach { block ->
            paparazzi.snapshot(name = "${block.name}_$mode") {
                HqFitTheme(darkTheme = dark) { ComponentGallery(blocks = listOf(block)) }
            }
        }
    }
}
