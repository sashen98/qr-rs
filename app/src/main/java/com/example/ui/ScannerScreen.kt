package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.scanner.CameraScannerView

@Composable
fun ScannerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Photo picker for scanning barcode from images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    viewModel.scanBitmap(bitmap)
                }
            } catch (_: Exception) {}
        }
    }

    val isScanningActive by viewModel.isScanningActive.collectAsState()
    val isTorchEnabled by viewModel.isTorchEnabled.collectAsState()
    val useFrontCamera by viewModel.useFrontCamera.collectAsState()

    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraScannerView(
                isScanningActive = isScanningActive,
                isTorchEnabled = isTorchEnabled,
                useFrontCamera = useFrontCamera,
                onBarcodeDetected = { raw, format ->
                    viewModel.onBarcodeScanned(raw, format)
                }
            )

            // Top Floating Controls: Flashlight & Camera Flip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Torch toggle
                FilledIconButton(
                    onClick = { viewModel.toggleTorch() },
                    modifier = Modifier.testTag("torch_toggle_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isTorchEnabled) Color(0xFFFFD600) else Color(0x66000000),
                        contentColor = if (isTorchEnabled) Color.Black else Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (isTorchEnabled) "Flash Off" else "Flash On"
                    )
                }

                // Header pill indicator
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x77000000)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isScanningActive) Color(0xFF00E676) else Color(0xFFFFAB00))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScanningActive) "Scanning Active" else "Paused",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Switch front/back camera
                FilledIconButton(
                    onClick = { viewModel.toggleCamera() },
                    modifier = Modifier.testTag("camera_switch_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0x66000000),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera"
                    )
                }
            }

            // Bottom Floating Controls: Scan Gallery Image & Manual Entry
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ඕනෑම බිල්පතක් හෝ සබන්/භාණ්ඩ බාර්කෝඩ් පෙට්ටියක් ස්කෑන් කරන්න",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Align bill QR or product barcode within the box",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pick from Gallery
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.testTag("pick_image_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x991E293B),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Gallery Image", fontSize = 13.sp)
                    }

                    // Manual Code Input
                    Button(
                        onClick = { showManualInputDialog = true },
                        modifier = Modifier.testTag("manual_code_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x991E293B),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Type Code", fontSize = 13.sp)
                    }
                }
            }

        } else {
            // Permission request screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "කැමරා අවසරය අවශ්‍යයි",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "බිල්පත්, සබන් පෙට්ටි, QR කේත සහ බාර්කෝඩ් ස්කෑන් කර මිල ගණන් බලාගැනීම සඳහා කරුණාකර කැමරා අවසරය ලබාදෙන්න.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.testTag("request_camera_permission_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("අවසර ලබාදෙන්න (Grant Permission)")
                }
            }
        }
    }

    // Manual input dialog
    if (showManualInputDialog) {
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = { Text("කේතය ඇතුළත් කරන්න (Enter Code)") },
            text = {
                Column {
                    Text(
                        text = "සබන් පෙට්ටියේ හෝ බිල්පතේ ඇති බාර්කෝඩ් අංකය ටයිප් කරන්න:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualCodeInput,
                        onValueChange = { manualCodeInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 8901030381001 or Rs. 450") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualCodeInput.isNotBlank()) {
                            viewModel.onBarcodeScanned(manualCodeInput.trim(), "Manual Input")
                            manualCodeInput = ""
                            showManualInputDialog = false
                        }
                    }
                ) {
                    Text("ස්කෑන් කරන්න")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
