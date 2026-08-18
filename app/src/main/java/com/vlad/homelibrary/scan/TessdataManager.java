package com.vlad.homelibrary.scan;

import android.content.Context;

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

public class TessdataManager {

    private static final long MIN_TRAINEDDATA_BYTES = 50_000L;
    private static final String[] TESSDATA_URLS = {
            "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/",
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"
    };

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    public File getTessParentDir(Context context) {
        return context.getFilesDir();
    }

    public File getTessdataDir(Context context) {
        return new File(getTessParentDir(context), "tessdata");
    }

    public boolean hasLanguageData(Context context, String[] codes) {
        File dir = getTessdataDir(context);
        for (String code : codes) {
            File file = new File(dir, code + ".traineddata");
            if (!file.exists() || file.length() < MIN_TRAINEDDATA_BYTES) {
                return false;
            }
        }
        return true;
    }

    public void ensureLanguageData(Context context, String[] codes, ProgressCallback callback)
            throws IOException {
        File dir = getTessdataDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create tessdata directory");
        }

        List<String> missing = new ArrayList<>();
        for (String code : codes) {
            File file = new File(dir, code + ".traineddata");
            if (!file.exists() || file.length() < MIN_TRAINEDDATA_BYTES) {
                if (file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                missing.add(code);
            }
        }

        int total = missing.size();
        for (int i = 0; i < missing.size(); i++) {
            String code = missing.get(i);
            if (callback != null) {
                callback.onProgress(i + 1, total, code);
            }
            downloadLanguage(dir, code);
        }
    }

    private void downloadLanguage(File tessdataDir, String code) throws IOException {
        File target = new File(tessdataDir, code + ".traineddata");
        IOException lastError = null;
        for (String base : TESSDATA_URLS) {
            try {
                downloadTo(base + code + ".traineddata", target);
                if (target.exists() && target.length() >= MIN_TRAINEDDATA_BYTES) {
                    return;
                }
            } catch (IOException e) {
                lastError = e;
                if (target.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                }
            }
        }
        throw lastError != null
                ? lastError
                : new IOException("Failed to download " + code);
    }

    private void downloadTo(String url, File target) throws IOException {
        File temp = new File(target.getAbsolutePath() + ".partial");
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "HomeLibraryAndroid/1.0")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download " + url + ": HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty body for " + url);
            }

            try (InputStream inputStream = body.byteStream();
                 FileOutputStream outputStream = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }

            if (target.exists() && !target.delete()) {
                throw new IOException("Cannot replace " + target.getName());
            }
            if (!temp.renameTo(target)) {
                throw new IOException("Cannot finalize " + target.getName());
            }
        } catch (IOException e) {
            if (temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
            throw e;
        }
    }

    public interface ProgressCallback {
        void onProgress(int current, int total, String languageCode);
    }
}
