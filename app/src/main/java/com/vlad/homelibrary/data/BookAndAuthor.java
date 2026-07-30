package com.vlad.homelibrary.data;

import androidx.room.Embedded;
import androidx.room.Relation;

public class BookAndAuthor {
    @Embedded
    public Book book;

    @Relation(
            parentColumn = "author_id",
            entityColumn = "id"
    )
    public Author author;
}
