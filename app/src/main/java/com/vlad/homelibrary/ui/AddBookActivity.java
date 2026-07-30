package com.vlad.homelibrary.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputLayout;
import com.vlad.homelibrary.R;
import com.vlad.homelibrary.data.Book;
import com.vlad.homelibrary.viewmodel.BookViewModel;

public class AddBookActivity extends AppCompatActivity {

    private ImageView imageAddCover;
    private String selectedImagePath = "";
    private EditText editTitle;
    private EditText editAuthor;
    private EditText editIsbn;
    private EditText editPageCount;
    private Button btnSave;
    private TextInputLayout layoutIsbn;
    private BookViewModel bookViewModel;
    private long currentBookId = -1;

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
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String scannedIsbn = result.getData().getStringExtra("scanned_isbn");
                    if (scannedIsbn != null) {
                        editIsbn.setText(scannedIsbn);
                    }
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

        editTitle = findViewById(R.id.edit_title);
        editAuthor = findViewById(R.id.edit_author);
        editIsbn = findViewById(R.id.edit_isbn);
        editPageCount = findViewById(R.id.edit_page_count);
        btnSave = findViewById(R.id.btn_save);
        layoutIsbn = findViewById(R.id.layout_isbn);
        imageAddCover = findViewById(R.id.image_add_cover);
        bookViewModel = new ViewModelProvider(this).get(BookViewModel.class);

        ViewAnimator.applyPressAnimation(btnSave);
        ViewAnimator.applyPressAnimation(imageAddCover);

        btnSave.setOnClickListener(v -> saveBook());

        imageAddCover.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));

        layoutIsbn.setEndIconOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, ScannerActivity.class);
            scannerLauncher.launch(intent);
        });

        android.content.Intent intent = getIntent();
        if (intent.hasExtra("EXTRA_ID")) {
            currentBookId = intent.getLongExtra("EXTRA_ID", -1);

            editTitle.setText(intent.getStringExtra("EXTRA_TITLE"));
            editAuthor.setText(intent.getStringExtra("EXTRA_AUTHOR"));
            editIsbn.setText(intent.getStringExtra("EXTRA_ISBN"));

            if (intent.hasExtra("EXTRA_PAGES")) {
                editPageCount.setText(String.valueOf(intent.getIntExtra("EXTRA_PAGES", 0)));
            }

            selectedImagePath = intent.getStringExtra("EXTRA_IMAGE_URI");
            if (selectedImagePath != null && !selectedImagePath.isEmpty()) {
                imageAddCover.setImageURI(android.net.Uri.parse(selectedImagePath));
            }

            btnSave.setText("Update Book");
            toolbar.setTitle("Edit Book");
        }
    }

    private void saveBook() {
        String title = editTitle.getText().toString().trim();
        String authorName = editAuthor.getText().toString().trim();

        if (title.isEmpty() || authorName.isEmpty()) {
            Toast.makeText(this, "Title and Author are required", Toast.LENGTH_SHORT).show();
            return;
        }

        Book newBook = new Book(title);

        if (currentBookId != -1) {
            newBook.setId(currentBookId);
        }

        newBook.setCoverImageUri(selectedImagePath);

        String isbnString = editIsbn.getText().toString().trim();
        if (isbnString.isEmpty()) {
            newBook.setIsbn("TEMP-" + System.currentTimeMillis());
        } else {
            newBook.setIsbn(isbnString);
        }

        String pageCountString = editPageCount.getText().toString().trim();
        if (!pageCountString.isEmpty()) {
            try {
                newBook.setPageCount(Integer.parseInt(pageCountString));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Pages must be a number", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        layoutIsbn.setError(null);

        bookViewModel.insertBookWithDetails(newBook, authorName, new BookViewModel.SaveCallback() {
            @Override
            public void onSuccess() {
                finish();
            }

            @Override
            public void onIsbnError() {
                layoutIsbn.setError("This ISBN already exists in your library!");
            }
        });
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