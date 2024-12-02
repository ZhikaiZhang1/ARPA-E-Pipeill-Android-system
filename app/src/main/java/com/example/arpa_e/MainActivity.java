package com.example.arpa_e;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
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
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;

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
import android.database.Cursor;
import androidx.documentfile.provider.DocumentFile;
//192.168.134.42
public class MainActivity extends AppCompatActivity {
    private static final String ESP32_HOSTNAME = "esp32-stream.local"; // ESP32 mDNS hostname
    private String esp32Ip = null; // Store the discovered IP here
    private static final String ESP32_URL_camera1 = "http://192.168.8.241:1000/video"; // Change this to your ESP32's IP address
    private static final String ESP32_URL_camera1_base = "http://192.168.8.241:1000/"; // Change this to your ESP32's IP address
    private static final String ESP32_URL_camera2 = "http://192.168.134.42:1001/video"; // Change this to your ESP32's IP address
    private static final String ESP32_URL_camera2_base = "http://192.168.134.42:1001/"; // Change this to your ESP32's IP address
    private static final String zip_create_cmd = "createzip";
    private static final String mode_cmd = "mode_select";

    private VideoView videoView;
    private List<File> frames = new ArrayList<>();
    private List<String> timestamps = new ArrayList<>();
    private final Executor executor = Executors.newSingleThreadExecutor();
    // Use an Executor for background execution
//    ExecutorService executor_zip = Executors.newSingleThreadExecutor();
    Handler zipHandler = new Handler(Looper.getMainLooper());
    ExecutorService executor_zip = Executors.newFixedThreadPool(2);
    ExecutorService executor_mode = Executors.newFixedThreadPool(2);

    private TextView syncStatusESP32_1, syncStatusESP32_2;
    private TextView downloadPercentageESP32_1, downloadPercentageESP32_2;
    private TextView modeESP32_1, modeESP32_2;

    private Handler download_update_handler = new Handler(Looper.getMainLooper());
    private Runnable updateDownloadTask;


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

        syncStatusESP32_1 = findViewById(R.id.syncStatusESP32_1);
        syncStatusESP32_2 = findViewById(R.id.syncStatusESP32_2);

