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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The profile picture: a small square JPEG in app-private storage, copied from HQ, which takes it from the
 * icon of the Notion DASHBOARD page. There is nothing to set here: change the page icon in Notion.
 */
object Avatar {
    private val SOURCE: Uri = Uri.parse("content://uk.woozy.hq.avatar/avatar")
    private fun file(c: Context) = File(c.filesDir, "avatar.jpg")
    private fun stampFile(c: Context) = File(c.filesDir, "avatar.stamp")
    fun stamp(c: Context): Long = file(c).takeIf { it.exists() }?.lastModified() ?: 0L

    @Volatile private var lastTry = 0L

    /**
     * Copies HQ's picture when it has a newer one. Returns true when the stored picture changed. HQ not installed,
     * no permission or no picture there: nothing happens and the stored picture (if any) stays. Tries at most once a minute.
     */
    fun syncFromHq(c: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTry < 60_000) return false
        lastTry = now
        return runCatching {
            val remote = c.contentResolver.query(SOURCE, null, null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
                ?: return@runCatching false
            val seen = stampFile(c).takeIf { it.exists() }?.readText()?.toLongOrNull()
            if (seen == remote && file(c).exists()) return@runCatching false
            val tmp = File(c.filesDir, "avatar.tmp")
            c.contentResolver.openInputStream(SOURCE)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (BitmapFactory.decodeFile(tmp.path) == null) { tmp.delete(); return@runCatching false }
            tmp.renameTo(file(c))
            stampFile(c).writeText(remote.toString())
            true
        }.getOrDefault(false)
    }

    fun load(c: Context): Bitmap? = file(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
}

/** The photo in a circle, or [initial] on the accent colour when there is none. */
@Composable
fun AvatarCircle(initial: String, size: Dp, modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val synced = remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        if (withContext(Dispatchers.IO) { Avatar.syncFromHq(context) }) synced.value++
    }
    val stamp = Avatar.stamp(context)
    val bitmap = remember(stamp, synced.value) { Avatar.load(context) }
    Box(modifier.size(size).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Profile picture", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else HqText(initial.take(1).uppercase(), (size.value * 0.4f).sp, FontWeight.Bold, c.onPrimary)
    }
}
