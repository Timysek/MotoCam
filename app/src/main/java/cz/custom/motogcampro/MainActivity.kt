package cz.custom.motogcampro

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CaptureRequest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

enum class MotoMode(val title: String, val icon: ImageVector, val inBottomBar: Boolean) {
    SLOW_MO("Pomalý pohyb", Icons.Default.SlowMotionVideo, true),
    VIDEO("Video", Icons.Default.Videocam, true),
    PHOTO("Fotka", Icons.Default.PhotoCamera, true),
    ULTRA_RES("Ultra-Res", Icons.Default.HighQuality, true),
    PORTRAIT("Portrét", Icons.Default.Portrait, true),
    MORE("Více", Icons.Default.GridView, true),

    PRO("Profesionální", Icons.Default.Tune, false),
    PANORAMA("Panorama", Icons.Default.PanoramaHorizontal, false),
    NIGHT("Noční vidění", Icons.Default.DarkMode, false),
    TILT_SHIFT("Tilt-shift", Icons.Default.BlurLinear, false),
    LONG_EXP("Dlouhá expozice", Icons.Default.ShutterSpeed, false),
    SCAN("Skenování", Icons.Default.DocumentScanner, false),
    SPOT_COLOR("Vybraná barva", Icons.Default.Colorize, false),
    PHOTO_BOOTH("Série fotek", Icons.Default.BurstMode, false),
    DUAL_CAMERA("Duální snímek", Icons.Default.FlipCameraAndroid, false)
}

enum class LivePhotoDuration(val seconds: Int, val label: String) {
    SEC_3(3, "3 s"),
    SEC_5(5, "5 s"),
    SEC_10(10, "10 s")
}

enum class HdrState(val label: String) {
    AUTO("HDR Auto"),
    ENHANCED("HDR+ Max"),
    OFF("HDR Vyp")
}

enum class FlashState(val label: String) {
    OFF_NIGHT("Noc"),
    ON("Blesk"),
    TORCH("Svítilna")
}

enum class AspectRatioMode(val label: String, val ratioFloat: Float, val cameraXRatio: Int) {
    RATIO_3_4("3:4", 3f / 4f, AspectRatio.RATIO_4_3),
    RATIO_9_16("9:16", 9f / 16f, AspectRatio.RATIO_16_9)
}

enum class VolumeKeyEvent {
    SHORT_PRESS,
    LONG_PRESS_START,
    LONG_PRESS_RELEASE
}

