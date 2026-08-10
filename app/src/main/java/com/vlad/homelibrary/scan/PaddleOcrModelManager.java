package com.vlad.homelibrary.scan;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class PaddleOcrModelManager {

    private static final String TAG = "PaddleOcrModels";

    public static final long DET_MIN_BYTES = 3_000_000L;

    public enum ScriptPack {
        ESLAV("eslav", "East Slavic / Cyrillic", 6_000_000L, 500L),
        ENGLISH("english", "English", 6_000_000L, 500L),
        LATIN("latin", "Latin scripts", 6_000_000L, 500L),
        GREEK("greek", "Greek", 6_000_000L, 400L),
        ARABIC("arabic", "Arabic / Persian / Urdu", 6_000_000L, 400L),
        KOREAN("korean", "Korean", 10_000_000L, 400L),
        CHINESE("chinese", "Chinese / Japanese", 70_000_000L, 1_000L);

        public final String id;
        public final String label;
        public final long minModelBytes;
        public final long minDictBytes;

        ScriptPack(String id, String label, long minModelBytes, long minDictBytes) {
            this.id = id;
            this.label = label;
            this.minModelBytes = minModelBytes;
            this.minDictBytes = minDictBytes;
        }
    }

    private static final String HF_LANG_BASE =
            "https://huggingface.co/monkt/paddleocr-onnx/resolve/main/languages/";
    private static final String HF_DET_URL =
            "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    public File getModelsRoot(Context context) {
        return new File(context.getFilesDir(), "paddleocr");
    }

    public File getPackDir(Context context, ScriptPack pack) {
        return new File(getModelsRoot(context), pack.id);
    }

    public File getRecModelFile(Context context, ScriptPack pack) {
        return new File(getPackDir(context, pack), "rec.onnx");
    }

    public File getDictFile(Context context, ScriptPack pack) {
        return new File(getPackDir(context, pack), "dict.txt");
    }

    public File getDetModelFile(Context context) {
        return new File(getModelsRoot(context), "det_v5_mobile.onnx");
    }

    public boolean hasDetModel(Context context) {
        File model = getDetModelFile(context);
        return model.exists() && model.length() >= DET_MIN_BYTES;
    }

    public boolean hasPack(Context context, ScriptPack pack) {
        File model = getRecModelFile(context, pack);
        File dict = getDictFile(context, pack);
        return model.exists() && model.length() >= pack.minModelBytes
                && dict.exists() && dict.length() >= pack.minDictBytes;
    }

    public void ensureDetModel(Context context, @Nullable ProgressCallback callback)
            throws IOException {
        if (hasDetModel(context)) {
            return;
        }
        deleteQuietly(getDetModelFile(context));
        if (callback != null) {
            callback.onProgress(1, 1, "Text detection");
        }
        File root = getModelsRoot(context);
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Cannot create paddleocr model dir");
        }
        Log.i(TAG, "Downloading OCR detection model");
        downloadFile(HF_DET_URL, getDetModelFile(context));
        if (!hasDetModel(context)) {
            throw new IOException("OCR detection model incomplete after download");
        }
    }

    public void ensurePack(Context context, ScriptPack pack, @Nullable ProgressCallback callback)
            throws IOException {
        ensurePacks(context, new ScriptPack[]{pack}, callback);
    }

    public void ensurePacks(Context context, ScriptPack[] packs, @Nullable ProgressCallback callback)
            throws IOException {
        List<ScriptPack> missing = new ArrayList<>();
        for (ScriptPack pack : packs) {
            if (!hasPack(context, pack)) {
                deleteQuietly(getRecModelFile(context, pack));
                deleteQuietly(getDictFile(context, pack));
                missing.add(pack);
            }
        }
        int total = missing.size();
        for (int i = 0; i < missing.size(); i++) {
            ScriptPack pack = missing.get(i);
            if (callback != null) {
                callback.onProgress(i + 1, total, pack.label);
            }
            Log.i(TAG, "Downloading OCR pack " + pack.id);
            downloadPack(context, pack);
            if (!hasPack(context, pack)) {
                throw new IOException("OCR pack incomplete after download: " + pack.id);
            }
        }
    }

    private void downloadPack(Context context, ScriptPack pack) throws IOException {
        File dir = getPackDir(context, pack);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create model dir for " + pack.id);
        }
        downloadFile(HF_LANG_BASE + pack.id + "/rec.onnx", getRecModelFile(context, pack));
        downloadFile(HF_LANG_BASE + pack.id + "/dict.txt", getDictFile(context, pack));
    }

    private void downloadFile(String url, File target) throws IOException {
        File temp = new File(target.getAbsolutePath() + ".partial");
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "HomeLibraryAndroid/1.0")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Download failed " + url + ": HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty body for " + url);
            }
            try (InputStream input = body.byteStream();
                 FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("Cannot replace " + target.getName());
            }
            if (!temp.renameTo(target)) {
                throw new IOException("Cannot finalize " + target.getName());
            }
        } catch (IOException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public interface ProgressCallback {
        void onProgress(int current, int total, @NonNull String packLabel);
    }
}
