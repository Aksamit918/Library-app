package com.vlad.homelibrary.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.vlad.homelibrary.R;
import com.vlad.homelibrary.data.Author;
import com.vlad.homelibrary.data.Book;
import com.vlad.homelibrary.data.LibraryDatabase;
import com.vlad.homelibrary.data.Publisher;
import com.vlad.homelibrary.lookup.BookMetadata;
import com.vlad.homelibrary.lookup.OpenLibraryClient;
import com.vlad.homelibrary.scan.ScanResultParser;
import com.vlad.homelibrary.viewmodel.BookViewModel;

import java.io.File;

public class AddBookActivity extends AppCompatActivity {

    private ImageView imageAddCover;
    private String selectedImagePath = "";
    private EditText editTitle;
    private EditText editAuthor;
    private EditText editIsbn;
    private EditText editPageCount;
    private EditText editAsin;
    private EditText editLccn;
    private EditText editOclc;
    private EditText editSubtitle;
    private EditText editOriginalTitle;
    private EditText editSecondaryContributors;
    private EditText editSeriesName;
    private EditText editSeriesNumber;
    private EditText editPublisher;
    private EditText editImprint;
    private EditText editPublicationYear;
    private EditText editEdition;
    private EditText editPrinting;
    private EditText editLanguage;
    private EditText editOriginalLanguage;
    private EditText editFormat;
    private EditText editDimensions;
    private EditText editWeight;
    private EditText editDustJacket;
    private EditText editGenres;
    private EditText editTags;
    private EditText editDewey;
    private EditText editLcc;
    private EditText editDescription;
    private EditText editLocation;
    private EditText editCondition;
    private EditText editDateAcquired;
    private EditText editPurchasePrice;
    private EditText editAcquiredFrom;
    private EditText editReadingStatus;
    private EditText editRating;
    private EditText editPersonalNotes;
    private CheckBox checkSigned;
    private Button btnSave;
    private Button btnLookupIsbn;
    private ProgressBar progressIsbnLookup;
    private TextInputLayout layoutIsbn;
    private LinearLayout containerDetailedFields;
    private MaterialButtonToggleGroup toggleAddMode;
    private BookViewModel bookViewModel;
    private final OpenLibraryClient openLibraryClient = new OpenLibraryClient();
    private long currentBookId = -1;
    private boolean detailedMode = false;
    private boolean editFormPopulated = false;
    private boolean lookupInProgress = false;
    private Book loadedBook;
    private String loadedPublisherName;

