package com.example.arpa_e;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.VideoView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.arthenica.mobileffmpeg.FFmpeg;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {
    private static final String ESP32_HOSTNAME = "esp32-stream.local"; // ESP32 mDNS hostname
    private String esp32Ip = null; // Store the discovered IP here
    private static final String ESP32_URL = "http://192.168.107.165:1000/video"; // Change this to your ESP32's IP address
    private VideoView videoView;

    // ActivityResultLauncher for the create file action
    private final ActivityResultLauncher<Intent> createFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        downloadAndSaveVideo(uri, "http://" + esp32Ip + ":1000/video");
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.videoView);
        discoverESP32Ip();
    }

    public void downloadVideo(View view) {
        saveFile("http://" + esp32Ip + ":1000/video");
    }

    private void saveFile(String videoUrl) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/avi"); // MIME type for your video
        intent.putExtra(Intent.EXTRA_TITLE, "esp32video.avi");
        createFileLauncher.launch(intent);
    }

    private void downloadAndSaveVideo(Uri uri, String videoUrl) {
        new Thread(() -> {
            try {
                Log.d("VideoDownload", "Attempting to connect to ESP32 at " + videoUrl + " at address: " + esp32Ip);
//                HttpURLConnection connection = (HttpURLConnection) new URL(videoUrl).openConnection();
//                connection.setRequestMethod("GET");
//                connection.setConnectTimeout(5000);
//                connection.setReadTimeout(5000);
//                Log.d("VideoConnection", "before connection");
//                connection.connect();
//                Log.d("VideoConnection", "after connection");
                URL url = new URL(videoUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                Log.d("VideoConnection", "before connection");
                try {
                    connection.connect();

                } catch (java.net.UnknownHostException e) {
                    Log.e("Connection Error", "Unknown host: Ensure the ESP32 hostname is correct", e);
                } catch (java.net.SocketTimeoutException e) {
                    Log.e("Connection Error", "Connection timed out: ESP32 may be unreachable", e);
                } catch (Exception e) {
                    Log.e("Connection Error", "An error occurred during connection", e);
                }

                Log.d("VideoConnection", "after connection");

                int code = connection.getResponseCode();
                Log.d("VideoConnection", "after response code");



                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e("Download Error", "Server returned HTTP " + connection.getResponseCode());
                    return;
                }
                Log.d("VideoDownload", "Connected to ESP32, starting download...");

                InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                OutputStream outputStream = getContentResolver().openOutputStream(uri);

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
                outputStream.close();
                inputStream.close();
                connection.disconnect();
                Log.d("VideoDownload", "Download complete, saved to URI: " + uri.toString());

                // Re-encode the video
                Uri encodedUri = Uri.fromFile(new File(getCacheDir(), "video.mp4"));
                String[] ffmpegCommand = {"-i", uri.getPath(), encodedUri.getPath()};
                FFmpeg.execute(ffmpegCommand);
                Log.d("VideoDownload", "Re-encoding complete, playing video");

                // Play the re-encoded video
                runOnUiThread(() -> playVideo(encodedUri));
            } catch (Exception e) {
                Log.e("Download Error", "Error downloading video", e);
            }
        }).start();
    }

    private void playVideo(Uri uri) {
        videoView.setVideoURI(uri);
        videoView.start();
    }

    private void discoverESP32Ip() {
        new Thread(() -> {
            try {

                InetAddress inetAddress = InetAddress.getByName(ESP32_HOSTNAME);
                esp32Ip = inetAddress.getHostAddress();

                // Discover the ESP32 service
            } catch (Exception e) {
                Log.e("ESP32 Discovery", "Error in discovery", e);
            }
        }).start();
    }
}
