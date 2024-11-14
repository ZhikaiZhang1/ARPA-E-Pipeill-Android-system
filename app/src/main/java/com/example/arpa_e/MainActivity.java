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
import android.provider.DocumentsContract;


import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import android.graphics.*;
import android.view.*;


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
    private static final String ESP32_URL = "http://192.168.141.165:1000/video"; // Change this to your ESP32's IP address
    private static final String ESP32_URL_base = "http://192.168.141.165:1000/"; // Change this to your ESP32's IP address

    private VideoView videoView;
    private List<File> frames = new ArrayList<>();
    private List<String> timestamps = new ArrayList<>();
    private final Executor executor = Executors.newSingleThreadExecutor();
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

    private void downloadAndSaveVideo(Uri outputUri, String videoUrl) {
        deleteExistingOutput(outputUri);
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
                while(true){
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
                    if (code != HttpURLConnection.HTTP_OK) {
                        Log.e("Download Error", "Server returned HTTP " + connection.getResponseCode());
                        return;
                    }
                    Log.d("VideoDownload", "Connected to ESP32, starting download...");

                    // Retrieve headers
                    String timestamp = connection.getHeaderField("X-Timestamp");
                    String framesLeftHeader = connection.getHeaderField("X-Frames-Left");
                    int framesLeft = framesLeftHeader != null ? Integer.parseInt(framesLeftHeader) : 0;

                    // Read the JPEG frame
                    InputStream inputStream = connection.getInputStream();

                    File frameFile = new File(framesDirectory, "frame_" + frameNumber + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(frameFile)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception e) {
                        Log.e("StreamDownload", "Error streaming single frame", e);
                    }
                    frames.add(frameFile);
                    timestamps.add(timestamp);
                    Log.d("VideoDownload", "Frame " + frameNumber + " with timestamp " + timestamp + " saved.");

                    inputStream.close();
                    connection.disconnect();

                    // Check if more frames are left
                    if (framesLeft == 0) {
                        Log.d("VideoDownload", "All frames received, number of frames produced is: " + count);
                        break;
                    }
                    // Increment frame number to request the next frame
                    frameNumber++;
                }

            // Convert frames to MP4
            convertFramesToMp4WithTimestamps(frames, outputUri);

            deleteFrames(frames);

//            runOnUiThread(() -> playVideo(outputUri));
        } catch (Exception e) {
            Log.e("StreamDownload", "Error streaming frames", e);
        }
    }).start();
    }


    private int findJpegStart(byte[] data) {
        // Look for JPEG start marker: 0xFFD8
        for (int i = 0; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8) {
                return i;
            }
        }
        return -1;
    }
    private void deleteExistingOutput(Uri outputUri) {
        try {
            getContentResolver().delete(outputUri, null, null);
            Log.d("VideoOutput", "Existing video output deleted successfully.");
        } catch (Exception e) {
            Log.e("VideoOutput", "Error deleting old output file", e);
        }
    }
    private void refreshMediaStore(File file) {
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(Uri.fromFile(file));
        sendBroadcast(scanIntent);
    }

    private int findJpegEnd(byte[] data) {
        // Look for JPEG end marker: 0xFFD9
        for (int i = 0; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD9) {
                return i + 1;
            }
        }
        return -1;
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

//            -f concat -safe 0 -i /data/user/0/com.example.arpa_e/cache/ffmpeg_input_list.txt -vsync vfr -pix_fmt yuv420p -c:v libx264 -y -loglevel debug /data/user/0/com.example.arpa_e/cache/video_converted_new.mp4
//            /data/user/0/com.example.arpa_e/cache/video_converted_new.mp4
//            FFmpeg.setLogLevel(FFmpeg.LOG_LEVEL_VERBOSE);

