package com.example.nasa2021;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    TextView myLabel;
    EditText myTextbox;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    private ImageView imageView;
    int n;
    String data;
    AnimationDrawable d;
    private ImageAnalysis analysis;
    private ProcessCameraProvider cameraProvider;
    private ImageLabeler labeler;
    private LocalModel model;
    private static final String TAG = "MainActivity";
    private long detectionTime;
    private boolean isDetectingBottles;
    private CameraDevice device;
    private CameraDevice.StateCallback stateCallback;
    private ImageReader reader;
    private String cameraId;
    private Handler handler;
    private CaptureRequest request;
    private CameraCaptureSession session;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View decorView = getWindow().getDecorView();
// Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        getSupportActionBar().hide();

        model=new LocalModel.Builder()
                .setAssetFilePath("model.tflite")
                .build();

        CustomImageLabelerOptions customImageLabelerOptions=new CustomImageLabelerOptions.Builder(model)
                .setConfidenceThreshold(.4f)
                .setMaxResultCount(1)
                .build();

        labeler= ImageLabeling.getClient(customImageLabelerOptions);


      analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        Log.i(TAG, "before the analysis starts");

        analysis.setAnalyzer(Executors.newSingleThreadExecutor(), image -> {
            Log.i(TAG, "Analysis started");
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            @SuppressLint("UnsafeOptInUsageError") Image mediaImage=image.getImage();
            if(mediaImage!=null){
                InputImage i=InputImage.fromMediaImage(mediaImage, rotationDegrees);
                Log.i(TAG, ""+i.getHeight()+" "+i.getWidth()+" "+i.getRotationDegrees());
                labeler.process(i).addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                    @Override
                    public void onSuccess(@NonNull @NotNull List<ImageLabel> imageLabels) {
                        Log.i(TAG, "successfully started to process the image");
                        String labels="";
                        for(ImageLabel label: imageLabels){
                            Log.i(TAG, label.getText());
                            labels=label.getText()+ " "+label.getConfidence();
                            Log.i(TAG, labels);
                        }


                        image.close();
                    }


                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull @NotNull Exception e) {
                        Log.i(TAG, "ImageLabeling process failed");
                        e.printStackTrace();
                        image.close();
                    }
                });
            }
        });

      //  CameraSelector selector=new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        CameraSelector selector= CameraSelector.DEFAULT_BACK_CAMERA;

        try{
            cameraProvider=ProcessCameraProvider.getInstance(this).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "before binding to lifecycle");

      //  cameraProvider.bindToLifecycle((LifecycleOwner) this, selector, analysis);

        ListenableFuture<ProcessCameraProvider> m=ProcessCameraProvider.getInstance(this);
        m.addListener(new Runnable() {
            @Override
            public void run() {
               try {
                   m.get().bindToLifecycle((LifecycleOwner) MainActivity.this, selector, analysis);
               }
               catch(InterruptedException e){
                   e.printStackTrace();
               }
               catch(ExecutionException e){
                   e.printStackTrace();
               }
            }
        }, ContextCompat.getMainExecutor(this));


     /*   Button openButton = (Button)findViewById(R.id.open);
        Button sendButton = (Button)findViewById(R.id.send);
        Button closeButton = (Button)findViewById(R.id.close);
        myLabel = (TextView)findViewById(R.id.label);
        myTextbox = (EditText)findViewById(R.id.entry);
*/
        //Open Button
//                try
//                {
//                    findBT();
 //                   openBT();
 //               }
 //               catch (IOException ex) { }


      /*          try
                {
                    sendData();
                }
                catch (IOException ex) { }
*/
        //Close button
                //try
                //{
                 //   closeBT();
                //}
                //catch (IOException ex) { }

    }

    void findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            Log.i(TAG, "No bluetooth device or adapter found");
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("HC-05"))
                {
                    Log.i(TAG, "HC-05 found");
                    mmDevice = device;
                    break;
                }
            }
        }
        Log.i(TAG, "Bluetooth device found");
       // myLabel.setText("Bluetooth Device Found");
    }

    void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();
        Log.i(TAG, "bluetooth opened");
       // myLabel.setText("Bluetooth Opened");
    }

    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                                        public void run()
                                        {
                                            if(data.startsWith("b")){
                                                d.stop();
                                                imageView.setBackgroundResource(R.drawable.unnffffamed);

                                            }
                                            else {
                                                imageView.setBackgroundResource(R.drawable.blink_animation);
                                                d=(AnimationDrawable) imageView.getBackground();
                                                d.start();
                                            }
         //                                   myLabel.setText(data);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    void sendData(String message) throws IOException
    {
        mmOutputStream.write(message.getBytes());
    }

    void closeBT() throws IOException
    {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
    //    myLabel.setText("Bluetooth Closed");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int getRotationCompensation(String cameraId, Activity activity, boolean isFrontFacing)
            throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = ORIENTATIONS.get(deviceRotation);

        // Get the device's sensor orientation.
        CameraManager cameraManager = (CameraManager) activity.getSystemService(CAMERA_SERVICE);
        int sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);

        if (isFrontFacing) {
            rotationCompensation = (sensorOrientation + rotationCompensation) % 360;
        } else { // back-facing
            rotationCompensation = (sensorOrientation - rotationCompensation + 360) % 360;
        }
        return rotationCompensation;
    }

}

