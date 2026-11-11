package ca.pkay.rcloneexplorer.Activities;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;

import ca.pkay.rcloneexplorer.util.ActivityHelper;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.util.MarkdownView;


public class ContributorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        ActivityHelper.applyTheme(this);
        try {
            setContentView(R.layout.activity_contributors);
        } catch (Exception e) {
            MarkdownView.closeOnMissingWebView(this, e);
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        MarkdownView markdownView = findViewById(R.id.markdownView);
        markdownView.loadAsset("contributors.md");
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