//            File file = new File(frames.get(10).getAbsolutePath());
//            if (file.exists()) {
//                Log.d("ListFileContent", "file " + frames.get(10).getAbsolutePath() + " does exist");
//            } else {
//                // File does not exist
//                Log.d("ListFileContent", "file " + frames.get(10).getAbsolutePath() + " doesn't exist");
//
//            }
//
//            Log.d("ListFileContent", "Total frames: " + frames.size());

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

    public void createVideo_MediaCodec(List<File> files, Uri outputUri, ContentResolver contentResolver, int width, int height, int frameRate, Context context) throws IOException {
        File tempFile = null;
        try {
            // Create a temporary file if API level is below 26
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                tempFile = new File(context.getCacheDir(), "temp_video.mp4");
            }

            // Configure the video format and encoder
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 1000000); // Adjust as needed
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1-second interval for keyframes

            MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            // Create a Surface for the encoder
            Surface surface = codec.createInputSurface();
            codec.start();

            // Initialize MediaMuxer based on API level
            MediaMuxer muxer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try (OutputStream outputStream = contentResolver.openOutputStream(outputUri)) {
                    if (outputStream == null) {
                        Log.e(TAG, "Unable to open output stream for URI: " + outputUri);
                        return;
                    }
                    FileDescriptor fileDescriptor = ((FileOutputStream) outputStream).getFD();
                    muxer = new MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                }
            } else {
                muxer = new MediaMuxer(tempFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean isMuxerStarted = false;
            int trackIndex = -1;

            // Encode each file as a frame
//            checkAndRequestPermissions();

            for (File file : files) {
//                checkAndRequestPermissions();
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode Bitmap from file: " + file.getAbsolutePath());
                    continue; // Skip files that can't be loaded as Bitmaps
                }

                // Draw the Bitmap onto the encoder surface
                Canvas canvas = surface.lockCanvas(null);
                canvas.drawBitmap(bitmap, 0, 0, null);
                surface.unlockCanvasAndPost(canvas);
                bitmap.recycle(); // Free memory

                // Retrieve and write the encoded frame to the muxer
                while (true) {
                    int encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 10000);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break; // No output available yet
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (isMuxerStarted) {
                            Log.e(TAG, "Format changed twice. Exiting.");
                            return;
                        }
                        MediaFormat newFormat = codec.getOutputFormat();
                        trackIndex = muxer.addTrack(newFormat);
                        muxer.start();
                        isMuxerStarted = true;
                    } else if (encoderStatus >= 0) {
                        ByteBuffer encodedData = codec.getOutputBuffer(encoderStatus);
                        if (encodedData == null) {
                            Log.e(TAG, "Encoder output buffer was null");
                            return;
                        }

                        // Write the encoded frame to the muxer
                        if (bufferInfo.size != 0) {
                            encodedData.position(bufferInfo.offset);
                            encodedData.limit(bufferInfo.offset + bufferInfo.size);
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                        }

                        codec.releaseOutputBuffer(encoderStatus, false);

                        // Break after encoding each frame
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "End of stream reached.");
                            break;
                        }
                    }
                }
            }

            // Finish encoding and release resources
            codec.signalEndOfInputStream();
            codec.stop();
            codec.release();

            if (isMuxerStarted) {
                muxer.stop();
            }
            muxer.release();

            // If using a temporary file, copy it to the output URI
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && tempFile != null) {
                try (OutputStream outputStream = contentResolver.openOutputStream(outputUri);
                     FileInputStream inputStream = new FileInputStream(tempFile)) {
                    if (outputStream == null) {
                        Log.e(TAG, "Failed to open output stream to URI for copying temp file.");
                        return;
                    }

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    Log.d(TAG, "Video successfully copied to output URI.");
                }
                // Delete the temporary file
                if (!tempFile.delete()) {
                    Log.w(TAG, "Failed to delete temporary file: " + tempFile.getAbsolutePath());
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "IOException occurred: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: " + e.getMessage(), e);
        }
    }
