package uk.woozy.fit.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import java.io.File

/** The profile picture: one small square JPEG in app-private storage. It never leaves the phone. */
object Avatar {
    private fun file(c: Context) = File(c.filesDir, "avatar.jpg")
    fun stamp(c: Context): Long = file(c).takeIf { it.exists() }?.lastModified() ?: 0L

    /** Centre-crops the chosen picture to a 384 px square. Returns false if it could not be read. */
    fun save(c: Context, uri: Uri): Boolean = runCatching {
        val size = 384
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val raw = c.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
        val side = minOf(raw.width, raw.height)
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, size, size, true)
        file(c).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)

    fun clear(c: Context) { file(c).delete() }

    fun load(c: Context): Bitmap? = file(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
}

/** The photo in a circle, or [initial] on the accent colour when there is none. */
@Composable
fun AvatarCircle(initial: String, size: Dp, modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val stamp = Avatar.stamp(context)
    val bitmap = remember(stamp) { Avatar.load(context) }
    Box(modifier.size(size).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Profile picture", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else HqText(initial.take(1).uppercase(), (size.value * 0.4f).sp, FontWeight.Bold, c.onPrimary)
    }
}
