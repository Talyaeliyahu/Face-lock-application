package com.facelockapp.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import android.media.Image
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ReadOnlyBufferException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.pow
import kotlin.math.sqrt

data class FaceEmbedding(val features: FloatArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceEmbedding
        return features.contentEquals(other.features)
    }

    override fun hashCode(): Int = features.contentHashCode()
}

// הסף לזיהוי - FaceNet מייצר embeddings מנורמלים ב-L2
// Cosine Similarity קרוב ל-1.0 = פנים מאותו אדם
// Cosine Similarity קרוב ל-0.0 = אנשים שונים
// עבור MobileFaceNet, threshold של 0.75-0.8 הוא בטוח יותר
private const val FACE_MATCH_THRESHOLD = 0.75f

// מספר דגימות לרישום - 5 זה אופטימלי
private const val ENROLLMENT_REQUIRED_SAMPLES = 5

// מרחק מקסימלי בין דגימות בזמן רישום
private const val ENROLLMENT_MAX_SAMPLE_DISTANCE = 0.8f

enum class FaceRecognitionState {
    WAITING_FOR_FACE,
    CHECKING_QUALITY,
    VERIFYING,
    FACE_NOT_MATCHED,
    FACE_MATCHED,
    NO_FACE_DETECTED,
    QUALITY_CHECK_FAILED,
    TOO_FAR,      // המשתמש רחוק מדי מהמצלמה
    TOO_CLOSE     // המשתמש קרוב מדי למצלמה
}

@Composable
fun FaceRecognitionView(
    isEnrollment: Boolean,
    onFaceEnrolled: ((FaceEmbedding) -> Unit)? = null,
    onFaceVerified: ((Boolean) -> Unit)? = null,
    storedEmbedding: FaceEmbedding? = null,
    onEnrollmentProgress: ((Int, Int) -> Unit)? = null,
    onStateChanged: ((FaceRecognitionState, String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { 
        PreviewView(context).apply {
            // השתמש ב-COMPATIBLE (TextureView) במקום PERFORMANCE (SurfaceView) - זה עובד טוב יותר בתוך scrollable containers
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var isLifecycleReady by remember { mutableStateOf(false) }
    
    // בדוק שה-lifecycle מוכן (RESUMED) לפני שמחברים את המצלמה
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleReady = event == Lifecycle.Event.ON_RESUME || 
                             lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initialize FaceNet model
    val faceNetModel = remember {
        try {
            FaceNetModel(context)
        } catch (e: Exception) {
            null
        }
    }

    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // Fast mode מספיק
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // לא צריך landmarks
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f) // פנים קטנות יותר
            .build()
        FaceDetection.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            faceDetector.close()
            faceNetModel?.close()
        }
    }

    LaunchedEffect(Unit) {
        cameraProvider = ProcessCameraProvider.getInstance(context).await(context)
    }

    LaunchedEffect(cameraProvider, faceNetModel, isLifecycleReady) {
        val provider = cameraProvider ?: return@LaunchedEffect
        if (faceNetModel == null) {
            onStateChanged?.invoke(FaceRecognitionState.QUALITY_CHECK_FAILED, "שגיאה בטעינת מודל זיהוי")
            return@LaunchedEffect
        }
        
        // המתן שה-lifecycle מוכן לפני שמחברים את המצלמה
        if (!isLifecycleReady) {
            return@LaunchedEffect
        }
        
        // המתן קצת כדי שה-PreviewView יהיה מוכן
        kotlinx.coroutines.delay(100)

        try {
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(previewView.display.rotation)
                .build()

            var processingImage = AtomicBoolean(false)
            var lastProcessTime = 0L
            val enrollmentSamples = mutableListOf<FaceEmbedding>()
            
            // זמן מינימלי בין עיבודים - 500ms אופטימלי
            val MIN_PROCESS_INTERVAL_MS = 500L
            
            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                val currentTime = System.currentTimeMillis()
                val timeSinceLastProcess = currentTime - lastProcessTime
                
                val shouldProcess = !processingImage.get() && 
                                  (isEnrollment || timeSinceLastProcess >= MIN_PROCESS_INTERVAL_MS)
                
                if (shouldProcess) {
                    processingImage.set(true)
                    lastProcessTime = currentTime
                    
                    // אל נעדכן את המצב כאן - זה יקרה ב-processImageWithFaceNet
                    // זה מונע עדכונים תכופים מדי של המצב ומאפשר להציג הודעות מרחק
                    
                    processImageWithFaceNet(
                        imageProxy,
                        faceDetector,
                        faceNetModel,
                        isEnrollment,
                        storedEmbedding,
                        onStateChanged,
                        processingImage,
                        onEnrolled = { embedding ->
                            val finalized = accumulateEnrollmentSample(
                                embedding,
                                enrollmentSamples,
                                onProgress = onEnrollmentProgress
                            )
                            processingImage.set(false)
                            if (finalized != null) {
                                onFaceEnrolled?.invoke(finalized)
                            }
                        },
                        onVerified = { isMatch ->
                            onFaceVerified?.invoke(isMatch)
                            processingImage.set(false)
                        },
                        onError = { processingImage.set(false) },
                        onNoFace = { processingImage.set(false) }
                    )
                } else {
                    imageProxy.close()
                }
            }

            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalysis
            )

        } catch (e: Exception) {
            // Camera binding failed
        }
    }

    AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
}

