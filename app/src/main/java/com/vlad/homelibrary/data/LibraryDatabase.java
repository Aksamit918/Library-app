package com.vlad.homelibrary.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Author.class, Publisher.class, Book.class}, version = 1, exportSchema = false)
public abstract class LibraryDatabase extends RoomDatabase {
    private static volatile LibraryDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);
    public static LibraryDatabase getDatabase(final android.content.Context context) {
        if (INSTANCE == null) {
            synchronized (LibraryDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = androidx.room.Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    LibraryDatabase.class,
                                    "library_database"
                            )
                            .build();
                }
            }
        }
        return INSTANCE;
    }
    public abstract AuthorDao authorDao();
    public abstract BookDao bookDao();
    public abstract PublisherDao publisherDao();
}
