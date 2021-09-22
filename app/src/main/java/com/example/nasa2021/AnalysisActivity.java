package com.example.nasa2021;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class AnalysisActivity extends AppCompatActivity {

    private LocalModel model;
    private ImageAnalysis analysis;
    private ImageLabeler labeler;
    private CustomImageLabelerOptions customImageLabelerOptions;
    private TextView labelButton;
    private static final String TAG="AnalysisActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);
        labelButton=(TextView) findViewById(R.id.label_name);
        model=new LocalModel.Builder()
                .setAssetFilePath("model.tflite")
                .build();

        customImageLabelerOptions=new CustomImageLabelerOptions.Builder(model)
                .setConfidenceThreshold(.4f)
                .setMaxResultCount(1)
                .build();

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

        analysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        labeler= ImageLabeling.getClient(customImageLabelerOptions);

        analysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                int rotation =image.getImageInfo().getRotationDegrees();
                @SuppressLint("UnsafeOptInUsageError") InputImage input =InputImage.fromMediaImage(image.getImage(), rotation);
                if(input!=null){

                    labeler.process(input).addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                        @Override
                        public void onSuccess(@NonNull @NotNull List<ImageLabel> imageLabels) {
                            Log.i(TAG, "successfully started to process the image");

                            if(imageLabels.size()>0) {
                                String labelName = imageLabels.get(0).getText() + " " + imageLabels.get(0).getConfidence();

                                labelButton.setText(labelName);
                                image.close();
                            }
                        }

                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull @NotNull Exception e) {
                            Log.i(TAG, "ImageLabeling process failed "+"\n"+e.getMessage()+"\n"+e.getCause().getMessage());
                            e.printStackTrace();
                            image.close();
                        }
                    });

                }

            }
        });
        try {
            ProcessCameraProvider.getInstance(this).get().bindToLifecycle((LifecycleOwner) AnalysisActivity.this, selector,analysis);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
