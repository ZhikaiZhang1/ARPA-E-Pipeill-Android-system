package com.example.arpa_e;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class VideoSelectionActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private VideoAdapter videoAdapter;
    private List<File> videoFiles = new ArrayList<>();
    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    private File currentDirectory = new File(downloadsDir, "received_frames"); // Change to your folder
    private Uri PlayVideoUri1 = null;
    private Uri PlayVideoUri2 = null;
    private boolean isFirstVideoSelected = false;
    private Stack<File> folderHistory = new Stack<>();  // To track folder navigation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_selection);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        videoAdapter = new VideoAdapter(videoFiles, this::onItemClicked);
        recyclerView.setAdapter(videoAdapter);

        loadFiles(currentDirectory); // Load files from initial directory
    }

    private void loadFiles(File directory) {
        File[] files = directory.listFiles();
        videoFiles.clear(); // Clear current list

        if (files != null) {
            for (File file : files) {
                // If it's a directory, add it to the list of subfolders
                if (file.isDirectory()) {
                    videoFiles.add(file);
                } else if (file.getName().endsWith(".mp4")) { // Only show .mp4 files
                    videoFiles.add(file);
                }
            }
        }

        videoAdapter.notifyDataSetChanged();
    }

    private void onItemClicked(File file) {
        if (file.isDirectory()) {
            // If the user selected a folder, push the current folder onto the stack and load its contents
            folderHistory.push(currentDirectory);
            loadFiles(file);
            currentDirectory = file;
        } else {
            if (!isFirstVideoSelected) {
                // If it's the first video, save the URI
                PlayVideoUri1 = Uri.fromFile(file);
                isFirstVideoSelected = true;
                Toast.makeText(this, "First video selected. Now select the second video.", Toast.LENGTH_SHORT).show();
            } else {
                // If it's the second video, save the URI and proceed to play the videos
                PlayVideoUri2 = Uri.fromFile(file);
                navigateToVideoPlayActivity();
            }
        }
    }

    private void navigateToVideoPlayActivity() {
        if (PlayVideoUri1 != null && PlayVideoUri2 != null) {
            Intent intent = new Intent(VideoSelectionActivity.this, VideoPlayActivity.class);
            intent.putExtra("PlayVideoUri1", PlayVideoUri1.toString());
            intent.putExtra("PlayVideoUri2", PlayVideoUri2.toString());
            startActivity(intent);
        } else {
            Toast.makeText(this, "Both videos must be selected before proceeding.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (!folderHistory.isEmpty()) {
            // If there is a folder in the history stack, navigate back to it
            currentDirectory = folderHistory.pop();
            loadFiles(currentDirectory);
        } else {
            // Otherwise, perform the default back action
            super.onBackPressed();
        }
    }
}
