package com.example.sc2006;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sc2006.ml.MobilenetV110224Quant;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ImageView imgview;
    private Bitmap bitmap;
    private Button predict;
    private Button select;
    private Button camerabtn;
    private TextView tv;

    private static final int IMAGE_SELECT_REQUEST = 250;
    private static final int CAMERA_CAPTURE_REQUEST = 200;
    private static final int CAMERA_PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgview = findViewById(R.id.imageView2);
        tv = findViewById(R.id.textView2);
        select = findViewById(R.id.button);
        predict = findViewById(R.id.button2);
        camerabtn = findViewById(R.id.camerabtn);

        checkAndRequestPermissions();

        select.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, IMAGE_SELECT_REQUEST);
        });

        predict.setOnClickListener(v -> {
            if (bitmap != null) {
                predictImage(bitmap);
            } else {
                Toast.makeText(MainActivity.this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });

        camerabtn.setOnClickListener(v -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(cameraIntent, CAMERA_CAPTURE_REQUEST);
        });
    }

    private void checkAndRequestPermissions() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            Toast.makeText(this, "Camera permission is already granted", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == IMAGE_SELECT_REQUEST && data != null && data.getData() != null) {
                Uri uri = data.getData();
                imgview.setImageURI(uri);
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == CAMERA_CAPTURE_REQUEST && data != null) {
                Bundle extras = data.getExtras();
                bitmap = (Bitmap) extras.get("data");
                imgview.setImageBitmap(bitmap);
            }
        }
    }

    private void predictImage(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        try {
            MobilenetV110224Quant model = MobilenetV110224Quant.newInstance(getApplicationContext());
            TensorImage tbuffer = TensorImage.fromBitmap(resized);
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.UINT8);
            inputFeature0.loadBuffer(tbuffer.getBuffer());

            MobilenetV110224Quant.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] outputArray = outputFeature0.getFloatArray();
            int maxIndex = getMaxIndex(outputArray);

            String[] labels = getLabels();
            if (maxIndex >= 0 && maxIndex < labels.length) {
                tv.setText(labels[maxIndex]);
            } else {
                Toast.makeText(this, "Invalid model output", Toast.LENGTH_SHORT).show();
            }

            model.close();
        } catch (Exception e) {
            Log.e("ModelExecution", "Error during model execution", e);
            Toast.makeText(this, "Model execution failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
    }

    private int getMaxIndex(float[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private String[] getLabels() {
        try {
            InputStream is = getAssets().open("label.txt"); // Ensure this matches the actual file name
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            ArrayList<String> labels = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
            reader.close();
            return labels.toArray(new String[0]);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load labels", Toast.LENGTH_SHORT).show();
            return new String[]{};
        }
    }

}
