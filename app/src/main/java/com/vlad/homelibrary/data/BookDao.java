package com.vlad.homelibrary.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Book book);

    @Query("SELECT * FROM books ORDER BY title ASC")
    LiveData<List<Book>> getAllBooks();

    @Query("SELECT * FROM books WHERE id = :bookId")
    LiveData<Book> getBookById(long bookId);

    @androidx.room.Transaction
    @Query("SELECT * FROM books ORDER BY title ASC")
    LiveData<List<BookAndAuthor>> getBooksAndAuthors();

    @Query("SELECT * FROM books WHERE title LIKE '%' || :searchQuery || '%' OR " +
            "isbn LIKE '%' || :searchQuery || '%' ORDER BY title ASC")
    LiveData<List<Book>> searchBooks(String searchQuery);

    @Query("SELECT * FROM books WHERE publisher_id = :publisherId ORDER BY title ASC")
    LiveData<List<Book>> getBooksByPublisherId(long publisherId);

    @Query("SELECT COUNT(id) FROM books WHERE isbn = :isbn AND id != :currentBookId")
    int checkIsbnExists(String isbn, long currentBookId);

    @androidx.room.Transaction
    @Query("SELECT books.* FROM books INNER JOIN authors ON books.author_id = authors.id " +
            "WHERE books.title LIKE '%' || :searchQuery || '%' " +
            "OR books.isbn LIKE '%' || :searchQuery || '%' " +
            "OR authors.name LIKE '%' || :searchQuery || '%' " +
            "ORDER BY books.title ASC")
    LiveData<List<BookAndAuthor>> searchBooksAndAuthors(String searchQuery);

    @androidx.room.Transaction
    @Query("SELECT books.* FROM books INNER JOIN authors ON books.author_id = authors.id WHERE books.title LIKE '%' || :searchQuery || '%' ORDER BY books.title ASC")
    LiveData<List<BookAndAuthor>> searchByTitle(String searchQuery);

    @androidx.room.Transaction
    @Query("SELECT books.* FROM books INNER JOIN authors ON books.author_id = authors.id WHERE authors.name LIKE '%' || :searchQuery || '%' ORDER BY books.title ASC")
    LiveData<List<BookAndAuthor>> searchByAuthor(String searchQuery);

    @androidx.room.Transaction
    @Query("SELECT books.* FROM books INNER JOIN authors ON books.author_id = authors.id WHERE books.isbn LIKE '%' || :searchQuery || '%' ORDER BY books.title ASC")
    LiveData<List<BookAndAuthor>> searchByIsbn(String searchQuery);

    @Update
    int update(Book book);

    @Delete
    int delete(Book book);
}
