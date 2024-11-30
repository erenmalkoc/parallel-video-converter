package dev.erenmalkoc.asyncvideoconverter.ui


import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class ConverterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private val _convertedFiles = MutableLiveData<List<File>>()
    val convertedFiles: LiveData<List<File>> get() = _convertedFiles

    fun setFilePaths(filePaths: List<String>) {
        _uiState.value = uiState.value.copy(filePaths = filePaths)
    }

    fun clearFilePaths() {
        _uiState.value = _uiState.value.copy(filePaths = listOf(), conversionInProgress = false, conversionSuccess = null)
    }

    suspend fun convertVideosToMp3Parallel(
        uris: List<Uri>,
        contentResolver: ContentResolver,
        outputDir: File
    ): List<File?> {
        return withContext(Dispatchers.IO) {
            uris.map { uri ->
                async {
                    convertSingleVideoToMp3(uri, contentResolver, outputDir)
                }
            }.awaitAll()
        }
    }

    private suspend fun convertSingleVideoToMp3(
        uri: Uri,
        contentResolver: ContentResolver,
        outputDir: File
    ): File? {
        val outputFile = File(outputDir, "converted_audio_${System.currentTimeMillis()}.mp3")
        val inputFilePath = contentResolver.openInputStream(uri)?.use { inputStream ->
            val tempInputFile = File(outputDir, "temp_input_${System.currentTimeMillis()}.mp4")
            tempInputFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            tempInputFile.absolutePath
        } ?: return null

        val commandString = "-i $inputFilePath -vn -ar 44100 -ac 2 -b:a 192k ${outputFile.absolutePath}"
        val ffmpegResult = FFmpegKit.execute(commandString)

        return if (ffmpegResult.returnCode.isValueSuccess) {
            outputFile
        } else {
            null
        }
    }

    fun startConversion(context: Context, uris: List<Uri>, outputDir: File, contentResolver: ContentResolver) {
        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(conversionInProgress = true, conversionSuccess = null)

            val outputFiles = convertVideosToMp3Parallel(
                uris = uris,
                contentResolver = contentResolver,
                outputDir = outputDir
            )


            if (outputFiles.isNotEmpty() && outputFiles.all { it != null }) {
                outputFiles.filterNotNull().forEach { file ->
                    addFileToMediaLibrary(context, file)
                    Log.d("BrowseFilesScreen", "File saved: ${file.absolutePath}")
                }
                _uiState.value = _uiState.value.copy(conversionInProgress = false, conversionSuccess = true)
            } else {
                Log.e("BrowseFilesScreen", "Conversion failed for some files.")
                _uiState.value = _uiState.value.copy(conversionInProgress = false, conversionSuccess = false)
            }
        }
    }



    fun addFileToMediaLibrary(context: Context, file: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
        }

        val uri =
            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            context.contentResolver.openOutputStream(it).use { outputStream ->
                file.inputStream().copyTo(outputStream!!)
            }
        }
    }

}