class MainActivity : ComponentActivity() {
    val volumeEvents = MutableSharedFlow<VolumeKeyEvent>(extraBufferCapacity = 8)
    private var isVolumeLongPressActive = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val needed = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (needed.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(needed)
        }
        setContent {
            val context = LocalContext.current
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                darkColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                MotoProCameraApp(volumeEvents = volumeEvents)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event != null) {
                if (event.repeatCount == 0) {
                    isVolumeLongPressActive = false
                } else if (event.repeatCount >= 1 && !isVolumeLongPressActive) {
                    isVolumeLongPressActive = true
                    volumeEvents.tryEmit(VolumeKeyEvent.LONG_PRESS_START)
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (isVolumeLongPressActive) {
                isVolumeLongPressActive = false
                volumeEvents.tryEmit(VolumeKeyEvent.LONG_PRESS_RELEASE)
            } else {
                volumeEvents.tryEmit(VolumeKeyEvent.SHORT_PRESS)
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

@OptIn(
    ExperimentalCamera2Interop::class,
    ExperimentalPersistentRecording::class,
    ExperimentalGetImage::class
)
@SuppressLint("MissingPermission")
@Composable
fun MotoProCameraApp(volumeEvents: MutableSharedFlow<VolumeKeyEvent>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val prefs = remember { context.getSharedPreferences("motocam_settings", Context.MODE_PRIVATE) }

    var activeMode by remember { mutableStateOf(MotoMode.PHOTO) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var showMoreDrawer by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var liveDuration by remember { mutableStateOf(LivePhotoDuration.SEC_5) }
    var livePhotoEnabled by remember { mutableStateOf(prefs.getBoolean("live_enabled", true)) }
    var preRecordLivePhoto by remember { mutableStateOf(prefs.getBoolean("pre_record", true)) }
    var redirectVideoToSystem by remember { mutableStateOf(prefs.getBoolean("redirect_video", false)) }

    var hdrState by remember { mutableStateOf(HdrState.AUTO) }
    var flashState by remember { mutableStateOf(FlashState.OFF_NIGHT) }
    var smileShutterEnabled by remember { mutableStateOf(false) }
    var autoWideSelfieEnabled by remember { mutableStateOf(true) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.RATIO_3_4) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var showGridAndLevel by remember { mutableStateOf(prefs.getBoolean("grid_level", true)) }
    var saveGpsLocation by remember { mutableStateOf(prefs.getBoolean("save_gps", true)) }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }
    var autoNightEnabled by remember { mutableStateOf(prefs.getBoolean("auto_night", true)) }

    var isLowLightScene by remember { mutableStateOf(false) }
    var sceneLumaValue by remember { mutableIntStateOf(120) }

    fun vibrate(type: Int = HapticFeedbackConstants.CLOCK_TICK) {
        if (hapticsEnabled) {
            view.performHapticFeedback(type)
        }
    }

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(saveGpsLocation) {
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { loc -> currentLocation = loc }
        if (saveGpsLocation && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                currentLocation = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, listener)
                }
                if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener)
                }
            } catch (_: Exception) {}
        }
        onDispose {
            try { locManager.removeUpdates(listener) } catch (_: Exception) {}
        }
    }

    var rollAngleDeg by remember { mutableFloatStateOf(0f) }
    var targetSurfaceRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    var uiIconRotationDeg by remember { mutableFloatStateOf(0f) }
    val animatedIconRotation by animateFloatAsState(
        targetValue = uiIconRotationDeg,
        animationSpec = tween(durationMillis = 250),
        label = "iconRot"
    )

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size >= 3) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val angle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                    rollAngleDeg = rollAngleDeg * 0.85f + angle * 0.15f

                    if (abs(z) < 7.8f) {
                        val normAngle = (angle + 360f) % 360f
                        when {
                            normAngle in 68f..112f -> {
                                targetSurfaceRotation = Surface.ROTATION_90
                                uiIconRotationDeg = 90f
                            }
                            normAngle in 248f..292f -> {
                                targetSurfaceRotation = Surface.ROTATION_270
                                uiIconRotationDeg = -90f
                            }
                            normAngle <= 22f || normAngle >= 338f -> {
                                targetSurfaceRotation = Surface.ROTATION_0
                                uiIconRotationDeg = 0f
                            }
                            normAngle in 158f..202f -> {
                                targetSurfaceRotation = Surface.ROTATION_180
                                uiIconRotationDeg = 180f
                            }
                        }
                        imageCapture?.targetRotation = targetSurfaceRotation
                        videoCapture?.targetRotation = targetSurfaceRotation
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accel != null) {
            sensorManager.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(sensorListener) }
    }

    val zoomAnimatable = remember { Animatable(1.0f) }
    var currentZoom by remember { mutableFloatStateOf(1.0f) }
    var isMacroActive by remember { mutableStateOf(false) }
    var showFineZoomSlider by remember { mutableStateOf(false) }
    var detectedFacesCount by remember { mutableIntStateOf(0) }
    var lastSmileCaptureMs by remember { mutableLongStateOf(0L) }

    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var exposureIndex by remember { mutableFloatStateOf(0f) }
    var lastFocusTimestamp by remember { mutableLongStateOf(0L) }

    var isRecordingVideo by remember { mutableStateOf(false) }
    var isVideoLocked by remember { mutableStateOf(false) }
    var isVideoPaused by remember { mutableStateOf(false) }
    var videoElapsedSeconds by remember { mutableIntStateOf(0) }
    var shutterDragOffsetY by remember { mutableFloatStateOf(0f) }

    var isCapturingLivePhoto by remember { mutableStateOf(false) }
    var livePhotoProgress by remember { mutableFloatStateOf(0f) }
    var shutterFlashEffect by remember { mutableStateOf(false) }
    var screenFlashOverlay by remember { mutableStateOf(false) }
    var isShutterPressed by remember { mutableStateOf(false) }

    var lastSavedUri by remember { mutableStateOf<Uri?>(null) }
    var lastThumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusBanner by remember { mutableStateOf("Připraveno") }

    var detectedBarcodeText by remember { mutableStateOf<String?>(null) }
    var detectedBarcodeRaw by remember { mutableStateOf<String?>(null) }
    var barcodeTimestampMs by remember { mutableLongStateOf(0L) }
    var dismissedBarcodeValue by remember { mutableStateOf<String?>(null) }
    var qrSwipeOffsetX by remember { mutableFloatStateOf(0f) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeVideoRecording by remember { mutableStateOf<Recording?>(null) }
    var activeLiveRecording by remember { mutableStateOf<Recording?>(null) }
    var queuedNextLivePhoto by remember { mutableStateOf(false) }

    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val faceDetector = remember {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        FaceDetection.getClient(opts)
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    fun refreshGalleryThumbnail(targetUri: Uri? = null) {
        coroutineScope.launch {
            try {
                val resolver = context.contentResolver
                val uriToLoad = targetUri ?: run {
                    val projection = arrayOf(MediaStore.Images.Media._ID)
                    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(0)
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        } else null
                    }
                }
                if (uriToLoad != null) {
                    lastSavedUri = uriToLoad
                    val bmp = resolver.loadThumbnail(uriToLoad, Size(200, 200), null)
                    lastThumbnailBitmap = bmp
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        refreshGalleryThumbnail()
    }

    LaunchedEffect(isLowLightScene, autoNightEnabled, camera) {
        val cam = camera ?: return@LaunchedEffect
        try {
            val c2Control = Camera2CameraControl.from(cam.cameraControl)
            if (autoNightEnabled && isLowLightScene) {
                c2Control.setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(8, 30))
                        .build()
                )
                cam.cameraControl.setExposureCompensationIndex(2)
            } else {
                c2Control.setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(15, 30))
                        .build()
                )
                if (focusTapOffset == null) {
                    cam.cameraControl.setExposureCompensationIndex(0)
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(detectedBarcodeText, barcodeTimestampMs) {
        if (detectedBarcodeText != null) {
            delay(15_000L)
            if (System.currentTimeMillis() - barcodeTimestampMs >= 14_900L) {
                dismissedBarcodeValue = detectedBarcodeRaw
                detectedBarcodeText = null
                qrSwipeOffsetX = 0f
            }
        }
    }

    LaunchedEffect(lastFocusTimestamp) {
        if (focusTapOffset != null) {
            delay(3500L)
            if (System.currentTimeMillis() - lastFocusTimestamp >= 3400L) {
                focusTapOffset = null
                showFineZoomSlider = false
            }
        }
    }

    LaunchedEffect(isRecordingVideo, isVideoPaused) {
        if (isRecordingVideo) {
            while (isRecordingVideo) {
                delay(1000L)
                if (!isVideoPaused) videoElapsedSeconds += 1
            }
        } else {
            videoElapsedSeconds = 0
        }
    }

    LaunchedEffect(isCapturingLivePhoto, liveDuration) {
        if (isCapturingLivePhoto) {
            val totalMs = liveDuration.seconds * 1000L
            val start = System.currentTimeMillis()
            while (isCapturingLivePhoto) {
                val elapsed = System.currentTimeMillis() - start
                livePhotoProgress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                delay(30L)
            }
            livePhotoProgress = 0f
        } else {
            livePhotoProgress = 0f
        }
    }

    fun animateZoomTo(targetZoom: Float, macro: Boolean = false) {
        vibrate(HapticFeedbackConstants.CLOCK_TICK)
        isMacroActive = macro
        autoWideSelfieEnabled = false
        coroutineScope.launch {
            zoomAnimatable.snapTo(currentZoom)
            zoomAnimatable.animateTo(
                targetValue = targetZoom,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            ) {
                currentZoom = value
                camera?.cameraControl?.setZoomRatio(value)
            }
            if (macro) {
                val centerPoint = previewView.meteringPointFactory.createPoint(
                    previewView.width / 2f,
                    previewView.height / 2f
                )
                val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(5, TimeUnit.SECONDS)
                    .build()
                camera?.cameraControl?.startFocusAndMetering(action)
            }
        }
        statusBanner = if (macro) "🌷 Makro aktivní (0.85×)" else "Zoom ${String.format("%.1f", targetZoom)}×"
    }

    fun setScreenMaxBrightness(enable: Boolean) {
        val activity = context as? Activity ?: return
        val params = activity.window.attributes
        params.screenBrightness = if (enable) 1.0f else -1.0f
        activity.window.attributes = params
        screenFlashOverlay = enable
    }

    fun triggerCaptureSequence() {
        vibrate(HapticFeedbackConstants.CONFIRM)

        if (isCapturingLivePhoto && activeLiveRecording != null) {
            statusBanner = "Ukládám 1. živou fotku a fotím další..."
            queuedNextLivePhoto = true
            try { activeLiveRecording?.stop() } catch (_: Exception) {}
            return
        }

        coroutineScope.launch {
            if (timerSeconds > 0) {
                for (sec in timerSeconds downTo 1) {
                    countdownRemaining = sec
                    vibrate(HapticFeedbackConstants.CLOCK_TICK)
                    delay(1000L)
                }
                countdownRemaining = 0
            }

            shutterFlashEffect = true
            launch {
                delay(90L)
                shutterFlashEffect = false
            }

            val useScreenFlash = isFrontCamera && flashState != FlashState.OFF_NIGHT
            if (useScreenFlash) {
                setScreenMaxBrightness(true)
                delay(300L)
            }

            val nightBoostNeeded = autoNightEnabled && isLowLightScene
            val isLive = activeMode == MotoMode.PHOTO && livePhotoEnabled

            if (isLive) {
                isCapturingLivePhoto = true
                statusBanner = if (nightBoostNeeded) "🌙 Noční Live Photo (${liveDuration.seconds}s)..." else "🔴 Nahrávám Živou fotku (${liveDuration.seconds}s)..."
                activeLiveRecording = startFixedLivePhotoCapture(
                    context = context,
                    imageCapture = imageCapture,
                    videoCapture = videoCapture,
                    durationSeconds = liveDuration.seconds,
                    targetRotation = targetSurfaceRotation,
                    location = if (saveGpsLocation) currentLocation else null,
                    nightBoost = nightBoostNeeded,
                    preRecord = preRecordLivePhoto,
                    onFinalize = { uri, msg ->
                        isCapturingLivePhoto = false
                        activeLiveRecording = null
                        setScreenMaxBrightness(false)
                        if (uri != null) refreshGalleryThumbnail(uri)
                        statusBanner = msg

                        if (queuedNextLivePhoto) {
                            queuedNextLivePhoto = false
                            Handler(Looper.getMainLooper()).postDelayed({
                                triggerCaptureSequence()
                            }, 200L)
                        }
                    }
                )
            } else {
                takeStandardPhoto(
                    context = context,
                    imageCapture = imageCapture,
                    targetRotation = targetSurfaceRotation,
                    location = if (saveGpsLocation) currentLocation else null,
                    nightBoost = nightBoostNeeded,
                    onDone = { uri, msg ->
                        setScreenMaxBrightness(false)
                        if (uri != null) refreshGalleryThumbnail(uri)
                        statusBanner = msg
                    }
                )
            }
        }
    }

    fun startVideoRecordingSession(locked: Boolean) {
        if (redirectVideoToSystem) {
            launchOriginalMotoCamera(context, "VIDEO")
            return
        }
        if (isRecordingVideo) return
        vibrate(HapticFeedbackConstants.LONG_PRESS)
        imageCapture?.targetRotation = targetSurfaceRotation
        videoCapture?.targetRotation = targetSurfaceRotation
        activeVideoRecording = startPersistentVideo(
            context = context,
            videoCapture = videoCapture,
            location = if (saveGpsLocation) currentLocation else null
        )
        isRecordingVideo = true
        isVideoLocked = locked
        isVideoPaused = false
        statusBanner = if (locked) "🔴 Nahrávám video" else "🔴 Držíš spoušť: Posuň nahoru pro 🔒 Zámek"
    }

    fun stopVideoRecordingSession() {
        if (!isRecordingVideo) return
        vibrate(HapticFeedbackConstants.CONFIRM)
        try { activeVideoRecording?.stop() } catch (_: Exception) {}
        isRecordingVideo = false
        isVideoLocked = false
        isVideoPaused = false
        shutterDragOffsetY = 0f
        statusBanner = "Video uloženo do Galerie"
    }

    LaunchedEffect(volumeEvents) {
        volumeEvents.collect { event ->
            when (event) {
                VolumeKeyEvent.SHORT_PRESS -> {
                    if (isRecordingVideo) {
                        stopVideoRecordingSession()
                    } else if (activeMode == MotoMode.VIDEO) {
                        if (redirectVideoToSystem) {
                            launchOriginalMotoCamera(context, "VIDEO")
                        } else {
                            startVideoRecordingSession(locked = true)
                        }
                    } else {
                        triggerCaptureSequence()
                    }
                }
                VolumeKeyEvent.LONG_PRESS_START -> {
                    if (!isRecordingVideo) {
                        startVideoRecordingSession(locked = false)
                    }
                }
                VolumeKeyEvent.LONG_PRESS_RELEASE -> {
                    if (isRecordingVideo && !isVideoLocked) {
                        stopVideoRecordingSession()
                    }
                }
            }
        }
    }

    LaunchedEffect(activeMode, isFrontCamera, hdrState, aspectRatioMode) {
        if (activeMode == MotoMode.MORE) return@LaunchedEffect

        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val extensionsManager = ExtensionsManager.getInstanceAsync(context, cameraProvider).get()

        val aspectStrategy = if (aspectRatioMode == AspectRatioMode.RATIO_3_4) {
            AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        } else {
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectStrategy)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()

        val previewBuilder = Preview.Builder().setResolutionSelector(resolutionSelector)
        Camera2Interop.Extender(previewBuilder)
            .setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)

        val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                try {
                    val yBuffer = mediaImage.planes[0].buffer
                    yBuffer.rewind()
                    var sum = 0L
                    val step = 64
                    var count = 0
                    val limit = yBuffer.limit()
                    var idx = 0
                    while (idx < limit) {
                        sum += (yBuffer.get(idx).toInt() and 0xFF)
                        count++
                        idx += step
                    }
                    if (count > 0) {
                        val avgLuma = (sum / count).toInt()
                        sceneLumaValue = avgLuma
                        isLowLightScene = avgLuma < 42
                    }
                } catch (_: Exception) {}

                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                val barcodeTask = barcodeScanner.process(inputImage).addOnSuccessListener { barcodes ->
                    val first = barcodes.firstOrNull()
                    if (first != null) {
                        val raw = first.rawValue ?: first.displayValue ?: ""
                        if (raw.isNotBlank() && raw != dismissedBarcodeValue) {
                            detectedBarcodeRaw = raw
                            detectedBarcodeText = when {
                                raw.startsWith("FIDO:/", ignoreCase = true) -> "🔑 Přístupový klíč (Passkey)"
                                raw.startsWith("WIFI:", ignoreCase = true) -> "📶 Wi-Fi síť"
                                else -> first.displayValue ?: raw
                            }
                            barcodeTimestampMs = System.currentTimeMillis()
                        }
                    }
                }

                val faceTask = faceDetector.process(inputImage).addOnSuccessListener { faces ->
                    detectedFacesCount = faces.size
                    if (isFrontCamera && autoWideSelfieEnabled && faces.isNotEmpty()) {
                        val targetSelfieZoom = if (faces.size >= 2) 1.0f else 1.2f
                        if (currentZoom != targetSelfieZoom) {
                            currentZoom = targetSelfieZoom
                            camera?.cameraControl?.setZoomRatio(targetSelfieZoom)
                        }
                    }

                    if (smileShutterEnabled && faces.isNotEmpty() && !isRecordingVideo && !isCapturingLivePhoto) {
                        val allSmiling = faces.all { (it.smilingProbability ?: 0f) > 0.72f }
                        val now = System.currentTimeMillis()
                        if (allSmiling && (now - lastSmileCaptureMs > 4000L)) {
                            lastSmileCaptureMs = now
                            Handler(Looper.getMainLooper()).post {
                                statusBanner = "😄 Úsměv rozpoznán!"
                                triggerCaptureSequence()
                            }
                        }
                    }
                }

                Tasks.whenAllComplete(barcodeTask, faceTask).addOnCompleteListener {
                    imageProxy.close()
                }
            } else {
                imageProxy.close()
            }
        }

        val imageBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
            .setTargetRotation(targetSurfaceRotation)
            .setResolutionSelector(resolutionSelector)

        if (activeMode == MotoMode.ULTRA_RES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Camera2Interop.Extender(imageBuilder).setCaptureRequestOption(
                CaptureRequest.SENSOR_PIXEL_MODE,
                CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION
            )
        }

        val imgCap = imageBuilder.build()
        imgCap.flashMode = if (!isFrontCamera && flashState == FlashState.ON) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        imageCapture = imgCap

        val vidQuality = if (activeMode == MotoMode.VIDEO) Quality.UHD else Quality.FHD
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(vidQuality, FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)))
            .setAspectRatio(aspectRatioMode.cameraXRatio)
            .build()
        val vidCap = VideoCapture.Builder(recorder)
            .setMirrorMode(MirrorMode.MIRROR_MODE_OFF)
            .setTargetRotation(targetSurfaceRotation)
            .build()
        videoCapture = vidCap

        var selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val extMode = when {
            activeMode == MotoMode.NIGHT -> ExtensionMode.NIGHT
            activeMode == MotoMode.PORTRAIT -> ExtensionMode.BOKEH
            hdrState == HdrState.ENHANCED -> ExtensionMode.HDR
            !livePhotoEnabled && autoNightEnabled && isLowLightScene -> ExtensionMode.NIGHT
            else -> ExtensionMode.NONE
        }

        if (extMode != ExtensionMode.NONE && extensionsManager.isExtensionAvailable(selector, extMode)) {
            selector = extensionsManager.getExtensionEnabledCameraSelector(selector, extMode)
        }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis, imgCap, vidCap)
        } catch (_: Exception) {
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis, imgCap)
        }

        val startZoom = if (isFrontCamera) 1.2f else currentZoom
        currentZoom = startZoom
        camera?.cameraControl?.setZoomRatio(startZoom)
        camera?.cameraControl?.enableTorch(!isFrontCamera && flashState == FlashState.TORCH)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = if (aspectRatioMode == AspectRatioMode.RATIO_3_4) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .align(Alignment.Center)
                    .offset(y = (-18).dp)
                    .clip(RoundedCornerShape(14.dp))
            } else {
                Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            }
                .background(Color.Black)
                .pointerInput(isFrontCamera) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) {
                            val minZ = if (isFrontCamera) 1.0f else 0.5f
                            val maxZ = if (isFrontCamera) 4.0f else 10.0f
                            val newZ = (currentZoom * zoomChange).coerceIn(minZ, maxZ)
                            currentZoom = newZ
                            isMacroActive = false
                            showFineZoomSlider = true
                            lastFocusTimestamp = System.currentTimeMillis()
                            camera?.cameraControl?.setZoomRatio(newZ)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        vibrate(HapticFeedbackConstants.CLOCK_TICK)
                        focusTapOffset = offset
                        lastFocusTimestamp = System.currentTimeMillis()
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                        )
                            .setAutoCancelDuration(5, TimeUnit.SECONDS)
                            .build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                        statusBanner = "🎯 Zaostřeno na vybraný bod"
                    }
                }
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            if (showGridAndLevel) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val gridColor = Color.White.copy(alpha = 0.28f)
                    drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.5f)
                    drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.5f)
                    drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.5f)
                    drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.5f)

                    val isLevel = abs(rollAngleDeg) < 1.5f || abs(abs(rollAngleDeg) - 90f) < 1.5f
                    val levelColor = if (isLevel) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f)
                    val cx = w / 2f
                    val cy = h / 2f
                    val lineHalf = 90f
                    val rad = Math.toRadians(rollAngleDeg.toDouble())
                    val dx = (kotlin.math.cos(rad) * lineHalf).toFloat()
                    val dy = (kotlin.math.sin(rad) * lineHalf).toFloat()
                    drawLine(
                        color = levelColor,
                        start = Offset(cx - dx, cy - dy),
                        end = Offset(cx + dx, cy + dy),
                        strokeWidth = if (isLevel) 4f else 2.5f
                    )
                }
            }

            focusTapOffset?.let { tap ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFFFFD54F),
                        radius = 34.dp.toPx(),
                        center = tap,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFFFD54F),
                        radius = 4.dp.toPx(),
                        center = tap
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (aspectRatioMode == AspectRatioMode.RATIO_9_16) 210.dp else 14.dp)
                        .width(220.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.WbSunny, contentDescription = "Jas", tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Slider(
                            value = exposureIndex,
                            onValueChange = { newEv ->
                                exposureIndex = newEv
                                lastFocusTimestamp = System.currentTimeMillis()
                                camera?.cameraControl?.setExposureCompensationIndex(newEv.roundToInt())
                            },
                            valueRange = -4f..4f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (shutterFlashEffect) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
            }

            if (screenFlashOverlay) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFF8E7).copy(alpha = 0.92f)))
            }

            if (countdownRemaining > 0) {
                Text(
                    text = "$countdownRemaining",
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 36.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        vibrate()
                        flashState = when (flashState) {
                            FlashState.OFF_NIGHT -> FlashState.ON
                            FlashState.ON -> FlashState.TORCH
                            FlashState.TORCH -> FlashState.OFF_NIGHT
                        }
                        imageCapture?.flashMode = if (!isFrontCamera && flashState == FlashState.ON) {
                            ImageCapture.FLASH_MODE_ON
                        } else {
                            ImageCapture.FLASH_MODE_OFF
                        }
                        camera?.cameraControl?.enableTorch(!isFrontCamera && flashState == FlashState.TORCH)
                    }) {
                        Icon(
                            imageVector = when (flashState) {
                                FlashState.OFF_NIGHT -> Icons.Default.DarkMode
                                FlashState.ON -> Icons.Default.FlashOn
                                FlashState.TORCH -> Icons.Default.Highlight
                            },
                            contentDescription = "Blesk",
                            tint = if (flashState == FlashState.OFF_NIGHT) Color.LightGray else Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp).rotate(animatedIconRotation)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(flashState.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = {
                        vibrate()
                        hdrState = when (hdrState) {
                            HdrState.AUTO -> HdrState.ENHANCED
                            HdrState.ENHANCED -> HdrState.OFF
                            HdrState.OFF -> HdrState.AUTO
                        }
                    }) {
                        Text(
                            text = hdrState.label,
                            color = when (hdrState) {
                                HdrState.ENHANCED -> Color(0xFFFFB300)
                                HdrState.AUTO -> primaryColor
                                HdrState.OFF -> Color.Gray
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = {
                        vibrate()
                        aspectRatioMode = if (aspectRatioMode == AspectRatioMode.RATIO_3_4) {
                            AspectRatioMode.RATIO_9_16
                        } else {
                            AspectRatioMode.RATIO_3_4
                        }
                    }) {
                        Text(
                            text = aspectRatioMode.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = {
                        vibrate()
                        livePhotoEnabled = !livePhotoEnabled
                        prefs.edit().putBoolean("live_enabled", livePhotoEnabled).apply()
                        statusBanner = if (livePhotoEnabled) "Živá fotka (${liveDuration.seconds}s) ZAP" else "Klasická fotka"
                    }) {
                        Icon(
                            imageVector = if (livePhotoEnabled) Icons.Default.MotionPhotosOn else Icons.Default.MotionPhotosOff,
                            contentDescription = "Živá fotka",
                            tint = if (livePhotoEnabled) primaryColor else Color.Gray,
                            modifier = Modifier.rotate(animatedIconRotation)
                        )
                    }

                    IconButton(onClick = {
                        vibrate()
                        launchNativeGoogleLens(context)
                    }) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Google Lens",
                            tint = Color.White,
                            modifier = Modifier.rotate(animatedIconRotation)
                        )
                    }

                    IconButton(onClick = {
                        vibrate()
                        showSettingsDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Nastavení",
                            tint = Color.White,
                            modifier = Modifier.rotate(animatedIconRotation)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isRecordingVideo) {
                    Surface(color = Color.Red, shape = RoundedCornerShape(12.dp)) {
                        val mins = videoElapsedSeconds / 60
                        val secs = videoElapsedSeconds % 60
                        Text(
                            text = if (isVideoPaused) "⏸ PAUZA %02d:%02d".format(mins, secs) else "● REC %02d:%02d".format(mins, secs),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                if (autoNightEnabled && isLowLightScene) {
                    Surface(color = Color(0xFF312E81), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = "✨ Auto Noc",
                            color = Color(0xFFFDE047),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(14.dp)) {
                    val gpsIcon = if (saveGpsLocation && currentLocation != null) "📍GPS " else ""
                    val orientIcon = if (targetSurfaceRotation == Surface.ROTATION_90 || targetSurfaceRotation == Surface.ROTATION_270) "↔️Šířka • " else ""
                    val preIcon = if (preRecordLivePhoto && livePhotoEnabled) "⏪Pre " else ""
                    Text(
                        text = "$gpsIcon$orientIcon${activeMode.title} • ${if (livePhotoEnabled && activeMode == MotoMode.PHOTO) "${preIcon}Live ${liveDuration.seconds}s" else "Klasická"} • $statusBanner",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
            }

            AnimatedVisibility(
                visible = detectedBarcodeText != null,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .offset { IntOffset(qrSwipeOffsetX.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(qrSwipeOffsetX) > 180f) {
                                    vibrate()
                                    dismissedBarcodeValue = detectedBarcodeRaw
                                    detectedBarcodeText = null
                                    qrSwipeOffsetX = 0f
                                } else {
                                    qrSwipeOffsetX = 0f
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                qrSwipeOffsetX += dragAmount
                            }
                        )
                    }
            ) {
                val raw = detectedBarcodeRaw ?: ""
                val isFido = raw.startsWith("FIDO:/", ignoreCase = true)
                val isUrl = raw.startsWith("http://", ignoreCase = true) ||
                        raw.startsWith("https://", ignoreCase = true) ||
                        raw.startsWith("tel:", ignoreCase = true) ||
                        raw.startsWith("mailto:", ignoreCase = true) ||
                        raw.startsWith("geo:", ignoreCase = true) ||
                        isFido

                Surface(
                    color = primaryContainer,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isFido) Icons.Default.Key else Icons.Default.QrCodeScanner,
                                contentDescription = "Kód",
                                tint = onPrimaryContainer
                            )
                            Text(
                                text = detectedBarcodeText ?: "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    dismissedBarcodeValue = detectedBarcodeRaw
                                    detectedBarcodeText = null
                                    qrSwipeOffsetX = 0f
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Zavřít", tint = onPrimaryContainer)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    vibrate()
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clip.setPrimaryClip(android.content.ClipData.newPlainText("Kód", raw))
                                    Toast.makeText(context, "Zkopírováno do schránky", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kopírovat", fontSize = 11.sp)
                            }

                            if (isUrl) {
                                Button(
                                    onClick = {
                                        vibrate(HapticFeedbackConstants.CONFIRM)
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Nelze otevřít: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFido) Icons.Default.Key else Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isFido) "Použít přístupový klíč" else "Otevřít odkaz",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .padding(bottom = 24.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = isRecordingVideo && !isVideoLocked) {
                Surface(
                    color = if (shutterDragOffsetY < -65f) primaryColor else Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Zámek", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Posuň prst sem pro uzamčení videa", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            AnimatedVisibility(visible = showFineZoomSlider) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 2.dp)
                ) {
                    Text("${String.format("%.1f", currentZoom)}×", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = currentZoom.coerceIn(0.5f, 10.0f),
                        onValueChange = { z ->
                            currentZoom = z
                            isMacroActive = false
                            lastFocusTimestamp = System.currentTimeMillis()
                            camera?.cameraControl?.setZoomRatio(z)
                        },
                        valueRange = 0.5f..10.0f,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showFineZoomSlider = false }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Skrýt", tint = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isFrontCamera) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isMacroActive) primaryContainer else Color.Transparent)
                            .clickable { animateZoomTo(0.85f, macro = !isMacroActive) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFlorist,
                                contentDescription = "Makro",
                                tint = if (isMacroActive) onPrimaryContainer else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Makro",
                                color = if (isMacroActive) onPrimaryContainer else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    listOf(0.5f to "0.5×", 1.0f to "1×", 2.0f to "2×", 3.0f to "3×").forEach { (z, label) ->
                        val selected = !isMacroActive && abs(currentZoom - z) < 0.08f
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (selected) primaryContainer else Color.Transparent)
                                .clickable {
                                    if (selected) {
                                        showFineZoomSlider = !showFineZoomSlider
                                    } else {
                                        animateZoomTo(z, macro = false)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) onPrimaryContainer else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.rotate(animatedIconRotation)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                vibrate()
                                showFineZoomSlider = !showFineZoomSlider
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = "Jemný zoom", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                } else {
                    listOf(1.0f to "👥 Širokoúhel", 1.2f to "👤 Portrét").forEach { (z, label) ->
                        val selected = abs(currentZoom - z) < 0.08f
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) primaryContainer else Color.Transparent)
                                .clickable { animateZoomTo(z, macro = false) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) onPrimaryContainer else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MotoMode.entries.filter { it.inBottomBar }.forEach { mode ->
                    val isSelected = activeMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) primaryColor else Color.Transparent)
                            .clickable {
                                vibrate()
                                when (mode) {
                                    MotoMode.MORE -> showMoreDrawer = true
                                    MotoMode.SLOW_MO -> launchOriginalMotoCamera(context, "SLOW_MOTION")
                                    MotoMode.VIDEO -> {
                                        if (redirectVideoToSystem) {
                                            launchOriginalMotoCamera(context, "VIDEO")
                                        } else {
                                            activeMode = mode
                                        }
                                    }
                                    else -> activeMode = mode
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = mode.title,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecordingVideo) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable {
                                vibrate()
                                if (isVideoPaused) {
                                    activeVideoRecording?.resume()
                                    isVideoPaused = false
                                } else {
                                    activeVideoRecording?.pause()
                                    isVideoPaused = true
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isVideoPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pauza",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                            .clickable {
                                vibrate()
                                openPhotoInGalleryApp(context, lastSavedUri)
                            }
                    ) {
                        val bmp = lastThumbnailBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Poslední fotka",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().rotate(animatedIconRotation)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Galerie",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.rotate(animatedIconRotation)
                                )
                            }
                        }
                    }
                }

                val shutterScale by animateFloatAsState(
                    targetValue = if (isShutterPressed) 0.86f else 1.0f,
                    animationSpec = tween(durationMillis = 100),
                    label = "shutterScale"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(84.dp)
                ) {
                    if (isCapturingLivePhoto) {
                        CircularProgressIndicator(
                            progress = { livePhotoProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = primaryColor,
                            strokeWidth = 5.dp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(shutterScale)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .background(
                                when {
                                    isRecordingVideo -> Color.Red
                                    isCapturingLivePhoto -> primaryColor
                                    activeMode == MotoMode.VIDEO -> Color.Red
                                    else -> Color.White
                                },
                                CircleShape
                            )
                            .pointerInput(activeMode, isRecordingVideo) {
                                detectTapGestures(
                                    onPress = {
                                        isShutterPressed = true
                                        tryAwaitRelease()
                                        isShutterPressed = false
                                    },
                                    onTap = {
                                        if (isRecordingVideo) {
                                            stopVideoRecordingSession()
                                        } else if (activeMode == MotoMode.VIDEO) {
                                            if (redirectVideoToSystem) {
                                                launchOriginalMotoCamera(context, "VIDEO")
                                            } else {
                                                startVideoRecordingSession(locked = true)
                                            }
                                        } else {
                                            triggerCaptureSequence()
                                        }
                                    }
                                )
                            }
                            .pointerInput(activeMode) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        shutterDragOffsetY = 0f
                                        startVideoRecordingSession(locked = false)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        shutterDragOffsetY += dragAmount.y
                                        if (shutterDragOffsetY < -65f && !isVideoLocked) {
                                            isVideoLocked = true
                                            vibrate(HapticFeedbackConstants.CONFIRM)
                                            statusBanner = "🔒 Video uzamčeno (nahrává dál)"
                                        }
                                    },
                                    onDragEnd = {
                                        if (!isVideoLocked) {
                                            stopVideoRecordingSession()
                                        }
                                        shutterDragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        if (!isVideoLocked) {
                                            stopVideoRecordingSession()
                                        }
                                        shutterDragOffsetY = 0f
                                    }
                                )
                            }
                    )
                }

                if (isRecordingVideo) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable {
                                vibrate(HapticFeedbackConstants.CONFIRM)
                                takeStandardPhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    targetRotation = targetSurfaceRotation,
                                    location = if (saveGpsLocation) currentLocation else null,
                                    nightBoost = false
                                ) { uri, _ ->
                                    if (uri != null) refreshGalleryThumbnail(uri)
                                    statusBanner = "📸 Snímek během videa uložen"
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Vyfotit", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable {
                                vibrate()
                                isFrontCamera = !isFrontCamera
                                isMacroActive = false
                                autoWideSelfieEnabled = true
                                statusBanner = if (isFrontCamera) "🤳 Selfie kamera (nezrcadlově)" else "📷 Zadní kamera"
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Otočit kameru",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.rotate(animatedIconRotation)
                            )
                        }
                    }
                }
            }
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Nastavení MotoCam Pro") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Délka Živé fotky se zvukem:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        LivePhotoDuration.entries.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { liveDuration = option }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(selected = liveDuration == option, onClick = { liveDuration = option })
                                Text(text = option.label, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏪ Záznam z půlky i PŘED stiskem", fontSize = 13.sp)
                            Switch(
                                checked = preRecordLivePhoto,
                                onCheckedChange = {
                                    preRecordLivePhoto = it
                                    prefs.edit().putBoolean("pre_record", it).apply()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎬 Režim Video otevře systémovou kameru", fontSize = 13.sp)
                            Switch(
                                checked = redirectVideoToSystem,
                                onCheckedChange = {
                                    redirectVideoToSystem = it
                                    prefs.edit().putBoolean("redirect_video", it).apply()
                                }
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Samospoušť:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "Vyp", 3 to "3 s", 10 to "10 s").forEach { (sec, label) ->
                                    FilterChip(
                                        selected = timerSeconds == sec,
                                        onClick = { timerSeconds = sec },
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✨ Automatické noční zesvětlení", fontSize = 13.sp)
                            Switch(
                                checked = autoNightEnabled,
                                onCheckedChange = {
                                    autoNightEnabled = it
                                    prefs.edit().putBoolean("auto_night", it).apply()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📍 Ukládat polohu GPS do fotek", fontSize = 13.sp)
                            Switch(
                                checked = saveGpsLocation,
                                onCheckedChange = {
                                    saveGpsLocation = it
                                    prefs.edit().putBoolean("save_gps", it).apply()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📏 Mřížka 3×3 a vodováha", fontSize = 13.sp)
                            Switch(
                                checked = showGridAndLevel,
                                onCheckedChange = {
                                    showGridAndLevel = it
                                    prefs.edit().putBoolean("grid_level", it).apply()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("😄 Automatické focení na úsměv", fontSize = 13.sp)
                            Switch(
                                checked = smileShutterEnabled,
                                onCheckedChange = { smileShutterEnabled = it }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📳 Haptická odezva (Vibrace)", fontSize = 13.sp)
                            Switch(
                                checked = hapticsEnabled,
                                onCheckedChange = {
                                    hapticsEnabled = it
                                    prefs.edit().putBoolean("haptics", it).apply()
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showSettingsDialog = false }) { Text("Hotovo") }
                }
            )
        }

        AnimatedVisibility(visible = showMoreDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f))
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(MotoMode.entries.filter { !it.inBottomBar }) { mode ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        vibrate()
                                        showMoreDrawer = false
                                        when (mode) {
                                            MotoMode.PANORAMA -> launchOriginalMotoCamera(context, "PANORAMA")
                                            MotoMode.SCAN -> launchOriginalMotoCamera(context, "SCAN")
                                            MotoMode.DUAL_CAMERA -> launchOriginalMotoCamera(context, "DUAL_CAPTURE")
                                            else -> activeMode = mode
                                        }
                                    }
                                    .padding(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = mode.icon,
                                            contentDescription = mode.title,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = mode.title, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(
                        onClick = { showMoreDrawer = false },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.width(200.dp).height(48.dp)
                    ) {
                        Text("Hotovo", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun startFixedLivePhotoCapture(
    context: Context,
    imageCapture: ImageCapture?,
    videoCapture: VideoCapture<Recorder>?,
    durationSeconds: Int,
    targetRotation: Int,
    location: Location?,
    nightBoost: Boolean,
    preRecord: Boolean,
    onFinalize: (Uri?, String) -> Unit
): Recording? {
    val imgCap = imageCapture ?: return null
    val vidCap = videoCapture ?: return null
    imgCap.targetRotation = targetRotation
    vidCap.targetRotation = targetRotation

    val stamp = System.currentTimeMillis()
    val jpg = File(context.cacheDir, "LP_$stamp.jpg")
    val mp4 = File(context.cacheDir, "LP_$stamp.mp4")
    val totalMs = durationSeconds * 1000L

    // Pokud je zapnutý před-záznam, hlavní snímek vyfotíme až v 40 % délky videa (takže polovina videa je dění před fotkou!)
    val snapMomentMs = if (preRecord) (totalMs * 0.4f).toLong().coerceAtLeast(800L) else 450L
    val remainingMs = (totalMs - snapMomentMs).coerceAtLeast(1000L)

    var rec: Recording? = null
    try {
        rec = vidCap.output.prepareRecording(context, FileOutputOptions.Builder(mp4).build())
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val uri = MotionPhotoMuxer.createMotionPhoto(
                        context = context,
                        jpegFile = jpg,
                        mp4File = mp4,
                        location = location,
                        nightBoost = nightBoost,
                        presentationTimestampUs = snapMomentMs * 1000L
                    )
                    onFinalize(uri, if (uri != null) "Živá fotka uložena!" else "Uloženo")
                }
            }

        Handler(Looper.getMainLooper()).postDelayed({
            val metadata = ImageCapture.Metadata().apply {
                isReversedHorizontal = false
                if (location != null) this.location = location
            }
            val opts = ImageCapture.OutputFileOptions.Builder(jpg).setMetadata(metadata).build()
            imgCap.takePicture(
                opts,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            try { rec?.stop() } catch (_: Exception) {}
                        }, remainingMs)
                    }
                    override fun onError(exc: ImageCaptureException) {
                        try { rec?.stop() } catch (_: Exception) {}
                    }
                }
            )
        }, snapMomentMs)
    } catch (e: Exception) {
        onFinalize(null, "Příprava kamery...")
    }
    return rec
}

private fun launchNativeGoogleLens(context: Context) {
    val pm = context.packageManager
    val standaloneLens = pm.getLaunchIntentForPackage("com.google.ar.lens")
    if (standaloneLens != null) {
        standaloneLens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(standaloneLens)
        return
    }
    try {
        val googleAppLens = Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).apply {
            setPackage("com.google.android.googlequicksearchbox")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(googleAppLens)
        return
    } catch (_: Exception) {}

    try {
        val searchIntent = pm.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
        if (searchIntent != null) {
            context.startActivity(searchIntent)
            return
        }
    } catch (_: Exception) {}

    Toast.makeText(context, "Aplikace Google Lens nebyla nalezena", Toast.LENGTH_SHORT).show()
}

private fun openPhotoInGalleryApp(context: Context, lastUri: Uri?) {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager?.isKeyguardLocked == true) {
        (context as? Activity)?.let { act ->
            keyguardManager.requestDismissKeyguard(act, null)
        }
    }

    val targetUri = lastUri ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val photosPackage = "com.google.android.apps.photos"
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "image/jpeg")
            setPackage(photosPackage)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {}

    try {
        val fallback = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "image/jpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(fallback)
    } catch (_: Exception) {}
}

private fun launchOriginalMotoCamera(context: Context, targetMode: String) {
    val motoPackages = listOf("com.motorola.camera3", "com.motorola.camera2")
    for (pkg in motoPackages) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.putExtra("android.intent.extra.CAMERA_MODE", targetMode)
            launchIntent.putExtra("com.motorola.camera.LAUNCH_MODE", targetMode)
            context.startActivity(launchIntent)
            return
        }
    }
    context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
}

@OptIn(ExperimentalPersistentRecording::class)
@SuppressLint("MissingPermission")
private fun startPersistentVideo(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    location: Location?
): Recording? {
    val vidCap = videoCapture ?: return null
    val cv = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "MOTO_VID_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
        if (location != null) {
            put(MediaStore.Video.Media.LATITUDE, location.latitude)
            put(MediaStore.Video.Media.LONGITUDE, location.longitude)
        }
    }
    val out = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setContentValues(cv)
        .setLocation(location)
        .build()
    return vidCap.output.prepareRecording(context, out).asPersistentRecording().withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context)) {}
}

