package dev.erenmalkoc.asyncvideoconverter

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.erenmalkoc.asyncvideoconverter.ui.screen.BrowseFilesScreen
import dev.erenmalkoc.asyncvideoconverter.ui.theme.AsyncVideoConverterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AsyncVideoConverterTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    BrowseFilesScreen(onBrowseClicked = { uri ->
                        uri?.let {
                            val filePath = getFilePathFromUri(this@MainActivity, it)
                            println("file path: $filePath")
                        } ?: println("no file selected")
                    })
                }

            }
        }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        var filePath: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                filePath = if (index >= 0) it.getString(index) else null
            }
        }
        return filePath
    }
}



