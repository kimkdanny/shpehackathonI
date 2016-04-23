package org.boofcv.objecttracking;

/**
 * Created by Kim on 4/23/16.
 */


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class HomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
    }

    public void openCanvas(View view) {
        Intent intent = new Intent(this, ObjectTrackerActivity.class);
        startActivity(intent);
    }
}