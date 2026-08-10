package com.vlad.homelibrary.lookup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OpenLibraryClient {

    public enum Status {
        SUCCESS,
        NOT_FOUND,
        NETWORK_ERROR
    }

    public static class LookupResult {
        public final Status status;
        public final BookMetadata metadata;

        public LookupResult(Status status, BookMetadata metadata) {
            this.status = status;
            this.metadata = metadata;
        }
    }

    private static final Pattern YEAR_PATTERN = Pattern.compile("(19|20)\\d{2}");
    private static final int MAX_SUBJECTS = 8;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    public static String normalizeIsbn(String rawIsbn) {
        if (rawIsbn == null) {
            return "";
        }
        return rawIsbn.replaceAll("[\\s-]", "").toUpperCase();
    }

    public LookupResult lookupByIsbn(String rawIsbn) {
        String isbn = normalizeIsbn(rawIsbn);
        if (isbn.isEmpty()) {
            return new LookupResult(Status.NOT_FOUND, null);
        }

        HttpUrl baseUrl = HttpUrl.parse("https://openlibrary.org/api/books");
        if (baseUrl == null) {
            return new LookupResult(Status.NETWORK_ERROR, null);
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("bibkeys", "ISBN:" + isbn)
                .addQueryParameter("format", "json")
                .addQueryParameter("jscmd", "data")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "HomeLibraryAndroid/1.0")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new LookupResult(Status.NETWORK_ERROR, null);
            }
            ResponseBody body = response.body();
            if (body == null) {
                return new LookupResult(Status.NETWORK_ERROR, null);
            }

            JSONObject root = new JSONObject(body.string());
            if (root.length() == 0) {
                return new LookupResult(Status.NOT_FOUND, null);
            }

            Iterator<String> keys = root.keys();
            if (!keys.hasNext()) {
                return new LookupResult(Status.NOT_FOUND, null);
            }

            JSONObject bookJson = root.getJSONObject(keys.next());
            BookMetadata metadata = parseBook(bookJson, isbn);
            return new LookupResult(Status.SUCCESS, metadata);
        } catch (Exception e) {
            return new LookupResult(Status.NETWORK_ERROR, null);
        }
    }

    public String downloadCoverToFile(String coverUrl, File destinationDirectory) {
        if (coverUrl == null || coverUrl.isBlank() || destinationDirectory == null) {
            return null;
        }

        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            return null;
        }

        Request request = new Request.Builder()
                .url(coverUrl)
                .header("User-Agent", "HomeLibraryAndroid/1.0")
                .get()
                .build();

        File outputFile = new File(destinationDirectory, "book_cover_" + System.currentTimeMillis() + ".jpg");

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }

            try (InputStream inputStream = body.byteStream();
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }
            return outputFile.getAbsolutePath();
        } catch (IOException e) {
            if (outputFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
            }
            return null;
        }
    }

    private BookMetadata parseBook(JSONObject bookJson, String isbn) throws Exception {
        BookMetadata metadata = new BookMetadata();
        metadata.setIsbn(isbn);

        if (bookJson.has("title") && !bookJson.isNull("title")) {
            metadata.setTitle(bookJson.getString("title").trim());
        }

        if (bookJson.has("authors")) {
            JSONArray authors = bookJson.getJSONArray("authors");
            if (authors.length() > 0) {
                JSONObject author = authors.getJSONObject(0);
                if (author.has("name")) {
                    metadata.setAuthor(author.getString("name").trim());
                }
            }
        }

        if (bookJson.has("publishers")) {
            JSONArray publishers = bookJson.getJSONArray("publishers");
            if (publishers.length() > 0) {
                JSONObject publisher = publishers.getJSONObject(0);
                if (publisher.has("name")) {
                    metadata.setPublisher(publisher.getString("name").trim());
                }
            }
        }

        if (bookJson.has("number_of_pages") && !bookJson.isNull("number_of_pages")) {
            metadata.setPageCount(bookJson.getInt("number_of_pages"));
        }

        if (bookJson.has("publish_date") && !bookJson.isNull("publish_date")) {
            metadata.setPublicationYear(parseYear(bookJson.getString("publish_date")));
        }

        if (bookJson.has("subjects")) {
            metadata.setGenres(joinSubjects(bookJson.getJSONArray("subjects")));
        }

        if (bookJson.has("cover")) {
            JSONObject cover = bookJson.getJSONObject("cover");
            if (cover.has("large")) {
                metadata.setCoverUrl(cover.getString("large"));
            } else if (cover.has("medium")) {
                metadata.setCoverUrl(cover.getString("medium"));
            } else if (cover.has("small")) {
                metadata.setCoverUrl(cover.getString("small"));
            }
        }

        return metadata;
    }

    private static Integer parseYear(String publishDate) {
        if (publishDate == null || publishDate.isBlank()) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(publishDate);
        String year = null;
        while (matcher.find()) {
            year = matcher.group();
        }
        if (year == null) {
            return null;
        }
        try {
            return Integer.parseInt(year);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String joinSubjects(JSONArray subjects) throws Exception {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < subjects.length() && names.size() < MAX_SUBJECTS; i++) {
            JSONObject subject = subjects.getJSONObject(i);
            if (subject.has("name")) {
                String name = subject.getString("name").trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        return String.join(", ", names);
    }
}
