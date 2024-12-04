package com.example.arpa_e;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private List<File> videoFiles;
    private OnItemClickListener listener;
    private Context context;

    public VideoAdapter(List<File> videoFiles, OnItemClickListener listener) {
        this.videoFiles = videoFiles;
        this.listener = listener;
    }

    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(VideoViewHolder holder, int position) {
        File file = videoFiles.get(position);
        holder.bind(file);
    }

    @Override
    public int getItemCount() {
        return videoFiles.size();
    }

    public interface OnItemClickListener {
        void onItemClick(File file);
    }

    public class VideoViewHolder extends RecyclerView.ViewHolder {

        private TextView nameTextView;

        public VideoViewHolder(View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            itemView.setOnClickListener(v -> listener.onItemClick(videoFiles.get(getAdapterPosition())));
        }

        public void bind(File file) {
            nameTextView.setText(file.getName());
        }
    }
}
