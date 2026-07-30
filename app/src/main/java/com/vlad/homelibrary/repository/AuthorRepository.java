package com.vlad.homelibrary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.vlad.homelibrary.data.Author;
import com.vlad.homelibrary.data.AuthorDao;
import com.vlad.homelibrary.data.LibraryDatabase;

import java.util.List;

public class AuthorRepository {
    private AuthorDao authorDao;
    private LiveData<List<Author>> allAuthors;

    public AuthorRepository(Application application) {
        LibraryDatabase db = LibraryDatabase.getDatabase(application);
        authorDao = db.authorDao();
        allAuthors = authorDao.getAllAuthors();
    }

    public LiveData<List<Author>> getAllAuthors() {
        return allAuthors;
    }

    public LiveData<Author> getAuthorById(long id) {
        return authorDao.getAuthorById(id);
    }

    public LiveData<List<Author>> searchAuthors(String query) {
        return authorDao.searchAuthors(query);
    }

    public void insert(Author author) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            authorDao.insert(author);
        });
    }

    public void update(Author author) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            authorDao.update(author);
        });
    }

    public void delete(Author author) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            authorDao.delete(author);
        });
    }
}
