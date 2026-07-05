package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.location.Location
import android.util.Base64
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.core.app.NotificationCompat
import com.example.data.local.AppDatabase
import com.example.data.model.DocItem
import com.example.data.model.DocumentChunk
import com.example.data.repository.MediAgentRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object RagServicePayload {
    private val payloads = java.util.concurrent.ConcurrentHashMap<String, DocPayload>()
    
    fun set(docId: String, payload: DocPayload) { payloads[docId] = payload }
    fun get(docId: String): DocPayload? = payloads[docId]
    fun clear(docId: String) { payloads.remove(docId) }

    var pendingFilePath: String? = null
    var pendingBase64Image: String? = null
    var pendingTextContent: String? = null
    var embeddingApiKey: String = ""
    var embeddingModel: String = ""
    var embeddingApiLink: String = ""
    var embeddingProvider: String = ""
    var llmApiKey: String = ""
    var llmModel: String = ""
    var llmApiLink: String = ""
    var llmProvider: String = ""
    var ragChunkSize: Int = 1000
}

data class DocPayload(
    val filePath: String = "",
    val base64Image: String = "",
    val textContent: String = ""
)

object RagProgressManager {
    val docProgress = MutableStateFlow<Map<String, Pair<Float, Int>>>(emptyMap())
    val docPaused = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val docCancelled = MutableStateFlow<Map<String, Boolean>>(emptyMap())
}

class RagForegroundService : Service() {

    private val CHANNEL_ID = "rag_indexing_channel"
    private val NOTIFICATION_ID = 5055

    private val currentJobStartTime = java.util.concurrent.atomic.AtomicLong(0L)
    private val currentJobEstimatedTotalSeconds = java.util.concurrent.atomic.AtomicInteger(15)

    private fun getUnifiedRemainingSeconds(): Int {
        val startTime = currentJobStartTime.get()
        if (startTime <= 0L) return 10
        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        val total = currentJobEstimatedTotalSeconds.get()
        return (total - elapsed).coerceAtLeast(2)
    }

