package com.example.nasa2021;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioRecord;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.StyledPlayerView;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.regex.Pattern;

public class CameraActivity extends AppCompatActivity {

    private static final int SELECT_DEVICE_REQUEST_CODE = 10;
    private Camera mCamera;
    private Camera.PictureCallback pictureCallback;
    private Camera.ShutterCallback shutterCallback;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static final String TAG = "CameraActivity";
    private CameraPreview preview;
    public static boolean started = false;
    private int rotation;
    private TextView textView;
    private ImageClassifier classifier;
    private SimpleExoPlayer player;
    private boolean stopWorker;
    private Thread workerThread;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mmDevice;
    private BluetoothSocket mmSocket;
    private InputStream mmInputStream;
    private OutputStream mmOutputStream;
    private int readBufferPosition;
    private byte[] readBuffer;
    private String data;
    private long detectedTime;
    private long receivedTime;
    private MediaItem next;
    private List<MediaItem> playList;
    private SpeechRecognizer recognizer;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Recognition is available on this system "+SpeechRecognizer.isRecognitionAvailable(this));
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
                Log.i(TAG, "ready to listen");
            }

            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onRmsChanged(float v) {

            }

            @Override
            public void onBufferReceived(byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {

            }

            @Override
            public void onError(int i) {

            }

            @Override
            public void onResults(Bundle bundle) {
                ArrayList<String> speech = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                for (String word : speech) {
                    Log.i(TAG, "Speech : " + word);
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {

            }

            @Override
            public void onEvent(int i, Bundle bundle) {

            }
        });
        recognizer.startListening(new Intent().setAction(RecognizerIntent.ACTION_RECOGNIZE_SPEECH));
        View decorView = getWindow().getDecorView();

        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        getSupportActionBar().hide();

        player = new SimpleExoPlayer.Builder(this).build();
        StyledPlayerView playerView = new StyledPlayerView(this);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        playerView.setBackgroundColor(getResources().getColor(R.color.black));
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setUseController(false);
        playerView.setPlayer(player);


        MediaItem i = new MediaItem.Builder().setUri("file:///android_asset/ask.mp4")
                .setMediaId("ASK")
                .build();

        MediaItem i3 = new MediaItem.Builder().setUri("file:///android_asset/thank.mp4")
                .setMediaId("Thank")
                .build();

        MediaItem i4 = new MediaItem.Builder().setUri("file:///android_asset/Stare.mp4")
                .setMediaId("STARE")
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    for (MediaItem item : playList) {
                        if (item.mediaId.equalsIgnoreCase("STARE")) {
                            next = item;
                            player.setRepeatMode(Player.REPEAT_MODE_ONE);
                            player.setMediaItem(next);
                            player.play();
                        }
                    }
                }
            }
        });
        playList = new ArrayList<>();
        playList.add(i);
        playList.add(i3);
        playList.add(i4);
        player.setMediaItem(i);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.prepare();

    //    ImageClassifier.ImageClassifierOptions options = ImageClassifier.ImageClassifierOptions.builder().setMaxResults(1).build();
        try {
            classifier = ImageClassifier.createFromFile(this, "lite25.tflite");
        } catch (IOException e) {
            e.printStackTrace();
        }
        safeCameraOpen();
        findBluetooth();
     try {
            openBluetooth();
        } catch (IOException e) {
            e.printStackTrace();
        }

        setContentView(R.layout.activity_preview);

        textView = new TextView(this);
        textView.setText("Nothing");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        textView.setLayoutParams(params);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(getResources().getColor(R.color.purple_500));

        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);

        Log.i(TAG, "Before starting " + String.valueOf(mCamera != null));
        preview = new CameraPreview(this, mCamera);

        FrameLayout camPreview = (FrameLayout) findViewById(R.id.camera_preview);
        camPreview.addView(preview);
        camPreview.addView(textView);
        camPreview.addView(playerView);
        player.play();
        pictureCallback = new Camera.PictureCallback() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void onPictureTaken(byte[] bytes, Camera camera) {
                Log.i(TAG, "picture taken");

                    TensorImage tensorImage = TensorImage.fromBitmap(getScaledBitmap(bytes));
                    List<Classifications> labels = classifier.classify(tensorImage);
                    String names = "";
                    for (Classifications label : labels) {
                        List<Category> cats = label.getCategories();
                        for (Category c : cats) {
                            if (c.getLabel().equalsIgnoreCase("Mountain Dew")) {
                                if (c.getScore() > 0.65 && System.currentTimeMillis() - detectedTime > 10000) {
                                  try {

                                        detectedTime = System.currentTimeMillis();
                                      sendData("b\n");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    Log.i(TAG, "DEW DETECTED");
                                }
                            }

                            names = names + c.getLabel() + " " + c.getScore() + "\n";
                        }
                    }
                    textView.setText(names);
                    Log.i(TAG, "" + names);

                    mCamera.startPreview();

            }
        };
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                mCamera.startPreview();
                Display display = ((WindowManager) getSystemService(WINDOW_SERVICE))
                        .getDefaultDisplay();

                int unsetRotation = display.getRotation();
                if (unsetRotation == 0) {
                    rotation = 0;
                } else if (unsetRotation == 1) {
                    rotation = 90;
                } else if (unsetRotation == 2) {
                    rotation = 180;
                } else if (unsetRotation == 3) {
                    rotation = 270;
                }
                mCamera.takePicture(null, null, pictureCallback);
                Log.d(TAG, "taken");
            }
        };
        timer.schedule(task, 5000, 5000);


    }

    private boolean safeCameraOpen() {
        boolean qOpened = false;

        try {
            mCamera = Camera.open();
            qOpened = (mCamera != null);
        } catch (Exception e) {
            Log.i(TAG, "failed to open Camera");
            e.printStackTrace();
        }

        return qOpened;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.release();
        player.release();
        recognizer.stopListening();
    }

    private Bitmap getScaledBitmap(byte[] bytes) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        options.inSampleSize = calculateInSampleSize(options);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options) {

        final int height = options.outHeight;
        final int width = options.outWidth;
        int reqHeight = options.outHeight / 2;
        int reqWidth = options.outWidth / 2;
        int inSampleSize = 1;
        Log.i(TAG, "Out "+ height);
        Log.i(TAG, "Out "+ width);
        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }


    void findBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.i(TAG, "No bluetooth device or adapter found");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.i(TAG, "device names "+device.getName());
                if (device.getName().equals("Nasa")) {
                    Log.i(TAG, device.getName()+" found");
                    mmDevice = device;
                    break;
                }
            }
        }
        Log.i(TAG, "Bluetooth device found");
    }

    void openBluetooth() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();
        Log.i(TAG, "bluetooth opened");

    }

    void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10;

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                                        public void run() {
                                            if (data.startsWith("m")) {
                                                    Log.i(TAG, "m");
                                                    for (MediaItem item : playList) {
                                                        if (item.mediaId.equalsIgnoreCase("ASK")) {
                                                            next = item;
                                                        }
                                                    }

                                                    new Handler(getMainLooper()).post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            player.setRepeatMode(Player.REPEAT_MODE_OFF);
                                                            player.setMediaItem(next);
                                                            player.prepare();
                                                            player.play();
                                                        }
                                                    });

                                            }

                                            else if (data.startsWith("r")){
                                             Log.i(TAG, "r");
                                                for (MediaItem item : playList) {
                                                    if (item.mediaId.equalsIgnoreCase("Thank")) {
                                                        next = item;
                                                    }
                                                }

                                                new Handler(getMainLooper()).post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        player.setRepeatMode(Player.REPEAT_MODE_OFF);
                                                        player.setMediaItem(next);
                                                        player.prepare();
                                                        player.play();
                                                    }
                                                });
                                            }
                                            else {

                                            }
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    private void sendData(String message) throws IOException {
       mmOutputStream.write(message.getBytes());
    }

    private void closeBluetooth() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
    }

}