        downloadPercentageESP32_1 = findViewById(R.id.downloadPercentageESP32_1);
        downloadPercentageESP32_2 = findViewById(R.id.downloadPercentageESP32_2);
        modeESP32_1 = findViewById(R.id.modeESP32_1);
        modeESP32_2 = findViewById(R.id.modeESP32_2);
        updateStatus("Not Synced", "Not Synced", "0%", "0%", "WIFI", "WIFI");
    }

    private void updateDownloadStatusWithHandler(String download1, String download2) {
        if (updateDownloadTask != null) {
            download_update_handler.removeCallbacks(updateDownloadTask); // Cancel previous task if it's already queued
        }

        updateDownloadTask = () -> {
            if (!download1.equals("NONE")) {
                downloadPercentageESP32_1.setText(download1);
            }
            if (!download2.equals("NONE")) {
                downloadPercentageESP32_2.setText(download2);
            }
        };

        download_update_handler.postDelayed(updateDownloadTask, 100); // Schedule update after 100ms
    }
    private void updateSynchStatus(String sync1, String sync2) {
        if (!sync1.equals("NONE")) {
            syncStatusESP32_1.setText(sync1);
        }
        if (!sync2.equals("NONE")) {
            syncStatusESP32_2.setText(sync2);
        }
    }
    private void updateDownloadStatus(String download1, String download2) {
        if (!download1.equals("NONE")) {
            downloadPercentageESP32_1.setText(download1);
        }
        if (!download2.equals("NONE")) {
            downloadPercentageESP32_2.setText(download2);
        }
    }
    private void updateModeStatus(String mode1, String mode2) {
        if (!mode1.equals("NONE")) {
            modeESP32_1.setText(mode1);
        }
        if (!mode2.equals("NONE")) {
            modeESP32_2.setText(mode2);
        }
    }

    private void updateStatus(String sync1, String sync2, String download1, String download2, String mode1, String mode2) {
        updateSynchStatus(sync1,sync2);
        updateDownloadStatus(download1,download2);
        updateModeStatus(mode1,mode2);
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
                String result1 = sendSyncTimeRequest(year, month, day, hour, minute, second, millisecond, 0);
                String result2 = sendSyncTimeRequest(year, month, day, hour, minute, second, millisecond, 1);


                // Update UI on the main thread
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        Toast.makeText(MainActivity.this, result1 + " " + result2, Toast.LENGTH_LONG).show();
//                    }
//                });
            }
        });
    }

    private void handleESP32Response(String response) {
//        ImageView camera1Checkmark = findViewById(R.id.camera1Checkmark);
//        ImageView camera2Checkmark = findViewById(R.id.camera2Checkmark);
        if (response.contains("Time synchronized successfully for camera 1")) {
//            camera1Checkmark.setVisibility(View.VISIBLE);
            updateSynchStatus("Synced", "NONE");
        } else if (response.contains("Time synchronized successfully for camera 2")) {
//            camera2Checkmark.setVisibility(View.VISIBLE);
            updateSynchStatus("NONE", "Synced");

        } else {
            Toast.makeText(this, "Unexpected response: " + response, Toast.LENGTH_LONG).show();
        }
    }

    private int getESP32ResponseID(String response) {
        if (response.contains("camera_1")) {
            return 1;
        } else if (response.contains("camera_2")) {
            return 2;
        } else {
            return 0;
        }
    }

    private String sendSyncTimeRequest(int year, int month, int day, int hour, int minute, int second, int millisecond, int CameraID) {
        String camera_str = "Camera "+(CameraID+1);

        try {
            // Construct URL and Open Connection
            String synch_time_url;
            if (CameraID == 0){
                synch_time_url = ESP32_URL_camera1_base+"syncTime";
            }
            if (CameraID == 1){
                synch_time_url = ESP32_URL_camera2_base+"syncTime";
            }
            else{
                synch_time_url = ESP32_URL_camera1_base+"syncTime";
            }
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
                // Read the response
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Pass the response to the handler on the main thread
                String esp32Response = response.toString();
                runOnUiThread(() -> handleESP32Response(esp32Response));

                return camera_str + " Time synchronized successfully";
            } else {
                return "Failed to synchronize time";
            }

        } catch (Exception e) {
            Log.e("SyncTime", "Error syncing time", e);
            return camera_str + " Error syncing time";
        }
    }

    private void saveFile(String videoUrl) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String filename = "esp32video"+timestamp+".mp4";
        downloadAndSaveVideo(filename,ESP32_URL_camera1);
        downloadAndSaveVideo(filename,ESP32_URL_camera2);


    }


    private void unzipFile(String zipFilePath, String targetDirectoryPath) {
        try {
            ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFilePath)));
            ZipEntry zipEntry;

            while ((zipEntry = zipInput.getNextEntry()) != null) {
                Log.d("unzipping", "unzipping file: "+ zipEntry.getName());
                Log.d("unzipping", "writing into: "+ targetDirectoryPath);


                File file = new File(targetDirectoryPath, zipEntry.getName());


                if (zipEntry.isDirectory()) {
                    file.mkdirs();
                    Log.d("unzipping", "zipEntry.isDirectory()");

                } else {
                    Log.d("unzipping", "before FileOutputStream");
                    FileOutputStream fileOutput = new FileOutputStream(file);
                    Log.d("unzipping", "after FileOutputStream");

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = zipInput.read(buffer)) != -1) {
                        fileOutput.write(buffer, 0, bytesRead);
                    }
                    Log.d("unzipping", "after reading FileOutputStream");

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



    private List<File> getFilesWithPrefix(String prefix, String frames_dir) {
        List<File> renamedFiles = new ArrayList<>();

        // Directory where the unzipped files are stored
        File directory = new File(frames_dir);

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

    public Uri appendStringToUriName(Uri uri, String prefix) {
        if (uri == null || prefix == null) return uri;

        // Get the last segment (the name) of the URI
        String lastSegment = uri.getLastPathSegment();
        if (lastSegment == null) return uri; // No name to append to

        // Add the prefix
        String modifiedName = prefix + lastSegment;

        // Rebuild the URI with the modified name
        Uri baseUri = uri.buildUpon().path(null).build(); // Remove the last path segment
        return baseUri.buildUpon().appendPath(modifiedName).build();
    }

    public static File createDirectoryIfNotExists(File parent_dir, String directoryName) {
        // Create a new File object for the target directory
        File targetDir = new File(parent_dir, directoryName);

        // Check if the directory exists
        if (!targetDir.exists()) {
            // Attempt to create the directory
            if (targetDir.mkdirs()) {
                System.out.println("Directory created: " + targetDir.getAbsolutePath());
            } else {
                System.err.println("Failed to create directory: " + targetDir.getAbsolutePath());
            }
        } else {
            System.out.println("Directory already exists: " + targetDir.getAbsolutePath());
        }

        return targetDir;
    }

    public static boolean prependToFileName(Context context, Uri fileUri, String prefix) {
        DocumentFile documentFile = DocumentFile.fromSingleUri(context, fileUri);

        if (documentFile != null && documentFile.canWrite()) {
            String originalName = documentFile.getName();
            if (originalName != null) {
                String newName = prefix + originalName;
                return documentFile.renameTo(newName);
            }
        }

        return false; // Could not rename the file
    }

    /**
     * Converts the URI to a file path, if possible.
     *
     * @param context The context to get the ContentResolver.
     * @param uri     The URI of the file.
     * @return The file path, or null if it cannot be determined.
     */
    private static String getFilePathFromUri(Context context, Uri uri) {
        String filePath = null;

        // Handle different types of URIs based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            String documentId = DocumentsContract.getDocumentId(uri);
            String[] split = documentId.split(":");
            String type = split[0];

            if ("primary".equalsIgnoreCase(type)) {
                filePath = context.getExternalFilesDir(null) + "/" + split[1];
            }
        } else {
            // For older versions of Android or non-document URIs
            String[] projection = { android.provider.MediaStore.Images.Media.DATA };
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                filePath = cursor.getString(columnIndex);
                cursor.close();
            }
        }

        return filePath;
    }

    public static void addTimestampToImageFile(File imageFile, String timestamp, File outputFile) {
        try {
            // Step 1: Load the image file into a Bitmap
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

            // Step 2: Create a mutable Bitmap if it's not mutable
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            // Step 3: Create a Canvas to draw on the mutable Bitmap
            Canvas canvas = new Canvas(mutableBitmap);

            // Step 4: Set up the Paint object for drawing text
            Paint paint = new Paint();
            paint.setColor(Color.YELLOW); // Set text color to yellow
            paint.setTextSize(50); // Set text size (adjust as needed)
            paint.setTypeface(Typeface.DEFAULT_BOLD); // Set font type
            paint.setAntiAlias(true); // Enable anti-aliasing for smooth text

            // Step 5: Set the position for the text (bottom left)
            int xPos = 10;
            int yPos = mutableBitmap.getHeight() - 30; // Adjust as needed

            // Step 6: Draw the text (timestamp) on the image
            canvas.drawText(timestamp, xPos, yPos, paint);

            // Step 7: Save the modified Bitmap back to a new file (JPEG)
            FileOutputStream out = new FileOutputStream(outputFile);
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // Compress as JPEG (100 = highest quality)
            out.flush();
            out.close();

            // Clean up
            bitmap.recycle();
            mutableBitmap.recycle();

            // Optionally, you can return or display the modified image here

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void downloadAndSaveVideo(String filename, String videoUrl) {
//        deleteExistingOutput(outputUri);
        new Thread(() -> {
            try {
                int frameNumber = 0;
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                framesDirectory = new File(downloadsDir, "received_frames"); //getCacheDir();
                Log.d("received_frames_dir", framesDirectory.getAbsolutePath());

                if (!framesDirectory.exists()) {
                    framesDirectory.mkdirs(); // Create the folder if it doesn't exist
                }

                Log.d("VideoDownload", "Attempting to connect to ESP32 at " + videoUrl + " at address: " + esp32Ip);
//                    URL url = new URL(videoUrl);
                URL url = new URL(videoUrl + "?frame=" + frameNumber);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                Log.d("VideoDownload", "after connect");

                int code = connection.getResponseCode();
                int CameraID = 0;
                int fullFileSize = 0;
                String esp32Response;
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e("Download Error", "Server returned HTTP " + connection.getResponseCode());
                    return;
                }
                else {
                    Log.d("VideoDownload", "Connected to ESP32, starting download...");
                    String cameraIdHeader = connection.getHeaderField("Camera-Id");
                    CameraID=Integer.parseInt(cameraIdHeader);

                    String fullFileSizeHeader = connection.getHeaderField("Filesize");
                    fullFileSize = Integer.parseInt(fullFileSizeHeader);
//                    Log.d("VideoDownload", "camera id is: " + CameraID + " File size is: ");




                }
                String suffix_file = "_camera_"+CameraID;
//                Uri outputUri_final = appendStringToUriName(outputUri, suffix_file);
                String zip_folder_name = "/images_with_timestamps"+suffix_file;
                String zipFilePath = framesDirectory.getAbsolutePath() + zip_folder_name+".zip";
                Log.d("naming_files", "camera id is: " + CameraID + " output name: " + zipFilePath + " File size is: " + fullFileSize);

                createDirectoryIfNotExists(framesDirectory, zip_folder_name);
//                Log.d("naming_files", "after creating dirs");
//                File downloadsDir_checking = Environment.getExternalStoragePublicDirectory(framesDirectory.getAbsolutePath());
//                checkOrCreateDirectory(downloadsDir_checking,zip_folder_name);



                InputStream inputStream_zip = new BufferedInputStream(connection.getInputStream());
                FileOutputStream fileOutput = new FileOutputStream(zipFilePath);

                Log.d("download_bytes", "starting downloading bytes!!!");

                byte[] buffer_zip = new byte[1024];
                int bytesRead_zip;
                int all_bytes_rad = 0;
                while ((bytesRead_zip = inputStream_zip.read(buffer_zip)) != -1) {
                    fileOutput.write(buffer_zip, 0, bytesRead_zip);
                    all_bytes_rad+=bytesRead_zip;

                    if (CameraID==1) {
                        updateDownloadStatusWithHandler(String.valueOf( Math.round((float) all_bytes_rad/fullFileSize*100))+"%","NONE");
                    }
                    else if (CameraID==2) {
                        updateDownloadStatusWithHandler("NONE", String.valueOf(Math.round((float) all_bytes_rad/fullFileSize*100))+"%");
                    }
                    Log.d("download_bytes", "download progress is: " + (float) all_bytes_rad/fullFileSize);

                }

                fileOutput.close();
                inputStream_zip.close();
                connection.disconnect();

                unzipFile(zipFilePath, framesDirectory.getAbsolutePath()+zip_folder_name);
                Log.d("unzipping", "finished unzipping");
                frames = getFilesWithPrefix("frame_", framesDirectory.getAbsolutePath()+zip_folder_name);
                sortFilesAndTimestamps(frames, timestamps);

                // Convert frames to MP4
                convertFramesToMp4WithTimestamps(frames, filename, framesDirectory.getAbsolutePath()+zip_folder_name, suffix_file);

                deleteFrames(frames);

//            runOnUiThread(() -> playVideo(outputUri));
            } catch (Exception e) {
                Log.e("StreamDownload", "Error streaming frames", e);
            }
        }).start();
    }

    public void zipcreation_send(int CameraID){
        String zipcmdurl;
        if (CameraID==0) {
            zipcmdurl = ESP32_URL_camera1_base + zip_create_cmd;
        }
        else {
            zipcmdurl = ESP32_URL_camera2_base + zip_create_cmd;
        }

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

                Toast.makeText(MainActivity.this, "Camera " + (CameraID +1)+" ZIP created successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Camera " + (CameraID +1)+" Failed to create ZIP. " + finalResult, Toast.LENGTH_LONG).show();
            }
        });

    }
    public void createZipOnESP32(View view) {
        Runnable camera1Task = () -> {
            zipcreation_send(0);
        };
        Runnable camera2Task = () -> {
            zipcreation_send(1);
        };
        executor_zip.execute(camera1Task);
        executor_zip.execute(camera2Task);
    }

    public void mode_select_send(int CameraID){
        String zipcmdurl;
        if (CameraID==0) {
            zipcmdurl = ESP32_URL_camera1_base + mode_cmd;
        }
        else {
            zipcmdurl = ESP32_URL_camera2_base + mode_cmd;
        }

//        String result;
        try {
            URL url = new URL(zipcmdurl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Configure the connection
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5 seconds timeout
            connection.setReadTimeout(5000);

            // Read the response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Update the TextView with the response
            Toast.makeText(this, response.toString(), Toast.LENGTH_LONG).show();
        }
        catch (Exception e) {
            Log.e("mode_select", "Error during mode select: ", e);
        }

    }
    public void ModeSelect(View view){
        Runnable camera1Mode = () -> {
            mode_select_send(0);
        };
        Runnable camera2Mode = () -> {
            mode_select_send(1);
        };
        executor_mode.execute(camera1Mode);
        executor_mode.execute(camera2Mode);
    }

    private void refreshMediaStore(File file) {
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(Uri.fromFile(file));
        sendBroadcast(scanIntent);
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


    private void convertFramesToMp4WithTimestamps(List<File> frames, String filename, String frames_dir, String prend_string) {
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
                addTimestampToImageFile(frame, timestamps.get(i), frame);

                Log.d("FFmpegtimestamp", timestamps.get(i));  // Direct FFmpeg logs to Android Logcat

                current_timestamp = extractTimestampFromString(timestamps.get(i));
                prev_timestamp = extractTimestampFromString(timestamps.get(i-1));

                float duration = (current_timestamp-prev_timestamp)/1000.f;
                int repeat = Math.round(duration/(1.0f/frameRate));
                for (int j=0;j<repeat;j++){
                    File dst = new File(frames_dir, "frame_repeated_"+count+".jpg");
                    copy(frame,dst);
                    frames_repeated.add(dst);
                    count++;
                }
            }

            // Step 3: Use FFmpeg to encode frames to MP4 with timestamps
            String output_file_name ="video_converted_" + timestamps.get(0) + ".mp4";
            File mp4File = new File(frames_dir, output_file_name);
            Log.d("FFmpegcmd", "before reaching to cmd!!");

            String ffmpegCommand =
                    "-loglevel verbose -framerate "+frameRate+" -y -i "+frames_dir+"/frame_repeated_%d.jpg -r 30 -c:v libx264 "+ mp4File.getAbsolutePath();


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
