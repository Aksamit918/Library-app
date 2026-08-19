package com.vlad.homelibrary.data;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Author.class, Publisher.class, Book.class}, version = 3, exportSchema = false)
public abstract class LibraryDatabase extends RoomDatabase {
    private static volatile LibraryDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`author_id` INTEGER NOT NULL, " +
                            "`publisher_id` INTEGER NOT NULL, " +
                            "`isbn` TEXT, " +
                            "`page_count` INTEGER, " +
                            "`cover_image_uri` TEXT, " +
                            "FOREIGN KEY(`author_id`) REFERENCES `authors`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`publisher_id`) REFERENCES `publishers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                            ")"
            );

            database.execSQL(
                    "INSERT INTO `books_new` (`id`, `title`, `author_id`, `publisher_id`, `isbn`, `page_count`, `cover_image_uri`) " +
                            "SELECT `id`, `title`, `author_id`, `publisher_id`, " +
                            "CASE WHEN `isbn` LIKE 'TEMP-%' THEN NULL ELSE `isbn` END, " +
                            "`page_count`, `cover_image_uri` FROM `books`"
            );

            database.execSQL("DROP TABLE `books`");
            database.execSQL("ALTER TABLE `books_new` RENAME TO `books`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_author_id` ON `books` (`author_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_publisher_id` ON `books` (`publisher_id`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_isbn` ON `books` (`isbn`)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`author_id` INTEGER, " +
                            "`publisher_id` INTEGER, " +
                            "`isbn` TEXT, " +
                            "`asin` TEXT, " +
                            "`lccn` TEXT, " +
                            "`oclc` TEXT, " +
                            "`subtitle` TEXT, " +
                            "`original_title` TEXT, " +
                            "`series_name` TEXT, " +
                            "`series_number` TEXT, " +
                            "`secondary_contributors` TEXT, " +
                            "`imprint` TEXT, " +
                            "`publication_year` INTEGER, " +
                            "`edition` TEXT, " +
                            "`printing` TEXT, " +
                            "`language` TEXT, " +
                            "`original_language` TEXT, " +
                            "`format` TEXT, " +
                            "`page_count` INTEGER, " +
                            "`dimensions` TEXT, " +
                            "`weight` TEXT, " +
                            "`dust_jacket` TEXT, " +
                            "`cover_image_uri` TEXT, " +
                            "`genres` TEXT, " +
                            "`tags` TEXT, " +
                            "`dewey` TEXT, " +
                            "`lcc` TEXT, " +
                            "`description` TEXT, " +
                            "`location` TEXT, " +
                            "`condition` TEXT, " +
                            "`signed` INTEGER, " +
                            "`date_acquired` TEXT, " +
                            "`purchase_price` REAL, " +
                            "`acquired_from` TEXT, " +
                            "`reading_status` TEXT, " +
                            "`rating` REAL, " +
                            "`personal_notes` TEXT, " +
                            "FOREIGN KEY(`author_id`) REFERENCES `authors`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`publisher_id`) REFERENCES `publishers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                            ")"
            );

            database.execSQL(
                    "INSERT INTO `books_new` (`id`, `title`, `author_id`, `publisher_id`, `isbn`, `page_count`, `cover_image_uri`) " +
                            "SELECT `id`, `title`, `author_id`, `publisher_id`, `isbn`, `page_count`, `cover_image_uri` FROM `books`"
            );

            database.execSQL("DROP TABLE `books`");
            database.execSQL("ALTER TABLE `books_new` RENAME TO `books`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_author_id` ON `books` (`author_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_publisher_id` ON `books` (`publisher_id`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_isbn` ON `books` (`isbn`)");
        }
    };

    public static LibraryDatabase getDatabase(final android.content.Context context) {
        if (INSTANCE == null) {
            synchronized (LibraryDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = androidx.room.Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    LibraryDatabase.class,
                                    "library_database"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