private fun takeStandardPhoto(
    context: Context,
    imageCapture: ImageCapture?,
    targetRotation: Int,
    location: Location?,
    nightBoost: Boolean,
    onDone: (Uri?, String) -> Unit
) {
    val imgCap = imageCapture ?: return
    imgCap.targetRotation = targetRotation
    val stamp = System.currentTimeMillis()
    val tempJpg = File(context.cacheDir, "STD_$stamp.jpg")

    val metadata = ImageCapture.Metadata().apply {
        isReversedHorizontal = false
        if (location != null) this.location = location
    }
    val tempOut = ImageCapture.OutputFileOptions.Builder(tempJpg).setMetadata(metadata).build()

    imgCap.takePicture(tempOut, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            if (nightBoost) {
                MotionPhotoMuxer.enhanceNightJpegIfNeeded(tempJpg, true)
            }
            if (location != null) {
                try {
                    val exif = ExifInterface(tempJpg.absolutePath)
                    exif.setLatLong(location.latitude, location.longitude)
                    exif.saveAttributes()
                } catch (_: Exception) {}
            }

            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "MOTO_PRO_$stamp.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
                if (location != null) {
                    put(MediaStore.Images.Media.LATITUDE, location.latitude)
                    put(MediaStore.Images.Media.LONGITUDE, location.longitude)
                }
            }
            val resolver = context.contentResolver
            val savedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (savedUri != null) {
                resolver.openOutputStream(savedUri)?.use { out ->
                    out.write(tempJpg.readBytes())
                }
            }
            tempJpg.delete()
            onDone(savedUri, if (nightBoost) "🌙 Noční fotka uložena" else "Fotka uložena")
        }

        override fun onError(exc: ImageCaptureException) {
            onDone(null, "Chyba: ${exc.message}")
        }
    })
}
