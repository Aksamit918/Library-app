package com.vlad.homelibrary.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.vlad.homelibrary.R;
import com.vlad.homelibrary.data.LibraryDatabase;
import com.vlad.homelibrary.scan.MultilingualOcrEngine;
import com.vlad.homelibrary.scan.ScanResultParser;
import com.vlad.homelibrary.scan.TessdataManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScannerActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView textScanHint;
    private MaterialButton btnCaptureOcr;
    private ProgressBar progressScan;
    private MaterialButtonToggleGroup toggleScanMode;

    private ProcessCameraProvider cameraProvider;
    private BarcodeScanner barcodeScanner;
    private final MultilingualOcrEngine ocrEngine = new MultilingualOcrEngine();
    private final AtomicBoolean barcodeHandled = new AtomicBoolean(false);
    private boolean ocrMode = false;
    private boolean ocrBusy = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show();
                    finish();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.preview_view);
        textScanHint = findViewById(R.id.text_scan_hint);
        btnCaptureOcr = findViewById(R.id.btn_capture_ocr);
        progressScan = findViewById(R.id.progress_scan);
        toggleScanMode = findViewById(R.id.toggle_scan_mode);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        toggleScanMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            setOcrMode(checkedId == R.id.btn_scan_ocr);
        });

        btnCaptureOcr.setOnClickListener(v -> captureAndRecognizeText());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setOcrMode(boolean enabled) {
        ocrMode = enabled;
        barcodeHandled.set(false);
        btnCaptureOcr.setVisibility(enabled ? View.VISIBLE : View.GONE);
        textScanHint.setText(enabled
                ? R.string.align_text_inside_target_frame
                : R.string.align_barcode_inside_target_frame);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    if (ocrMode || barcodeHandled.get()) {
                        imageProxy.close();
                        return;
                    }

                    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
                    android.media.Image mediaImage = imageProxy.getImage();
                    if (mediaImage == null) {
                        imageProxy.close();
                        return;
                    }

                    InputImage image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.getImageInfo().getRotationDegrees()
                    );

                    barcodeScanner.process(image)
                            .addOnSuccessListener(barcodes -> {
                                if (ocrMode || barcodeHandled.get()) {
                                    return;
                                }
                                for (Barcode barcode : barcodes) {
                                    String rawValue = barcode.getRawValue();
                                    if (rawValue != null && rawValue.trim().length() >= 3) {
                                        if (barcodeHandled.compareAndSet(false, true)) {
                                            returnBarcodeResult(rawValue.trim());
                                        }
                                        return;
                                    }
                                }
                            })
                            .addOnFailureListener(Throwable::printStackTrace)
                            .addOnCompleteListener(task -> imageProxy.close());
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                );
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.camera_start_failed, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void returnBarcodeResult(@NonNull String rawValue) {
        ScanResultParser.ScanType type = ScanResultParser.parseBarcode(rawValue);
        String normalized = ScanResultParser.normalize(rawValue);
        String value = type == ScanResultParser.ScanType.OTHER ? rawValue : normalized;

        Intent returnIntent = new Intent();
        returnIntent.putExtra(ScanResultParser.EXTRA_SCANNED_TYPE, type.name());
        returnIntent.putExtra(ScanResultParser.EXTRA_SCANNED_VALUE, value);
        if (type == ScanResultParser.ScanType.ISBN) {
            returnIntent.putExtra(ScanResultParser.EXTRA_SCANNED_ISBN, value);
        }
        setResult(RESULT_OK, returnIntent);

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        finish();
    }

    private void captureAndRecognizeText() {
        if (ocrBusy) {
            return;
        }
        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            Toast.makeText(this, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        ocrBusy = true;
        progressScan.setVisibility(View.VISIBLE);
        btnCaptureOcr.setEnabled(false);
        Toast.makeText(this, R.string.ocr_preparing_languages, Toast.LENGTH_SHORT).show();

        final Bitmap frame = bitmap;
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            try {
                TessdataManager tessdataManager = ocrEngine.getTessdataManager();
                if (!tessdataManager.hasAllLanguageData(this)) {
                    tessdataManager.ensureLanguageData(this, (current, total, languageCode) ->
                            runOnUiThread(() -> textScanHint.setText(
                                    getString(R.string.ocr_downloading_language, languageCode, current, total)
                            ))
                    );
                }

                List<String> lines = ocrEngine.recognizeLines(this, frame);
                runOnUiThread(() -> {
                    progressScan.setVisibility(View.GONE);
                    btnCaptureOcr.setEnabled(true);
                    ocrBusy = false;
                    textScanHint.setText(R.string.align_text_inside_target_frame);
                    showLineChooser(lines);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressScan.setVisibility(View.GONE);
                    btnCaptureOcr.setEnabled(true);
                    ocrBusy = false;
                    textScanHint.setText(R.string.align_text_inside_target_frame);
                    Toast.makeText(this, R.string.ocr_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showLineChooser(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            Toast.makeText(this, R.string.ocr_no_text_found, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = lines.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.ocr_choose_title_line)
                .setItems(items, (dialog, which) -> returnTitleText(items[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void returnTitleText(String titleText) {
        Intent returnIntent = new Intent();
        returnIntent.putExtra(ScanResultParser.EXTRA_SCANNED_TYPE, ScanResultParser.ScanType.TITLE_TEXT.name());
        returnIntent.putExtra(ScanResultParser.EXTRA_SCANNED_VALUE, titleText);
        setResult(RESULT_OK, returnIntent);
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }
}
