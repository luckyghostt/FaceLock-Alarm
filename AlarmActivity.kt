package com.facelock.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class AlarmActivity : ComponentActivity() {
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { /* PIN fallback remains available */ }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            var verified by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Look at the camera and blink") }
            var pin by remember { mutableStateOf("") }
            var showPin by remember { mutableStateOf(false) }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF65D6FF), background = Color(0xFF08111F))) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF08111F)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("WAKE UP", color = Color.White, fontSize = 30.sp)
                    Text(status, color = Color(0xFF65D6FF))
                    if (!verified) {
                        CameraChallenge(
                            onStatus = { status = it },
                            onVerified = { verified = true; status = "Verified — good morning!" }
                        )
                    } else {
                        Text("Face verified", color = Color(0xFF70E1A1), fontSize = 22.sp)
                    }
                    if (verified) {
                        Button(onClick = { stopAlarm() }, modifier = Modifier.fillMaxWidth()) { Text("STOP ALARM") }
                    }
                    OutlinedButton(onClick = { showPin = !showPin }, modifier = Modifier.fillMaxWidth()) {
                        Text("Emergency PIN")
                    }
                    if (showPin) {
                        OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) }, label = { Text("Enter PIN") })
                        Button(onClick = {
                            val saved = getSharedPreferences("facelock", MODE_PRIVATE).getString("pin", "")
                            if (pin.length >= 4 && pin == saved) stopAlarm()
                            else status = "Incorrect PIN"
                        }) { Text("Unlock with PIN") }
                    }
                    Text("No snooze. Complete verification or use your emergency PIN.", color = Color(0xFF9CB1C9), fontSize = 11.sp)
                }
            }
        }
    }
    private fun stopAlarm() {
        stopService(android.content.Intent(this, AlarmRingingService::class.java))
        finish()
    }
}

@Composable
private fun CameraChallenge(onStatus: (String) -> Unit, onVerified: () -> Unit) {
    val context = LocalContext.current
    var cameraReady by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build()
        )
    }
    var sawClosed by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf(false) }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(320.dp),
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val media = proxy.image
                        if (media == null) { proxy.close(); return@setAnalyzer }
                        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        detector.process(image)
                            .addOnSuccessListener { faces ->
                                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                                if (face == null) onStatus("Position your face in the frame")
                                else {
                                    val left = face.leftEyeOpenProbability ?: 0f
                                    val right = face.rightEyeOpenProbability ?: 0f
                                    if (left < 0.30f && right < 0.30f) sawClosed = true
                                    if (sawClosed && left > 0.75f && right > 0.75f && !complete) {
                                        complete = true
                                        onStatus("Eyes open — blink verified")
                                        onVerified()
                                    } else if (!sawClosed) onStatus("Blink once, then keep both eyes open")
                                    else onStatus("Keep both eyes open")
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            context as androidx.lifecycle.LifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
                        )
                        cameraReady = true
                    } catch (_: Exception) { onStatus("Camera unavailable — use Emergency PIN") }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
    DisposableEffect(Unit) {
        onDispose { detector.close(); executor.shutdown() }
    }
}