public static void encodeImagesToMp4(List<File> imageFiles, String outputFilePath) throws Exception {
    Log.i(TAG, "Starting to encode images to MP4...");

    // Initialize MediaCodec and MediaMuxer
    MediaCodec codec = createMediaCodec();
    MediaMuxer muxer = createMediaMuxer(outputFilePath);

    int videoTrackIndex = -1;
    long timestampUs = 0;

    // Loop through each image and encode it
    for (int i = 0; i < imageFiles.size(); i++) {
        File imageFile = imageFiles.get(i);

        Log.i(TAG, "Processing image " + (i + 1) + " of " + imageFiles.size() + ": " + imageFile.getName());

        // Read the image file into a byte buffer (assuming it's a JPEG)
        byte[] imageData = readImage(imageFile);
        Log.d(TAG, "Image data read successfully, size: " + imageData.length + " bytes");

        // Encode the image and write the encoded data to the muxer
        videoTrackIndex = encodeImageToVideo(codec, muxer, imageData, videoTrackIndex, timestampUs);
        timestampUs += 1000000 / FRAME_RATE; // 1 second divided by the frame rate

        Log.d(TAG, "Timestamp updated: " + timestampUs);
    }

    // Finalize the video encoding process
    stopAndReleaseResources(codec, muxer);

    Log.i(TAG, "MP4 file created at: " + outputFilePath);
}

    private static MediaCodec createMediaCodec() throws Exception {
        Log.i(TAG, "Creating MediaCodec for encoding...");

        // Try to create the encoder by MIME type
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            Log.i(TAG, "MediaCodec created successfully with MIME type: " + VIDEO_MIME_TYPE);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create MediaCodec with MIME type: " + VIDEO_MIME_TYPE, e);
            throw new Exception("Failed to create MediaCodec", e);
        }

        // Create and configure the format
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);  // I-frames every 5 seconds

        // Configure the codec
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            Log.i(TAG, "MediaCodec configured and started successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to configure or start MediaCodec.", e);
            throw new Exception("Failed to configure MediaCodec", e);
        }

        return codec;
    }


    private static MediaMuxer createMediaMuxer(String outputFilePath) throws Exception {
        Log.i(TAG, "Creating MediaMuxer for output file: " + outputFilePath);
        return new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    private static byte[] readImage(File imageFile) throws Exception {
        Log.d(TAG, "Reading image file: " + imageFile.getAbsolutePath());
        FileInputStream fis = new FileInputStream(imageFile);
        byte[] imageData = new byte[(int) imageFile.length()];
        fis.read(imageData);
        fis.close();
        Log.d(TAG, "Image file read successfully.");
        return imageData;
    }

    private static int encodeImageToVideo(MediaCodec codec, MediaMuxer muxer, byte[] imageData, int videoTrackIndex, long timestampUs) throws Exception {
        Log.d(TAG, "Encoding image to video, timestamp: " + timestampUs);
        ByteBuffer inputBuffer = ByteBuffer.wrap(imageData);

        // Get input buffer and queue the image data
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int inputBufferIndex = codec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer codecInputBuffer = codec.getInputBuffer(inputBufferIndex);
            codecInputBuffer.clear();
            codecInputBuffer.put(inputBuffer);
            codec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.remaining(), timestampUs, 0);
            Log.d(TAG, "Input buffer queued, index: " + inputBufferIndex);
        } else {
            Log.w(TAG, "No input buffer available, skipping this frame");
        }

        // Retrieve the encoded data from the output buffer
        int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);

            if (videoTrackIndex == -1) {
                // Add video track to muxer if it's not added already
                MediaFormat outputFormat = codec.getOutputFormat();
                videoTrackIndex = muxer.addTrack(outputFormat);
                muxer.start();
                Log.i(TAG, "Video track added to muxer.");
            }

            // Write encoded data to the muxer
            muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
            codec.releaseOutputBuffer(outputBufferIndex, false);
            Log.d(TAG, "Output buffer released, index: " + outputBufferIndex);

            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
        }

        return videoTrackIndex;
    }

    private static void stopAndReleaseResources(MediaCodec codec, MediaMuxer muxer) {
        Log.i(TAG, "Stopping and releasing resources...");
        codec.stop();
        codec.release();
        muxer.stop();
        muxer.release();
        Log.i(TAG, "Resources released successfully.");
    }

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
