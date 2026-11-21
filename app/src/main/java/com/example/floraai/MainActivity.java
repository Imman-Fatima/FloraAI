
package com.example.floraai;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ImageView flowerImage;
    private Button selectButton, predictButton;
    private TextView resultText;
    private Bitmap selectedBitmap;
    private Interpreter interpreter;
    private List<String> flowerLabels;

    private static final int IMAGE_SIZE = 224;
    private static final int IMAGE_PICK_CODE = 100;
    private static final float CONFIDENCE_THRESHOLD = 0.60f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button historyButton = findViewById(R.id.historyButton);

        historyButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        flowerImage = findViewById(R.id.flowerImage);
        selectButton = findViewById(R.id.selectButton);
        predictButton = findViewById(R.id.predictButton);
        resultText = findViewById(R.id.resultText);

        try {
            interpreter = new Interpreter(loadModelFile());
            flowerLabels = loadLabels();

            Toast.makeText(this,
                    "FloraAI Model Loaded Successfully!",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this,
                    "Model Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        selectButton.setOnClickListener(v -> openGallery());

        predictButton.setOnClickListener(v -> {
            if (selectedBitmap == null) {
                Toast.makeText(this,
                        "Please select an image first!",
                        Toast.LENGTH_SHORT).show();
            } else if (interpreter != null) {
                predictFlower();
            }
        });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_PICK_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == IMAGE_PICK_CODE
                && resultCode == Activity.RESULT_OK
                && data != null) {

            try {
                Uri imageUri = data.getData();

                selectedBitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), imageUri);

                flowerImage.setImageBitmap(selectedBitmap);
                resultText.setText(
                        "Image selected! Click Recognize Flower.");

            } catch (Exception e) {
                Toast.makeText(this,
                        "Image could not be loaded!",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private MappedByteBuffer loadModelFile() throws Exception {

        try (android.content.res.AssetFileDescriptor fileDescriptor =
                     getAssets().openFd("FloraAI_model.tflite");
             FileInputStream inputStream =
                     new FileInputStream(fileDescriptor.getFileDescriptor());
             FileChannel fileChannel = inputStream.getChannel()) {

            return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.getStartOffset(),
                    fileDescriptor.getDeclaredLength());
        }
    }

    private List<String> loadLabels() throws Exception {

        List<String> labels = new ArrayList<>();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        getAssets().open("flower_labels.txt")));

        String line;

        while ((line = reader.readLine()) != null) {
            labels.add(line.trim());
        }

        reader.close();

        if (labels.size() != 6) {
            throw new Exception("Expected 6 labels, found " + labels.size());
        }

        return labels;
    }

    private void predictFlower() {

        try {
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                    selectedBitmap,
                    IMAGE_SIZE,
                    IMAGE_SIZE,
                    true);

            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(
                    4 * IMAGE_SIZE * IMAGE_SIZE * 3);

            inputBuffer.order(ByteOrder.nativeOrder());

            for (int y = 0; y < IMAGE_SIZE; y++) {
                for (int x = 0; x < IMAGE_SIZE; x++) {

                    int pixel = resizedBitmap.getPixel(x, y);

                    float red = android.graphics.Color.red(pixel);
                    float green = android.graphics.Color.green(pixel);
                    float blue = android.graphics.Color.blue(pixel);

                    inputBuffer.putFloat(red);
                    inputBuffer.putFloat(green);
                    inputBuffer.putFloat(blue);
                }
            }

            inputBuffer.rewind();

            float[][] output = new float[1][6];

            interpreter.run(inputBuffer, output);

            int bestIndex = 0;
            float confidence = output[0][0];

            for (int i = 1; i < 6; i++) {
                if (output[0][i] > confidence) {
                    confidence = output[0][i];
                    bestIndex = i;
                }
            }

            String predictedClass = flowerLabels.get(bestIndex);
            float percentage = confidence * 100;

            if (predictedClass.equals("not_flower")
                    || confidence < CONFIDENCE_THRESHOLD) {

                resultText.setText(
                        "Not a Flower\n\n" +
                                "Confidence: " +
                                String.format(Locale.US, "%.2f", percentage) + "%");

            } else {

                String information = getFlowerInformation(predictedClass);

                resultText.setText(
                        "Flower: " + predictedClass +
                                "\nConfidence: " +
                                String.format(Locale.US, "%.2f", percentage) + "%" +
                                "\n\n" + information);

                // Save recognized flower to history
                saveToHistory(predictedClass);
            }

        } catch (Exception e) {
            Toast.makeText(this,
                    "Prediction Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void saveToHistory(String flowerName) {

        try {
            File historyFolder = new File(getFilesDir(), "history_images");

            if (!historyFolder.exists()) {
                historyFolder.mkdirs();
            }

            String fileName = "flower_" +
                    System.currentTimeMillis() + ".jpg";

            File imageFile = new File(historyFolder, fileName);

            FileOutputStream outputStream = new FileOutputStream(imageFile);

            selectedBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    90,
                    outputStream);

            outputStream.close();

            String date = new SimpleDateFormat(
                    "dd MMM yyyy, hh:mm a",
                    Locale.getDefault()).format(new Date());

            SharedPreferences preferences =
                    getSharedPreferences("FloraAIHistory", MODE_PRIVATE);

            String oldHistory = preferences.getString("history", "[]");

            JSONArray historyArray = new JSONArray(oldHistory);

            JSONObject record = new JSONObject();

            record.put("name", flowerName);
            record.put("image", imageFile.getAbsolutePath());
            record.put("date", date);

            historyArray.put(record);

            preferences.edit()
                    .putString("history", historyArray.toString())
                    .apply();

            Toast.makeText(this,
                    "Saved to History!",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this,
                    "History Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String getFlowerInformation(String flowerName) {

        switch (flowerName.toLowerCase(Locale.ROOT)) {

            case "daisy":
                return "Scientific Name: Bellis perennis\n" +
                        "Color: White, pink or yellow\n" +
                        "Features: Small flower with a yellow center\n" +
                        "Fact: Daisies open during the day.";

            case "dandelion":
                return "Scientific Name: Taraxacum officinale\n" +
                        "Color: Yellow\n" +
                        "Features: Bright yellow flower that turns into a seed head\n" +
                        "Fact: Its seeds spread with the wind.";

            case "rose":
                return "Scientific Name: Rosa\n" +
                        "Color: Red, pink, white and many more\n" +
                        "Features: Soft petals and often thorny stems\n" +
                        "Fact: Roses are commonly associated with love.";

            case "sunflower":
                return "Scientific Name: Helianthus annuus\n" +
                        "Color: Yellow petals with a dark center\n" +
                        "Features: Large flower head\n" +
                        "Fact: Young sunflowers track the sun.";

            case "tulip":
                return "Scientific Name: Tulipa\n" +
                        "Color: Red, yellow, pink, purple and more\n" +
                        "Features: Cup-shaped petals\n" +
                        "Fact: Tulips are popular spring flowers.";

            default:
                return "Flower information is not available.";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (interpreter != null) {
            interpreter.close();
        }
    }
}
