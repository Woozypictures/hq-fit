package uk.woozy.fit.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.FoodEntity
import uk.woozy.fit.data.db.Foods
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.off.OffClient
import uk.woozy.fit.data.off.OffResult
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.model.Meal
import java.util.concurrent.Executors

private sealed interface ScanState {
    data object Scanning : ScanState
    data class Looking(val code: String) : ScanState
    data class Found(val food: FoodEntity) : ScanState
    data class Missing(val code: String) : ScanState
    data class Offline(val code: String) : ScanState
}

/** Camera barcode scanner. Looks in your saved foods first, then Open Food Facts; a miss can become a custom food. */
@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanScreen(
    db: HqFitDatabase,
    off: OffClient,
    meal: Meal,
    date: String,
    onBack: () -> Unit,
    onCreate: (barcode: String) -> Unit,
    onLogged: () -> Unit,
) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    val names by produceState(emptyMap<String, Pair<String, String>>(), db) {
        value = db.nutrients().all().associate { it.key to (it.name to it.unit) }
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    fun lookup(code: String) {
        state = ScanState.Looking(code)
        scope.launch {
            val saved = db.foods().byBarcode(code)
            if (saved != null) {
                state = ScanState.Found(saved)
                return@launch
            }
            state = when (val r = off.product(code)) {
                is OffResult.Found -> ScanState.Found(Foods.saveDraft(db, r.value.copy(barcode = r.value.barcode ?: code), System.currentTimeMillis()))
                OffResult.Missing -> ScanState.Missing(code)
                OffResult.Offline -> ScanState.Offline(code)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (granted && state is ScanState.Scanning) {
            CameraPreview(onCode = { lookup(it) })
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            BackBar("Scan barcode", onBack)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (val s = state) {
                    ScanState.Scanning -> HqText(
                        if (granted) "Point the camera at a barcode" else "Camera permission is needed to scan. You can still search and create foods by hand.",
                        15.sp, FontWeight.Medium, c.ink,
                    )
                    is ScanState.Looking -> HqText("Looking up ${s.code}…", 15.sp, FontWeight.Medium)
                    is ScanState.Missing -> {
                        HqText("${s.code} is not in Open Food Facts.", 15.sp, FontWeight.Medium)
                        HqPillButton("Create food from this barcode", onClick = { onCreate(s.code) }, modifier = Modifier.fillMaxWidth())
                        HqPillButton("Scan again", onClick = { state = ScanState.Scanning }, modifier = Modifier.fillMaxWidth(), style = PillStyle.Outline)
                    }
                    is ScanState.Offline -> {
                        HqText("Could not reach Open Food Facts. Nothing was changed.", 15.sp, FontWeight.Medium)
                        HqPillButton("Try again", onClick = { lookup(s.code) }, modifier = Modifier.fillMaxWidth())
                        HqPillButton("Create food from this barcode", onClick = { onCreate(s.code) }, modifier = Modifier.fillMaxWidth(), style = PillStyle.Outline)
                    }
                    is ScanState.Found -> Unit
                }
                if (!granted) HqPillButton("Allow camera", onClick = { ask.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    (state as? ScanState.Found)?.let { f ->
        AmountSheet(db, f.food, meal, date, names, onDone = { state = ScanState.Scanning }, onLogged = onLogged)
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = context as ComponentActivity
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    var handled by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || handled) {
                        proxy.close()
                    } else {
                        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                val raw = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                if (raw != null && !handled) {
                                    handled = true
                                    onCode(raw)
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}
