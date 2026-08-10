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
public interface AuthorDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Author author);

    @Query("SELECT * FROM authors ORDER BY name ASC")
    LiveData<List<Author>> getAllAuthors();

    @Query("SELECT * FROM authors WHERE id = :authorId")
    LiveData<Author> getAuthorById(long authorId);

    @Query("SELECT * FROM authors WHERE id = :authorId LIMIT 1")
    Author getAuthorByIdSync(long authorId);

    @Query("SELECT * FROM authors WHERE name LIKE '%' || :searchQuery || '%' ORDER BY name ASC")
    LiveData<List<Author>> searchAuthors(String searchQuery);

    @Query("SELECT * FROM authors WHERE name = :name LIMIT 1")
    Author getAuthorByNameSync(String name);

    @Update
    int update(Author author);

    @Delete
    int delete(Author author);
}
