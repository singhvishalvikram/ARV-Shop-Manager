package com.arvshop.admin.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.arvshop.admin.R;
import com.arvshop.admin.ui.dashboard.DashboardActivity;

/** Owner login. Launch screen; skips straight to dashboard if a session exists. */
public class LoginActivity extends AppCompatActivity {

    private AuthViewModel viewModel;
    private EditText phoneInput;
    private EditText passwordInput;
    private Button loginButton;
    private ProgressBar progress;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        if (viewModel.isLoggedIn()) {
            goToDashboard();
            return;
        }

        setContentView(R.layout.activity_login);
        phoneInput = findViewById(R.id.login_phone);
        passwordInput = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        progress = findViewById(R.id.login_progress);
        errorText = findViewById(R.id.login_error);

        loginButton.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.go_to_signup).setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

        viewModel.loginState().observe(this, result -> {
            if (result == null) return;
            switch (result.status) {
                case LOADING:
                    setLoading(true);
                    errorText.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    setLoading(false);
                    goToDashboard();
                    break;
                case ERROR:
                    setLoading(false);
                    errorText.setText(result.message);
                    errorText.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }

    private void attemptLogin() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (phone.isEmpty() || password.isEmpty()) {
            errorText.setText(R.string.login_missing_fields);
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        viewModel.login(phone, password);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        phoneInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
    }

    private void goToDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