    private final ActivityResultLauncher<String> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            resultUri -> {
                if (resultUri != null) {
                    imageAddCover.setImageURI(resultUri);
                    selectedImagePath = copyImageToInternalStorage(resultUri);
                }
            }
    );

    private final ActivityResultLauncher<android.content.Intent> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }

                android.content.Intent data = result.getData();
                String typeName = data.getStringExtra(ScanResultParser.EXTRA_SCANNED_TYPE);
                String value = data.getStringExtra(ScanResultParser.EXTRA_SCANNED_VALUE);
                if (value == null || value.isBlank()) {
                    value = data.getStringExtra(ScanResultParser.EXTRA_SCANNED_ISBN);
                }
                if (value == null || value.isBlank()) {
                    return;
                }

                ScanResultParser.ScanType type = ScanResultParser.ScanType.ISBN;
                if (typeName != null) {
                    try {
                        type = ScanResultParser.ScanType.valueOf(typeName);
                    } catch (IllegalArgumentException ignored) {
                        type = ScanResultParser.parseBarcode(value);
                    }
                }

                switch (type) {
                    case ISBN:
                        editIsbn.setText(value);
                        lookupIsbnMetadata();
                        break;
                    case ASIN:
                        setDetailedMode(true);
                        if (editAsin.getText().toString().trim().isEmpty()) {
                            editAsin.setText(value);
                        }
                        Toast.makeText(this, R.string.scan_asin_filled, Toast.LENGTH_SHORT).show();
                        break;
                    case TITLE_TEXT:
                        if (editTitle.getText().toString().trim().isEmpty()) {
                            editTitle.setText(value);
                        } else {
                            Toast.makeText(this, getString(R.string.scan_title_suggestion, value), Toast.LENGTH_LONG).show();
                        }
                        break;
                    case OTHER:
                    default:
                        Toast.makeText(this, getString(R.string.scan_other_code, value), Toast.LENGTH_LONG).show();
                        break;
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_book);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        for (int i = 0; i < toolbar.getChildCount(); i++) {
            if (toolbar.getChildAt(i) instanceof android.widget.ImageButton) {
                ViewAnimator.applyPressAnimation(toolbar.getChildAt(i));
                break;
            }
        }

        bindViews();
        bookViewModel = new ViewModelProvider(this).get(BookViewModel.class);

        ViewAnimator.applyPressAnimation(btnSave);
        ViewAnimator.applyPressAnimation(imageAddCover);

        setDetailedMode(false);

        toggleAddMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            setDetailedMode(checkedId == R.id.btn_mode_detailed);
        });

        btnSave.setOnClickListener(v -> saveBook());
        imageAddCover.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));
        layoutIsbn.setEndIconOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, ScannerActivity.class);
            scannerLauncher.launch(intent);
        });
        btnLookupIsbn.setOnClickListener(v -> lookupIsbnMetadata());

        android.content.Intent intent = getIntent();
        if (intent.hasExtra("EXTRA_ID")) {
            currentBookId = intent.getLongExtra("EXTRA_ID", -1);
            btnSave.setText(R.string.update_book);
            toolbar.setTitle(R.string.edit_book);

            bookViewModel.getBookById(currentBookId).observe(this, book -> {
                if (book != null && !editFormPopulated) {
                    populateForm(book);
                    editFormPopulated = true;
                }
            });
        }
    }

    private void bindViews() {
        editTitle = findViewById(R.id.edit_title);
        editAuthor = findViewById(R.id.edit_author);
        editIsbn = findViewById(R.id.edit_isbn);
        editPageCount = findViewById(R.id.edit_page_count);
        editAsin = findViewById(R.id.edit_asin);
        editLccn = findViewById(R.id.edit_lccn);
        editOclc = findViewById(R.id.edit_oclc);
        editSubtitle = findViewById(R.id.edit_subtitle);
        editOriginalTitle = findViewById(R.id.edit_original_title);
        editSecondaryContributors = findViewById(R.id.edit_secondary_contributors);
        editSeriesName = findViewById(R.id.edit_series_name);
        editSeriesNumber = findViewById(R.id.edit_series_number);
        editPublisher = findViewById(R.id.edit_publisher);
        editImprint = findViewById(R.id.edit_imprint);
        editPublicationYear = findViewById(R.id.edit_publication_year);
        editEdition = findViewById(R.id.edit_edition);
        editPrinting = findViewById(R.id.edit_printing);
        editLanguage = findViewById(R.id.edit_language);
        editOriginalLanguage = findViewById(R.id.edit_original_language);
        editFormat = findViewById(R.id.edit_format);
        editDimensions = findViewById(R.id.edit_dimensions);
        editWeight = findViewById(R.id.edit_weight);
        editDustJacket = findViewById(R.id.edit_dust_jacket);
        editGenres = findViewById(R.id.edit_genres);
        editTags = findViewById(R.id.edit_tags);
        editDewey = findViewById(R.id.edit_dewey);
        editLcc = findViewById(R.id.edit_lcc);
        editDescription = findViewById(R.id.edit_description);
        editLocation = findViewById(R.id.edit_location);
        editCondition = findViewById(R.id.edit_condition);
        editDateAcquired = findViewById(R.id.edit_date_acquired);
        editPurchasePrice = findViewById(R.id.edit_purchase_price);
        editAcquiredFrom = findViewById(R.id.edit_acquired_from);
        editReadingStatus = findViewById(R.id.edit_reading_status);
        editRating = findViewById(R.id.edit_rating);
        editPersonalNotes = findViewById(R.id.edit_personal_notes);
        checkSigned = findViewById(R.id.check_signed);
        btnSave = findViewById(R.id.btn_save);
        btnLookupIsbn = findViewById(R.id.btn_lookup_isbn);
        progressIsbnLookup = findViewById(R.id.progress_isbn_lookup);
        layoutIsbn = findViewById(R.id.layout_isbn);
        imageAddCover = findViewById(R.id.image_add_cover);
        containerDetailedFields = findViewById(R.id.container_detailed_fields);
        toggleAddMode = findViewById(R.id.toggle_add_mode);
    }

    private void lookupIsbnMetadata() {
        if (lookupInProgress) {
            return;
        }

        String isbn = OpenLibraryClient.normalizeIsbn(editIsbn.getText().toString());
        if (isbn.isEmpty()) {
            Toast.makeText(this, R.string.lookup_isbn_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setLookupLoading(true);

        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            OpenLibraryClient.LookupResult result = openLibraryClient.lookupByIsbn(isbn);
            String coverPath = null;
            if (result.status == OpenLibraryClient.Status.SUCCESS
                    && result.metadata != null
                    && result.metadata.getCoverUrl() != null
                    && !hasCoverSelected()) {
                File coversDir = new File(getFilesDir(), "covers");
                coverPath = openLibraryClient.downloadCoverToFile(result.metadata.getCoverUrl(), coversDir);
            }

            final String downloadedCoverPath = coverPath;
            runOnUiThread(() -> {
                setLookupLoading(false);
                if (result.status == OpenLibraryClient.Status.SUCCESS && result.metadata != null) {
                    applyMetadataToForm(result.metadata, downloadedCoverPath);
                    Toast.makeText(this, R.string.lookup_success, Toast.LENGTH_SHORT).show();
                } else if (result.status == OpenLibraryClient.Status.NOT_FOUND) {
                    Toast.makeText(this, R.string.lookup_not_found, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.lookup_network_error, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setLookupLoading(boolean loading) {
        lookupInProgress = loading;
        progressIsbnLookup.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLookupIsbn.setEnabled(!loading);
        layoutIsbn.setEnabled(!loading);
    }

    private boolean hasCoverSelected() {
        return selectedImagePath != null && !selectedImagePath.trim().isEmpty();
    }

    private void applyMetadataToForm(BookMetadata metadata, String downloadedCoverPath) {
        fillIfEmpty(editTitle, metadata.getTitle());
        fillIfEmpty(editAuthor, metadata.getAuthor());
        fillIfEmpty(editPublisher, metadata.getPublisher());
        fillIfEmpty(editGenres, metadata.getGenres());

        if (isEmpty(editPageCount) && metadata.getPageCount() != null && metadata.getPageCount() > 0) {
            editPageCount.setText(String.valueOf(metadata.getPageCount()));
        }
        if (isEmpty(editPublicationYear) && metadata.getPublicationYear() != null) {
            editPublicationYear.setText(String.valueOf(metadata.getPublicationYear()));
        }

        if (downloadedCoverPath != null && !downloadedCoverPath.isEmpty() && !hasCoverSelected()) {
            selectedImagePath = downloadedCoverPath;
            imageAddCover.setImageURI(android.net.Uri.parse(downloadedCoverPath));
        }

        if (metadata.hasDetailedFields()) {
            setDetailedMode(true);
        }

        if (metadata.getPublisher() != null && !metadata.getPublisher().isBlank()) {
            loadedPublisherName = textOrNull(editPublisher);
        }
    }

    private static boolean isEmpty(EditText editText) {
        return editText.getText().toString().trim().isEmpty();
    }

    private static void fillIfEmpty(EditText editText, String value) {
        if (value != null && !value.isBlank() && isEmpty(editText)) {
            editText.setText(value);
        }
    }

    private void setDetailedMode(boolean enabled) {
        detailedMode = enabled;
        containerDetailedFields.setVisibility(enabled ? View.VISIBLE : View.GONE);
        int buttonId = enabled ? R.id.btn_mode_detailed : R.id.btn_mode_quick;
        if (toggleAddMode.getCheckedButtonId() != buttonId) {
            toggleAddMode.check(buttonId);
        }
    }

    private void populateForm(Book book) {
        loadedBook = book;
        editTitle.setText(book.getTitle());

        String existingIsbn = book.getIsbn();
        if (existingIsbn != null && !existingIsbn.startsWith("TEMP-")) {
            editIsbn.setText(existingIsbn);
        }

        if (book.getPageCount() != null && book.getPageCount() > 0) {
            editPageCount.setText(String.valueOf(book.getPageCount()));
        }

        selectedImagePath = book.getCoverImageUri();
        if (selectedImagePath != null && !selectedImagePath.isEmpty()) {
            imageAddCover.setImageURI(android.net.Uri.parse(selectedImagePath));
        }

        setText(editAsin, book.getAsin());
        setText(editLccn, book.getLccn());
        setText(editOclc, book.getOclc());
        setText(editSubtitle, book.getSubtitle());
        setText(editOriginalTitle, book.getOriginalTitle());
        setText(editSecondaryContributors, book.getSecondaryContributors());
        setText(editSeriesName, book.getSeriesName());
        setText(editSeriesNumber, book.getSeriesNumber());
        setText(editImprint, book.getImprint());
        if (book.getPublicationYear() != null) {
            editPublicationYear.setText(String.valueOf(book.getPublicationYear()));
        }
        setText(editEdition, book.getEdition());
        setText(editPrinting, book.getPrinting());
        setText(editLanguage, book.getLanguage());
        setText(editOriginalLanguage, book.getOriginalLanguage());
        setText(editFormat, book.getFormat());
        setText(editDimensions, book.getDimensions());
        setText(editWeight, book.getWeight());
        setText(editDustJacket, book.getDustJacket());
        setText(editGenres, book.getGenres());
        setText(editTags, book.getTags());
        setText(editDewey, book.getDewey());
        setText(editLcc, book.getLcc());
        setText(editDescription, book.getDescription());
        setText(editLocation, book.getLocation());
        setText(editCondition, book.getCondition());
        setText(editDateAcquired, book.getDateAcquired());
        if (book.getPurchasePrice() != null) {
            editPurchasePrice.setText(String.valueOf(book.getPurchasePrice()));
        }
        setText(editAcquiredFrom, book.getAcquiredFrom());
        setText(editReadingStatus, book.getReadingStatus());
        if (book.getRating() != null) {
            editRating.setText(String.valueOf(book.getRating()));
        }
        setText(editPersonalNotes, book.getPersonalNotes());
        checkSigned.setChecked(Boolean.TRUE.equals(book.getSigned()));

        if (book.hasDetailedFields()) {
            setDetailedMode(true);
        }

        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            LibraryDatabase db = LibraryDatabase.getDatabase(getApplicationContext());
            String authorName = null;
            String publisherName = null;
            if (book.getAuthorId() != null) {
                Author author = db.authorDao().getAuthorByIdSync(book.getAuthorId());
                if (author != null) {
                    authorName = author.getName();
                }
            }
            if (book.getPublisherId() != null) {
                Publisher publisher = db.publisherDao().getPublisherByIdSync(book.getPublisherId());
                if (publisher != null) {
                    publisherName = publisher.getName();
                }
            }
            final String finalAuthorName = authorName;
            final String finalPublisherName = publisherName;
            runOnUiThread(() -> {
                loadedPublisherName = finalPublisherName;
                if (finalAuthorName != null) {
                    editAuthor.setText(finalAuthorName);
                }
                if (finalPublisherName != null) {
                    editPublisher.setText(finalPublisherName);
                }
            });
        });
    }

    private void saveBook() {
        String title = textOrNull(editTitle);
        if (title == null) {
            Toast.makeText(this, R.string.title_required, Toast.LENGTH_SHORT).show();
            return;
        }

        Book newBook = new Book(title);
        if (currentBookId != -1) {
            newBook.setId(currentBookId);
        }

        String coverPath = selectedImagePath != null ? selectedImagePath.trim() : "";
        newBook.setCoverImageUri(coverPath.isEmpty() ? null : coverPath);
        newBook.setIsbn(textOrNull(editIsbn));

        String pageCountString = editPageCount.getText().toString().trim();
        if (!pageCountString.isEmpty()) {
            try {
                newBook.setPageCount(Integer.parseInt(pageCountString));
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.pages_must_be_number, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String authorName = textOrNull(editAuthor);
        String publisherName = null;

        if (detailedMode) {
            String yearString = editPublicationYear.getText().toString().trim();
            if (!yearString.isEmpty()) {
                try {
                    Integer.parseInt(yearString);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, R.string.year_must_be_number, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String priceString = editPurchasePrice.getText().toString().trim();
            if (!priceString.isEmpty()) {
                try {
                    Double.parseDouble(priceString);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, R.string.price_must_be_number, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String ratingString = editRating.getText().toString().trim();
            if (!ratingString.isEmpty()) {
                try {
                    Float.parseFloat(ratingString);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, R.string.rating_must_be_number, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            applyDetailedFieldsFromForm(newBook);
            publisherName = textOrNull(editPublisher);
        } else if (loadedBook != null) {
            copyDetailedFields(loadedBook, newBook);
            publisherName = textOrNull(editPublisher) != null
                    ? textOrNull(editPublisher)
                    : loadedPublisherName;
            if (!isEmpty(editGenres)) {
                newBook.setGenres(textOrNull(editGenres));
            }
            if (!isEmpty(editPublicationYear)) {
                try {
                    newBook.setPublicationYear(Integer.parseInt(editPublicationYear.getText().toString().trim()));
                } catch (NumberFormatException ignored) {
                    // keep previously loaded year
                }
            }
        } else {
            applyDetailedFieldsFromForm(newBook);
            publisherName = textOrNull(editPublisher);
        }

        layoutIsbn.setError(null);

        bookViewModel.insertBookWithDetails(newBook, authorName, publisherName, new BookViewModel.SaveCallback() {
            @Override
            public void onSuccess() {
                finish();
            }

            @Override
            public void onIsbnError() {
                layoutIsbn.setError(getString(R.string.isbn_already_exists));
            }
        });
    }

    private void applyDetailedFieldsFromForm(Book newBook) {
        newBook.setAsin(textOrNull(editAsin));
        newBook.setLccn(textOrNull(editLccn));
        newBook.setOclc(textOrNull(editOclc));
        newBook.setSubtitle(textOrNull(editSubtitle));
        newBook.setOriginalTitle(textOrNull(editOriginalTitle));
        newBook.setSecondaryContributors(textOrNull(editSecondaryContributors));
        newBook.setSeriesName(textOrNull(editSeriesName));
        newBook.setSeriesNumber(textOrNull(editSeriesNumber));
        newBook.setImprint(textOrNull(editImprint));
        newBook.setEdition(textOrNull(editEdition));
        newBook.setPrinting(textOrNull(editPrinting));
        newBook.setLanguage(textOrNull(editLanguage));
        newBook.setOriginalLanguage(textOrNull(editOriginalLanguage));
        newBook.setFormat(textOrNull(editFormat));
        newBook.setDimensions(textOrNull(editDimensions));
        newBook.setWeight(textOrNull(editWeight));
        newBook.setDustJacket(textOrNull(editDustJacket));
        newBook.setGenres(textOrNull(editGenres));
        newBook.setTags(textOrNull(editTags));
        newBook.setDewey(textOrNull(editDewey));
        newBook.setLcc(textOrNull(editLcc));
        newBook.setDescription(textOrNull(editDescription));
        newBook.setLocation(textOrNull(editLocation));
        newBook.setCondition(textOrNull(editCondition));
        newBook.setDateAcquired(textOrNull(editDateAcquired));
        newBook.setAcquiredFrom(textOrNull(editAcquiredFrom));
        newBook.setReadingStatus(textOrNull(editReadingStatus));
        newBook.setPersonalNotes(textOrNull(editPersonalNotes));
        newBook.setSigned(checkSigned.isChecked() ? Boolean.TRUE : null);

        String yearString = editPublicationYear.getText().toString().trim();
        if (!yearString.isEmpty()) {
            newBook.setPublicationYear(Integer.parseInt(yearString));
        }

        String priceString = editPurchasePrice.getText().toString().trim();
        if (!priceString.isEmpty()) {
            newBook.setPurchasePrice(Double.parseDouble(priceString));
        }

        String ratingString = editRating.getText().toString().trim();
        if (!ratingString.isEmpty()) {
            newBook.setRating(Float.parseFloat(ratingString));
        }
    }

    private void copyDetailedFields(Book source, Book target) {
        target.setAsin(source.getAsin());
        target.setLccn(source.getLccn());
        target.setOclc(source.getOclc());
        target.setSubtitle(source.getSubtitle());
        target.setOriginalTitle(source.getOriginalTitle());
        target.setSecondaryContributors(source.getSecondaryContributors());
        target.setSeriesName(source.getSeriesName());
        target.setSeriesNumber(source.getSeriesNumber());
        target.setImprint(source.getImprint());
        target.setPublicationYear(source.getPublicationYear());
        target.setEdition(source.getEdition());
        target.setPrinting(source.getPrinting());
        target.setLanguage(source.getLanguage());
        target.setOriginalLanguage(source.getOriginalLanguage());
        target.setFormat(source.getFormat());
        target.setDimensions(source.getDimensions());
        target.setWeight(source.getWeight());
        target.setDustJacket(source.getDustJacket());
        target.setGenres(source.getGenres());
        target.setTags(source.getTags());
        target.setDewey(source.getDewey());
        target.setLcc(source.getLcc());
        target.setDescription(source.getDescription());
        target.setLocation(source.getLocation());
        target.setCondition(source.getCondition());
        target.setSigned(source.getSigned());
        target.setDateAcquired(source.getDateAcquired());
        target.setPurchasePrice(source.getPurchasePrice());
        target.setAcquiredFrom(source.getAcquiredFrom());
        target.setReadingStatus(source.getReadingStatus());
        target.setRating(source.getRating());
        target.setPersonalNotes(source.getPersonalNotes());
    }

    private static void setText(EditText editText, String value) {
        if (value != null) {
            editText.setText(value);
        }
    }

    private static String textOrNull(EditText editText) {
        String value = editText.getText().toString().trim();
        return value.isEmpty() ? null : value;
    }

    private String copyImageToInternalStorage(android.net.Uri uri) {
        try {
            String fileName = "book_cover_" + System.currentTimeMillis() + ".jpg";
            java.io.File directory = new java.io.File(getFilesDir(), "covers");
            if (!directory.exists()) directory.mkdirs();
            java.io.File file = new java.io.File(directory, fileName);
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            java.io.OutputStream outputStream = new java.io.FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
