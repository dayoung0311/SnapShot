package com.example.snapshot.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snapshot.R;
import com.example.snapshot.databinding.ItemTagBinding;
import com.example.snapshot.model.Tag;
import com.google.android.material.chip.Chip;

import java.util.List;

public class TagAdapter extends RecyclerView.Adapter<TagAdapter.TagViewHolder> {
    
    private final List<Tag> tags;
    private final Context context;
    private OnTagClickListener listener;
    private OnTagLongClickListener longClickListener;
    private boolean isViewMode = true; // 포스트 조회 모드(true) 또는 편집 모드(false)
    
    public TagAdapter(Context context, List<Tag> tags) {
        this.context = context;
        this.tags = tags;
        this.isViewMode = true; // 기본값은 조회 모드
    }
    
    public TagAdapter(Context context, List<Tag> tags, boolean isViewMode) {
        this.context = context;
        this.tags = tags;
        this.isViewMode = isViewMode;
    }
    
    public void setViewMode(boolean isViewMode) {
        this.isViewMode = isViewMode;
    }
    
    public void setOnTagClickListener(OnTagClickListener listener) {
        this.listener = listener;
    }
    
    public void setOnTagLongClickListener(OnTagLongClickListener listener) {
        this.longClickListener = listener;
    }
    
    @NonNull
    @Override
    public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Chip chip = (Chip) LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tag, parent, false);
        return new TagViewHolder(chip);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
        Tag tag = tags.get(position);
        holder.bind(tag, position);
    }
    
    @Override
    public int getItemCount() {
        return tags.size();
    }
    
    public interface OnTagClickListener {
        void onTagClicked(int position);
    }
    
    public interface OnTagLongClickListener {
        void onTagEdit(int position);
        void onTagDelete(int position);
        void onTagSave(int position);
    }
    
    class TagViewHolder extends RecyclerView.ViewHolder {
        
        private final Chip chip;
        
        public TagViewHolder(@NonNull Chip chip) {
            super(chip);
            this.chip = chip;
        }
        
        public void bind(Tag tag, int position) {
            // 태그 유형별 아이콘 및 텍스트 설정
            switch (tag.getTagType()) {
                case Tag.TYPE_LOCATION:
                    chip.setChipIconResource(R.drawable.ic_location);
                    chip.setText(tag.getName());
                    break;
                case Tag.TYPE_PRODUCT:
                    chip.setChipIconResource(R.drawable.ic_product);
                    chip.setText(tag.getName());
                    break;
                case Tag.TYPE_BRAND:
                    chip.setChipIconResource(R.drawable.ic_brand);
                    chip.setText(tag.getName());
                    break;
                case Tag.TYPE_PRICE:
                    chip.setChipIconResource(R.drawable.ic_price);
                    chip.setText(tag.getName());
                    break;
                case Tag.TYPE_EVENT:
                    chip.setChipIconResource(R.drawable.ic_event);
                    chip.setText(tag.getName());
                    break;
                default:
                    chip.setChipIconResource(0);
                    chip.setText(tag.getName());
                    break;
            }
            
            // 클릭 리스너 설정
            chip.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTagClicked(position);
                }
            });
            
            // 롱클릭 리스너 설정
            chip.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    // 현재 isViewMode 상태 로깅
                    android.util.Log.d("TagAdapter", "롱 클릭 발생 - isViewMode: " + isViewMode + 
                            ", 태그 ID: " + tag.getTagId() + ", 태그 이름: " + tag.getName());
                    showPopupMenu(v, position);
                    return true;
                }
                return false;
            });
        }
        
        private void showPopupMenu(android.view.View view, int position) {
            PopupMenu popupMenu = new PopupMenu(context, view);
            
            // 조회 모드와 편집 모드에 따라 다른 메뉴 표시
            if (isViewMode) {
                // 조회 모드에서는 저장 메뉴만 표시
                android.util.Log.d("TagAdapter", "조회 모드 팝업 메뉴 표시 - 태그 저장 메뉴");
                popupMenu.inflate(R.menu.menu_tag_save);
                
                popupMenu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.menu_save_tag) {
                        if (longClickListener != null) {
                            android.util.Log.d("TagAdapter", "태그 저장 메뉴 클릭");
                            longClickListener.onTagSave(position);
                            return true;
                        }
                    }
                    return false;
                });
            } else {
                // 편집 모드에서는 수정/삭제 메뉴 표시
                android.util.Log.d("TagAdapter", "편집 모드 팝업 메뉴 표시 - 태그 편집/삭제 메뉴");
                popupMenu.inflate(R.menu.menu_tag_edit);
                
                popupMenu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.menu_edit_tag) {
                        if (longClickListener != null) {
                            android.util.Log.d("TagAdapter", "태그 편집 메뉴 클릭");
                            longClickListener.onTagEdit(position);
                            return true;
                        }
                    } else if (itemId == R.id.menu_delete_tag) {
                        if (longClickListener != null) {
                            android.util.Log.d("TagAdapter", "태그 삭제 메뉴 클릭");
                            longClickListener.onTagDelete(position);
                            return true;
                        }
                    }
                    return false;
                });
            }
            
            popupMenu.show();
        }
    }
} 