
package com.example.floraai;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class HistoryActivity extends AppCompatActivity {

    LinearLayout historyContainer;
    TextView emptyHistoryText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyContainer = findViewById(R.id.historyContainer);
        emptyHistoryText = findViewById(R.id.emptyHistoryText);

        loadHistory();
    }

    private void loadHistory() {

        try {
            historyContainer.removeAllViews();

            String savedHistory = getSharedPreferences(
                    "FloraAIHistory", MODE_PRIVATE)
                    .getString("history", "[]");

            JSONArray historyArray = new JSONArray(savedHistory);

            if (historyArray.length() == 0) {
                emptyHistoryText.setVisibility(TextView.VISIBLE);
                return;
            }

            emptyHistoryText.setVisibility(TextView.GONE);

            for (int i = historyArray.length() - 1; i >= 0; i--) {

                JSONObject record = historyArray.getJSONObject(i);

                String flowerName = record.getString("name");
                String imagePath = record.getString("image");
                String date = record.getString("date");

                addHistoryItem(flowerName, imagePath, date);
            }

        } catch (Exception e) {
            emptyHistoryText.setVisibility(TextView.VISIBLE);
            emptyHistoryText.setText("Unable to load history.");
        }
    }

    private void addHistoryItem(String name, String imagePath, String date) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(12, 12, 12, 12);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(20);
        background.setStroke(1, Color.LTGRAY);
        card.setBackground(background);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);

        cardParams.setMargins(0, 0, 0, 14);
        card.setLayoutParams(cardParams);

        ImageView imageView = new ImageView(this);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(90, 90);

        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        File imageFile = new File(imagePath);

        if (imageFile.exists()) {
            imageView.setImageBitmap(
                    BitmapFactory.decodeFile(imagePath));
        }

        card.addView(imageView);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(16, 0, 0, 0);

        TextView nameText = new TextView(this);
        nameText.setText(name.substring(0, 1).toUpperCase()
                + name.substring(1));
        nameText.setTextSize(19);
        nameText.setTextColor(Color.rgb(21, 101, 192));
        nameText.setTypeface(null, Typeface.BOLD);

        TextView dateText = new TextView(this);
        dateText.setText(date);
        dateText.setTextSize(13);
        dateText.setTextColor(Color.DKGRAY);
        dateText.setPadding(0, 8, 0, 0);

        details.addView(nameText);
        details.addView(dateText);

        card.addView(details);
        historyContainer.addView(card);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }
}
