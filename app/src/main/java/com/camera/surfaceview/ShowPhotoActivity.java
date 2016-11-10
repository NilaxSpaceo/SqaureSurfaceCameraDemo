package com.camera.surfaceview;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;

public class ShowPhotoActivity extends Activity {

    private String imagePath;

    private ImageView ivImage;

    private boolean fromEdit = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_photo);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey("path")) {
                imagePath = extras.getString("path");
            }
        }
        InitControls();
    }

    void InitControls() {
        ivImage = (ImageView) findViewById(R.id.ivImage);
        ivImage.setImageURI(Uri.parse(imagePath));
    }


}
