package com.example.snapshot.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.snapshot.MainActivity;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityLoginBinding;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    
    private ActivityLoginBinding binding;
    private UserRepository userRepository;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 뷰 바인딩 초기화
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        
        // 이미 로그인된 사용자 확인
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser != null) {
            // 이미 로그인된 경우 메인 화면으로 이동
            navigateToMainActivity();
            return;
        }
        
        setupListeners();
    }
    
    private void setupListeners() {
        // 로그인 버튼 클릭 리스너
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        
        // 회원가입 텍스트 클릭 리스너
        binding.tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
    
    private void attemptLogin() {
        // 입력값 가져오기
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        
        // 유효성 검사
        if (TextUtils.isEmpty(email)) {
            binding.etEmail.setError(getString(R.string.error_empty_fields));
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            binding.etPassword.setError(getString(R.string.error_empty_fields));
            return;
        }
        
        // 로딩 표시
        showLoading(true);
        
        // Firebase 로그인 시도
        userRepository.loginUser(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            // 로그인 성공
                            Toast.makeText(LoginActivity.this, R.string.success_login, Toast.LENGTH_SHORT).show();
                            navigateToMainActivity();
                        }
                    } else {
                        // 로그인 실패
                        Toast.makeText(LoginActivity.this, R.string.error_login, Toast.LENGTH_SHORT).show();
                    }
                    showLoading(false);
                });
    }
    
    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        // 백 스택 비우기 (뒤로 가기 시 로그인 화면으로 돌아오지 않게)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.tvSignUp.setEnabled(!isLoading);
    }
} 