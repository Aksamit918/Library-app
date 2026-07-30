package com.vlad.homelibrary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.vlad.homelibrary.data.Publisher;
import com.vlad.homelibrary.repository.PublisherRepository;

import java.util.List;

public class PublisherViewModel extends AndroidViewModel {
    private PublisherRepository publisherRepository;
    private LiveData<List<Publisher>> allPublishers;

    public PublisherViewModel(@NonNull Application application) {
        super(application);
        publisherRepository = new PublisherRepository(application);
        allPublishers = publisherRepository.getAllPublishers();
    }

    public LiveData<List<Publisher>> getAllPublishers() {
        return allPublishers;
    }

    public LiveData<Publisher> getPublisherById(long id) {
        return publisherRepository.getPublisherById(id);
    }

    public LiveData<List<Publisher>> searchPublishers(String query) {
        return publisherRepository.searchPublishers(query);
    }

    public void insert(Publisher publisher) {
        publisherRepository.insert(publisher);
    }

    public void update(Publisher publisher) {
        publisherRepository.update(publisher);
    }

    public void delete(Publisher publisher) {
        publisherRepository.delete(publisher);
    }
}
