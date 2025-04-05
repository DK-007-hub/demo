package com.example.myapplication;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

public class DoctorHomeActivity extends AppCompatActivity {
    private static final String TAG = "DoctorHomeActivity";
    private static final String PREF_NAME = "DoctorLogin";
    
    private TextView tvDoctorName;
    private TextView tvDoctorDepartment;
    private CardView cardSchedule;
    private CardView cardMyPatients;
    private ImageButton btnLogout;
    
    private int currentDoctorId = -1;
    private String currentDoctorName = "";
    private String currentDoctorDepartment = "";
    private String currentDoctorPosition = "";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_home);
        
        // 检查登录状态
        checkLoginStatus();
        
        // 初始化视图
        initViews();
        
        // 设置医生信息
        displayDoctorInfo();
        
        // 设置点击事件
        setupClickListeners();
    }
    
    private void checkLoginStatus() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("logged_in", false);
        
        if (!isLoggedIn) {
            // 如果未登录，跳转到登录页面
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        
        // 获取当前登录的医生信息
        String doctorIdStr = prefs.getString("doctor_id", "-1");
        try {
            currentDoctorId = Integer.parseInt(doctorIdStr);
        } catch (NumberFormatException e) {
            currentDoctorId = -1;
        }
        currentDoctorName = prefs.getString("name", "");
        currentDoctorDepartment = prefs.getString("department", "");
        currentDoctorPosition = prefs.getString("position", "");
        
        Log.d(TAG, "当前登录医生: ID=" + currentDoctorId + 
                ", 姓名=" + currentDoctorName + 
                ", 科室=" + currentDoctorDepartment +
                ", 职位=" + currentDoctorPosition);
    }
    
    private void initViews() {
        // 设置标题栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        
        // 获取视图引用
        tvDoctorName = findViewById(R.id.tv_doctor_name);
        tvDoctorDepartment = findViewById(R.id.tv_doctor_department);
        cardSchedule = findViewById(R.id.card_schedule);
        cardMyPatients = findViewById(R.id.card_my_patients);
        btnLogout = findViewById(R.id.btn_logout);
    }
    
    private void displayDoctorInfo() {
        if (currentDoctorName.isEmpty()) {
            tvDoctorName.setText("未知医生");
        } else {
            tvDoctorName.setText(currentDoctorName + " " + currentDoctorPosition);
        }
        
        if (currentDoctorDepartment.isEmpty()) {
            tvDoctorDepartment.setText("未知科室");
        } else {
            tvDoctorDepartment.setText(currentDoctorDepartment);
        }
    }
    
    private void setupClickListeners() {
        // 排班表按钮点击事件
        cardSchedule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DoctorHomeActivity.this, ScheduleActivity.class);
                startActivity(intent);
            }
        });
        
        // 我的患者按钮点击事件
        cardMyPatients.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DoctorHomeActivity.this, MyPatientsActivity.class);
                startActivity(intent);
            }
        });
        
        // 登出按钮点击事件
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogoutConfirmationDialog();
            }
        });
    }
    
    private void showLogoutConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("登出确认")
               .setMessage("确定要退出登录吗？")
               .setPositiveButton("确定", (dialog, which) -> {
                   // 清除登录信息
                   SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                   SharedPreferences.Editor editor = prefs.edit();
                   editor.clear();
                   editor.apply();
                   
                   // 跳转到登录页面
                   Intent intent = new Intent(this, LoginActivity.class);
                   startActivity(intent);
                   finish();
               })
               .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
               .show();
    }
} 