package com.example.snapshot.ui.search;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;

import com.google.android.gms.maps.MapView;

public class TouchableMapView extends MapView {

    public TouchableMapView(Context context) {
        super(context);
    }

    public TouchableMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchableMapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 터치 이벤트가 발생하면 부모 뷰(ViewPager 등)가 이벤트를 가로채지 못하도록 요청합니다.
        // 이렇게 하면 사용자가 지도를 드래그할 때 탭이 전환되지 않습니다.
        ViewParent parent = getParent();
        if (parent != null) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // 지도를 누르거나 움직일 때는 부모가 가로채지 못하게 합니다.
                    parent.requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 손가락을 떼거나 터치가 취소되면 부모가 다시 가로챌 수 있게 합니다.
                    parent.requestDisallowInterceptTouchEvent(false);
                    break;
            }
        }
        // 원래 MapView의 터치 이벤트 처리 로직을 실행합니다.
        return super.dispatchTouchEvent(ev);
    }
} 