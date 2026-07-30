package com.vlad.homelibrary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.vlad.homelibrary.data.Author;
import com.vlad.homelibrary.repository.AuthorRepository;

import java.util.List;

public class AuthorViewModel extends AndroidViewModel {
    private AuthorRepository authorRepository;
    private LiveData<List<Author>> allAuthors;

    public AuthorViewModel(@NonNull Application application) {
        super(application);
        authorRepository = new AuthorRepository(application);
        allAuthors = authorRepository.getAllAuthors();
    }

    public LiveData<List<Author>> getAllAuthors() {
        return allAuthors;
    }

    public LiveData<Author> getAuthorById(long id) {
        return authorRepository.getAuthorById(id);
    }

    public LiveData<List<Author>> searchAuthors(String query) {
        return authorRepository.searchAuthors(query);
    }

    public void insert(Author author) {
        authorRepository.insert(author);
    }

    public void update(Author author) {
        authorRepository.update(author);
    }

    public void delete(Author author) {
        authorRepository.delete(author);
    }
}
