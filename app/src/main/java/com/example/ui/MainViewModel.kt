package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ScanItem
import com.example.data.ScanRepository
import com.example.scanner.BarcodePriceParser
import com.example.scanner.FeedbackManager
import com.example.scanner.ParsedBarcodeResult
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScanModalState(
    val scanItem: ScanItem,
    val parsedResult: ParsedBarcodeResult
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScanRepository
    private val feedbackManager = FeedbackManager(application)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ScanRepository(db.scanDao())
    }

    val historyItems: StateFlow<List<ScanItem>> = repository.allScans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isScanningActive = MutableStateFlow(true)
    val isScanningActive: StateFlow<Boolean> = _isScanningActive.asStateFlow()

    private val _isTorchEnabled = MutableStateFlow(false)
    val isTorchEnabled: StateFlow<Boolean> = _isTorchEnabled.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(false)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private val _currentModal = MutableStateFlow<ScanModalState?>(null)
    val currentModal: StateFlow<ScanModalState?> = _currentModal.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun onBarcodeScanned(rawValue: String, format: String) {
        if (!_isScanningActive.value) return

        // Pause scanning to prevent duplicates
        _isScanningActive.value = false

        // Sound tick & vibration
        feedbackManager.triggerScanFeedback()

        val parsed = BarcodePriceParser.parse(rawValue, format)

        viewModelScope.launch {
            val item = ScanItem(
                rawValue = rawValue,
                format = format,
                title = parsed.title,
                price = parsed.displayPrice,
                priceValue = parsed.numericPrice,
                currency = parsed.currency,
                category = parsed.category,
                details = parsed.details,
                timestamp = System.currentTimeMillis()
            )

            val insertedId = repository.insert(item)
            val savedItem = item.copy(id = insertedId)

            _currentModal.value = ScanModalState(
                scanItem = savedItem,
                parsedResult = parsed
            )
        }
    }

    fun openHistoryItem(item: ScanItem) {
        val parsed = BarcodePriceParser.parse(item.rawValue, item.format)
        _currentModal.value = ScanModalState(
            scanItem = item,
            parsedResult = parsed
        )
    }

    fun dismissModal() {
        _currentModal.value = null
        // Smoothly resume camera scanning
        _isScanningActive.value = true
    }

    /**
     * Fast delete of the currently viewed scan
     */
    fun quickDeleteCurrentScan() {
        val current = _currentModal.value ?: return
        viewModelScope.launch {
            repository.deleteById(current.scanItem.id)
            dismissModal()
        }
    }

    fun deleteScan(item: ScanItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun clearAllScans() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun updatePrice(item: ScanItem, newPriceStr: String) {
        val cleaned = newPriceStr.replace(",", "").trim()
        val num = cleaned.toDoubleOrNull()
        val formatted = if (num != null) {
            "${item.currency} ${String.format(java.util.Locale.US, "%.2f", num)}"
        } else {
            newPriceStr
        }

        val updated = item.copy(
            price = formatted,
            priceValue = num
        )

        viewModelScope.launch {
            repository.update(updated)
            // Update active modal if showing
            val current = _currentModal.value
            if (current != null && current.scanItem.id == item.id) {
                val updatedParsed = current.parsedResult.copy(
                    displayPrice = formatted,
                    numericPrice = num
                )
                _currentModal.value = ScanModalState(updated, updatedParsed)
            }
        }
    }

    fun toggleTorch() {
        _isTorchEnabled.value = !_isTorchEnabled.value
    }

    fun toggleCamera() {
        _useFrontCamera.value = !_useFrontCamera.value
    }

    fun scanBitmap(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val first = barcodes[0]
                    first.rawValue?.let { raw ->
                        val formatStr = when (first.format) {
                            Barcode.FORMAT_QR_CODE -> "QR Code"
                            Barcode.FORMAT_EAN_13 -> "EAN-13"
                            else -> "Barcode"
                        }
                        onBarcodeScanned(raw, formatStr)
                    }
                } else {
                    _statusMessage.value = "No QR code or barcode found in this image."
                }
            }
            .addOnFailureListener {
                _statusMessage.value = "Could not scan image."
            }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        feedbackManager.release()
    }
}
