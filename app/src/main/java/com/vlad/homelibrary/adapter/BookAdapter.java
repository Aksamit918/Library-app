package com.vlad.homelibrary.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vlad.homelibrary.R;
import com.vlad.homelibrary.data.BookAndAuthor;

import java.util.ArrayList;
import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(BookAndAuthor bookAndAuthor);
    }

    private OnItemClickListener listener;
    private List<BookAndAuthor> books = new ArrayList<>();

    public BookAndAuthor getBookAt(int position) {
        return books.get(position);
    }

    public void setBooks(List<BookAndAuthor> books) {
        this.books = books;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        BookAndAuthor currentItem = books.get(position);

        holder.textTitle.setText(currentItem.book.getTitle());
        holder.textAuthor.setText(currentItem.author.getName());
        holder.textIsbn.setText("ISBN: " + currentItem.book.getIsbn());

        if (currentItem.book.getCoverImageUri() != null && !currentItem.book.getCoverImageUri().isEmpty()) {
            com.bumptech.glide.Glide.with(holder.itemView.getContext())
                    .load(currentItem.book.getCoverImageUri())
                    .into(holder.imageCover);
        } else {
            com.bumptech.glide.Glide.with(holder.itemView.getContext())
                    .load(R.drawable.ic_book)
                    .into(holder.imageCover);
        }

        if (currentItem.book.getPageCount() != null && currentItem.book.getPageCount() > 0) {
            holder.textPageCount.setText(currentItem.book.getPageCount() + " pages");
            holder.textPageCount.setVisibility(View.VISIBLE);
        } else {
            holder.textPageCount.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(currentItem);
            }
        });

        com.vlad.homelibrary.ui.ViewAnimator.applyPressAnimation(holder.itemView);
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    class BookViewHolder extends RecyclerView.ViewHolder {
        private TextView textTitle;
        private TextView textAuthor;
        private ImageView imageCover;
        private TextView textIsbn;
        private TextView textPageCount;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_book_title);
            textAuthor = itemView.findViewById(R.id.text_book_author);
            textIsbn = itemView.findViewById(R.id.text_book_isbn);
            textPageCount = itemView.findViewById(R.id.text_book_page_count);
            imageCover = itemView.findViewById(R.id.image_book_cover);
        }
    }
}
