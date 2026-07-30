package com.vlad.homelibrary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.vlad.homelibrary.data.Book;
import com.vlad.homelibrary.data.BookAndAuthor;
import com.vlad.homelibrary.data.BookDao;
import com.vlad.homelibrary.data.LibraryDatabase;

import java.util.List;

public class BookRepository {
    private BookDao bookDao;
    private LiveData<List<Book>> allBooks;
    public BookRepository(Application application) {
        LibraryDatabase db = LibraryDatabase.getDatabase(application);
        bookDao = db.bookDao();
        allBooks = bookDao.getAllBooks();
    }

    public LiveData<List<Book>> getAllBooks() {
        return allBooks;
    }

    public LiveData<Book> getBookById(long id) {
        return bookDao.getBookById(id);
    }

    public LiveData<List<Book>> searchBooks(String query) {
        return bookDao.searchBooks(query);
    }

    public LiveData<List<Book>> getBooksByPublisherId(long publisherId) {
        return bookDao.getBooksByPublisherId(publisherId);
    }

    public LiveData<List<BookAndAuthor>> getBooksAndAuthors() {
        return bookDao.getBooksAndAuthors();
    }

    public LiveData<List<BookAndAuthor>> searchBooksAndAuthors(String query) {
        return bookDao.searchBooksAndAuthors(query);
    }

    public LiveData<List<BookAndAuthor>> searchByTitle(String query) { return bookDao.searchByTitle(query); }

    public LiveData<List<BookAndAuthor>> searchByAuthor(String query) { return bookDao.searchByAuthor(query); }

    public LiveData<List<BookAndAuthor>> searchByIsbn(String query) { return bookDao.searchByIsbn(query); }

    public void insert(Book book) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            bookDao.insert(book);
        });
    }

    public void update(Book book) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            bookDao.update(book);
        });
    }

    public void delete(Book book) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            bookDao.delete(book);
        });
    }
}
