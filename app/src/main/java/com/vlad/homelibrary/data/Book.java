package com.vlad.homelibrary.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "books",
        indices = {
                @Index(value = {"author_id"}),
                @Index(value = {"publisher_id"}),
                @Index(value = {"isbn"}, unique = true)
        },
        foreignKeys = {
                @ForeignKey(
                        entity = Author.class,
                        parentColumns = "id",
                        childColumns = "author_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Publisher.class,
                        parentColumns = "id",
                        childColumns = "publisher_id",
                        onDelete = ForeignKey.CASCADE
                )
        }
)
public class Book {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String title;

    @ColumnInfo(name = "author_id")
    private long authorId;

    @ColumnInfo(name = "publisher_id")
    private long publisherId;

    @NonNull
    private String isbn;

    @ColumnInfo(name = "page_count")
    private Integer pageCount;

    @ColumnInfo(name = "cover_image_uri")
    private String coverImageUri;

    public Book(@NonNull String title) {
        this.title = title;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    public long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(long authorId) {
        this.authorId = authorId;
    }

    public long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(long publisherId) {
        this.publisherId = publisherId;
    }

    @NonNull
    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(@NonNull String isbn) {
        this.isbn = isbn;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public String getCoverImageUri() {
        return coverImageUri;
    }

    public void setCoverImageUri(String coverImageUri) {
        this.coverImageUri = coverImageUri;
    }
}
