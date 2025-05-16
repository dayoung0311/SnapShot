package com.example.snapshot.ui.test;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.snapshot.R;
import com.example.snapshot.model.Tag;
import com.example.snapshot.repository.TagRepository;
import com.example.snapshot.repository.UserRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

public class TagSaveTestActivity extends AppCompatActivity {

    private EditText etTagId;
    private Button btnSaveTag;
    private Button btnCheckTag;
    private Button btnUnsaveTag;
    private TextView tvResult;
    
    private TagRepository tagRepository;
    private UserRepository userRepository;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_save_test);
        
        // 뷰 초기화
        etTagId = findViewById(R.id.et_tag_id);
        btnSaveTag = findViewById(R.id.btn_save_tag);
        btnCheckTag = findViewById(R.id.btn_check_tag);
        btnUnsaveTag = findViewById(R.id.btn_unsave_tag);
        tvResult = findViewById(R.id.tv_result);
        
        // 저장소 초기화
        tagRepository = TagRepository.getInstance();
        userRepository = UserRepository.getInstance();
        
        // 현재 로그인된 사용자 확인
        FirebaseUser user = userRepository.getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            tvResult.setText("현재 사용자 ID: " + currentUserId);
        } else {
            tvResult.setText("로그인 필요");
            disableButtons();
        }
        
        // 버튼 리스너 설정
        btnSaveTag.setOnClickListener(v -> saveTag());
        btnCheckTag.setOnClickListener(v -> checkTag());
        btnUnsaveTag.setOnClickListener(v -> unsaveTag());
    }
    
    private void saveTag() {
        String tagId = etTagId.getText().toString().trim();
        if (tagId.isEmpty()) {
            Toast.makeText(this, "태그 ID를 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvResult.setText("태그 저장 중...");
        
        // 태그 존재 여부 먼저 확인
        tagRepository.getTagById(tagId)
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Tag tag = documentSnapshot.toObject(Tag.class);
                    tvResult.append("\n태그 정보: " + tag.getName() + " (" + tag.getTagType() + ")");
                    
                    // 태그 저장
                    tagRepository.saveTagForUser(currentUserId, tagId)
                        .addOnSuccessListener(aVoid -> {
                            tvResult.append("\n태그 저장 성공!");
                            tagRepository.incrementTagUseCount(tagId);
                        })
                        .addOnFailureListener(e -> {
                            tvResult.append("\n태그 저장 실패: " + e.getMessage());
                        });
                } else {
                    tvResult.append("\n존재하지 않는 태그 ID입니다.");
                }
            })
            .addOnFailureListener(e -> {
                tvResult.append("\n태그 조회 실패: " + e.getMessage());
            });
    }
    
    private void checkTag() {
        String tagId = etTagId.getText().toString().trim();
        if (tagId.isEmpty()) {
            Toast.makeText(this, "태그 ID를 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvResult.setText("태그 저장 상태 확인 중...");
        
        tagRepository.isTagSavedByUser(currentUserId, tagId)
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    tvResult.append("\n이 태그는 저장되어 있습니다.");
                    // 태그 세부 정보 로드
                    tagRepository.getTagById(tagId)
                        .addOnSuccessListener(tagSnapshot -> {
                            if (tagSnapshot.exists()) {
                                Tag tag = tagSnapshot.toObject(Tag.class);
                                tvResult.append("\n태그 정보: " + tag.getName() + " (" + tag.getTagType() + ")");
                            }
                        });
                } else {
                    tvResult.append("\n이 태그는 저장되어 있지 않습니다.");
                }
            })
            .addOnFailureListener(e -> {
                tvResult.append("\n태그 저장 상태 확인 실패: " + e.getMessage());
            });
    }
    
    private void unsaveTag() {
        String tagId = etTagId.getText().toString().trim();
        if (tagId.isEmpty()) {
            Toast.makeText(this, "태그 ID를 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvResult.setText("태그 저장 취소 중...");
        
        tagRepository.unsaveTagForUser(currentUserId, tagId)
            .addOnSuccessListener(aVoid -> {
                tvResult.append("\n태그 저장 취소 성공!");
            })
            .addOnFailureListener(e -> {
                tvResult.append("\n태그 저장 취소 실패: " + e.getMessage());
            });
    }
    
    private void disableButtons() {
        btnSaveTag.setEnabled(false);
        btnCheckTag.setEnabled(false);
        btnUnsaveTag.setEnabled(false);
    }
} 