private suspend fun <T> ListenableFuture<T>.await(context: Context): T {
    return suspendCoroutine { continuation ->
        addListener({ continuation.resume(get()) }, ContextCompat.getMainExecutor(context))
    }
}

// FaceNet Model Wrapper
/**
 * מחלקה לעבודה עם MobileFaceNet - מודל לזיהוי פנים
 * המודל ממיר תמונת פנים ל-embedding (192 מספרים) שמייצגים את הפנים באופן ייחודי
 */
class FaceNetModel(context: Context) {
    private val interpreter: Interpreter
    
    // MobileFaceNet parameters
    private val inputSize = 112  // MobileFaceNet expects 112x112 images
    val embeddingSize = 192  // MobileFaceNet outputs 192-dimensional embeddings
    
    // Statistics for normalization (calculated from training data)
    private val meanValues = floatArrayOf(127.5f, 127.5f, 127.5f)
    private val stdValues = floatArrayOf(128f, 128f, 128f)

    init {
        try {
            val modelFile = FileUtil.loadMappedFile(context, "mobilefacenet.tflite")
            
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Use 4 threads for better performance
                setUseNNAPI(false) // Disable NNAPI for compatibility
            }
            
            interpreter = Interpreter(modelFile, options)
            
            // Verify model output size
            val outputTensor = interpreter.getOutputTensor(0)
            val actualSize = outputTensor.shape()[1]
            
            if (actualSize != embeddingSize) {
                throw IllegalStateException("Model output size ($actualSize) doesn't match expected ($embeddingSize)")
            }
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to load MobileFaceNet model: ${e.message}", e)
        }
    }

    /**
     * ממיר תמונת פנים ל-embedding
     * @param bitmap תמונת הפנים (יכולה להיות בכל גודל - תותאם אוטומטית)
     * @return embedding של 192 מספרים המייצגים את הפנים
     */
    fun getEmbedding(bitmap: Bitmap): FloatArray {
        try {
            // Step 1: Resize image to 112x112
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            
            // Step 2: Convert to ByteBuffer (model input format)
            val input = convertBitmapToByteBuffer(resized)
            
            // Step 3: Run inference
            val output = Array(1) { FloatArray(embeddingSize) }
            interpreter.run(input, output)
            
            val embedding = output[0]
            
            // Step 4: L2 Normalize (critical for face recognition!)
            val normalized = l2Normalize(embedding)
            
            // Verify normalization (should be ~1.0)
            val norm = sqrt(normalized.sumOf { it.toDouble() * it.toDouble() }.toFloat())
            Log.d("FaceNetModel", "Generated embedding: size=${normalized.size}, L2 norm=$norm")
            
            return normalized
            
        } catch (e: Exception) {
            Log.e("FaceNetModel", "Failed to generate embedding", e)
            throw RuntimeException("Failed to generate face embedding: ${e.message}", e)
        }
    }

    /**
     * ממיר Bitmap ל-ByteBuffer בפורמט שהמודל מצפה לו
     * Normalization: (pixel - mean) / std
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                
                // Extract RGB channels
                val r = (value shr 16 and 0xFF).toFloat()
                val g = (value shr 8 and 0xFF).toFloat()
                val b = (value and 0xFF).toFloat()
                
                // Normalize: (pixel - mean) / std
                byteBuffer.putFloat((r - meanValues[0]) / stdValues[0])
                byteBuffer.putFloat((g - meanValues[1]) / stdValues[1])
                byteBuffer.putFloat((b - meanValues[2]) / stdValues[2])
            }
        }
        
        return byteBuffer
    }

    /**
     * L2 Normalization - הופך את ה-embedding ל-unit vector
     * זה קריטי! בלי זה ההשוואה לא תעבוד!
     */
    private fun l2Normalize(embedding: FloatArray): FloatArray {
        // Calculate L2 norm (magnitude of the vector)
        var sumSquares = 0.0
        for (value in embedding) {
            sumSquares += (value * value).toDouble()
        }
        val norm = sqrt(sumSquares).toFloat()
        
        if (norm == 0f) {
            return embedding
        }
        
        // Divide each element by the norm
        val normalized = FloatArray(embedding.size)
        for (i in embedding.indices) {
            normalized[i] = embedding[i] / norm
        }
        
        return normalized
    }

    /**
     * סוגר את המודל ומשחרר משאבים
     */
    fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            // Ignore errors on close
        }
    }
}

