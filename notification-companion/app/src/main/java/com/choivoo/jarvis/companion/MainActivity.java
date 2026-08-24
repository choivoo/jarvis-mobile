package com.choivoo.jarvis.companion;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 96, 48, 48);
        root.setBackgroundColor(0xFF090B10);

        TextView title = new TextView(this);
        title.setText("JARVIS\nNotification Companion");
        title.setTextColor(0xFFF5F7FB);
        title.setTextSize(26f);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("이 선택형 Companion은 Android 알림 접근 권한만 담당합니다.\n\n허용하면 최근 알림의 앱 이름, 제목, 본문을 JARVIS 본체로 전달합니다. 비밀번호, 인증코드, 결제정보 같은 민감 데이터는 JARVIS 메모리로 저장하지 마세요.\n\n메인 JARVIS 앱과 같은 서명으로 빌드된 Companion만 브리지에 접근할 수 있습니다.");
        info.setTextColor(0xFFB8C1D1);
        info.setTextSize(16f);
        info.setPadding(0, 56, 0, 40);
        root.addView(info);

        Button access = new Button(this);
        access.setText("알림 접근 설정 열기");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(access);

        TextView note = new TextView(this);
        note.setText("참고: 브라우저에서 직접 설치한 Notification Listener APK는 Google Play Protect 정책에 따라 설치가 제한될 수 있습니다. 본체 JARVIS는 이 권한을 포함하지 않습니다.");
        note.setTextColor(0xFF8C97AA);
        note.setTextSize(13f);
        note.setPadding(0, 40, 0, 0);
        root.addView(note);

        setContentView(root);
    }
}
