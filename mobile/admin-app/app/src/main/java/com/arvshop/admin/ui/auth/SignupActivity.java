package com.arvshop.admin.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.arvshop.admin.R;
import com.arvshop.admin.ui.dashboard.DashboardActivity;

/**
 * Owner sign-up (create shop account). Mirrors the server's SignupRequest
 * constraints: password >= 8 chars. On success the session is stored and we go
 * straight to the dashboard (no need to log in again).
 */
public class SignupActivity extends AppCompatActivity {

    private SignupViewModel viewModel;
    private EditText nameInput, phoneInput, passwordInput;
    private Button signupButton;
    private ProgressBar progress;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.signup_title);
        }

        nameInput = findViewById(R.id.signup_name);
        phoneInput = findViewById(R.id.signup_phone);
        passwordInput = findViewById(R.id.signup_password);
        signupButton = findViewById(R.id.signup_button);
        progress = findViewById(R.id.signup_progress);
        errorText = findViewById(R.id.signup_error);

        signupButton.setOnClickListener(v -> attemptSignup());

        viewModel = new ViewModelProvider(this).get(SignupViewModel.class);
        viewModel.state().observe(this, result -> {
            if (result == null) return;
            switch (result.status) {
                case LOADING:
                    setLoading(true);
                    errorText.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    setLoading(false);
                    Intent intent = new Intent(this, DashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                    break;
                case ERROR:
                    setLoading(false);
                    errorText.setText(result.message);
                    errorText.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }

    private void attemptSignup() {
        String name = nameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (phone.length() < 4) {
            errorText.setText(R.string.signup_phone_invalid);
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        if (password.length() < 8) {
            errorText.setText(R.string.signup_password_short);
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        viewModel.signup(phone, password, name);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        signupButton.setEnabled(!loading);
        nameInput.setEnabled(!loading);
        phoneInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
