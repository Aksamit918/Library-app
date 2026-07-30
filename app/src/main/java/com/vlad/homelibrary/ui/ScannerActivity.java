package com.vlad.homelibrary.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.vlad.homelibrary.R;

public class ScannerActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.preview_view);

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required to scan barcodes", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
        );

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        com.google.common.util.concurrent.ListenableFuture<androidx.camera.lifecycle.ProcessCameraProvider> cameraProviderFuture =
                androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                androidx.camera.lifecycle.ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                com.google.mlkit.vision.barcode.BarcodeScannerOptions options =
                        new com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
                                .build();

                com.google.mlkit.vision.barcode.BarcodeScanner scanner =
                        com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options);

                androidx.camera.core.ImageAnalysis imageAnalysis =
                        new androidx.camera.core.ImageAnalysis.Builder()
                                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(androidx.core.content.ContextCompat.getMainExecutor(this), imageProxy -> {
                    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
                    android.media.Image mediaImage = imageProxy.getImage();

                    if (mediaImage != null) {
                        com.google.mlkit.vision.common.InputImage image =
                                com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                        scanner.process(image)
                                .addOnSuccessListener(barcodes -> {
                                    for (com.google.mlkit.vision.barcode.common.Barcode barcode : barcodes) {
                                        String rawValue = barcode.getRawValue();

                                        if (rawValue != null && rawValue.length() >= 3) {
                                            android.content.Intent returnIntent = new android.content.Intent();
                                            returnIntent.putExtra("scanned_isbn", rawValue);
                                            setResult(RESULT_OK, returnIntent);

                                            cameraProvider.unbindAll();
                                            finish();
                                            return;
                                        }
                                    }
                                })
                                .addOnFailureListener(Throwable::printStackTrace)
                                .addOnCompleteListener(task -> {
                                    imageProxy.close();
                                });
                    } else {
                        imageProxy.close();
                    }
                });

                cameraProvider.unbindAll();

                androidx.camera.core.Camera camera = cameraProvider.bindToLifecycle(
                        this,
                        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                );

                camera.getCameraControl().setZoomRatio(1.5f);

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show();
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }
}