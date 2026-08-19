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
    private Long authorId;

    @ColumnInfo(name = "publisher_id")
    private Long publisherId;

    private String isbn;

    private String asin;
    private String lccn;
    private String oclc;

    private String subtitle;

    @ColumnInfo(name = "original_title")
    private String originalTitle;

    @ColumnInfo(name = "series_name")
    private String seriesName;

    @ColumnInfo(name = "series_number")
    private String seriesNumber;

    @ColumnInfo(name = "secondary_contributors")
    private String secondaryContributors;

    private String imprint;

    @ColumnInfo(name = "publication_year")
    private Integer publicationYear;

    private String edition;
    private String printing;
    private String language;

    @ColumnInfo(name = "original_language")
    private String originalLanguage;

    private String format;

    @ColumnInfo(name = "page_count")
    private Integer pageCount;

    private String dimensions;
    private String weight;

    @ColumnInfo(name = "dust_jacket")
    private String dustJacket;

    @ColumnInfo(name = "cover_image_uri")
    private String coverImageUri;

    private String genres;
    private String tags;
    private String dewey;
    private String lcc;
    private String description;

    private String location;
    private String condition;
    private Boolean signed;

    @ColumnInfo(name = "date_acquired")
    private String dateAcquired;

    @ColumnInfo(name = "purchase_price")
    private Double purchasePrice;

    @ColumnInfo(name = "acquired_from")
    private String acquiredFrom;

    @ColumnInfo(name = "reading_status")
    private String readingStatus;

    private Float rating;

    @ColumnInfo(name = "personal_notes")
    private String personalNotes;

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

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getAsin() {
        return asin;
    }

    public void setAsin(String asin) {
        this.asin = asin;
    }

    public String getLccn() {
        return lccn;
    }

    public void setLccn(String lccn) {
        this.lccn = lccn;
    }

    public String getOclc() {
        return oclc;
    }

    public void setOclc(String oclc) {
        this.oclc = oclc;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }

    public String getSeriesNumber() {
        return seriesNumber;
    }

    public void setSeriesNumber(String seriesNumber) {
        this.seriesNumber = seriesNumber;
    }

    public String getSecondaryContributors() {
        return secondaryContributors;
    }

    public void setSecondaryContributors(String secondaryContributors) {
        this.secondaryContributors = secondaryContributors;
    }

    public String getImprint() {
        return imprint;
    }

    public void setImprint(String imprint) {
        this.imprint = imprint;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public void setPublicationYear(Integer publicationYear) {
        this.publicationYear = publicationYear;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getPrinting() {
        return printing;
    }

    public void setPrinting(String printing) {
        this.printing = printing;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }

    public void setOriginalLanguage(String originalLanguage) {
        this.originalLanguage = originalLanguage;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public String getDustJacket() {
        return dustJacket;
    }

    public void setDustJacket(String dustJacket) {
        this.dustJacket = dustJacket;
    }

    public String getCoverImageUri() {
        return coverImageUri;
    }

    public void setCoverImageUri(String coverImageUri) {
        this.coverImageUri = coverImageUri;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDewey() {
        return dewey;
    }

    public void setDewey(String dewey) {
        this.dewey = dewey;
    }

    public String getLcc() {
        return lcc;
    }

    public void setLcc(String lcc) {
        this.lcc = lcc;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public Boolean getSigned() {
        return signed;
    }

    public void setSigned(Boolean signed) {
        this.signed = signed;
    }

    public String getDateAcquired() {
        return dateAcquired;
    }

    public void setDateAcquired(String dateAcquired) {
        this.dateAcquired = dateAcquired;
    }

    public Double getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(Double purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public String getAcquiredFrom() {
        return acquiredFrom;
    }

    public void setAcquiredFrom(String acquiredFrom) {
        this.acquiredFrom = acquiredFrom;
    }

    public String getReadingStatus() {
        return readingStatus;
    }

    public void setReadingStatus(String readingStatus) {
        this.readingStatus = readingStatus;
    }

    public Float getRating() {
        return rating;
    }

    public void setRating(Float rating) {
        this.rating = rating;
    }

    public String getPersonalNotes() {
        return personalNotes;
    }

    public void setPersonalNotes(String personalNotes) {
        this.personalNotes = personalNotes;
    }

    public boolean hasDetailedFields() {
        return isPresent(asin) || isPresent(lccn) || isPresent(oclc)
                || isPresent(subtitle) || isPresent(originalTitle)
                || isPresent(seriesName) || isPresent(seriesNumber)
                || isPresent(secondaryContributors) || isPresent(imprint)
                || publicationYear != null || isPresent(edition) || isPresent(printing)
                || isPresent(language) || isPresent(originalLanguage) || isPresent(format)
                || isPresent(dimensions) || isPresent(weight) || isPresent(dustJacket)
                || isPresent(genres) || isPresent(tags) || isPresent(dewey) || isPresent(lcc)
                || isPresent(description) || isPresent(location) || isPresent(condition)
                || signed != null || isPresent(dateAcquired) || purchasePrice != null
                || isPresent(acquiredFrom) || isPresent(readingStatus)
                || rating != null || isPresent(personalNotes);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
