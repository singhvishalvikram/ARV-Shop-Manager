package com.arvshop.admin.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.arvshop.admin.R;
import com.google.android.material.snackbar.Snackbar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * White-label + display settings editor. Reads the full settings map and writes back
 * only the fields exposed here; unknown keys are preserved server-side (POST merges).
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsViewModel viewModel;
    private EditText title, subtitle, whatsapp, currency, location;
    private Button saveButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_settings);
        }

        title = findViewById(R.id.set_title);
        subtitle = findViewById(R.id.set_subtitle);
        whatsapp = findViewById(R.id.set_whatsapp);
        currency = findViewById(R.id.set_currency);
        location = findViewById(R.id.set_location);
        saveButton = findViewById(R.id.set_save);
        progress = findViewById(R.id.set_progress);

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        saveButton.setOnClickListener(v -> save());

        viewModel.loadState().observe(this, result -> {
            if (result == null) return;
            if (result.status == com.arvshop.admin.core.Result.Status.SUCCESS) {
                fill(result.data);
            } else if (result.status == com.arvshop.admin.core.Result.Status.ERROR) {
                Snackbar.make(saveButton, result.message, Snackbar.LENGTH_LONG).show();
            }
        });
        viewModel.saveState().observe(this, result -> {
            if (result == null) return;
            switch (result.status) {
                case LOADING:
                    progress.setVisibility(View.VISIBLE);
                    saveButton.setEnabled(false);
                    break;
                case SUCCESS:
                    progress.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    Snackbar.make(saveButton, R.string.settings_saved, Snackbar.LENGTH_SHORT).show();
                    break;
                case ERROR:
                    progress.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    Snackbar.make(saveButton, result.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });

        viewModel.load();
    }

    private void fill(Map<String, String> s) {
        title.setText(s.getOrDefault("app_title", ""));
        subtitle.setText(s.getOrDefault("app_subtitle", ""));
        whatsapp.setText(s.getOrDefault("whatsapp_number", ""));
        currency.setText(s.getOrDefault("currency_symbol", "₹"));
        location.setText(s.getOrDefault("shop_location", ""));
    }

    private void save() {
        Map<String, String> changed = new LinkedHashMap<>();
        changed.put("app_title", title.getText().toString().trim());
        changed.put("app_subtitle", subtitle.getText().toString().trim());
        changed.put("whatsapp_number", whatsapp.getText().toString().trim());
        changed.put("currency_symbol", currency.getText().toString().trim());
        changed.put("shop_location", location.getText().toString().trim());
        viewModel.save(changed);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