private fun processImageWithFaceNet(
    imageProxy: ImageProxy,
    faceDetector: com.google.mlkit.vision.face.FaceDetector,
    faceNetModel: FaceNetModel,
    isEnrollment: Boolean,
    storedEmbedding: FaceEmbedding?,
    onStateChanged: ((FaceRecognitionState, String?) -> Unit)?,
    processingImageRef: AtomicBoolean,
    onEnrolled: (FaceEmbedding) -> Unit,
    onVerified: (Boolean) -> Unit,
    onError: () -> Unit,
    onNoFace: () -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        // אל נעדכן את המצב כאן - זה גורם לעדכונים תכופים מדי
        
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.first()
                    
                    // בזמן enrollment, בדוק מרחק לפני בדיקת איכות
                    if (isEnrollment) {
                        val distanceCheck = checkFaceDistance(face, mediaImage.width, mediaImage.height)
                        Log.d("FaceRecognitionView", "Distance check: isOptimal=${distanceCheck.isOptimal}, state=${distanceCheck.state}, message=${distanceCheck.message}, avgRatio=${(face.boundingBox.width().toFloat() / mediaImage.width + face.boundingBox.height().toFloat() / mediaImage.height) / 2f}")
                        if (!distanceCheck.isOptimal) {
                            // עדכן את המצב עם הודעת המרחק
                            onStateChanged?.invoke(
                                distanceCheck.state ?: FaceRecognitionState.QUALITY_CHECK_FAILED,
                                distanceCheck.message
                            )
                            processingImageRef.set(false)
                            imageProxy.close()
                            return@addOnSuccessListener
                        }
                    } else {
                        // אם זה לא enrollment, עדכן "מחפש פנים" רק אם אין פנים או אם צריך
                        onStateChanged?.invoke(FaceRecognitionState.WAITING_FOR_FACE, "מחפש פנים...")
                    }
                    
                    // בדוק את איכות הפנים (מרכז, גודל מינימלי)
                    val qualityCheck = checkFaceQuality(face, mediaImage.width, mediaImage.height)
                    
                    if (!qualityCheck.isGood) {
                        // עדכן את המצב שהפנים לא במרכז/לא קרובות מספיק
                        onStateChanged?.invoke(FaceRecognitionState.QUALITY_CHECK_FAILED, qualityCheck.reason)
                        // אבל אל תעצור - המשך לבדוק את הפנים הבאות
                        // אם הפנים יחזרו למרכז, הקוד יזהה אותן
                        processingImageRef.set(false) // שחרר את ה-flag כדי שהקוד יוכל לנסות שוב
                        imageProxy.close()
                        return@addOnSuccessListener
                    }
                    
                    // אם הפנים במרכז ובאיכות טובה, נסה לזהות אותן
                    // עדכן את המצב רק לפני עיבוד משמעותי
                    onStateChanged?.invoke(FaceRecognitionState.VERIFYING, "מעבד...")
                    
                    try {
                        // Convert ImageProxy to Bitmap and extract face
                        val fullBitmap = mediaImageToBitmap(mediaImage)
                        val rotated = rotateBitmap(fullBitmap, imageProxy.imageInfo.rotationDegrees)
                        val faceBitmap = extractFaceFromBitmap(rotated, face)
                        
                        if (faceBitmap == null || faceBitmap.isRecycled) {
                            onError()
                            imageProxy.close()
                            return@addOnSuccessListener
                        }
                        
                        // Get embedding from MobileFaceNet
                        val embedding = faceNetModel.getEmbedding(faceBitmap)
                        val faceEmbedding = FaceEmbedding(embedding)
                        
                        if (isEnrollment) {
                            onEnrolled(faceEmbedding)
                        } else {
                            if (storedEmbedding != null) {
                                // Compare using cosine similarity
                                val similarity = cosineSimilarity(faceEmbedding.features, storedEmbedding.features)
                                val isMatch = similarity > FACE_MATCH_THRESHOLD
                                
                                // Log detailed information for debugging
                                Log.i("FaceRecognitionView", "═══════════════════════════════════════")
                                Log.i("FaceRecognitionView", "🔍 MOBILEFACENET VERIFICATION:")
                                Log.i("FaceRecognitionView", "   Cosine Similarity: $similarity")
                                Log.i("FaceRecognitionView", "   Threshold: $FACE_MATCH_THRESHOLD")
                                Log.i("FaceRecognitionView", "   Difference: ${similarity - FACE_MATCH_THRESHOLD}")
                                Log.i("FaceRecognitionView", "   Match: ${if (isMatch) "✅ YES - SAME PERSON" else "❌ NO - DIFFERENT PERSON"}")
                                Log.i("FaceRecognitionView", "═══════════════════════════════════════")
                                
                                if (isMatch) {
                                    onStateChanged?.invoke(FaceRecognitionState.FACE_MATCHED, "פנים תואמות! ✅")
                                } else {
                                    onStateChanged?.invoke(FaceRecognitionState.FACE_NOT_MATCHED, "הפנים לא תואמות ❌")
                                }
                                
                                onVerified(isMatch)
                            } else {
                                Log.e("FaceRecognitionView", "❌ No stored embedding for comparison!")
                                onVerified(false)
                            }
                        }
                    } catch (e: Exception) {
                        onError()
                    }
                } else {
                    // אם אין פנים, עדכן "מחפש פנים..."
                    if (isEnrollment) {
                        onStateChanged?.invoke(FaceRecognitionState.WAITING_FOR_FACE, "מחפש פנים...")
                    }
                    onNoFace()
                }
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                onStateChanged?.invoke(FaceRecognitionState.QUALITY_CHECK_FAILED, "שגיאה בזיהוי פנים")
                onError()
                imageProxy.close()
            }
    } else {
        onStateChanged?.invoke(FaceRecognitionState.QUALITY_CHECK_FAILED, "שגיאה בקריאת תמונה")
        onError()
        imageProxy.close()
    }
}

