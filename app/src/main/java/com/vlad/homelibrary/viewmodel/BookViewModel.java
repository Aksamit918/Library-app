package com.vlad.homelibrary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.vlad.homelibrary.data.Author;
import com.vlad.homelibrary.data.AuthorDao;
import com.vlad.homelibrary.data.Book;
import com.vlad.homelibrary.data.BookAndAuthor;
import com.vlad.homelibrary.data.BookDao;
import com.vlad.homelibrary.data.LibraryDatabase;
import com.vlad.homelibrary.data.Publisher;
import com.vlad.homelibrary.data.PublisherDao;
import com.vlad.homelibrary.repository.BookRepository;

import java.util.List;

public class BookViewModel extends AndroidViewModel {
    private BookRepository bookRepository;
    private LiveData<List<Book>> allBooks;

    public BookViewModel(@NonNull Application application) {
        super(application);
        bookRepository = new BookRepository(application);
        allBooks = bookRepository.getAllBooks();
    }

    public LiveData<List<Book>> getAllBooks() {
        return allBooks;
    }

    public LiveData<Book> getBookById(long id) {
        return bookRepository.getBookById(id);
    }

    public LiveData<List<Book>> searchBooks(String query) {
        return bookRepository.searchBooks(query);
    }

    public LiveData<List<Book>> getBooksByPublisherId(long publisherId) {
        return bookRepository.getBooksByPublisherId(publisherId);
    }

    public LiveData<List<BookAndAuthor>> getBooksAndAuthors() {
        return bookRepository.getBooksAndAuthors();
    }

    public LiveData<List<BookAndAuthor>> searchBooksAndAuthors(String query) {
        return bookRepository.searchBooksAndAuthors(query);
    }

    public LiveData<List<BookAndAuthor>> searchByTitle(String query) { return bookRepository.searchByTitle(query); }

    public LiveData<List<BookAndAuthor>> searchByAuthor(String query) { return bookRepository.searchByAuthor(query); }

    public LiveData<List<BookAndAuthor>> searchByIsbn(String query) { return bookRepository.searchByIsbn(query); }

    public void insert(Book book) {
        bookRepository.insert(book);
    }

    public void insertBookWithDetails(Book book, String fullAuthorName, String publisherName, SaveCallback callback) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {

            LibraryDatabase db = LibraryDatabase.getDatabase(getApplication());
            AuthorDao authorDao = db.authorDao();
            PublisherDao publisherDao = db.publisherDao();
            BookDao bookDao = db.bookDao();

            int duplicateCount = 0;
            String isbn = book.getIsbn();
            if (isbn != null && !isbn.isBlank()) {
                duplicateCount = bookDao.checkIsbnExists(isbn, book.getId());
            }

            if (duplicateCount > 0) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onIsbnError);
                return;
            }

            if (fullAuthorName != null && !fullAuthorName.isBlank()) {
                Author existingAuthor = authorDao.getAuthorByNameSync(fullAuthorName.trim());
                if (existingAuthor != null) {
                    book.setAuthorId(existingAuthor.getId());
                } else {
                    book.setAuthorId(authorDao.insert(new Author(fullAuthorName.trim())));
                }
            } else {
                book.setAuthorId(null);
            }

            if (publisherName != null && !publisherName.isBlank()) {
                Publisher existingPub = publisherDao.getPublisherByNameSync(publisherName.trim());
                if (existingPub != null) {
                    book.setPublisherId(existingPub.getId());
                } else {
                    book.setPublisherId(publisherDao.insert(new Publisher(publisherName.trim())));
                }
            } else {
                book.setPublisherId(null);
            }

            if (book.getId() != 0) {
                bookDao.update(book);
            } else {
                bookDao.insert(book);
            }

            new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onSuccess);
        });
    }

    public void update(Book book) {
        bookRepository.update(book);
    }

    public void delete(Book book) {
        bookRepository.delete(book);
    }

    public interface SaveCallback {
        void onSuccess();
        void onIsbnError();
    }
}