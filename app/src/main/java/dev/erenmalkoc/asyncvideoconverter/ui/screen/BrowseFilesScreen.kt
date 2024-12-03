package dev.erenmalkoc.asyncvideoconverter.ui.screen

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.erenmalkoc.asyncvideoconverter.R
import dev.erenmalkoc.asyncvideoconverter.ui.ConverterUiState
import dev.erenmalkoc.asyncvideoconverter.ui.ConverterViewModel



@Composable
fun BrowseFilesScreen(
    converterViewModel: ConverterViewModel = viewModel(),
    onBrowseClicked: (Uri?) -> Unit,
) {
    val mediumPadding = dimensionResource(R.dimen.padding_medium)
    val converterUiState by converterViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(mediumPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_autorenew),
            contentDescription = "Conversion Icon",
            modifier = Modifier.size(80.dp).padding(mediumPadding),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
        )

        Text(
            text = "Select the files you want to convert.",
            style = MaterialTheme.typography.titleMedium,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(mediumPadding),
            verticalArrangement = Arrangement.spacedBy(mediumPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            VideoPicker(onVideosPicked = { uris ->
                converterViewModel.setFilePaths(uris.map { it.toString() })
                uris.forEach { uri -> onBrowseClicked(uri) }
            })
            if (converterUiState.filePaths.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(mediumPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.padding(mediumPadding),
                        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(mediumPadding),
                            verticalArrangement = Arrangement.spacedBy(mediumPadding),
                            horizontalAlignment = Alignment.Start
                        ) {

                            Text(
                                text = "Selected Files: ",
                                style = MaterialTheme.typography.titleMedium,
                            )

                            VideoConversionProgress(
                                converterViewModel = converterViewModel,
                                videoPaths = converterUiState.filePaths
                            )

                            val context = LocalContext.current
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                OutlinedButton(
                                    onClick = { converterViewModel.clearFilePaths() },
                                ) {
                                    Text(text = "Cancel")
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Button(
                                    onClick = {
                                        val uris = converterUiState.filePaths.map { Uri.parse(it) }
                                        val outputDir = context.getExternalFilesDir(null) ?: context.cacheDir

                                        if (!outputDir.exists()) {
                                            outputDir.mkdirs()
                                        }

                                        converterViewModel.startConversion(
                                            context = context,
                                            uris = uris,
                                            outputDir = outputDir,
                                            contentResolver = context.contentResolver
                                        )
                                    }
                                ) {
                                    Text(text = "Convert All")
                                }
                            }
                        }
                    }
                }
            }

            ConversionStatus(converterUiState = converterUiState)
        }
    }
}



@Composable
fun VideoPicker(onVideosPicked: (List<Uri>) -> Unit) {
    val pickMultipleMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            Log.d("PhotoPicker", "Number of items selected: ${uris.size}")
        } else {
            Log.d("PhotoPicker", "No media selected")
        }

        onVideosPicked(uris)
    }

    Button(onClick = {
        pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }) {
        Text("Browse Files")
    }
}

@Composable
fun VideoConversionProgress(
    converterViewModel: ConverterViewModel,
    videoPaths: List<String>
) {
    val progressState by converterViewModel.progressState.collectAsState()

    Column {
        videoPaths.forEachIndexed { index, path ->
            val progress = progressState.getOrNull(index) ?: 0f
            val progressPercentage = (progress).toInt()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video ${index + 1}: $path",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )

                if (progressPercentage > 0) {
                    Text(
                        text = "$progressPercentage%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                        color = Color.Red
                    )
                } else {
                    Text(
                        text = "Waiting...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                        color = Color.Gray
                    )
                }
            }
        }
    }
}


@Composable
fun ConversionStatus(converterUiState: ConverterUiState) {
    if (converterUiState.conversionInProgress) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .align(Alignment.CenterVertically)
            )
            Text(
                text = "Converting... Please Wait",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    } else {
        when (converterUiState.conversionSuccess) {
            true -> Text(
                text = "Conversion successful! Check your files.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Blue
            )

            false -> Text(
                text = "Conversion failed. Check your logs.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Red
            )

            null -> {}
        }
    }
}