private fun extractFaceBitmap(imageProxy: ImageProxy, face: Face): Bitmap {
    val mediaImage = imageProxy.image ?: throw IllegalStateException("Image is null")
    val bitmap = mediaImageToBitmap(mediaImage)
    val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
    return extractFaceFromBitmap(rotated, face)
}

private fun extractFaceFromBitmap(bitmap: Bitmap, face: Face): Bitmap {
    val boundingBox = face.boundingBox
    
    // Add 30% margin around face
    val margin = 0.3f
    val marginX = (boundingBox.width() * margin).toInt()
    val marginY = (boundingBox.height() * margin).toInt()
    
    val left = maxOf(0, boundingBox.left - marginX)
    val top = maxOf(0, boundingBox.top - marginY)
    val right = minOf(bitmap.width, boundingBox.right + marginX)
    val bottom = minOf(bitmap.height, boundingBox.bottom + marginY)
    
    val width = right - left
    val height = bottom - top
    
    return if (width > 0 && height > 0) {
        Bitmap.createBitmap(bitmap, left, top, width, height)
    } else {
        bitmap
    }
}

// המרת MediaImage (YUV_420_888) ל-Bitmap
// YUV_420_888 format: Y plane (full size), U and V planes (half size, interleaved)
private fun mediaImageToBitmap(mediaImage: Image): Bitmap {
    try {
        val width = mediaImage.width
        val height = mediaImage.height
        
        val yBuffer = mediaImage.planes[0].buffer
        val uBuffer = mediaImage.planes[1].buffer
        val vBuffer = mediaImage.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        // YUV_420_888: Y plane is full size, U and V are half size
        // Convert to NV21: Y plane + interleaved VU (V first, then U)
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)
        
        // Interleave V and U planes (V first, then U) for NV21 format
        val uvSize = uSize + vSize
        val uvBuffer = ByteArray(uvSize)
        
        // Read V and U planes
        val vArray = ByteArray(vSize)
        val uArray = ByteArray(uSize)
        vBuffer.get(vArray)
        uBuffer.get(uArray)
        
        // Interleave: VU VU VU... (for NV21)
        var uvIndex = 0
        for (i in 0 until minOf(vSize, uSize)) {
            uvBuffer[uvIndex++] = vArray[i]
            uvBuffer[uvIndex++] = uArray[i]
        }
        
        // Copy remaining if sizes differ
        if (vSize > uSize) {
            System.arraycopy(vArray, uSize, uvBuffer, uvIndex, vSize - uSize)
        } else if (uSize > vSize) {
            System.arraycopy(uArray, vSize, uvBuffer, uvIndex, uSize - vSize)
        }
        
        // Copy interleaved UV to NV21 array
        System.arraycopy(uvBuffer, 0, nv21, ySize, uvSize)
        
        // Convert NV21 to JPEG and then to Bitmap
        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )
        
        val out = java.io.ByteArrayOutputStream()
        val success = yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, width, height),
            100,
            out
        )
        
        if (!success) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        
        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        if (bitmap == null) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
        
        return bitmap
    } catch (e: Exception) {
        return Bitmap.createBitmap(mediaImage.width, mediaImage.height, Bitmap.Config.ARGB_8888)
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

private data class FaceQualityResult(val isGood: Boolean, val reason: String)

private data class FaceDistanceResult(
    val isOptimal: Boolean,
    val state: FaceRecognitionState?,
    val message: String
)

/**
 * בודק את המרחק של הפנים מהמצלמה על בסיס גודל ה-bounding box
 * מרחק אופטימלי: 30-40 ס"מ (בערך 30-50% מרוחב/גובה התמונה)
 * 
 * @param face הפנים המזוהות
 * @param imageWidth רוחב התמונה
 * @param imageHeight גובה התמונה
 * @return FaceDistanceResult עם המצב וההודעה המתאימים
 */
private fun checkFaceDistance(face: Face, imageWidth: Int, imageHeight: Int): FaceDistanceResult {
    val boundingBox = face.boundingBox
    val faceWidth = boundingBox.width()
    val faceHeight = boundingBox.height()
    
    // חשב את היחס בין גודל הפנים לגודל התמונה
    val faceWidthRatio = faceWidth.toFloat() / imageWidth
    val faceHeightRatio = faceHeight.toFloat() / imageHeight
    val avgRatio = (faceWidthRatio + faceHeightRatio) / 2f
    
    // מרחק אופטימלי: 30-50% מהתמונה (בערך 30-40 ס"מ)
    // פנים קטנות מדי (< 30%) = רחוק מדי
    // פנים גדולות מדי (> 50%) = קרוב מדי
    // פנים בגודל טוב (30-50%) = מרחק אופטימלי
    
    return when {
        avgRatio < 0.30f -> {
            FaceDistanceResult(
                isOptimal = false,
                state = FaceRecognitionState.TOO_FAR,
                message = "מחפש פנים, יש להתקרב למצלמה"
            )
        }
        avgRatio > 0.50f -> {
            FaceDistanceResult(
                isOptimal = false,
                state = FaceRecognitionState.TOO_CLOSE,
                message = "מחפש פנים, יש להרחיק את המצלמה"
            )
        }
        else -> {
            FaceDistanceResult(
                isOptimal = true,
                state = null,
                message = "מרחק מצוין!"
            )
        }
    }
}

private fun checkFaceQuality(face: Face, imageWidth: Int, imageHeight: Int): FaceQualityResult {
    val boundingBox = face.boundingBox
    val faceWidth = boundingBox.width()
    val faceHeight = boundingBox.height()
    
    // גודל מינימלי - 15% מהתמונה
    val minFaceSize = minOf(imageWidth, imageHeight) * 0.15f
    val faceSize = sqrt((faceWidth * faceHeight).toFloat())
    if (faceSize < minFaceSize) {
        return FaceQualityResult(false, "התקרב למצלמה")
}

    // במרכז - 35% מהתמונה
    val faceCenterX = boundingBox.centerX()
    val faceCenterY = boundingBox.centerY()
    val imageCenterX = imageWidth / 2f
    val imageCenterY = imageHeight / 2f
    val centerDistanceX = kotlin.math.abs(faceCenterX - imageCenterX) / imageWidth
    val centerDistanceY = kotlin.math.abs(faceCenterY - imageCenterY) / imageHeight
    
    if (centerDistanceX > 0.35f || centerDistanceY > 0.35f) {
        return FaceQualityResult(false, "הזז את הפנים למרכז")
    }
    
    return FaceQualityResult(true, "איכות טובה")
}

// Cosine Similarity - הדרך הנכונה להשוות FaceNet embeddings
private fun cosineSimilarity(embedding1: FaceEmbedding, embedding2: FaceEmbedding): Float {
    return cosineSimilarity(embedding1.features, embedding2.features)
}

/**
 * מחשב Cosine Similarity בין שני embeddings
 * ערך קרוב ל-1.0 = פנים מאותו אדם
 * ערך קרוב ל-0.0 = אנשים שונים
 */
private fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
    if (embedding1.size != embedding2.size) {
        return 0f
    }
    
    // Dot product (after L2 normalization, this IS the cosine similarity)
    var dotProduct = 0f
    for (i in embedding1.indices) {
        dotProduct += embedding1[i] * embedding2[i]
    }
    
    return dotProduct
}

