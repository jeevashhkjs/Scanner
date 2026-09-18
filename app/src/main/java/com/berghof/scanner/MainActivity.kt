package com.berghof.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.berghof.scanner.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var lastValue: String? = null
    private var isProcessing = false
    private var scanForResult = false

    private val requestPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        scanForResult = intent.getBooleanExtra(EXTRA_SCAN_FOR_RESULT, false)
        if (scanForResult) {
            binding.btnPasteField.visibility = android.view.View.GONE
            binding.btnWebVisu.visibility = android.view.View.GONE
            binding.tvAccessibilityHint.visibility = android.view.View.GONE
            binding.labelResult.text = "Point the camera at the barcode/QR code to fill the field"
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCopy.setOnClickListener {
            lastValue?.let { copyToClipboard(it) } ?: toast("Scan something first")
        }

        binding.btnPasteField.setOnClickListener {
            val value = lastValue
            if (value == null) {
                toast("Scan something first")
                return@setOnClickListener
            }
            if (!PasteAccessibilityService.isRunning) {
                toast("Enable the Accessibility Service first")
                openAccessibilitySettings()
                return@setOnClickListener
            }
            val ok = PasteAccessibilityService.pasteIntoFocusedField(value)
            toast(if (ok) "Pasted into focused field" else "No editable field is focused right now")
        }

        binding.btnWebVisu.setOnClickListener {
            val intent = Intent(this, WebVisuActivity::class.java)
            lastValue?.let { intent.putExtra(WebVisuActivity.EXTRA_SCANNED_VALUE, it) }
            startActivity(intent)
        }

        binding.tvAccessibilityHint.setOnClickListener { openAccessibilitySettings() }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_ALL_FORMATS
                )
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(scanner, imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
            } catch (e: Exception) {
                toast("Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImageProxy(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isProcessing) {
            imageProxy.close()
            return
        }
        isProcessing = true
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull()?.rawValue
                if (!value.isNullOrEmpty() && value != lastValue) {
                    onNewValueScanned(value)
                }
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    private fun onNewValueScanned(value: String) {
        lastValue = value
        runOnUiThread {
            binding.tvResult.text = value
            copyToClipboard(value)
            if (scanForResult) {
                val result = Intent().putExtra(EXTRA_SCANNED_VALUE, value)
                setResult(RESULT_OK, result)
                finish()
            } else {
                binding.labelResult.text = "Scanned value (auto-copied to clipboard):"
            }
        }
    }

    private fun copyToClipboard(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Scanned value", value))
        toast("Copied to clipboard")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        const val EXTRA_SCAN_FOR_RESULT = "scan_for_result"
        const val EXTRA_SCANNED_VALUE = "scanned_value"
    }
}
