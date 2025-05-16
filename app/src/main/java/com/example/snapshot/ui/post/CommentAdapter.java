package com.example.snapshot.ui.post;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.model.Comment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.Timestamp;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.widget.Toast; // For temporary testing
import androidx.core.content.ContextCompat; // For getting color

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private static final String TAG = "CommentAdapter";
    private final List<Comment> comments;
    private final Context context;
    private final OnCommentActionListener listener;

    // Regex pattern to find @mentions (adjust if username rules differ)
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]+)"); // Matches @ followed by letters, numbers, or underscore

    public CommentAdapter(List<Comment> comments, Context context, OnCommentActionListener listener) {
        this.comments = comments;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        Log.d(TAG, "Binding comment - ID: " + comment.getCommentId() +
                ", Depth: " + comment.getDepth() +
                ", ParentID: " + (comment.getParentId() == null ? "null" : comment.getParentId()) +
                ", Text: '" + comment.getText() +
                "', User: " + comment.getUserName());
        
        // Load user profile image
        if (comment.getUserProfileImageUrl() != null && !comment.getUserProfileImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(comment.getUserProfileImageUrl())
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .into(holder.userProfile);
        } else {
            holder.userProfile.setImageResource(R.drawable.default_profile);
        }
        
        holder.userName.setText(comment.getUserName());

        // --- Process comment text for mentions ---
        String commentText = comment.getText();
        if (commentText == null) {
            commentText = ""; // Avoid NullPointerException
        }
        SpannableString spannableString = new SpannableString(commentText);
        Matcher matcher = MENTION_PATTERN.matcher(commentText);

        while (matcher.find()) {
            final String mentionedUsername = matcher.group(1); // Get username without @
            if (mentionedUsername != null) {
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        Log.d(TAG, "Mention clicked: @" + mentionedUsername);
                        if (listener != null) {
                            // Ensure listener call is safe even if activity is destroyed
                            try {
                                listener.onMentionClicked(mentionedUsername); // Notify listener
                            } catch (Exception e) {
                                Log.e(TAG, "Error calling onMentionClicked listener", e);
                            }
                        }
                        // Temporary test: Show Toast message
                        // Toast.makeText(context, "Mention clicked: @" + mentionedUsername, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        try {
                            // Use ContextCompat for safer color retrieval
                            ds.setColor(ContextCompat.getColor(context, R.color.mention_color));
                        } catch (Exception e) {
                            // Fallback color if mention_color is not defined
                            ds.setColor(ContextCompat.getColor(context, R.color.purple_500)); // Example fallback
                            Log.w(TAG, "mention_color not found, using fallback.");
                        }
                        ds.setUnderlineText(false); // Remove underline (optional)
                    }
                };
                spannableString.setSpan(clickableSpan, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        holder.commentContent.setText(spannableString);
        holder.commentContent.setMovementMethod(LinkMovementMethod.getInstance()); // Make links clickable
        holder.commentContent.setHighlightColor(ContextCompat.getColor(context, android.R.color.transparent)); // Remove click highlight (optional)
        Log.d(TAG, "Set Texts on ViewHolder - User: " + holder.userName.getText() + ", Content: '" + holder.commentContent.getText() + "'");
        // --- End of mention processing ---
        
        if (comment.getTimestamp() != null) {
            holder.commentTime.setText(formatTimestamp(comment.getTimestamp()));
        } else {
            holder.commentTime.setText("방금 전");
        }
        
        // 답글 들여쓰기 처리
        if (comment.getDepth() > 0) {
            int indentWidth = comment.getDepth() * dpToPx(32);
            Log.d(TAG, "Applying indent for reply. Comment ID: " + comment.getCommentId() + ", Depth: " + comment.getDepth() + ", Calculated Indent: " + indentWidth + "px");
            holder.indentSpace.getLayoutParams().width = indentWidth;
            holder.indentSpace.setVisibility(View.VISIBLE);
        } else {
            Log.d(TAG, "No indent for top-level comment. Comment ID: " + comment.getCommentId());
            holder.indentSpace.getLayoutParams().width = 0;
            holder.indentSpace.setVisibility(View.GONE);
        }
        holder.indentSpace.requestLayout();
        
        holder.moreOptions.setVisibility(View.VISIBLE);
        holder.moreOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.moreOptions);
            popup.inflate(R.menu.comment_options_menu);

            MenuItem editItem = popup.getMenu().findItem(R.id.menu_comment_edit);
            MenuItem deleteItem = popup.getMenu().findItem(R.id.menu_comment_delete);

            String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

            if (comment.getUserId() != null && comment.getUserId().equals(currentUserId)) {
                editItem.setVisible(true);
                deleteItem.setVisible(true);
            } else {
                editItem.setVisible(false);
                deleteItem.setVisible(false);
            }

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_comment_reply) {
                    listener.onReplyComment(holder.getAdapterPosition());
                    return true;
                } else if (itemId == R.id.menu_comment_edit) {
                    listener.onEditComment(holder.getAdapterPosition());
                    return true;
                } else if (itemId == R.id.menu_comment_delete) {
                    listener.onDeleteComment(holder.getAdapterPosition());
                    return true;
                }
                return false;
            });
            popup.show();
        });
        
        holder.userProfile.setOnClickListener(v -> listener.onUserProfileClicked(holder.getAdapterPosition()));
        holder.userName.setOnClickListener(v -> listener.onUserProfileClicked(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        CircleImageView userProfile;
        TextView userName, commentContent, commentTime;
        ImageView moreOptions;
        View indentSpace;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            userProfile = itemView.findViewById(R.id.iv_comment_user_profile);
            userName = itemView.findViewById(R.id.tv_comment_user_name);
            commentContent = itemView.findViewById(R.id.tv_comment_text);
            commentTime = itemView.findViewById(R.id.tv_comment_timestamp);
            moreOptions = itemView.findViewById(R.id.iv_comment_more_options);
            indentSpace = itemView.findViewById(R.id.view_comment_indent);
        }
    }

    private String formatTimestamp(Timestamp timestamp) {
        long timestampMillis = timestamp.toDate().getTime();
        long currentTimeMillis = System.currentTimeMillis();
        long timeDifference = currentTimeMillis - timestampMillis;

        if (timeDifference < DateUtils.MINUTE_IN_MILLIS) {
            return "방금 전";
        } else if (timeDifference < DateUtils.HOUR_IN_MILLIS) {
            long minutes = timeDifference / DateUtils.MINUTE_IN_MILLIS;
            return minutes + "분 전";
        } else if (timeDifference < DateUtils.DAY_IN_MILLIS) {
            long hours = timeDifference / DateUtils.HOUR_IN_MILLIS;
            return hours + "시간 전";
        } else if (timeDifference < DateUtils.WEEK_IN_MILLIS) {
            long days = timeDifference / DateUtils.DAY_IN_MILLIS;
            return days + "일 전";
        } else if (timeDifference < (DateUtils.WEEK_IN_MILLIS * 4)) {
            long weeks = timeDifference / DateUtils.WEEK_IN_MILLIS;
            return weeks + "주 전";
        } else if (timeDifference < DateUtils.YEAR_IN_MILLIS) {
            long monthsApproximation = timeDifference / (DateUtils.WEEK_IN_MILLIS * 4);
            return monthsApproximation + "개월 전";
        } else {
            long years = timeDifference / DateUtils.YEAR_IN_MILLIS;
            return years + "년 전";
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    public interface OnCommentActionListener {
        void onReplyComment(int position);
        void onEditComment(int position);
        void onDeleteComment(int position);
        void onUserProfileClicked(int position);
        void onMentionClicked(String username);
    }
} 