package com.danikula.videocache.sample;

import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import org.androidannotations.annotations.EActivity;

@EActivity(R.layout.activity_single_video)
public class SingleVideoActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        if (state == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.containerView, VideoFragment.build(Video.ORANGE_1.url))
                    .commit();
        }
    }
}
