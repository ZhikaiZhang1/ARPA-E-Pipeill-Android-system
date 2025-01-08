package com.example.arpa_e;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;
import androidx.core.content.ContextCompat;

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

        holder.itemView.setOnClickListener(v -> {
            notifyDataSetChanged(); // Refresh to remove highlights from other items
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorSelected));
            listener.onItemClick(file);
        });
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.fileIcon);
            holder.name = convertView.findViewById(R.id.nameTextView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        File currentFile = videoFiles.get(position);
        holder.name.setText(currentFile.getName());

        // Check if the item is a folder or a file
        if (currentFile.isDirectory()) {
            holder.icon.setImageResource(R.drawable.ic_folder); // Folder icon
        } else if (currentFile.isFile()) {
            if (isVideoFile(currentFile)) {
                holder.icon.setImageResource(R.drawable.play_video_icon); // Video file icon
            } else {
                holder.icon.setImageResource(R.drawable.file_place_holder); // Default icon for non-video files
            }
        }

        return convertView;
    }

    private boolean isVideoFile(File file) {
        String[] videoExtensions = {".mp4", ".mkv", ".avi"};
        for (String ext : videoExtensions) {
            if (file.getName().toLowerCase().endsWith(ext)) {
                return true;
            }
        }
        return false;
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

    static class ViewHolder {
        ImageView icon;
        TextView name;
    }
}
