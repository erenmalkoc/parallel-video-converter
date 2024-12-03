package dev.erenmalkoc.asyncvideoconverter.ui

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class ConverterViewModel : ViewModel() {

    private val _progressState = MutableStateFlow<List<Float>>(emptyList())
    val progressState: StateFlow<List<Float>> = _progressState.asStateFlow()

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private val _convertedFiles = MutableLiveData<List<File>>()
    val convertedFiles: LiveData<List<File>> get() = _convertedFiles

    fun setFilePaths(filePaths: List<String>) {
        _uiState.value = _uiState.value.copy(filePaths = filePaths)
    }

    fun clearFilePaths() {
        _uiState.value = _uiState.value.copy(
            filePaths = listOf(),
            conversionInProgress = false,
            conversionSuccess = null
        )
    }

    fun initializeProgressState(size: Int) {
        _progressState.value = List(size) { 0f }
    }


    private fun updateProgress(index: Int, progress: Float) {

        val currentProgress = _progressState.value.toMutableList()
        if (index in currentProgress.indices) {
            currentProgress[index] = progress
            _progressState.value = currentProgress
        }
    }


    fun startConversion(
        context: Context,
        uris: List<Uri>,
        outputDir: File,
        contentResolver: ContentResolver
    ) {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(conversionInProgress = true, conversionSuccess = null)

            initializeProgressState(uris.size)

            val outputFiles = withContext(Dispatchers.IO) {
                uris.mapIndexed { index, uri ->
                    async {
                        convertSingleVideoToMp3(uri, contentResolver, outputDir, index)

                    }
                }.awaitAll()
            }


            val successfulFiles = outputFiles.filterNotNull()
            if (successfulFiles.size == uris.size) {
                successfulFiles.forEach { addFileToMediaLibrary(context, it) }
                _uiState.value =
                    _uiState.value.copy(conversionInProgress = false, conversionSuccess = true)
                _convertedFiles.postValue(successfulFiles)
            } else {
                Log.e("ConverterViewModel", "Some files failed to convert.")
                _uiState.value =
                    _uiState.value.copy(conversionInProgress = false, conversionSuccess = false)
            }
        }
    }

    private suspend fun convertSingleVideoToMp3(
        uri: Uri,
        contentResolver: ContentResolver,
        outputDir: File,
        index: Int
    ): File? {
        val tempInputFile = File(outputDir, "temp_input_${System.currentTimeMillis()}.mp4")
        val outputFile = File(outputDir, "converted_audio_${System.currentTimeMillis()}.mp3")

        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    tempInputFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw Exception("Failed to copy input stream.")

                val totalDurationMs = getVideoDurationUsingTempFile(contentResolver, uri)

                val commandString =
                    "-i ${tempInputFile.absolutePath} -vn -ar 44100 -ac 2 -b:a 192k ${outputFile.absolutePath}"
                Log.d("FFmpegCommand", "Command: $commandString")

                val isSuccess = executeFFmpegCommandAsync(commandString, index, totalDurationMs)
                if (isSuccess) {
                    Log.d("ConverterViewModel", "FFmpeg command completed successfully.")
                } else {
                    Log.e("ConverterViewModel", "FFmpeg command failed.")
                }

                if (outputFile.exists()) {
                    outputFile
                } else {
                    Log.e("ConverterViewModel", "Output file not created: ${outputFile.absolutePath}")
                    null
                }
            } catch (e: Exception) {
                Log.e("ConverterViewModel", "Error during conversion: ${e.message}")
                null
            } finally {
                if (tempInputFile.exists()) {
                    tempInputFile.delete()
                }
            }
        }
    }


    suspend fun getVideoDurationUsingTempFile(contentResolver: ContentResolver, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        val tempFile = File.createTempFile("temp_video", ".mp4")

        return withContext(Dispatchers.IO) {
            try {

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw Exception("Failed to copy input stream to temporary file.")


                retriever.setDataSource(tempFile.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationStr?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                Log.e("getVideoDurationUsingTempFile", "Error getting video duration: ${e.message}")
                0L
            } finally {
                retriever.release()
                tempFile.delete()
            }
        }
    }


    suspend fun executeFFmpegCommandAsync(
        command: String,
        index: Int,
        totalDurationMs: Long
    ): Boolean {
        return suspendCoroutine { continuation ->
            FFmpegKit.executeAsync(command,
                { session ->
                    val returnCode = session.returnCode
                    val logs = session.allLogsAsString
                    Log.d("FFmpegKitSession", "Session completed with return code: $returnCode")
                    Log.d("FFmpegLogs", logs)

                    if (returnCode.isValueSuccess) {
                        viewModelScope.launch(Dispatchers.Main) {
                            updateProgress(index, 100f)
                        }
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                },
                { log ->
                    Log.d("FFmpegKitLog", log.message)
                },
                { statistics ->
                    val currentTimeMs = statistics.time
                    if (totalDurationMs > 0) {
                        val progress = (currentTimeMs.toFloat() / totalDurationMs.toFloat()) * 100


                        viewModelScope.launch(Dispatchers.Main) {
                            updateProgress(index, progress)
                        }

                        Log.d("ConverterViewModel", "İlerleme durumu: $progress%")
                    }
                }
            )
        }
    }

    private fun addFileToMediaLibrary(context: Context, file: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
        }

        context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().copyTo(outputStream)
                }
            } ?: Log.e("ConverterViewModel", "Failed to add file to media library.")
    }
}
