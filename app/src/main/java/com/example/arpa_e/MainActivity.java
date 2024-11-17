package com.example.arpa_e;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.VideoView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.arthenica.mobileffmpeg.FFmpeg;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.text.*;
import java.io.*;
import android.os.Environment;
import android.os.Build;
import com.arthenica.mobileffmpeg.Config;
import android.media.*;
import java.nio.ByteBuffer;
import android.util.Log;
import java.util.Calendar;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import android.os.Handler;
import android.os.Looper;

import android.provider.DocumentsContract;


import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import android.graphics.*;
import android.view.*;
import java.util.zip.*;
import androidx.core.util.Pair;


//permmisions
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import android.widget.Button;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
    private static final String ESP32_HOSTNAME = "esp32-stream.local"; // ESP32 mDNS hostname
    private String esp32Ip = null; // Store the discovered IP here
    private static final String ESP32_URL = "http://192.168.14.165:1000/video"; // Change this to your ESP32's IP address
    private static final String ESP32_URL_base = "http://192.168.14.165:1000/"; // Change this to your ESP32's IP address
    private static final String zip_create_cmd = "createzip";
    private VideoView videoView;
    private List<File> frames = new ArrayList<>();
    private List<String> timestamps = new ArrayList<>();
    private final Executor executor = Executors.newSingleThreadExecutor();
    // Use an Executor for background execution
    ExecutorService executor_zip = Executors.newSingleThreadExecutor();
    Handler zipHandler = new Handler(Looper.getMainLooper());
    int frameRate = 10;
    private static final int PICK_VIDEO_REQUEST = 1;

    private File framesDirectory;
    private static final String TAG = "VideoEncoder";
    private static final String VIDEO_MIME_TYPE = "video/mp4v-es"; // H.264 video
    private static final int VIDEO_WIDTH = 640;  // Set video width
    private static final int VIDEO_HEIGHT = 480;  // Set video height
    private static final int FRAME_RATE = 10;  // Frames per second
    private static final int BIT_RATE = 5000000;  // Bitrate for video (5Mbps)
    private static final int I_FRAME_INTERVAL = 5; // I-frames every 5 seconds

    // ActivityResultLauncher for the create file action
    private final ActivityResultLauncher<Intent> createFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        downloadAndSaveVideo(uri,ESP32_URL);// "http://" + esp32Ip + ":1000/video");
                    }
                }
            });

    private ActivityResultLauncher<Intent> pickVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
    result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri selectedVideoUri = result.getData().getData();
            if (selectedVideoUri != null) {
                playVideo(selectedVideoUri);
            }
        }
    }
        );

    // Set click listener on the download button to open video picker
//        downloadButton.setOnClickListener(new View.OnClickListener() {
//        @Override
//        public void onClick(View v) {
//            openVideoPicker();
//        }
//    });

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
    public void SynchTime(View view){
        syncTimeWithESP32();
    }
    public void PLAY_VID(View view){
        openVideoPicker();
    }

    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");

        // Optional: start in a specific folder
        Uri folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Movies");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri);

        pickVideoLauncher.launch(intent);  // Launch the picker with the new ActivityResultLauncher
    }

    private void playVideo(Uri videoUri) {
        videoView.setVisibility(View.VISIBLE);  // Make the VideoView visible
        videoView.setVideoURI(videoUri);
        videoView.requestFocus();
        videoView.start();
    }


    private void syncTimeWithESP32() {
        // Get the current time
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1; // Month is 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int millisecond = calendar.get(Calendar.MILLISECOND);

        // Execute network request on a background thread
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String result = sendSyncTimeRequest(year, month, day, hour, minute, second, millisecond);

                // Update UI on the main thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private String sendSyncTimeRequest(int year, int month, int day, int hour, int minute, int second, int millisecond) {
        try {
            // Construct URL and Open Connection
            String synch_time_url = ESP32_URL_base+"syncTime";
            URL url = new URL(synch_time_url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Prepare the POST data
            String postData = "year=" + year + "&month=" + month + "&day=" + day +
                    "&hour=" + hour + "&minute=" + minute + "&second=" + second +
                    "&millisecond=" + millisecond;

            // Send the POST data
            OutputStream os = connection.getOutputStream();
            byte[] input = postData.getBytes("utf-8");
            os.write(input, 0, input.length);

            // Get the response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return "Time synchronized successfully";
            } else {
                return "Failed to synchronize time";
            }

        } catch (Exception e) {
            Log.e("SyncTime", "Error syncing time", e);
            return "Error syncing time";
        }
    }

    private void saveFile(String videoUrl) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/mp4"); // MIME type for your video
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "esp32video"+timestamp+".mp4");
        createFileLauncher.launch(intent);
    }

    private void unzipFile(String zipFilePath, String targetDirectoryPath) {
        try {
            ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFilePath)));
            ZipEntry zipEntry;

            while ((zipEntry = zipInput.getNextEntry()) != null) {
                Log.d("unzipping", "unzipping file: "+ zipEntry.getName());
                File file = new File(targetDirectoryPath, zipEntry.getName());

                if (zipEntry.isDirectory()) {
                    file.mkdirs();
                } else {
                    FileOutputStream fileOutput = new FileOutputStream(file);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = zipInput.read(buffer)) != -1) {
                        fileOutput.write(buffer, 0, bytesRead);
                    }
                    fileOutput.close();
                }

                zipInput.closeEntry();

            }
            zipInput.close();
