package com.example.nasa2021;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback{
    private SurfaceHolder holder;
    private Camera mCamera;
    private static String TAG="CameraPreview";

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera=camera;
        holder=getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            CameraActivity.started=true;
        }
        catch (IOException e){
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (holder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }
       List<Camera.Size> sizes =mCamera.getParameters().getSupportedPreviewSizes();
        String availableSizes="";
        for(Camera.Size s: sizes){
            availableSizes=s+"height : "+s.height+" width : "+s.width+"\n";
        }
        Log.i("SIZES", availableSizes);
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(640, 480);
        requestLayout();
        mCamera.setParameters(parameters);


        // set preview size and make any resize, rotate or
        // reformatting changes here
        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            CameraActivity.started=true;
        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

    }
}
