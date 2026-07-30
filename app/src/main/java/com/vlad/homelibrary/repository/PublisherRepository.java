package com.vlad.homelibrary.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.vlad.homelibrary.data.LibraryDatabase;
import com.vlad.homelibrary.data.Publisher;
import com.vlad.homelibrary.data.PublisherDao;

import java.util.List;

public class PublisherRepository {
    private PublisherDao publisherDao;
    private LiveData<List<Publisher>> allPublishers;

    public PublisherRepository(Application application) {
        LibraryDatabase db = LibraryDatabase.getDatabase(application);
        publisherDao = db.publisherDao();
        allPublishers = publisherDao.getAllPublishers();
    }

    public LiveData<List<Publisher>> getAllPublishers() {
        return allPublishers;
    }

    public LiveData<Publisher> getPublisherById(long id) {
        return publisherDao.getPublisherById(id);
    }

    public LiveData<List<Publisher>> searchPublishers(String query) {
        return publisherDao.searchPublishers(query);
    }

    public void insert(Publisher publisher) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            publisherDao.insert(publisher);
        });
    }

    public void update(Publisher publisher) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            publisherDao.update(publisher);
        });
    }

    public void delete(Publisher publisher) {
        LibraryDatabase.databaseWriteExecutor.execute(() -> {
            publisherDao.delete(publisher);
        });
    }
}
