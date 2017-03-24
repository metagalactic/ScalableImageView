package com.github.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.github.metagalactic.views.ScalableImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ScalableImageViewDemoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scalable_image_view_demo);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ScalableImageView imageView = (ScalableImageView) findViewById(R.id.scalable_image_view);
        List<String> imageStrings = getImageStrings();
        Random rand = new Random();
        int index = rand.nextInt(imageStrings.size());
        Glide.with(getApplicationContext())
                .load(imageStrings.get(index))
                .into(imageView);
    }

    //Sample images from myntra site
    private List<String> getImageStrings() {
        List<String> images = new ArrayList<>();
        images.add("http://assets.myntassets.com/w_720,q_90/v1/images/style/properties/Ira-Soleil-Women-Black-Printed-Kurti_c86a54f87d73cf9b6516ea35a2f0c98c_images.jpg");
        images.add("http://assets.myntassets.com/w_720,q_90/v1/images/style/properties/Ira-Soleil-Women-Black-Printed-Kurti_90553860893fd3b40a4f6781f50b3aaa_images.jpg");
        images.add("http://assets.myntassets.com/w_720,q_90/v1/images/style/properties/Ira-Soleil-Women-Black-Printed-Kurti_8009a64be1e7e4b4c7b67019552d57e6_images.jpg");
        images.add("http://assets.myntassets.com/w_720,q_90/v1/images/style/properties/Ira-Soleil-Women-Black-Printed-Kurti_c86a54f87d73cf9b6516ea35a2f0c98c_images.jpg");
        return images;
    }


}
