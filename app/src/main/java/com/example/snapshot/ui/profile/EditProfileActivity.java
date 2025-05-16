package com.example.snapshot.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityEditProfileBinding;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.UserRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.io.IOException;
import java.io.InputStream;

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private UserRepository userRepository;
    private User currentUserData;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    selectedImageUri = result.getData().getData();
                    Glide.with(this)
                            .load(selectedImageUri)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(binding.ivProfileImage);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = UserRepository.getInstance();

        setupToolbar();
        loadCurrentUserProfile();
        setupListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("프로필 편집");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void loadCurrentUserProfile() {
        FirebaseUser firebaseUser = userRepository.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showLoading(true);
        userRepository.getUserById(firebaseUser.getUid())
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        currentUserData = documentSnapshot.toObject(User.class);
                        if (currentUserData != null) {
                            populateProfileData();
                        }
                    } else {
                        Toast.makeText(this, "사용자 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void populateProfileData() {
        if (currentUserData == null) return;

        binding.etName.setText(currentUserData.getUsername());
        binding.etBio.setText(currentUserData.getBio());

        if (currentUserData.getProfilePicUrl() != null && !currentUserData.getProfilePicUrl().isEmpty()) {
            Glide.with(this)
                    .load(currentUserData.getProfilePicUrl())
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(binding.ivProfileImage);
        }
    }

    private void setupListeners() {
        binding.btnChangePhoto.setOnClickListener(v -> openImagePicker());
        binding.btnSave.setOnClickListener(v -> saveProfileChanges());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void saveProfileChanges() {
        String nameFromInput = binding.etName.getText().toString().trim();
        String bio = binding.etBio.getText().toString().trim();

        if (TextUtils.isEmpty(nameFromInput)) {
            binding.tilName.setError("이름을 입력해주세요.");
            return;
        }
        binding.tilName.setError(null);

        if (currentUserData == null) {
            Toast.makeText(this, "사용자 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        User updatedUser = new User();
        updatedUser.setUserId(currentUserData.getUserId());
        updatedUser.setUsername(nameFromInput);
        updatedUser.setBio(bio);
        updatedUser.setEmail(currentUserData.getEmail()); // 기존 이메일 유지
        updatedUser.setFollowers(currentUserData.getFollowers());
        updatedUser.setFollowing(currentUserData.getFollowing());
        updatedUser.setFollowerCount(currentUserData.getFollowerCount());
        updatedUser.setFollowingCount(currentUserData.getFollowingCount());
        updatedUser.setSavedTags(currentUserData.getSavedTags());

        if (selectedImageUri != null) {
            try {
                InputStream imageStream = getContentResolver().openInputStream(selectedImageUri);
                byte[] imageData = new byte[imageStream.available()];
                imageStream.read(imageData);
                imageStream.close();

                userRepository.uploadProfileImage(currentUserData.getUserId(), imageData)
                        .addOnSuccessListener(taskSnapshot -> taskSnapshot.getStorage().getDownloadUrl()
                                .addOnSuccessListener(uri -> {
                                    updatedUser.setProfilePicUrl(uri.toString());
                                    updateUserDocument(updatedUser);
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Toast.makeText(this, "이미지 URL 가져오기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }))
                        .addOnFailureListener(e -> {
                            showLoading(false);
                            Toast.makeText(this, "이미지 업로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } catch (IOException e) {
                showLoading(false);
                Toast.makeText(this, "이미지 처리 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            updatedUser.setProfilePicUrl(currentUserData.getProfilePicUrl()); // 기존 이미지 URL 유지
            updateUserDocument(updatedUser);
        }
    }

    private void updateUserDocument(User user) {
        userRepository.createOrUpdateUser(user)
                .addOnSuccessListener(aVoid -> {
                    // Firestore 업데이트 성공 후 Firebase Auth 프로필 업데이트
                    FirebaseUser firebaseUser = userRepository.getCurrentUser();
                    if (firebaseUser != null) {
                        UserProfileChangeRequest.Builder profileUpdatesBuilder = new UserProfileChangeRequest.Builder();
                        boolean needsAuthUpdate = false;

                        // 이름 변경 확인 및 설정 (null 체크 추가)
                        if (user.getUsername() != null && !user.getUsername().equals(firebaseUser.getDisplayName())) {
                            profileUpdatesBuilder.setDisplayName(user.getUsername());
                            needsAuthUpdate = true;
                        } else if (user.getUsername() != null && firebaseUser.getDisplayName() == null) {
                            // 기존 displayName이 null이고 새 이름이 있는 경우
                            profileUpdatesBuilder.setDisplayName(user.getUsername());
                            needsAuthUpdate = true;
                        }


                        // 프로필 사진 URL 변경 확인 및 설정 (null 체크 추가)
                        if (user.getProfilePicUrl() != null && !user.getProfilePicUrl().isEmpty()) {
                            Uri photoUri = Uri.parse(user.getProfilePicUrl());
                            if (firebaseUser.getPhotoUrl() == null || !photoUri.equals(firebaseUser.getPhotoUrl())) {
                                 profileUpdatesBuilder.setPhotoUri(photoUri);
                                 needsAuthUpdate = true;
                            }
                        } else if (user.getProfilePicUrl() == null || user.getProfilePicUrl().isEmpty()) {
                            // 프로필 사진을 삭제하는 경우 (기존 사진이 있었는데 새 URL이 없는 경우)
                            if (firebaseUser.getPhotoUrl() != null) {
                                profileUpdatesBuilder.setPhotoUri(null); // Auth 프로필 사진 삭제
                                needsAuthUpdate = true;
                            }
                        }


                        if (needsAuthUpdate) {
                            UserProfileChangeRequest profileUpdates = profileUpdatesBuilder.build();
                            firebaseUser.updateProfile(profileUpdates)
                                    .addOnCompleteListener(task -> {
                                        showLoading(false);
                                        if (task.isSuccessful()) {
                                            Toast.makeText(EditProfileActivity.this, "프로필이 업데이트되었습니다.", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(EditProfileActivity.this, "프로필이 업데이트되었으나, 일부 정보(Auth) 업데이트에 실패했습니다.", Toast.LENGTH_LONG).show();
                                        }
                                        setResult(Activity.RESULT_OK);
                                        finish();
                                    });
                        } else {
                            // Auth 정보 변경 없음
                            showLoading(false);
                            Toast.makeText(EditProfileActivity.this, "프로필이 업데이트되었습니다.", Toast.LENGTH_SHORT).show();
                            setResult(Activity.RESULT_OK);
                            finish();
                        }
                    } else {
                        // firebaseUser가 null인 경우 (이론상 발생하기 어려움)
                        showLoading(false);
                        Toast.makeText(EditProfileActivity.this, "프로필이 업데이트되었으나, 현재 사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_LONG).show();
                        setResult(Activity.RESULT_OK);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(EditProfileActivity.this, "프로필 업데이트 실패(Firestore): " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!isLoading);
        binding.btnChangePhoto.setEnabled(!isLoading);
        binding.etName.setEnabled(!isLoading);
        binding.etBio.setEnabled(!isLoading);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 