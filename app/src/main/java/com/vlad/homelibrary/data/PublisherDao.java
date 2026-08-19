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
public interface PublisherDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Publisher publisher);

    @Query("SELECT * FROM publishers ORDER BY name ASC")
    LiveData<List<Publisher>> getAllPublishers();

    @Query("SELECT * FROM publishers WHERE id = :publisherId")
    LiveData<Publisher> getPublisherById(long publisherId);

    @Query("SELECT * FROM publishers WHERE id = :publisherId LIMIT 1")
    Publisher getPublisherByIdSync(long publisherId);

    @Query("SELECT * FROM publishers WHERE name = :name LIMIT 1")
    Publisher getPublisherByNameSync(String name);

    @Query("SELECT * FROM publishers WHERE name LIKE '%' || :searchQuery || '%'")
    LiveData<List<Publisher>> searchPublishers(String searchQuery);

    @Update
    int update(Publisher publisher);

    @Delete
    int delete(Publisher publisher);
}
