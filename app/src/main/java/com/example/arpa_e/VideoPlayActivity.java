package com.example.arpa_e;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.VideoView;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.File;


public class VideoPlayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_play);

        VideoView videoView1 = findViewById(R.id.videoView1);
        VideoView videoView2 = findViewById(R.id.videoView2);
//        videoView.setVisibility(View.VISIBLE);  // Make the VideoView visible
//        videoView.setVideoURI(videoUri);
//        videoView.requestFocus();
//        videoView.start();

        // Get the video URIs from the intent
        String videoUri1 = getIntent().getStringExtra("PlayVideoUri1");
        String videoUri2 = getIntent().getStringExtra("PlayVideoUri2");


        String filename1 = Uri.parse(videoUri1).getLastPathSegment();
        String filename2 = Uri.parse(videoUri2).getLastPathSegment();

        // Extract timestamps
        long timestamp1 = extractTimestampFromFilename(filename1);
        long timestamp2 = extractTimestampFromFilename(filename2);
        if (timestamp1 == -1 || timestamp2 == -1) {
            // Invalid filenames
            Log.d("playvideoLog", "time error, filename1: " + filename1+" timestamp1: " + timestamp1 + " and filename2: " + filename2+" timestamp2: "+timestamp2);

            return;
        }

        long delay = Math.abs(timestamp1 - timestamp2);

        if (timestamp1 < timestamp2) {
            // Video 1 is earlier
            playVideo(videoView1, Uri.parse(videoUri1));
            new Handler().postDelayed(() -> playVideo(videoView2, Uri.parse(videoUri2)), delay);
        } else {
            // Video 2 is earlier
            playVideo(videoView2, Uri.parse(videoUri2));
            new Handler().postDelayed(() -> playVideo(videoView1, Uri.parse(videoUri1)), delay);
        }
    }
    private void playVideo(VideoView videoView, Uri videoUri) {
        videoView.setVideoURI(videoUri);
        videoView.start();
    }
    public static long extractTimestampFromFilename(String filename) {
        // Example: video_converted_2024-12-04-14-30-45-123
        File file = new File(filename);
        String fileName = file.getName();
        String[] parts = fileName.split("_");
        if (parts.length < 3) return -1;

        String timestamp = parts[2]; // Extract the timestamp portion
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
        try {
            Date date = format.parse(timestamp);
            return date.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return -1;
        }
    }
}