    private val textRecognizer by lazy {
        com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val activeJobs = mutableMapOf<String, Job>()

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private val repository: MediAgentRepository by lazy {
        MediAgentRepository(AppDatabase.getDatabase(applicationContext).mediAgentDao())
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 32
            })
            .build()
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "MediAgent::RagWakeLock").apply {
                    acquire(30 * 60 * 1000L) // 30 minutes max or until released
                }
            }
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MediAgent::RagWifiLock").apply {
                    acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildProgressNotification("Initializing...", 0f),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildProgressNotification("Initializing...", 0f))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acquireLocks()
        if (intent != null) {
            val action = intent.action
            val docId = intent.getStringExtra("doc_id") ?: ""
            val sourceName = intent.getStringExtra("source_name") ?: "Document"
            val fileType = intent.getStringExtra("file_type") ?: "doc"
            val pageCount = intent.getIntExtra("page_count", 1)
            val categoryName = intent.getStringExtra("category_name") ?: ""
            val categoryColor = intent.getStringExtra("category_color") ?: ""

            if (docId.isNotBlank()) {
                if (action == "ACTION_CANCEL") {
                    cancelJob(docId)
                } else if (action == "ACTION_RESUME") {
                    resumeJob(docId, sourceName, fileType, pageCount, categoryName, categoryColor)
                } else {
                    startIndexingJob(docId, sourceName, fileType, pageCount, categoryName, categoryColor)
                }
            }
        }
        return START_STICKY
    }

    private fun startIndexingJob(docId: String, sourceName: String, fileType: String, pageCount: Int, categoryName: String = "", categoryColor: String = "") {
        activeJobs[docId]?.cancel()
        
        // Mark as active and not paused or cancelled
        RagProgressManager.docPaused.value = RagProgressManager.docPaused.value.toMutableMap().apply { put(docId, false) }
        RagProgressManager.docCancelled.value = RagProgressManager.docCancelled.value.toMutableMap().apply { put(docId, false) }

        val docPayload = RagServicePayload.get(docId)
        val textContent = docPayload?.textContent ?: RagServicePayload.pendingTextContent ?: ""
        val imageBase64 = docPayload?.base64Image ?: RagServicePayload.pendingBase64Image ?: ""
        val pendingFilePath = docPayload?.filePath ?: RagServicePayload.pendingFilePath ?: ""

        // Capture static configuration variables
        val embedKey = RagServicePayload.embeddingApiKey
        val embedModel = RagServicePayload.embeddingModel
        val embedLink = RagServicePayload.embeddingApiLink
        val embedProvider = RagServicePayload.embeddingProvider
        val llmKey = RagServicePayload.llmApiKey
        val llmModelSpec = RagServicePayload.llmModel
        val llmLink = RagServicePayload.llmApiLink
        val llmProvider = RagServicePayload.llmProvider
        val serviceChunkSize = RagServicePayload.ragChunkSize

        val job = serviceScope.launch {
            currentJobStartTime.set(System.currentTimeMillis())
            // Dynamic initial time estimate based on page count
            val ocrEst = (pageCount * 1.5).toInt().coerceAtLeast(3)
            val estChunks = pageCount * 4
            val batchEst = (((estChunks + 95) / 96) * 3.0).toInt().coerceAtLeast(3)
            val initialEst = ocrEst + batchEst + 5
            currentJobEstimatedTotalSeconds.set(initialEst.coerceAtLeast(10))
            // Pre-initialize basic doc info for recovery in catch blocks
            var initialDoc = DocItem(
                id = docId,
                fileSource = sourceName,
                fileType = fileType,
                r2Url = "https://pub-r2.mediagent.net/$docId/$sourceName",
                status = "PROCESSING",
                pageCount = pageCount,
                summary = "Analyzing clinical contents and indexing document into on-device vector repository...",
                primaryConditions = "Primary Care, Outpatient Triage",
                categoryName = categoryName,
                categoryColor = categoryColor
            )
            try {
                // Initialize Doc State checking existing chunks to support checkpointing
                val existingChunksCheck = repository.getChunksForDocument(docId)
                initialDoc = DocItem(
                    id = docId,
                    fileSource = sourceName,
                    fileType = fileType,
                    r2Url = "https://pub-r2.mediagent.net/$docId/$sourceName",
                    status = "PROCESSING",
                    pageCount = pageCount,
                    summary = if (existingChunksCheck.isNotEmpty()) {
                        "Resuming indexing: Reusing ${existingChunksCheck.size} completed vectors successfully cached."
                    } else {
                        "Analyzing clinical contents and indexing document into on-device vector repository..."
                    },
                    primaryConditions = "Primary Care, Outpatient Triage",
                    categoryName = categoryName,
                    categoryColor = categoryColor
                )
                repository.insertDocument(initialDoc)

                updateNotificationAndProgress(docId, sourceName, 0.05f, getUnifiedRemainingSeconds())

                // Perform text extraction
                val pageDetailsList = mutableListOf<PageDetails>()
                var pdfPageCount = pageCount

                if (fileType.lowercase() == "pdf") {
                    updateNotificationAndProgress(docId, sourceName, 0.08f, getUnifiedRemainingSeconds())
                    repository.insertDocument(initialDoc.copy(
                        summary = "PDF Engine: Opening document stream and rendering pages for local OCR..."
                    ))
                    val (extractedPages, computedPageCount) = if (pendingFilePath.isNotBlank()) {
                        extractTextFromPdfFile(File(pendingFilePath), docId, sourceName)
                    } else {
                        extractTextFromPdfBase64(imageBase64, docId, sourceName)
                    }
                    pdfPageCount = computedPageCount
                    initialDoc = initialDoc.copy(pageCount = pdfPageCount)
                    repository.insertDocument(initialDoc)
                    
                    pageDetailsList.addAll(extractedPages)
                } else if (fileType.lowercase() == "image") {
                    updateNotificationAndProgress(docId, sourceName, 0.12f, getUnifiedRemainingSeconds())
                    repository.insertDocument(initialDoc.copy(
                        summary = "OCR Analysis: Parsing and transcribing text from document image..."
                    ))

                    try {
                        val bitmap = if (pendingFilePath.isNotBlank()) {
                            BitmapFactory.decodeFile(pendingFilePath)
                        } else if (imageBase64.isNotBlank()) {
                            val decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        } else null

                        if (bitmap != null) {
                            val imagesDir = File(applicationContext.filesDir, "extracted_pdf_images").apply { mkdirs() }
                            val pageImageFile = File(imagesDir, "image_${docId}.jpg")
                            var savedPath: String? = null
                            try {
                                java.io.FileOutputStream(pageImageFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                }
                                savedPath = pageImageFile.absolutePath
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val ocrText = runMlKitOcr(bitmap)
                            updateNotificationAndProgress(docId, sourceName, 0.20f, getUnifiedRemainingSeconds())
                            pageDetailsList.add(PageDetails(pageIndex = 1, text = ocrText, imagePath = savedPath))
                            bitmap.recycle()
                        } else {
                            pageDetailsList.add(PageDetails(pageIndex = 1, text = "Error decoding image bitmap for on-device OCR.", imagePath = null))
                        }
                    } catch (e: Exception) {
                        pageDetailsList.add(PageDetails(pageIndex = 1, text = "Error processing multimodal image document: ${e.message}", imagePath = null))
                    }
                } else if (fileType.lowercase() == "doc") {
                    try {
                        val text = if (pendingFilePath.isNotBlank()) {
                            File(pendingFilePath).readText(Charsets.UTF_8)
                        } else if (imageBase64.isNotBlank()) {
                            val decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                            String(decodedBytes, Charsets.UTF_8)
                        } else ""

                        if (text.startsWith("%PDF")) {
                            updateNotificationAndProgress(docId, sourceName, 0.08f, getUnifiedRemainingSeconds())
                            val (extractedPages, computedPageCount) = if (pendingFilePath.isNotBlank()) {
                                extractTextFromPdfFile(File(pendingFilePath), docId, sourceName)
                            } else {
                                extractTextFromPdfBase64(imageBase64, docId, sourceName)
                            }
                            pdfPageCount = computedPageCount
                            initialDoc = initialDoc.copy(pageCount = pdfPageCount, fileType = "pdf")
                            repository.insertDocument(initialDoc)
                            pageDetailsList.addAll(extractedPages)
                        } else {
                            pageDetailsList.add(PageDetails(pageIndex = 1, text = text, imagePath = null))
                        }
                    } catch (e: Exception) {
                        pageDetailsList.add(PageDetails(pageIndex = 1, text = textContent, imagePath = null))
                    }
                } else {
                    pageDetailsList.add(PageDetails(pageIndex = 1, text = textContent, imagePath = null))
                }

                val combinedExtractedText = pageDetailsList.joinToString("\n") { it.text }
                var wasSuccessful = true

                if (combinedExtractedText.isBlank() || combinedExtractedText.startsWith("Failed") || combinedExtractedText.startsWith("Error") || combinedExtractedText.startsWith("Error decoding")) {
                    wasSuccessful = false
                }

                // Launch brief generation in parallel with the first embedding batch
                val briefJob = if (wasSuccessful && combinedExtractedText.isNotBlank()) {
                    serviceScope.async(Dispatchers.IO) {
                        try {
                            generateDocBrief(combinedExtractedText, llmModelSpec, llmKey, llmLink, llmProvider)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            "Could not generate clinical summary: ${e.message}"
                        }
                    }
                } else {
                    null
                }

                if (wasSuccessful) {
                    val allChunksToProcess = mutableListOf<ChunkPayload>()
                    pageDetailsList.forEach { pageDetails ->
                        val pageChunks = chunkText(pageDetails.text, chunkSize = serviceChunkSize)
                        pageChunks.forEach { txt ->
                            allChunksToProcess.add(ChunkPayload(
                                text = txt,
                                pageIndex = pageDetails.pageIndex,
                                imagePath = pageDetails.imagePath
                            ))
                        }
                    }
                    val totalChunks = allChunksToProcess.size
                    
                    // Retrieve existing embedded chunks to support resumption
                    val existingChunks = repository.getChunksForDocument(docId)
                    val existingChunkCount = existingChunks.size
                    
                    val completedChunks = AtomicInteger(existingChunkCount)
                    
                    val startTime = System.currentTimeMillis()
                    val lastDbUpdateMs = AtomicLong(0L)
                    val averageTaskDurationMs = AtomicLong(1500L)

                    // Parallel chunk optimization
                    val maxConcurrency = minOf(Runtime.getRuntime().availableProcessors(), 8).coerceAtLeast(3)
                    val semaphore = Semaphore(maxConcurrency)

                    // Group all chunks into batches of 96 (safe limit for OpenAI and Gemini)
                    val BATCH_SIZE = 96
                    val chunksToEmbed = allChunksToProcess.filter { chunkPayload ->
                        val existing = existingChunks.any { 
                            it.pageIndex == chunkPayload.pageIndex && it.chunkText == chunkPayload.text 
                        }
                        !existing
                    }
                    
                    val batchedChunks = chunksToEmbed.chunked(BATCH_SIZE)
                    
                    // Send initial correct progress in resumption configurations
                    val baseProgress = if (imageBase64.isNotBlank() || pendingFilePath.isNotBlank()) 0.20f else 0.10f
                    val initialProgressFraction = if (totalChunks > 0) {
                        baseProgress + ((1f - baseProgress - 0.05f) * (existingChunkCount.toFloat() / totalChunks.toFloat()))
                    } else {
                        baseProgress
                    }
                    val elapsedSoFar = ((System.currentTimeMillis() - currentJobStartTime.get()) / 1000).toInt()
                    val totalEstEmbeddingSeconds = (((chunksToEmbed.size + BATCH_SIZE - 1) / BATCH_SIZE) * 3.0).toInt().coerceAtLeast(10)
                    currentJobEstimatedTotalSeconds.set(elapsedSoFar + totalEstEmbeddingSeconds)

                    val initialEstRemainingSeconds = getUnifiedRemainingSeconds()
                    updateNotificationAndProgress(docId, sourceName, initialProgressFraction, initialEstRemainingSeconds)

                    val jobContext = coroutineContext

                    if (chunksToEmbed.isNotEmpty()) {
                        batchedChunks.forEachIndexed { batchIdx, batch ->
                            // Pause/cancel check once per batch
                            while (RagProgressManager.docPaused.value[docId] == true) {
                                if (RagProgressManager.docCancelled.value[docId] == true || !jobContext.isActive) {
                                    throw CancellationException("Index job cancelled by user")
                                }
                                delay(500L)
                            }

                            if (RagProgressManager.docCancelled.value[docId] == true || !jobContext.isActive) {
                                throw CancellationException("Index job cancelled by user")
                            }

                            val batchStartNum = existingChunkCount + batchIdx * BATCH_SIZE
                            val batchEndNum = minOf(totalChunks, existingChunkCount + (batchIdx + 1) * BATCH_SIZE)

                            repository.insertDocument(initialDoc.copy(
                                summary = "Index Vectorization: Embedding chunks ${batchStartNum + 1} to $batchEndNum of $totalChunks..."
                            ))

                            val singleTaskStart = System.currentTimeMillis()
                            var vectors: List<List<Float>> = emptyList()
                            var success = false
                            var attempts = 0
                            var lastException: Exception? = null

                            while (!success && attempts < 3) {
                                attempts++
                                try {
                                    if (embedKey.isNotBlank()) {
                                        vectors = withContext(Dispatchers.IO) {
                                            generateEmbeddingsBatch(
                                                batch.map { it.text }, embedModel, embedKey, embedLink, embedProvider
                                            )
                                        }
                                    } else {
                                        vectors = batch.map { emptyList() }
                                    }
                                    success = true
                                } catch (e: Exception) {
                                    lastException = e
                                    if (e is UnknownHostException || e is SocketTimeoutException || e.message?.contains("connect") == true || e.message?.contains("HTTP") == true) {
                                        delay(1000L * attempts)
                                    } else {
                                        break // Non-retryable
                                    }
                                }
                            }

                            if (success) {
                                val taskTime = System.currentTimeMillis() - singleTaskStart
                                val currentAvg = averageTaskDurationMs.get()
                                val newAvg = (0.8 * currentAvg + 0.2 * taskTime).toLong().coerceIn(200L, 10000L)
                                averageTaskDurationMs.set(newAvg)
                            }

                            if (!success) {
                                // AUTO PAUSE ON NETWORK OR EMBEDDING EXCEPTION
                                RagProgressManager.docPaused.value = RagProgressManager.docPaused.value.toMutableMap().apply {
                                    put(docId, true)
                                }
                                repository.insertDocument(initialDoc.copy(
                                    summary = "Auto-Paused due to network or model issue. Please check your connection and tap Resume.\n\nTechnical details: ${lastException?.message}"
                                ))

                                // Suspend until resumed or cancelled
                                while (RagProgressManager.docPaused.value[docId] == true) {
                                    if (RagProgressManager.docCancelled.value[docId] == true || !jobContext.isActive) {
                                        throw CancellationException("Cancelled")
                                    }
                                    delay(500L)
                                }

                                val resumeStart = System.currentTimeMillis()
                                // Re-attempt after resume
                                if (embedKey.isNotBlank()) {
                                    vectors = withContext(Dispatchers.IO) {
                                        generateEmbeddingsBatch(
                                            batch.map { it.text }, embedModel, embedKey, embedLink, embedProvider
                                        )
                                    }
                                } else {
                                    vectors = batch.map { emptyList() }
                                }
                                val resumeTime = System.currentTimeMillis() - resumeStart
                                val currentAvg = averageTaskDurationMs.get()
                                val newAvg = (0.8 * currentAvg + 0.2 * resumeTime).toLong().coerceIn(200L, 10000L)
                                averageTaskDurationMs.set(newAvg)
                            }

                            val chunks = batch.zip(vectors).map { (payload, vec) ->
                                DocumentChunk(
                                    docId = docId,
                                    docSource = sourceName,
                                    chunkText = payload.text,
                                    embeddingJson = JSONArray(vec).toString(),
                                    pageIndex = payload.pageIndex,
                                    imagePath = payload.imagePath
                                )
                            }
                            try {
                                repository.insertDocumentChunks(chunks)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val completed = existingChunkCount + (batchIdx + 1) * BATCH_SIZE
                            val progressFraction = baseProgress + ((1f - baseProgress - 0.05f) * (completed.toFloat() / totalChunks.toFloat()))

                            // Dynamically shift to real-time estimated time required based on actual processing speed
                            val elapsedNow = ((System.currentTimeMillis() - currentJobStartTime.get()) / 1000).toInt()
                            val remainingBatches = batchedChunks.size - (batchIdx + 1)
                            val remainingEstSeconds = (remainingBatches * (averageTaskDurationMs.get() / 1000.0)).toInt()
                            currentJobEstimatedTotalSeconds.set(elapsedNow + remainingEstSeconds)

                            updateNotificationAndProgress(docId, sourceName, progressFraction.coerceAtMost(0.95f), getUnifiedRemainingSeconds())
                        }
                    }
                }

                val finalSummary = if (wasSuccessful) {
                    if (combinedExtractedText.isNotBlank()) {
                        briefJob?.await() ?: "Summary generation failed or timed out."
                    } else {
                        "No accessible clinical reference standards found in $sourceName."
                    }
                } else {
                    "Document processing did not succeed. OCR/Text Extraction encountered an error:\n\n" + 
                        if (combinedExtractedText.isBlank()) "No readable text content was detected in the file." else combinedExtractedText
                }

                // Finished successfully, mark as READY
                val finalDoc = initialDoc.copy(
                    status = if (wasSuccessful) "READY" else "ERROR",
                    summary = finalSummary
                )
                repository.insertDocument(finalDoc)

            } catch (e: CancellationException) {
                // Job was cancelled explicitly by the user, OR system stopped the coroutine
                withContext(NonCancellable) {
                    if (RagProgressManager.docCancelled.value[docId] == true) {
                        repository.deleteDocumentById(docId)
                        repository.deleteChunksByDocId(docId)
                    } else {
                        // Retain document and the cached chunks!
                        repository.insertDocument(initialDoc.copy(
                            status = "PROCESSING",
                            summary = "Indexing paused or interrupted. Ready to resume."
                        ))
                    }
                }
            } catch (e: Exception) {
                // Handle parsing errors and network errors directly with no user status tag prefix
                val technicalError = e.message ?: "Unknown process pipeline issue"
                val finalDoc = DocItem(
                    id = docId,
                    fileSource = sourceName,
                    fileType = fileType,
                    r2Url = "https://pub-r2.mediagent.net/$docId/$sourceName",
                    status = "ERROR",
                    summary = "Could not generate vector embeddings for document chunks. Please verify your internet connection or check the Embedding model and API Key specifiers in Settings.\n\nDetails: $technicalError",
                    pageCount = pageCount,
                    primaryConditions = "Primary Care, Outpatient Triage",
                    categoryName = categoryName,
                    categoryColor = categoryColor
                )
                repository.insertDocument(finalDoc)
            } finally {
                if (pendingFilePath.isNotBlank()) {
                    try {
                        val tf = File(pendingFilePath)
                        if (tf.exists()) tf.delete()
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
                // Clear active states
                RagProgressManager.docProgress.value = RagProgressManager.docProgress.value.toMutableMap().apply {
                    remove(docId)
                }
                RagServicePayload.clear(docId)
                activeJobs.remove(docId)
                checkStopSelf()
            }
        }
        activeJobs[docId] = job
    }

    private fun resumeJob(docId: String, sourceName: String, fileType: String, pageCount: Int, categoryName: String, categoryColor: String) {
        RagProgressManager.docPaused.value = RagProgressManager.docPaused.value.toMutableMap().apply {
            put(docId, false)
        }
        val activeJob = activeJobs[docId]
        if (activeJob == null || !activeJob.isActive) {
            startIndexingJob(docId, sourceName, fileType, pageCount, categoryName, categoryColor)
        } else {
            val currentPair = RagProgressManager.docProgress.value[docId] ?: Pair(0.10f, 15)
            updateNotificationAndProgress(docId, sourceName, currentPair.first, currentPair.second)
        }
    }

    private fun cancelJob(docId: String) {
        RagProgressManager.docCancelled.value = RagProgressManager.docCancelled.value.toMutableMap().apply {
            put(docId, true)
        }
        activeJobs[docId]?.cancel()
        activeJobs.remove(docId)
        
        serviceScope.launch {
            repository.deleteDocumentById(docId)
            checkStopSelf()
        }
    }

    private fun checkStopSelf() {
        if (activeJobs.isEmpty()) {
            releaseLocks()
            stopSelf()
        }
    }

    private suspend fun runMlKitOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resume("Error during ML Kit OCR extraction: ${e.message}")
                }
        } catch (e: Exception) {
            continuation.resume("Error initializing Google ML OCR engine: ${e.message}")
        }
    }

    data class OcrResult(val text: String, val textBlocks: List<android.graphics.Rect>)

    private suspend fun runMlKitOcrWithBoxes(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val boxes = visionText.textBlocks.mapNotNull { it.boundingBox }
                    continuation.resume(OcrResult(visionText.text, boxes))
                }
                .addOnFailureListener { e ->
                    continuation.resume(OcrResult("Error during ML Kit OCR extraction: ${e.message}", emptyList()))
                }
        } catch (e: Exception) {
            continuation.resume(OcrResult("Error initializing Google ML OCR engine: ${e.message}", emptyList()))
        }
    }

    private fun detectImageBlocks(
        bitmap: Bitmap,
        textRects: List<android.graphics.Rect>,
        docId: String,
        pageIndex: Int,
        imagesDir: File
    ): List<String> {
        val savedPaths = mutableListOf<String>()
        val origW = bitmap.width
        val origH = bitmap.height
        
        // Scale down to a optimized resolution for extremely fast scanning (1/4th the previous resolution)
        val targetW = 180
        val targetH = 250
        val scaledBitmap = try {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } catch (e: Exception) {
            return emptyList()
        }
        
        val actualW = scaledBitmap.width
        val actualH = scaledBitmap.height
        
        val scaleX = actualW.toFloat() / origW
        val scaleY = actualH.toFloat() / origH
        
        val scaledTextRects = textRects.map { rect ->
            android.graphics.Rect(
                (rect.left * scaleX).toInt(),
                (rect.top * scaleY).toInt(),
                (rect.right * scaleX).toInt(),
                (rect.bottom * scaleY).toInt()
            )
        }
        
        val pixels = IntArray(actualW * actualH)
        try {
            scaledBitmap.getPixels(pixels, 0, actualW, 0, 0, actualW, actualH)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Use single contiguous flat BooleanArray instead of multi-dimensional Array of Arrays. Reduces heap allocations by 99%.
        val isContent = BooleanArray(actualW * actualH)
        for (x in 0 until actualW) {
            for (y in 0 until actualH) {
                val idx = y * actualW + x
                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                
                // Keep background pixels as false, and any actual line/drawing/color pixel as true
                isContent[idx] = (r < 240 || g < 240 || b < 240)
            }
        }
        
        // Erase text blocks from the binary content grid so we ignore standard text characters
        for (rect in scaledTextRects) {
            // Add safety padding around text to discard character tails or noise
            val left = (rect.left - 2).coerceIn(0, actualW - 1)
            val top = (rect.top - 2).coerceIn(0, actualH - 1)
            val right = (rect.right + 2).coerceIn(0, actualW - 1)
            val bottom = (rect.bottom + 2).coerceIn(0, actualH - 1)
            for (tx in left..right) {
                for (ty in top..bottom) {
                    isContent[ty * actualW + tx] = false
                }
            }
        }
        
        // Find connected components using high-performance primitive flood fill (prevents GC-overhead crash)
        val visited = BooleanArray(actualW * actualH)
        val components = mutableListOf<android.graphics.Rect>()
        val dx = intArrayOf(-1, 1, 0, 0)
        val dy = intArrayOf(0, 0, -1, 1)
        
        for (x in 0 until actualW) {
            for (y in 0 until actualH) {
                val startIdx = y * actualW + x
                if (isContent[startIdx] && !visited[startIdx]) {
                    var minX = x
                    var maxX = x
                    var minY = y
                    var maxY = y
                    var pixelCount = 0
                    
                    val queue = java.util.ArrayDeque<Int>()
                    queue.add(startIdx)
                    visited[startIdx] = true
                    
                    while (!queue.isEmpty()) {
                        val curr = queue.poll() ?: break
                        val cx = curr % actualW
                        val cy = curr / actualW
                        pixelCount++
                        
                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy
                        
                        for (i in 0..3) {
                            val nx = cx + dx[i]
                            val ny = cy + dy[i]
                            if (nx in 0 until actualW && ny in 0 until actualH) {
                                val nIdx = ny * actualW + nx
                                if (isContent[nIdx] && !visited[nIdx]) {
                                    visited[nIdx] = true
                                    queue.add(nIdx)
                                }
                            }
                        }
                    }
                    
                    val compW = maxX - minX + 1
                    val compH = maxY - minY + 1
                    
                    // Filter components that represent actual drawings, illustrations or charts rather than noise.
                    // Threshold is scaled down proportionally to the lower work resolution.
                    if (compW >= 15 && compH >= 15 && pixelCount > 30) {
                        // Apply expansion padding to capture diagram boundaries accurately
                        val padLeft = (minX - 3).coerceIn(0, actualW - 1)
                        val padTop = (minY - 3).coerceIn(0, actualH - 1)
                        val padRight = (maxX + 3).coerceIn(0, actualW - 1)
                        val padBottom = (maxY + 3).coerceIn(0, actualH - 1)
                        components.add(android.graphics.Rect(padLeft, padTop, padRight, padBottom))
                    }
                }
            }
        }
        
        // Merge adjacent or overlapping components with a high-performance 1-pass pass-through list
        val rectList = mutableListOf<android.graphics.Rect>()
        for (comp in components) {
            var mergedIndex = -1
            for (i in 0 until rectList.size) {
                val existing = rectList[i]
                val expandedExisting = android.graphics.Rect(
                    existing.left - 10,
                    existing.top - 10,
                    existing.right + 10,
                    existing.bottom + 10
                )
                if (android.graphics.Rect.intersects(expandedExisting, comp)) {
                    mergedIndex = i
                    break
                }
            }
            if (mergedIndex != -1) {
                val existing = rectList[mergedIndex]
                rectList[mergedIndex] = android.graphics.Rect(
                    kotlin.math.min(existing.left, comp.left),
                    kotlin.math.min(existing.top, comp.top),
                    kotlin.math.max(existing.right, comp.right),
                    kotlin.math.max(existing.bottom, comp.bottom)
                )
            } else {
                rectList.add(comp)
            }
        }
        
        // Clean up scaled work bitmap
        try {
            scaledBitmap.recycle()
        } catch (e: Exception) {}
        
        // Crop detected figures out of the original full-high resolution bitmap and save them
        rectList.forEachIndexed { index, rect ->
            val origLeft = (rect.left / scaleX).toInt().coerceIn(0, origW - 1)
            val origTop = (rect.top / scaleY).toInt().coerceIn(0, origH - 1)
            val origRight = (rect.right / scaleX).toInt().coerceIn(0, origW - 1)
            val origBottom = (rect.bottom / scaleY).toInt().coerceIn(0, origH - 1)
            
            val cropW = origRight - origLeft
            val cropH = origBottom - origTop
            
            if (cropW > 40 && cropH > 40) {
                try {
                    val cropBitmap = Bitmap.createBitmap(bitmap, origLeft, origTop, cropW, cropH)
                    val cropFile = File(imagesDir, "pdf_${docId}_page_${pageIndex}_img_${index + 1}.jpg")
                    java.io.FileOutputStream(cropFile).use { out ->
                        cropBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    savedPaths.add(cropFile.absolutePath)
                    cropBitmap.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        return savedPaths
    }

    data class PageDetails(val pageIndex: Int, val text: String, val imagePath: String? = null)
    data class ChunkPayload(val text: String, val pageIndex: Int, val imagePath: String?)

    private suspend fun extractTextFromPdfFile(tempFile: File, docId: String, sourceName: String): Pair<List<PageDetails>, Int> {
        var pageCount = 0
        try {
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount
            
            val imagesDir = File(applicationContext.filesDir, "extracted_pdf_images").apply { mkdirs() }
            
            // Render pages sequentially (PdfRenderer not thread-safe), but pipeline OCR+detection
            data class RenderedPage(val index: Int, val bitmap: Bitmap)
            val pageDetailsArray = Array<PageDetails?>(pageCount) { null }
            
            // Use a channel to pipeline: render -> process
            val renderChannel = kotlinx.coroutines.channels.Channel<RenderedPage>(capacity = 2)
            
            // Producer: renders pages one at a time on the main caller scope (sequentially)
            val renderJob = serviceScope.launch {
                try {
                    for (i in 0 until pageCount) {
                        yield()
                        val page = renderer.openPage(i)
                        
                        // Determine bitmap size
                        val pageW = page.width
                        val pageH = page.height
                        val maxDim = maxOf(pageW, pageH)
                        val scaleFactor = when {
                            maxDim <= 0 -> 1.0f
                            maxDim > 1200 -> 1200f / maxDim // Downscale very large pages
                            maxDim < 800 -> 1.5f            // Warm upscale for small pages/scans to improve OCR
                            else -> 1.2f                     // Balanced baseline scale
                        }
                        val width = (pageW * scaleFactor).toInt().coerceAtLeast(1)
                        val height = (pageH * scaleFactor).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        
                        // Fill static white background
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        
                        renderChannel.send(RenderedPage(i, bmp))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    renderChannel.close()
                }
            }
            
            // Consumer: runs OCR + detection on each rendered bitmap (can overlap with next render)
            val processJob = serviceScope.launch(Dispatchers.Default) {
                try {
                    for (rendered in renderChannel) {
                        val i = rendered.index
                        val bitmap = rendered.bitmap
                        
                        // Execute standard ML Kit text recognition
                        val ocrResult = runMlKitOcrWithBoxes(bitmap)
                        val textContent = if (ocrResult.text.isNotBlank() && !ocrResult.text.startsWith("Error")) ocrResult.text else ""
                        
                        // Pre-render and save the full page JPEG
                        try {
                            val fullPageFile = File(imagesDir, "pdf_${docId}_page_${i + 1}_full.jpg")
                            java.io.FileOutputStream(fullPageFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        // Skip flood fill on text-dense pages to save CPU & memory
                        val extractedImages = if (ocrResult.textBlocks.size > 35) {
                            emptyList()
                        } else {
                            detectImageBlocks(bitmap, ocrResult.textBlocks, docId, i + 1, imagesDir)
                        }
                        
                        val savedPath = if (extractedImages.isNotEmpty()) extractedImages.joinToString(";") else null
                        
                        pageDetailsArray[i] = PageDetails(pageIndex = i + 1, text = textContent, imagePath = savedPath)
                        bitmap.recycle()
                        
                        // Live progress reporting during PDF page-by-page OCR extraction (from 8% to 20%)
                        val progressFraction = if (pageCount > 0) ((i + 1).toFloat() / pageCount) else 1f
                        val extractionProgress = 0.08f + progressFraction * 0.12f
                        
                        try {
                            val elapsedSoFar = ((System.currentTimeMillis() - currentJobStartTime.get()) / 1000.0)
                            val estOcrTimePerPage = elapsedSoFar / (i + 1)
                            val remainingPages = pageCount - (i + 1)
                            val remainingOcrSeconds = (remainingPages * estOcrTimePerPage).toInt()
                            val estChunks = pageCount * 4
                            val remainingEmbeddingSeconds = (((estChunks + 95) / 96) * 3).toInt().coerceAtLeast(2)
                            val totalNewEst = (elapsedSoFar + remainingOcrSeconds + remainingEmbeddingSeconds + 4).toInt()
                            currentJobEstimatedTotalSeconds.set(totalNewEst.coerceAtLeast(10))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        updateNotificationAndProgress(docId, sourceName, extractionProgress, getUnifiedRemainingSeconds())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            renderJob.join()
            processJob.join()
            
            renderer.close()
            pfd.close()
            
            val finalCount = if (pageCount > 0) pageCount else 1
            return Pair(pageDetailsArray.filterNotNull().toList(), finalCount)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val finalCount = if (pageCount > 0) pageCount else 1
        return Pair(emptyList(), finalCount)
    }

    private suspend fun extractTextFromPdfBase64(pdfBase64: String, docId: String, sourceName: String): Pair<List<PageDetails>, Int> {
        val tempFile = File(cacheDir, "temp_render.pdf")
        try {
            val pdfBytes = Base64.decode(pdfBase64, Base64.DEFAULT)
            tempFile.writeBytes(pdfBytes)
            return extractTextFromPdfFile(tempFile, docId, sourceName)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        return Pair(emptyList(), 1)
    }

    private fun generateDocBrief(text: String, model: String, apiKey: String, apiLink: String, provider: String): String {
        if (text.isBlank()) return "Empty document content."
        val keyToUse = if (apiKey.isBlank() || apiKey == "mock_key" || apiKey == "your_gemini_api_key") {
            try {
                com.example.BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        } else {
            apiKey
        }
        if (keyToUse.isBlank()) {
            return if (text.length > 250) text.substring(0, 250) + "..." else text
        }
        val baseUrl = if (apiLink.isBlank()) "https://generativelanguage.googleapis.com" else apiLink.trim().removeSuffix("/")
        val p = provider.lowercase()
        val u = baseUrl.lowercase()
        val isOai = p.contains("openai") || u.contains("openai") || u.contains("/v1") || u.contains("local")

        val url = if (isOai) {
            if (baseUrl.endsWith("chat/completions")) baseUrl else "$baseUrl/chat/completions"
        } else {
            "$baseUrl/v1beta/models/$model:generateContent?key=$keyToUse"
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val promptText = "Provide a highly detailed, comprehensive clinical summary (around 150-250 words) of this medical/guideline document, including primary target conditions, specific diagnostic index themes, and any multilingual keywords or symptoms (e.g., in English, Spanish, Hindi/Hinglish, etc.) covered in this document. Do NOT output markdown, lists, or headers. Output only the detailed summary plain text.\n\nDocument snippet:\n${if (text.length > 3000) text.take(3000) else text}"

        val requestBody = if (isOai) {
            val req = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", promptText)
                    })
                })
                put("max_tokens", 500)
            }
            req.toString()
        } else {
            val req = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                        })
                    })
                })
            }
            req.toString()
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody(mediaType))
            .apply {
                if (isOai) {
                    addHeader("Authorization", "Bearer $keyToUse")
                }
            }
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    val json = JSONObject(bodyStr)
                    if (isOai) {
                        json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } else {
                        json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .trim()
                    }
                } else {
                    buildLocalFallbackBrief(text)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            buildLocalFallbackBrief(text)
        }
    }

    private fun buildLocalFallbackBrief(text: String): String {
        if (text.isBlank()) return "Empty clinical reference guideline."
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val titleLine = lines.firstOrNull { it.length > 5 } ?: "Clinical Document Reference"
        
        val medicalKeywords = listOf(
            "acupuncture", "stimulation", "color", "therapy", "points", "dosage", 
            "contraindications", "vitals", "kidney", "liver", "heart", "diet", 
            "exercise", "treatment", "protocol", "medication", "herbs"
        )
        val matchedWords = medicalKeywords.filter { text.lowercase(java.util.Locale.getDefault()).contains(it) }
        val tagsString = if (matchedWords.isNotEmpty()) {
            "Topics: " + matchedWords.distinct().take(6).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        } else {
            "Topics: General Physical Guidelines"
        }
        
        val snippet = if (text.length > 180) text.substring(0, 180).replace(Regex("\\s+"), " ") + "..." else text
        return "Clinical Reference Book: \"$titleLine\"\nMatched $tagsString.\nSnippet: $snippet"
    }

    private fun generateEmbeddingDirect(text: String, model: String, apiKey: String, apiLink: String, provider: String): List<Float> {
        val keyToUse = if (apiKey.isBlank() || apiKey == "mock_key" || apiKey == "your_gemini_api_key") {
            try {
                com.example.BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        } else {
            apiKey
        }
        val client = httpClient
        val baseUrl = if (apiLink.isBlank()) "https://generativelanguage.googleapis.com" else apiLink.trim().removeSuffix("/")
        
        val p = provider.lowercase()
        val u = baseUrl.lowercase()
        val isOai = p.contains("openai") || u.contains("openai") || u.contains("/v1") || u.contains("local")

        val url = if (isOai) {
            if (baseUrl.endsWith("embeddings")) baseUrl else "$baseUrl/embeddings"
        } else {
            "$baseUrl/v1beta/models/$model:embedContent?key=$keyToUse"
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val jsonRequest = if (isOai) {
            JSONObject().apply {
                put("model", model)
                put("input", text)
            }
        } else {
            JSONObject().apply {
                put("content", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    })
                })
            }
        }

        val body = jsonRequest.toString().toRequestBody(mediaType)
        val reqBuilder = Request.Builder().url(url).post(body)
        if (isOai) {
            reqBuilder.addHeader("Authorization", "Bearer $keyToUse")
            reqBuilder.addHeader("Content-Type", "application/json")
        }
        val request = reqBuilder.build()
        
        client.newCall(request).execute().use { response ->
            val resBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                if (resBody.isBlank()) throw IOException("Empty response body received from Embedding API.")
                val resJson = JSONObject(resBody)
                val valuesArray = if (isOai) {
                    resJson.getJSONArray("data").getJSONObject(0).getJSONArray("embedding")
                } else {
                    val embeddingObj = resJson.getJSONObject("embedding")
                    embeddingObj.getJSONArray("values")
                }
                val result = mutableListOf<Float>()
                for (i in 0 until valuesArray.length()) {
                    result.add(valuesArray.getDouble(i).toFloat())
                }
                return result
            } else {
                val errorDetails = if (resBody.isNotBlank()) {
                    try {
                        val errObj = JSONObject(resBody)
                        if (errObj.has("error")) {
                            errObj.getJSONObject("error").getString("message")
                        } else {
                            resBody
                        }
                    } catch (e: Exception) {
                        resBody
                    }
                } else "Unreachable custom API host or SSL error."
                throw IOException("HTTP ${response.code}: $errorDetails")
            }
        }
    }

    private fun generateEmbeddingsBatch(
        texts: List<String>, model: String, apiKey: String, apiLink: String, provider: String
    ): List<List<Float>> {
        val keyToUse = if (apiKey.isBlank() || apiKey == "mock_key" || apiKey == "your_gemini_api_key") {
            try {
                com.example.BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        } else {
            apiKey
        }
        val baseUrl = if (apiLink.isBlank()) "https://generativelanguage.googleapis.com" else apiLink.trim().removeSuffix("/")
        
        val p = provider.lowercase()
        val u = baseUrl.lowercase()
        val isOai = p.contains("openai") || u.contains("openai") || u.contains("/v1") || u.contains("local")

        val url = if (isOai) {
            if (baseUrl.endsWith("embeddings")) baseUrl else "$baseUrl/embeddings"
        } else {
            "$baseUrl/v1beta/models/$model:batchEmbedContents?key=$keyToUse"
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()

        val jsonRequest = if (isOai) {
            JSONObject().apply {
                put("model", model)
                put("input", JSONArray(texts))
            }
        } else {
            val requests = JSONArray()
            for (text in texts) {
                requests.put(JSONObject().apply {
                    put("model", "models/$model")
                    put("content", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", text) })
                        })
                    })
                })
            }
            JSONObject().apply {
                put("requests", requests)
            }
        }

        val body = jsonRequest.toString().toRequestBody(mediaType)
        val reqBuilder = Request.Builder().url(url).post(body)
        if (isOai) {
            reqBuilder.addHeader("Authorization", "Bearer $keyToUse")
            reqBuilder.addHeader("Content-Type", "application/json")
        }
        val request = reqBuilder.build()

        return httpClient.newCall(request).execute().use { response ->
            val resBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                if (resBody.isBlank()) throw IOException("Empty response body received from Batch Embedding API.")
                val resJson = JSONObject(resBody)
                if (isOai) {
                    val data = resJson.getJSONArray("data")
                    (0 until data.length()).map { i ->
                        val arr = data.getJSONObject(i).getJSONArray("embedding")
                        val floatList = mutableListOf<Float>()
                        for (j in 0 until arr.length()) {
                            floatList.add(arr.getDouble(j).toFloat())
                        }
                        floatList
                    }
                } else {
                    val embeddings = resJson.getJSONArray("embeddings")
                    (0 until embeddings.length()).map { i ->
                        val arr = embeddings.getJSONObject(i).getJSONArray("values")
                        val floatList = mutableListOf<Float>()
                        for (j in 0 until arr.length()) {
                            floatList.add(arr.getDouble(j).toFloat())
                        }
                        floatList
                    }
                }
            } else {
                val errorDetails = if (resBody.isNotBlank()) {
                    try {
                        val errObj = JSONObject(resBody)
                        if (errObj.has("error")) {
                            errObj.getJSONObject("error").getString("message")
                        } else {
                            resBody
                        }
                    } catch (e: Exception) {
                        resBody
                    }
                } else "Unreachable custom API host or SSL error."
                throw IOException("Embedding API returned error ($response): $errorDetails")
            }
        }
    }

    private fun chunkText(text: String, chunkSize: Int = 1000, overlap: Int = 200): List<String> {
        val chunks = mutableListOf<String>()
        if (text.isBlank()) return chunks
        var startIndex = 0
        while (startIndex < text.length) {
            val endIndex = kotlin.math.min(startIndex + chunkSize, text.length)
            chunks.add(text.substring(startIndex, endIndex))
            startIndex += chunkSize - overlap
            if (startIndex >= text.length || chunkSize <= overlap) break
        }
        return chunks
    }

    private val lastNotificationUpdateMs = java.util.concurrent.atomic.AtomicLong(0L)
    private val notificationLock = Any()

    private fun updateNotificationAndProgress(docId: String, sourceName: String, progress: Float, remainingSec: Int) {
        val now = System.currentTimeMillis()
        val lastTime = lastNotificationUpdateMs.get()
        
        // Throttle progress flow updates to at most once per second to prevent main thread Compose recomposition freezes
        if (progress >= 0.98f || now - lastTime >= 1000L) {
            lastNotificationUpdateMs.set(now)
            
            synchronized(notificationLock) {
                RagProgressManager.docProgress.value = RagProgressManager.docProgress.value.toMutableMap().apply {
                    put(docId, Pair(progress, remainingSec))
                }
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val isPaused = RagProgressManager.docPaused.value[docId] == true
            
            val formattedTime = if (remainingSec > 0) formatDuration(remainingSec) else "estimating"
            val desc = if (isPaused) {
                "Paused: $sourceName ($formattedTime remaining)"
            } else {
                "Indexing: $sourceName ($formattedTime remaining)"
            }
            
            try {
                notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(desc, progress))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildProgressNotification(text: String, progress: Float): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("RAG Index Pipeline Active")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, (progress * 100).toInt(), false)
            .setCategory(Notification.CATEGORY_SERVICE)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RAG Document Indexing Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status and estimations of clinic vector indexing"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            textRecognizer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceJob.cancel()
        releaseLocks()
        super.onDestroy()
    }
}

fun formatDuration(secondsInput: Int): String {
    if (secondsInput <= 0) return "0s"
    val h = secondsInput / 3600
    val m = (secondsInput % 3600) / 60
    val s = secondsInput % 60
    return when {
        h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
        m > 0 -> if (s > 0) "${m}m ${s}s" else "${m}m"
        else -> "${s}s"
    }
}
