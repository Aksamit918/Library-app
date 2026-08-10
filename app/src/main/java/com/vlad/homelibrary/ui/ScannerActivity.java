package com.vlad.homelibrary.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.card.MaterialCardView;
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
import com.vlad.homelibrary.scan.ScanZoneHelper;
import com.vlad.homelibrary.scan.TessdataManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScannerActivity extends AppCompatActivity {

    public static final String EXTRA_START_OCR_MODE = "extra_start_ocr_mode";

    private PreviewView previewView;
    private MaterialCardView scannerFrame;
    private TextView textScanHint;
    private MaterialButton btnCaptureOcr;
    private ProgressBar progressScan;

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
        scannerFrame = findViewById(R.id.card_scanner_frame);
        textScanHint = findViewById(R.id.text_scan_hint);
        btnCaptureOcr = findViewById(R.id.btn_capture_ocr);
        progressScan = findViewById(R.id.progress_scan);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        btnCaptureOcr.setOnClickListener(v -> captureAndRecognizeText());

        // ISBN opens barcode mode; title and other text fields open OCR mode.
        setOcrMode(getIntent().getBooleanExtra(EXTRA_START_OCR_MODE, false));

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
        updateScanFrameSize(enabled);
    }

    private void updateScanFrameSize(boolean ocrEnabled) {
        ViewGroup.LayoutParams params = scannerFrame.getLayoutParams();
        if (ocrEnabled) {
            params.width = dp(300);
            params.height = dp(220);
        } else {
            params.width = dp(280);
            params.height = dp(140);
        }
        scannerFrame.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
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
                                    if (rawValue == null || rawValue.trim().length() < 3) {
                                        continue;
                                    }
                                    if (!ScanZoneHelper.isBarcodeInsideScanZone(
                                            barcode.getBoundingBox(),
                                            imageProxy,
                                            previewView,
                                            scannerFrame
                                    )) {
                                        continue;
                                    }
                                    if (barcodeHandled.compareAndSet(false, true)) {
                                        returnBarcodeResult(rawValue.trim());
                                    }
                                    return;
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
        Bitmap fullBitmap = previewView.getBitmap();
        if (fullBitmap == null) {
            Toast.makeText(this, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap cropped = ScanZoneHelper.cropToScanZone(fullBitmap, previewView, scannerFrame);
        if (cropped != fullBitmap) {
            fullBitmap.recycle();
        }
        if (cropped == null) {
            Toast.makeText(this, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        ocrBusy = true;
        progressScan.setVisibility(View.VISIBLE);
        btnCaptureOcr.setEnabled(false);
        Toast.makeText(this, R.string.ocr_preparing_languages, Toast.LENGTH_SHORT).show();

        final Bitmap frame = cropped;
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

                MultilingualOcrEngine.OcrResult ocrResult = ocrEngine.recognize(this, frame);
                runOnUiThread(() -> {
                    if (!frame.isRecycled()) {
                        frame.recycle();
                    }
                    progressScan.setVisibility(View.GONE);
                    btnCaptureOcr.setEnabled(true);
                    ocrBusy = false;
                    textScanHint.setText(R.string.align_text_inside_target_frame);
                    showOcrChooser(ocrResult);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (!frame.isRecycled()) {
                        frame.recycle();
                    }
                    progressScan.setVisibility(View.GONE);
                    btnCaptureOcr.setEnabled(true);
                    ocrBusy = false;
                    textScanHint.setText(R.string.align_text_inside_target_frame);
                    Toast.makeText(this, R.string.ocr_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showOcrChooser(MultilingualOcrEngine.OcrResult ocrResult) {
        if (ocrResult == null || ocrResult.isEmpty()) {
            Toast.makeText(this, R.string.ocr_no_text_found, Toast.LENGTH_SHORT).show();
            return;
        }

        final String fullText = ocrResult.fullText;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ocr_result, null);
        View cardFullText = dialogView.findViewById(R.id.card_ocr_full_text);
        TextView textFull = dialogView.findViewById(R.id.text_ocr_full);
        MaterialButton btnFullText = dialogView.findViewById(R.id.btn_ocr_full_text);
        TextView linesHint = dialogView.findViewById(R.id.text_ocr_lines_hint);
        android.widget.ListView listLines = dialogView.findViewById(R.id.list_ocr_lines);

        textFull.setText(fullText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.ocr_result_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        View.OnClickListener useFullText = v -> {
            dialog.dismiss();
            returnTitleText(fullText);
        };
        cardFullText.setOnClickListener(useFullText);
        btnFullText.setOnClickListener(useFullText);

        List<String> lines = ocrResult.lines;
        if (lines != null && lines.size() > 1) {
            linesHint.setVisibility(View.VISIBLE);
            listLines.setVisibility(View.VISIBLE);
            listLines.setAdapter(new android.widget.ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    lines
            ));
            listLines.setOnItemClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                returnTitleText(lines.get(position));
            });

            // Keep dialog usable when many lines are recognized.
            listLines.post(() -> {
                int maxHeight = (int) (getResources().getDisplayMetrics().density * 220);
                if (listLines.getHeight() > maxHeight) {
                    ViewGroup.LayoutParams params = listLines.getLayoutParams();
                    params.height = maxHeight;
                    listLines.setLayoutParams(params);
                }
            });
        } else {
            linesHint.setVisibility(View.GONE);
            listLines.setVisibility(View.GONE);
        }

        dialog.show();
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
