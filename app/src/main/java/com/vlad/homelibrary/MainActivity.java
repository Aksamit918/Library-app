package com.vlad.homelibrary;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;
import com.vlad.homelibrary.adapter.BookAdapter;
import com.vlad.homelibrary.data.BookAndAuthor;
import com.vlad.homelibrary.ui.AddBookActivity;
import com.vlad.homelibrary.ui.ViewAnimator;
import com.vlad.homelibrary.viewmodel.BookViewModel;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BookViewModel bookViewModel;

    private BookAdapter adapter;

    private LiveData<List<BookAndAuthor>> currentBooksLiveData;

    private int currentFilterMode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recycler_view_books);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(bookAndAuthor -> {
            Intent intent = new Intent(MainActivity.this, AddBookActivity.class);
            intent.putExtra("EXTRA_ID", bookAndAuthor.book.getId());
            startActivity(intent);
        });

        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fab = findViewById(R.id.fab_add_book);
        ViewAnimator.applyPressAnimation(fab);
        fab.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddBookActivity.class)));

        bookViewModel = new ViewModelProvider(this).get(BookViewModel.class);

        observeData(bookViewModel.getBooksAndAuthors());

        EditText editSearch = findViewById(R.id.edit_search);
        TextInputLayout layoutSearch = findViewById(R.id.layout_search);

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                triggerSearch(s.toString().trim());
            }
        });

        layoutSearch.setEndIconOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(MainActivity.this, v);
            popup.getMenu().add(0, 0, 0, "Search All");
            popup.getMenu().add(0, 1, 1, "Title Only");
            popup.getMenu().add(0, 2, 2, "Author Only");
            popup.getMenu().add(0, 3, 3, "ISBN Only");

            popup.setOnMenuItemClickListener(item -> {
                currentFilterMode = item.getItemId();
                layoutSearch.setHint(item.getTitle());

                triggerSearch(editSearch.getText().toString().trim());
                return true;
            });
            popup.show();
        });

        setupSwipeToDelete(recyclerView);
    }

    private void observeData(LiveData<List<BookAndAuthor>> newLiveData) {
        if (currentBooksLiveData != null) {
            currentBooksLiveData.removeObservers(this);
        }
        currentBooksLiveData = newLiveData;
        currentBooksLiveData.observe(this, bookAndAuthors -> adapter.setBooks(bookAndAuthors));
    }

    private void setupSwipeToDelete(RecyclerView recyclerView) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                BookAndAuthor deletedItem = adapter.getBookAt(viewHolder.getAdapterPosition());
                bookViewModel.delete(deletedItem.book);
                Toast.makeText(MainActivity.this, "Book deleted", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                View itemView = viewHolder.itemView;
                android.graphics.drawable.ColorDrawable background = new android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#EF5350"));

                if (dX > 0) {
                    background.setBounds(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + ((int) dX), itemView.getBottom());
                } else if (dX < 0) {
                    background.setBounds(itemView.getRight() + ((int) dX), itemView.getTop(), itemView.getRight(), itemView.getBottom());
                } else {
                    background.setBounds(0, 0, 0, 0);
                }
                background.draw(c);
            }
        }).attachToRecyclerView(recyclerView);
    }

    private void triggerSearch(String query) {
        if (query.isEmpty()) {
            observeData(bookViewModel.getBooksAndAuthors());
            return;
        }

        switch (currentFilterMode) {
            case 1: observeData(bookViewModel.searchByTitle(query)); break;
            case 2: observeData(bookViewModel.searchByAuthor(query)); break;
            case 3: observeData(bookViewModel.searchByIsbn(query)); break;
            default: observeData(bookViewModel.searchBooksAndAuthors(query)); break;
        }
    }
}