private fun accumulateEnrollmentSample(
    newSample: FaceEmbedding,
    samples: MutableList<FaceEmbedding>,
    onProgress: ((Int, Int) -> Unit)? = null
): FaceEmbedding? {
    if (samples.isNotEmpty()) {
        val last = samples.last()
        val similarity = cosineSimilarity(newSample.features, last.features)
        val distance = 1 - similarity
        
        if (distance > ENROLLMENT_MAX_SAMPLE_DISTANCE) {
            return null
        }
    }

    samples.add(newSample)
    onProgress?.invoke(samples.size, ENROLLMENT_REQUIRED_SAMPLES)

    if (samples.size >= ENROLLMENT_REQUIRED_SAMPLES) {
        val dim = samples.first().features.size
        val avg = FloatArray(dim)

        samples.forEach { sample ->
            for (i in 0 until dim) {
                avg[i] += sample.features[i]
            }
        }

        for (i in 0 until dim) {
            avg[i] /= samples.size.toFloat()
        }
        
        // L2 normalize the averaged embedding
        val norm = sqrt(avg.sumOf { it.toDouble() * it.toDouble() }.toFloat())
        for (i in avg.indices) {
            avg[i] = avg[i] / norm
        }

        samples.clear()
        return FaceEmbedding(avg)
    }

    return null
}
