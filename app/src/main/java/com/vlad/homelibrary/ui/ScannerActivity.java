package com.vlad.homelibrary.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
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
import com.vlad.homelibrary.scan.BookCoverDetector;
import com.vlad.homelibrary.scan.CoverCropView;
import com.vlad.homelibrary.scan.CoverImageLoader;
import com.vlad.homelibrary.scan.MultilingualOcrEngine;
import com.vlad.homelibrary.scan.OcrScriptChoice;
import com.vlad.homelibrary.scan.PhotoLassoView;
import com.vlad.homelibrary.scan.ScanResultParser;
import com.vlad.homelibrary.scan.ScanZoneHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScannerActivity extends AppCompatActivity {

    public static final String EXTRA_START_OCR_MODE = "extra_start_ocr_mode";
    public static final String EXTRA_START_COVER_MODE = "extra_start_cover_mode";
    public static final String EXTRA_COVER_SOURCE_URI = "extra_cover_source_uri";
    public static final String EXTRA_COVER_IMAGE_PATH = "extra_cover_image_path";
    private static final String PREFS_OCR = "ocr_prefs";
    private static final String PREF_SCRIPT = "ocr_script_choice";

    private PreviewView previewView;
    private PhotoLassoView photoLassoView;
    private CoverCropView coverCropView;
    private MaterialCardView scannerFrame;
    private TextView textScanHint;
    private MaterialButton btnTakePhoto;
    private MaterialButton btnRetakePhoto;
    private MaterialButton btnOcrScript;
    private LinearLayout barCoverCropActions;
    private MaterialButton btnCoverRetake;
    private MaterialButton btnUseCover;
    private ProgressBar progressScan;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private BarcodeScanner barcodeScanner;
    private final MultilingualOcrEngine ocrEngine = new MultilingualOcrEngine();
    private final AtomicBoolean barcodeHandled = new AtomicBoolean(false);

    private boolean ocrMode = false;
    private boolean coverMode = false;
    private boolean selectingOnPhoto = false;
    private boolean croppingCover = false;
    private boolean coverFromCamera = false;
    private boolean ocrBusy = false;
    private boolean coverBusy = false;
    private boolean capturingPhoto = false;
    @NonNull
    private OcrScriptChoice selectedScript = OcrScriptChoice.CYRILLIC;
    @Nullable
    private Bitmap capturedPhoto;

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
        photoLassoView = findViewById(R.id.photo_lasso_view);
        coverCropView = findViewById(R.id.cover_crop_view);
        scannerFrame = findViewById(R.id.card_scanner_frame);
        textScanHint = findViewById(R.id.text_scan_hint);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnRetakePhoto = findViewById(R.id.btn_retake_photo);
        btnOcrScript = findViewById(R.id.btn_ocr_script);
        barCoverCropActions = findViewById(R.id.bar_cover_crop_actions);
        btnCoverRetake = findViewById(R.id.btn_cover_retake);
        btnUseCover = findViewById(R.id.btn_use_cover);
        progressScan = findViewById(R.id.progress_scan);

        selectedScript = loadScriptChoice();
        updateScriptButton();
        btnOcrScript.setOnClickListener(v -> showScriptChooser());

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        btnTakePhoto.setOnClickListener(v -> takePhoto());
        btnRetakePhoto.setOnClickListener(v -> returnToPhotoCapture());
        btnCoverRetake.setOnClickListener(v -> returnToCoverCapture());
        btnUseCover.setOnClickListener(v -> approveCoverCrop());

        photoLassoView.setSelectionListener(new PhotoLassoView.SelectionListener() {
            @Override
            public void onRegionSelected(@Nullable Bitmap croppedRegion) {
                recognizeSelectedText(croppedRegion);
            }

            @Override
            public void onSelectionTooSmall() {
                Toast.makeText(ScannerActivity.this, R.string.ocr_selection_too_small, Toast.LENGTH_SHORT).show();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (coverMode && croppingCover && !coverBusy) {
                    if (coverFromCamera) {
                        returnToCoverCapture();
                    } else {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                } else if (ocrMode && selectingOnPhoto && !ocrBusy) {
                    returnToPhotoCapture();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        coverMode = getIntent().getBooleanExtra(EXTRA_START_COVER_MODE, false);
        String coverSource = getIntent().getStringExtra(EXTRA_COVER_SOURCE_URI);
        if (coverMode) {
            applyCoverCaptureUi();
            if (coverSource != null && !coverSource.isBlank()) {
                loadCoverFromUri(Uri.parse(coverSource));
                return;
            }
        } else {
            setOcrMode(getIntent().getBooleanExtra(EXTRA_START_OCR_MODE, false));
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @NonNull
    private OcrScriptChoice loadScriptChoice() {
        SharedPreferences prefs = getSharedPreferences(PREFS_OCR, MODE_PRIVATE);
        if (!prefs.contains(PREF_SCRIPT)) {
            return OcrScriptChoice.defaultForDeviceLocale();
        }
        return OcrScriptChoice.fromId(prefs.getString(PREF_SCRIPT, null));
    }

    private void saveScriptChoice(@NonNull OcrScriptChoice choice) {
        getSharedPreferences(PREFS_OCR, MODE_PRIVATE)
                .edit()
                .putString(PREF_SCRIPT, choice.id)
                .apply();
    }

    private void updateScriptButton() {
        btnOcrScript.setText(selectedScript.labelRes);
    }

    private void showScriptChooser() {
        if (ocrBusy) {
            return;
        }
        OcrScriptChoice[] choices = OcrScriptChoice.values();
        CharSequence[] labels = new CharSequence[choices.length];
        int checked = 0;
        for (int i = 0; i < choices.length; i++) {
            labels[i] = getString(choices[i].labelRes);
            if (choices[i] == selectedScript) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.ocr_choose_script_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    selectedScript = choices[which];
                    saveScriptChoice(selectedScript);
                    updateScriptButton();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setOcrMode(boolean enabled) {
        ocrMode = enabled;
        barcodeHandled.set(false);
        selectingOnPhoto = false;

        if (enabled) {
            scannerFrame.setVisibility(View.GONE);
            photoLassoView.setVisibility(View.GONE);
            photoLassoView.setDrawingEnabled(false);
            btnTakePhoto.setVisibility(View.VISIBLE);
            btnRetakePhoto.setVisibility(View.GONE);
            btnOcrScript.setVisibility(View.VISIBLE);
            textScanHint.setText(R.string.take_photo_of_text);
            coverCropView.setVisibility(View.GONE);
            barCoverCropActions.setVisibility(View.GONE);
        } else {
            photoLassoView.setVisibility(View.GONE);
            photoLassoView.setDrawingEnabled(false);
            photoLassoView.clearSelection();
            photoLassoView.clearPhoto();
            coverCropView.setVisibility(View.GONE);
            barCoverCropActions.setVisibility(View.GONE);
            btnTakePhoto.setVisibility(View.GONE);
            btnRetakePhoto.setVisibility(View.GONE);
            btnOcrScript.setVisibility(View.GONE);
            scannerFrame.setVisibility(View.VISIBLE);
            textScanHint.setText(R.string.align_barcode_inside_target_frame);
            ViewGroup.LayoutParams params = scannerFrame.getLayoutParams();
            params.width = dp(280);
            params.height = dp(140);
            scannerFrame.setLayoutParams(params);
        }
    }

    private void applyCoverCaptureUi() {
        coverMode = true;
        croppingCover = false;
        coverFromCamera = false;
        barcodeHandled.set(true);

        photoLassoView.setVisibility(View.GONE);
        photoLassoView.setDrawingEnabled(false);
        coverCropView.setVisibility(View.GONE);
        coverCropView.setAdjustEnabled(false);
        barCoverCropActions.setVisibility(View.GONE);
        btnOcrScript.setVisibility(View.GONE);
        btnRetakePhoto.setVisibility(View.GONE);
        btnTakePhoto.setVisibility(View.VISIBLE);
        previewView.setVisibility(View.VISIBLE);

        scannerFrame.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = scannerFrame.getLayoutParams();
        params.width = dp(220);
        params.height = dp(300);
        scannerFrame.setLayoutParams(params);
        textScanHint.setText(R.string.take_photo_of_cover);
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
                bindCameraUseCases();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.camera_start_failed, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || selectingOnPhoto || croppingCover) {
            return;
        }

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.unbindAll();

        if (ocrMode || coverMode) {
            int rotation = previewView.getDisplay() != null
                    ? previewView.getDisplay().getRotation()
                    : android.view.Surface.ROTATION_0;
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(new ResolutionSelector.Builder()
                            .setResolutionStrategy(new ResolutionStrategy(
                                    new Size(1920, 1440),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build())
                    .setTargetRotation(rotation)
                    .build();
            cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
            );
            return;
        }

        imageCapture = null;
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            if (ocrMode || coverMode || barcodeHandled.get()) {
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
                        if (ocrMode || coverMode || barcodeHandled.get()) {
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

        cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
        );
    }

    private void takePhoto() {
        if ((!ocrMode && !coverMode) || capturingPhoto || ocrBusy || coverBusy || imageCapture == null) {
            return;
        }

        capturingPhoto = true;
        btnTakePhoto.setEnabled(false);
        progressScan.setVisibility(View.VISIBLE);

        imageCapture.takePicture(
                LibraryDatabase.databaseWriteExecutor,
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Bitmap bitmap = imageProxyToBitmap(image);
                        image.close();
                        runOnUiThread(() -> {
                            capturingPhoto = false;
                            progressScan.setVisibility(View.GONE);
                            btnTakePhoto.setEnabled(true);

                            if (bitmap == null) {
                                Toast.makeText(ScannerActivity.this, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (coverMode) {
                                enterCoverCrop(bitmap, true);
                            } else {
                                enterPhotoSelection(bitmap);
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        runOnUiThread(() -> {
                            capturingPhoto = false;
                            progressScan.setVisibility(View.GONE);
                            btnTakePhoto.setEnabled(true);
                            Toast.makeText(ScannerActivity.this, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    @Nullable
    private static Bitmap imageProxyToBitmap(@NonNull ImageProxy image) {
        try {
            Bitmap bitmap = image.toBitmap();
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation == 0) {
                return ensureArgb8888(bitmap);
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return ensureArgb8888(rotated);
        } catch (Exception e) {
            e.printStackTrace();
            return decodeJpegFallback(image);
        }
    }

    @Nullable
    private static Bitmap decodeJpegFallback(@NonNull ImageProxy image) {
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                return null;
            }
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation == 0) {
                return ensureArgb8888(bitmap);
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return ensureArgb8888(rotated);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @NonNull
    private static Bitmap ensureArgb8888(@NonNull Bitmap bitmap) {
        if (bitmap.getConfig() == Bitmap.Config.ARGB_8888) {
            return bitmap;
        }
        Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (copy == null) {
            return bitmap;
        }
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return copy;
    }

    private void enterPhotoSelection(@NonNull Bitmap photo) {
        clearCapturedPhoto();
        capturedPhoto = photo;
        selectingOnPhoto = true;

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        previewView.setVisibility(View.GONE);
        btnTakePhoto.setVisibility(View.GONE);
        btnRetakePhoto.setVisibility(View.VISIBLE);
        btnOcrScript.setVisibility(View.VISIBLE);
        textScanHint.setText(R.string.draw_around_text_to_scan);

        photoLassoView.setVisibility(View.VISIBLE);
        photoLassoView.setPhoto(photo);
        photoLassoView.setDrawingEnabled(false);
        photoLassoView.post(() -> photoLassoView.setDrawingEnabled(true));
    }

    private void returnToPhotoCapture() {
        if (ocrBusy) {
            return;
        }

        selectingOnPhoto = false;
        clearCapturedPhoto();

        photoLassoView.setDrawingEnabled(false);
        photoLassoView.clearSelection();
        photoLassoView.clearPhoto();
        photoLassoView.setVisibility(View.GONE);

        previewView.setVisibility(View.VISIBLE);
        btnTakePhoto.setVisibility(View.VISIBLE);
        btnRetakePhoto.setVisibility(View.GONE);
        btnOcrScript.setVisibility(View.VISIBLE);
        textScanHint.setText(R.string.take_photo_of_text);

        bindCameraUseCases();
    }

    private void loadCoverFromUri(@NonNull Uri uri) {
        coverFromCamera = false;
        croppingCover = true;
        previewView.setVisibility(View.GONE);
        scannerFrame.setVisibility(View.GONE);
        btnTakePhoto.setVisibility(View.GONE);
        btnOcrScript.setVisibility(View.GONE);
        barCoverCropActions.setVisibility(View.GONE);
        progressScan.setVisibility(View.VISIBLE);
        textScanHint.setText(R.string.finding_cover_edges);

        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            Bitmap bitmap = CoverImageLoader.load(this, uri);
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    recycleQuietly(bitmap);
                    return;
                }
                if (bitmap == null) {
                    progressScan.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.cover_crop_failed, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                enterCoverCrop(bitmap, false);
            });
        });
    }

    private void enterCoverCrop(@NonNull Bitmap photo, boolean fromCamera) {
        clearCapturedPhoto();
        capturedPhoto = photo;
        coverFromCamera = fromCamera;
        croppingCover = true;

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        previewView.setVisibility(View.GONE);
        scannerFrame.setVisibility(View.GONE);
        btnTakePhoto.setVisibility(View.GONE);
        btnRetakePhoto.setVisibility(View.GONE);
        btnOcrScript.setVisibility(View.GONE);
        photoLassoView.setVisibility(View.GONE);

        coverCropView.setVisibility(View.VISIBLE);
        coverCropView.setPhoto(photo);
        coverCropView.setAdjustEnabled(false);
        barCoverCropActions.setVisibility(View.VISIBLE);
        btnCoverRetake.setVisibility(fromCamera ? View.VISIBLE : View.GONE);
        btnCoverRetake.setEnabled(false);
        btnUseCover.setEnabled(false);
        progressScan.setVisibility(View.VISIBLE);
        textScanHint.setText(R.string.finding_cover_edges);

        coverBusy = true;
        detectCoverEdges(photo);
    }

    private void detectCoverEdges(@NonNull Bitmap photo) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            float[] quad = BookCoverDetector.detect(photo);
            runOnUiThread(() -> {
                if (isDestroyed() || !croppingCover || capturedPhoto != photo) {
                    return;
                }
                coverCropView.setBitmapQuad(quad);
                coverCropView.setAdjustEnabled(true);
                coverBusy = false;
                progressScan.setVisibility(View.GONE);
                btnUseCover.setEnabled(true);
                btnCoverRetake.setEnabled(true);
                textScanHint.setText(R.string.adjust_cover_edges);
            });
        });
    }

    private void returnToCoverCapture() {
        if (coverBusy) {
            return;
        }

        croppingCover = false;
        coverFromCamera = false;
        clearCapturedPhoto();
        coverCropView.setAdjustEnabled(false);
        coverCropView.clearPhoto();
        coverCropView.setVisibility(View.GONE);
        barCoverCropActions.setVisibility(View.GONE);
        progressScan.setVisibility(View.GONE);
        applyCoverCaptureUi();
        bindCameraUseCases();
    }

    private void approveCoverCrop() {
        if (coverBusy || !croppingCover) {
            return;
        }

        Bitmap cropped = coverCropView.cropSelected();
        if (cropped == null) {
            Toast.makeText(this, R.string.cover_crop_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        coverBusy = true;
        coverCropView.setAdjustEnabled(false);
        btnUseCover.setEnabled(false);
        btnCoverRetake.setEnabled(false);
        progressScan.setVisibility(View.VISIBLE);

        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            String path = saveCoverBitmap(cropped);
            recycleQuietly(cropped);
            runOnUiThread(() -> {
                coverBusy = false;
                btnUseCover.setEnabled(true);
                btnCoverRetake.setEnabled(true);
                progressScan.setVisibility(View.GONE);
                coverCropView.setAdjustEnabled(true);

                if (path == null || path.isEmpty()) {
                    Toast.makeText(this, R.string.cover_crop_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                returnCoverResult(path);
            });
        });
    }

    @Nullable
    private String saveCoverBitmap(@NonNull Bitmap bitmap) {
        FileOutputStream output = null;
        try {
            File directory = new File(getFilesDir(), "covers");
            if (!directory.exists() && !directory.mkdirs()) {
                return null;
            }
            File file = new File(directory, "book_cover_" + System.currentTimeMillis() + ".jpg");
            output = new FileOutputStream(file);
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                return null;
            }
            output.flush();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void returnCoverResult(@NonNull String path) {
        clearCapturedPhoto();
        Intent returnIntent = new Intent();
        returnIntent.putExtra(EXTRA_COVER_IMAGE_PATH, path);
        setResult(RESULT_OK, returnIntent);
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        finish();
    }

    private void clearCapturedPhoto() {
        photoLassoView.clearPhoto();
        coverCropView.clearPhoto();
        if (capturedPhoto != null && !capturedPhoto.isRecycled()) {
            capturedPhoto.recycle();
        }
        capturedPhoto = null;
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

    private void recognizeSelectedText(@Nullable Bitmap cropped) {
        if (ocrBusy) {
            return;
        }
        if (cropped == null) {
            photoLassoView.clearSelection();
            Toast.makeText(this, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        ocrBusy = true;
        photoLassoView.setDrawingEnabled(false);
        btnRetakePhoto.setEnabled(false);
        btnOcrScript.setEnabled(false);
        progressScan.setVisibility(View.VISIBLE);

        final Bitmap frame = cropped;
        final OcrScriptChoice script = selectedScript;
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            try {
                MultilingualOcrEngine.OcrResult ocrResult = ocrEngine.recognize(
                        this,
                        frame,
                        script,
                        (current, total, packLabel) -> runOnUiThread(() ->
                                textScanHint.setText(getString(
                                        R.string.ocr_downloading_language,
                                        packLabel,
                                        current,
                                        total
                                ))
                        )
                );
                runOnUiThread(() -> {
                    recycleQuietly(frame);
                    finishOcrAttempt();
                    showOcrChooser(ocrResult);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    recycleQuietly(frame);
                    finishOcrAttempt();
                    Toast.makeText(this, R.string.ocr_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void finishOcrAttempt() {
        progressScan.setVisibility(View.GONE);
        ocrBusy = false;
        photoLassoView.clearSelection();
        photoLassoView.setDrawingEnabled(true);
        btnRetakePhoto.setEnabled(true);
        btnOcrScript.setEnabled(true);
        textScanHint.setText(R.string.draw_around_text_to_scan);
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
        clearCapturedPhoto();
        Intent returnIntent = new Intent();
        returnIntent.putExtra(ScanResultParser.EXTRA_SCANNED_TYPE, ScanResultParser.ScanType.TITLE_TEXT.name());
        returnIntent.putExtra(ScanResultParser.EXTRA_SCANNED_VALUE, titleText);
        setResult(RESULT_OK, returnIntent);
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        finish();
    }

    private static void recycleQuietly(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    @Override
    protected void onDestroy() {
        clearCapturedPhoto();
        super.onDestroy();
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }
}
