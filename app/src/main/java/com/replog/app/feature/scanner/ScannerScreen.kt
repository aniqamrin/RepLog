package com.replog.app.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.replog.app.domain.logic.DetectedFood
import com.replog.app.ui.components.EmptyState
import com.replog.app.ui.components.RpCard
import com.replog.app.ui.components.SectionHeader
import java.io.File

@Composable
fun ScannerScreen(
    onDone: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.phase) {
        ScanPhase.CAMERA -> CameraCapture(
            onCaptured = { file -> viewModel.analyzePhoto(file) },
            onCancel = onDone,
            newFile = viewModel::newCaptureFile
        )
        ScanPhase.ANALYZING -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.photoPath?.let {
                AsyncImage(model = File(it), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(240.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Analyzing your meal…", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "AI estimates from photos are approximate. You can edit every item before saving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ScanPhase.REVIEW -> ReviewScreen(state, viewModel, onDone)
        ScanPhase.ERROR -> ErrorScreen(state, viewModel, onDone)
    }
}

@Composable
private fun CameraCapture(
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
    newFile: () -> File
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyState("Camera access needed", "Grant camera permission to scan your food.")
            OutlinedButton(onClick = onCancel) { Text("Back") }
        }
        return
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    controller.bindToLifecycle(lifecycleOwner)
                    this.controller = controller
                }
            }
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = {
                val file = newFile()
                val options = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(file).build()
                controller.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: androidx.camera.core.ImageCapture.OutputFileResults) {
                            onCaptured(file)
                        }
                        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            }) { Text("📷  Capture") }
        }
    }
}
