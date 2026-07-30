package com.vlad.homelibrary.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "publishers", indices = {@Index(value = {"name"}, unique = true)})
public class Publisher {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String name;

    public Publisher(String name) {
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }
}