//            Toast.makeText(this, "Unzipping complete", Toast.LENGTH_SHORT).show();
            Log.d("unzipping", "successfully unzipped file");

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("unzipping", "Error unzipping", e);
//            Toast.makeText(this, "Unzipping failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }



    private List<File> getFilesWithPrefix(String prefix) {
        List<File> renamedFiles = new ArrayList<>();

        // Directory where the unzipped files are stored
        File directory = new File(framesDirectory.getAbsolutePath());

        // Check if directory exists
        if (directory.exists() && directory.isDirectory()) {
            // List all files in the directory
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();

                    // Match files starting with "frame_" and process them
                    if (fileName.startsWith(prefix) && fileName.contains("_")) {
                        // Extract "frame_<framenumber>" from the file name
                        int secondUnderscoreIndex = fileName.indexOf("_", fileName.indexOf("_") + 1);
                        if (secondUnderscoreIndex != -1) {
                            String newFileName = fileName.substring(0, secondUnderscoreIndex) + ".jpg";  // Adjust extension if needed
                            File newFile = new File(directory, newFileName);

                            String timestamp = fileName.substring(secondUnderscoreIndex + 1, fileName.lastIndexOf('.'));
                            timestamps.add(timestamp);

                            // Rename the file
                            if (file.renameTo(newFile)) {
                                renamedFiles.add(newFile);  // Add the renamed file to the list
                                Log.d("RenameSuccess", "Renamed to: " + newFile.getAbsolutePath());
                            } else {
                                Log.e("RenameError", "Failed to rename: " + file.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        } else {
            Log.e("FileSearch", "Unzipped directory does not exist.");
        }

        return renamedFiles;
    }

    private void sortFilesAndTimestamps(List<File> fileList, List<String> timestampList) {
        // Combine both lists into a single list of pairs
        List<Pair<File, String>> pairedList = new ArrayList<>();
        for (int i = 0; i < fileList.size(); i++) {
            pairedList.add(new Pair<>(fileList.get(i), timestampList.get(i)));
        }

        // Sort the combined list by extracting the frame number from the filenames
        Collections.sort(pairedList, new Comparator<Pair<File, String>>() {
            @Override
            public int compare(Pair<File, String> pair1, Pair<File, String> pair2) {
                int frameNumber1 = extractFrameNumber(pair1.first.getName());
                int frameNumber2 = extractFrameNumber(pair2.first.getName());
                return Integer.compare(frameNumber1, frameNumber2);
            }
        });

        // Clear and refill the original lists in sorted order
        fileList.clear();
        timestampList.clear();
        for (Pair<File, String> pair : pairedList) {
            fileList.add(pair.first);
            timestampList.add(pair.second);
        }
    }

    // Helper method to extract the frame number from a filename (e.g., "frame_001.jpg" -> 1)
    private int extractFrameNumber(String fileName) {
        try {
            String frameNumberPart = fileName.substring(fileName.indexOf("_") + 1, fileName.lastIndexOf('.'));
            return Integer.parseInt(frameNumberPart);
        } catch (Exception e) {
            Log.e("FrameNumberError", "Invalid frame number in filename: " + fileName);
            return -1; // Default value for error cases
        }
    }
    private void downloadAndSaveVideo(Uri outputUri, String videoUrl) {
//        deleteExistingOutput(outputUri);
        new Thread(() -> {
            try {
                int frameNumber = 0;
                File outputFile = new File(outputUri.getPath());
//                File parentDir = outputFile.getParentFile();
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                framesDirectory = new File(downloadsDir, "received_frames"); //getCacheDir();
                Log.d("received_frames_dir", framesDirectory.getAbsolutePath());

                if (!framesDirectory.exists()) {
                    framesDirectory.mkdirs(); // Create the folder if it doesn't exist
                }
                int count = 0;
                long current_timestamp = 0;
                long prev_timestamp = 0;

                Log.d("VideoDownload", "Attempting to connect to ESP32 at " + videoUrl + " at address: " + esp32Ip);
//                    URL url = new URL(videoUrl);
                String zipFilePath = framesDirectory.getAbsolutePath() + "/images_with_timestamps.zip";
                URL url = new URL(videoUrl + "?frame=" + frameNumber);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                Log.d("VideoDownload", "after connect");

                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e("Download Error", "Server returned HTTP " + connection.getResponseCode());
                    return;
                }
                Log.d("VideoDownload", "Connected to ESP32, starting download...");


                InputStream inputStream_zip = new BufferedInputStream(connection.getInputStream());
                FileOutputStream fileOutput = new FileOutputStream(zipFilePath);

                byte[] buffer_zip = new byte[1024];
                int bytesRead_zip;
                while ((bytesRead_zip = inputStream_zip.read(buffer_zip)) != -1) {
                    fileOutput.write(buffer_zip, 0, bytesRead_zip);
                }

                fileOutput.close();
                inputStream_zip.close();
                connection.disconnect();

                unzipFile(zipFilePath, framesDirectory.getAbsolutePath());
                Log.d("unzipping", "finished unzipping");
                frames = getFilesWithPrefix("frame_");
                sortFilesAndTimestamps(frames, timestamps);

                // Convert frames to MP4
                convertFramesToMp4WithTimestamps(frames, outputUri);

                deleteFrames(frames);

//            runOnUiThread(() -> playVideo(outputUri));
            } catch (Exception e) {
                Log.e("StreamDownload", "Error streaming frames", e);
            }
        }).start();
    }

    public void createZipOnESP32(View view) {
        String zipcmdurl = ESP32_URL_base+zip_create_cmd;

        executor_zip.execute(() -> {
            String result;
            try {
                URL urlObj = new URL(zipcmdurl);
                HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    reader.close();
                    inputStream.close();
                    result = response.toString();
                } else {
                    result = "Error: " + responseCode;
                }
            } catch (Exception e) {
                e.printStackTrace();
                result = "Error: " + e.getMessage();
            }

            // Update the UI on the main thread
            String finalResult = result;
            zipHandler.post(() -> {
                if (finalResult.contains("successful")) {

                    Toast.makeText(MainActivity.this, "ZIP created successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to create ZIP. " + finalResult, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void refreshMediaStore(File file) {
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(Uri.fromFile(file));
        sendBroadcast(scanIntent);
    }

    private String parseTimestampFromHeaders(byte[] data) {
        // Parse the timestamp from the headers in the HTTP stream response.
        // This will depend on the ESP32's format (e.g., look for "X-Timestamp" header if added).
        // Placeholder: Replace with actual parsing logic.
        return "timestamp_placeholder";
    }

    public static void copy(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    private void convertFramesToMp4WithTimestamps(List<File> frames, Uri outputUri) {
        List<File> frames_repeated = new ArrayList<>();
        Config.enableLogCallback(message -> {
            Log.d("FFmpegLog", message.getText());  // Direct FFmpeg logs to Android Logcat
        });

//        clearFramesCache();
        try {
            // Step 1: Sort frames by timestamp
//            Collections.sort(frames, Comparator.comparingLong(this::extractFrameNumberFromFilename));

            // Step 2: Create an FFmpeg input list file with durations
            File listFile = new File(framesDirectory, "ffmpeg_input_list.txt");
            long current_timestamp;
            long prev_timestamp;
            int count=0;
            for (int i = 1; i < frames.size(); i++) {
                File frame = frames.get(i-1);
                Log.d("FFmpegtimestamp", timestamps.get(i));  // Direct FFmpeg logs to Android Logcat

                current_timestamp = extractTimestampFromString(timestamps.get(i));
                prev_timestamp = extractTimestampFromString(timestamps.get(i-1));

                float duration = (current_timestamp-prev_timestamp)/1000.f;
                int repeat = Math.round(duration/(1.0f/frameRate));
                for (int j=0;j<repeat;j++){
                    File dst = new File(framesDirectory, "frame_repeated_"+count+".jpg");
                    copy(frame,dst);
                    frames_repeated.add(dst);
                    count++;
                }
            }

            // Step 3: Use FFmpeg to encode frames to MP4 with timestamps
            File mp4File = new File(framesDirectory, "video_converted_new.mp4");
            Log.d("FFmpegcmd", "before reaching to cmd!!");

            String ffmpegCommand =
                    "-loglevel verbose -framerate "+frameRate+" -y -i "+framesDirectory+"/frame_repeated_%d.jpg -r 30 -c:v libx264 "+ mp4File.getAbsolutePath();


            try{
                int ffmpeg_returns = FFmpeg.execute(ffmpegCommand);
                Log.d("FFmpegcmd", ffmpegCommand);

                if (ffmpeg_returns !=0) {
                    Log.e("FFmpegError", "FFmpeg command failed with return code: " + ffmpeg_returns);
                }

            } catch (Exception e) {
                Log.e("FFmpegError","cannot produced designated file due to: ", e);

            }
            Log.d("framefile","frame list path is: "+listFile.getAbsolutePath() + " and mp4file path is: " + mp4File.getAbsolutePath());


            // Step 4: Copy re-encoded MP4 to the output URI
            try (InputStream mp4InputStream = new BufferedInputStream(new FileInputStream(mp4File));
                 OutputStream outputStream = getContentResolver().openOutputStream(outputUri)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = mp4InputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            } catch (Exception e) {
                Log.e("ReEncode", "Error during MP4 Encoding: ", e);
            }
            refreshMediaStore(mp4File);
            Log.d("VideoConversion", "MP4 conversion and saving completed.");
        } catch (Exception e) {
            Log.e("FFmpeg", "Error during video conversion with timestamps", e);
        }
        deleteFrames(frames_repeated);
    }

    public void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires the new media-specific permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // For Android 12 and below, use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d("VideoEncoder", "Permission granted.");
                    // Permission granted - continue with processing
                } else {
                    Log.e("VideoEncoder", "Permission denied.");
                    // Handle the permission denial
                }
            });

private void deleteFrames(List<File> frames) {
    for (File frame : frames) {
        if (frame.exists()) {
            if (frame.delete()) {
                Log.d("Frame Cleanup", "Deleted frame: " + frame.getName());
            } else {
                Log.e("Frame Cleanup", "Failed to delete frame: " + frame.getName());
            }
        }
    }
}
    private long extractTimestampFromString(String stamp) {
        // Split the timestamp string into [yyyy, mm, dd, hh, mm, ss, sss]
        String[] timeParts = stamp.split("-");
        int year = Integer.parseInt(timeParts[0]);
        int month = Integer.parseInt(timeParts[1]);
        int day = Integer.parseInt(timeParts[2]);
        int hours = Integer.parseInt(timeParts[3]);
        int minutes = Integer.parseInt(timeParts[4]);
        int seconds = Integer.parseInt(timeParts[5]);
        int milliseconds = Integer.parseInt(timeParts[6]);

        // Calculate the days passed since the epoch (Jan 1, 1970)
//        long daysSinceEpoch = calculateDaysSinceEpoch(year, month, day);

        // Convert days, hours, minutes, seconds, and milliseconds to total milliseconds
        long totalMilliseconds = hours * 3600L * 1000
                + minutes * 60L * 1000
                + seconds * 1000L
                + milliseconds;

        return totalMilliseconds;
    }

    // Helper method to calculate days since the Unix epoch (January 1, 1970)
    private long calculateDaysSinceEpoch(int year, int month, int day) {
        // Days in each month from Jan to Dec (non-leap year)
        int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        long days = 0;

        // Add days for each full year since 1970
        for (int y = 1970; y < year; y++) {
            days += isLeapYear(y) ? 366 : 365;
        }

        // Add days for each full month in the current year
        for (int m = 1; m < month; m++) {
            days += daysInMonth[m - 1];
            // Add one day if it's February in a leap year
            if (m == 2 && isLeapYear(year)) {
                days += 1;
            }
        }

        // Add days for the current month
        days += day - 1; // Subtract 1 because `day` is 1-based (e.g., Jan 1 is day 1)

        return days;
    }

    // Helper method to check if a year is a leap year
    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    private long extractTimestampFromFilename(File frameFile) {
        String name = frameFile.getName();
        // Assuming format "frame_<hh-mm-ss-sss>.jpg"
        String timestampStr = name.split("_")[2].split("\\.")[0]; // Extract "hh-mm-ss-sss" part

        String[] timeParts = timestampStr.split("-"); // Split into [hh, mm, ss, sss]
        int hours = Integer.parseInt(timeParts[0]);
        int minutes = Integer.parseInt(timeParts[1]);
        int seconds = Integer.parseInt(timeParts[2]);
        int milliseconds = Integer.parseInt(timeParts[3]);

        // Convert timestamp to milliseconds
        return (hours * 3600 + minutes * 60 + seconds) * 1000 + milliseconds;
    }

    private int extractFrameNumberFromFilename(File frameFile) {
        String name = frameFile.getName();
        // Split on "_" and ignore the "frame" prefix and timestamp portion
        String[] parts = name.split("_");

        // The frame number should be at index 1
        return Integer.parseInt(parts[1]);
    }
    private void clearFramesCache() {
        File cacheDir = getCacheDir();
        if (cacheDir.isDirectory()) {
            for (File file : cacheDir.listFiles()) {
                if (file.getName().startsWith("frame_")) {
                    file.delete();
                }
            }
        }
    }
//    private void playVideo(Uri uri) {
//        videoView.setVideoURI(uri);
//        videoView.start();
//    }

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
