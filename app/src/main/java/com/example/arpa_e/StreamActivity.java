package com.example.arpa_e;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StreamActivity extends AppCompatActivity {

    private static final String ESP32_IP = "192.168.8.45";
    private static final int ESP32_PORT = 100;
    private static final String TAG = "Esp32SocketClient";
    private enum Mode {
        LINE, BYTE
    }
    private Mode mode = Mode.LINE;
    private byte[] buffer = new byte[64];
    private ByteArrayOutputStream allbyteBuffer = new ByteArrayOutputStream();
    private StringBuilder lineBuffer = new StringBuilder();  // For accumulating line data
    private int bufSize = 0;  // Expected size for image data

    private ImageView streamImageView;
    private boolean isStreaming = true;
    private int frame_bytes_received = 0;
    int imgW;
    int imgH;
//    InputStream inputStream;
    Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        streamImageView = findViewById(R.id.streamImageView);
        Button btnStopStream = findViewById(R.id.btnStopStream);

        // Start streaming in a separate thread
        new Thread(this::startStreaming).start();
        // Stop streaming and close activity on button click
        btnStopStream.setOnClickListener(v -> {
            isStreaming = false;
            finish();
        });
    }

    private void startStreaming() {
        try {
            socket = new Socket(ESP32_IP, ESP32_PORT);
            OutputStream outputStream = socket.getOutputStream();
            Log.d("secondwindow", " ip seems to be connected");

            String helloMessage = "hello-"+ESP32_PORT+"\n"; // Replace "1234" with the desired port or identifier
            InputStream inputStream = socket.getInputStream();
            outputStream.write(helloMessage.getBytes());
            outputStream.flush();

            while (isStreaming) {

                processReceivedData(buffer,inputStream);

                // Decode raw JPEG frame

            }

        } catch (Exception e) {
            Log.e("secondwindow", "Error opening second window", e);

        }
    }
    private void removeProcessedBytes(int bytesToRemove) {
        // Remove bytes from ByteArrayOutputStream
        byte[] currentBytes = allbyteBuffer.toByteArray();
        allbyteBuffer.reset();
        allbyteBuffer.write(currentBytes, bytesToRemove, currentBytes.length - bytesToRemove);

    }
    public byte[] getRawBytes(ByteArrayOutputStream byteBuffer) {
        return byteBuffer.toByteArray();
    }
    private void processReceivedData(byte[] buffer,InputStream inputStream) {
        try {
            int bytesRead = inputStream.read(buffer);
            String receivedData = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

            if (mode == Mode.LINE) {
                // Accumulate data in line buffer
                lineBuffer.append(receivedData);
                allbyteBuffer.write(buffer);


                // Process complete lines using maxsplit equivalent
                if (lineBuffer.toString().contains("\n")) {
                    // Split into at most 2 parts (maxsplit=1)
                    int newlineIndex = lineBuffer.indexOf("\n");
                    String dataStr = lineBuffer.substring(0, newlineIndex).trim();  // Equivalent to rstrip()
                    lineBuffer.delete(0, newlineIndex + 1);  // Remove processed part from buffer
                    removeProcessedBytes(newlineIndex + 1);
                    // Decode and split data
                    handleLineData(dataStr);
                }
            } else if (mode == Mode.BYTE) {
                frame_bytes_received+=bytesRead;
                // Process binary data
                String needle = "camera-end";
                int index = receivedData.indexOf(needle);
                if (index != -1) {
                    int newlineIndex_after = receivedData.indexOf("\n", index);
                    // Substring exists, append the content before the index to StringBuilder
                    lineBuffer.append(receivedData.substring(0, index));
                    // only write first index bytes to allbytebuffer
                    allbyteBuffer.write(buffer, index+1, buffer.length - index - 1);

//                    removeProcessedBytes(newlineIndex + 1);
                    Log.d(TAG, "an entire image has been sent, actual length is: " + frame_bytes_received);


                    frame_bytes_received = 0;
                    byte[] buffer_temp = lineBuffer.toString().getBytes(StandardCharsets.UTF_8);
                    byte[] raw_buffer = getRawBytes(allbyteBuffer);

                    byte[] imageData = new byte[raw_buffer.length];
                    System.arraycopy(raw_buffer, 0, imageData, 0, raw_buffer.length);
//                    lineBuffer.delete(0, lineBuffer.length());
                    lineBuffer.setLength(0);
                    allbyteBuffer.reset();
                    lineBuffer.append(receivedData.substring(newlineIndex_after+1));
                    //TODO append the rest to allbyteBuffer
                    byte[] temp_bytes = Arrays.copyOfRange(buffer, newlineIndex_after+1, buffer.length);
                    allbyteBuffer.write(temp_bytes);


                    // Process image data (e.g., convert to Bitmap or save as a file)
                    Log.d(TAG, "handleImageData started, raw_buffer.length is: " + raw_buffer.length + " linebuffer length is: " + lineBuffer.length() + " imageData length is: " + imageData.length);

                    handleImageData(raw_buffer);

                    mode = Mode.LINE;  // Switch back to LINE mode after processing

                }
                else{
                    lineBuffer.append(receivedData);
                    allbyteBuffer.write(buffer);

                }
            }
        }catch (Exception e){
            Log.e(TAG, "reading data error", e);
        }
    }

    private void handleImageData(byte[] imageData) {
        Log.d(TAG, "Received image data, size: " + imageData.length);

        // Example: Convert to Bitmap and display or save the image
        // Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
        // imageView.setImageBitmap(bitmap);
//        new Thread(() -> {
            try {
                re_encode_img(imageData, imageData.length);
            } catch (Exception e) {
                Log.e(TAG, "Error encoding frames", e);
            }
//        });
    }

    private void handleLineData(String line) {
        // Split line into components
        String[] parts = line.split(" ", -1);  // Keep all fields like Python's split

        if (parts.length < 1) return;

        switch (parts[0]) {
            case "encoder":
                if (parts.length >= 4) {
                    int encStamp = Integer.parseInt(parts[1]);
                    float[] encVals = new float[3];
                    encVals[0] = Float.parseFloat(parts[2]);
                    encVals[1] = Float.parseFloat(parts[3]);
                    encVals[2] = Float.parseFloat(parts[4]);

                    Log.d(TAG, "Encoder Data: " + encStamp + " - " + encVals[0] + ", " + encVals[1] + ", " + encVals[2]);
                }
                break;

            case "camera":
                if (parts.length >= 5) {
                    imgW = Integer.parseInt(parts[1]);
                    imgH = Integer.parseInt(parts[2]);
                    bufSize = Integer.parseInt(parts[3]);  // Expected binary data size
                    int imgStamp = Integer.parseInt(parts[4]);
                    mode = Mode.BYTE;  // Switch to BYTE mode for receiving image data

                    Log.d(TAG, "Camera Info: " + imgW + "x" + imgH + ", Buffer size: " + bufSize);
                }
                break;

            case "camera-end":
                if (parts.length >= 2) {
                    float imgDuration = Float.parseFloat(parts[1]) / 1000.0f;
                    Log.d(TAG, "Camera Duration: " + imgDuration + " seconds");
                }
                break;

            default:
                Log.d(TAG, "Unhandled Line: " + line);
        }
    }

    private void re_encode_img(byte[] frameBuffer, int frameSize){
        Log.d("JPEG Check", "First 5 bytes: " + Arrays.toString(Arrays.copyOfRange(frameBuffer, 0, 5)));
        Bitmap bitmap = BitmapFactory.decodeByteArray(frameBuffer, 0, frameSize);
        if (bitmap == null){
            Log.d(TAG, "bitmap null");

        }
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, imgW, imgH, true);

        if (resizedBitmap != null) {
            Log.d(TAG, "after bitmap");


            // Re-encode as JPEG
            ByteArrayOutputStream encodedOutput = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, encodedOutput);
            byte[] jpegBytes = encodedOutput.toByteArray();
            Bitmap jpegBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);


            // Display re-encoded frame
//            runOnUiThread(() -> streamImageView.setVisibility(View.VISIBLE));
            runOnUiThread(() -> streamImageView.setImageBitmap(jpegBitmap));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isStreaming = false;
    }
}
