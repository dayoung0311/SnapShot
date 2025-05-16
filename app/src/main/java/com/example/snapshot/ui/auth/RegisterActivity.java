package com.example.snapshot.ui.auth;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.snapshot.MainActivity;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityRegisterBinding;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RegisterActivity extends AppCompatActivity {
    
    private ActivityRegisterBinding binding;
    private UserRepository userRepository;
    private Uri profileImageUri;
    
    // 이미지 선택 결과를 처리하는 런처
    private final ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    profileImageUri = uri;
                    binding.ivProfilePic.setImageURI(uri);
                }
            });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 뷰 바인딩 초기화
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        
        setupToolbar();
        setupListeners();
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    private void setupListeners() {
        // 프로필 이미지 추가 버튼 클릭 리스너
        binding.ivAddPhoto.setOnClickListener(v -> getContent.launch("image/*"));
        
        // 회원가입 버튼 클릭 리스너
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        
        // 로그인 텍스트 클릭 리스너
        binding.tvLogin.setOnClickListener(v -> finish());
    }
    
    private void attemptRegister() {
        // 입력값 가져오기
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();
        
        // 유효성 검사
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || 
                TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, R.string.error_empty_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            binding.etConfirmPassword.setError(getString(R.string.error_password_mismatch));
            return;
        }
        
        // 로딩 표시
        showLoading(true);
        
        // Firebase 회원가입 시도
        userRepository.registerUser(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        if (firebaseUser != null) {
                            // 유저 정보 생성
                            createUserProfile(firebaseUser.getUid(), name, email);
                        }
                    } else {
                        // 회원가입 실패
                        Toast.makeText(RegisterActivity.this, R.string.error_register,
                                Toast.LENGTH_SHORT).show();
                        showLoading(false);
                    }
                });
    }
    
    private void createUserProfile(String userId, String name, String email) {
        // 기본 유저 프로필 생성
        User user = new User(userId, name, email, "");
        
        // 프로필 이미지가 있는 경우 업로드
        if (profileImageUri != null) {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), profileImageUri);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] data = baos.toByteArray();
                
                // 이미지 업로드
                userRepository.uploadProfileImage(userId, data)
                        .addOnSuccessListener(taskSnapshot -> {
                            // 이미지 URL 가져오기
                            taskSnapshot.getStorage().getDownloadUrl()
                                    .addOnSuccessListener(uri -> {
                                        user.setProfilePicUrl(uri.toString());
                                        saveUserToDatabase(user);
                                    });
                        })
                        .addOnFailureListener(e -> {
                            // 이미지 업로드 실패해도 기본 정보는 저장
                            saveUserToDatabase(user);
                        });
            } catch (IOException e) {
                // 이미지 처리 실패해도 기본 정보는 저장
                saveUserToDatabase(user);
            }
        } else {
            // 이미지 없는 경우 기본 정보만 저장
            saveUserToDatabase(user);
        }
    }
    
    private void saveUserToDatabase(User user) {
        userRepository.createOrUpdateUser(user)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        // 회원가입 성공
                        Toast.makeText(RegisterActivity.this, R.string.success_register,
                                Toast.LENGTH_SHORT).show();
                        navigateToMainActivity();
                    } else {
                        // 유저 정보 저장 실패
                        Toast.makeText(RegisterActivity.this, R.string.error_register,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void navigateToMainActivity() {
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        // 백 스택 비우기 (뒤로 가기 시 로그인/회원가입 화면으로 돌아오지 않게)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!isLoading);
        binding.etName.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.etConfirmPassword.setEnabled(!isLoading);
        binding.ivAddPhoto.setEnabled(!isLoading);
        binding.tvLogin.setEnabled(!isLoading);
    }
} 