package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.myapplication.model.Doctor;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final String PREF_NAME = "DoctorLogin";
    
    private TextInputEditText etDoctorId;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private ProgressBar loginProgress;
    private TextView tvLoginStatus;
    
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        // 初始化视图
        etDoctorId = findViewById(R.id.et_doctor_id);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        loginProgress = findViewById(R.id.login_progress);
        tvLoginStatus = findViewById(R.id.tv_login_status);
        
        // 检查是否已登录
        if (isLoggedIn()) {
            navigateToHome();
            return;
        }
        
        // 设置登录按钮点击事件
        btnLogin.setOnClickListener(v -> {
            String doctorId = etDoctorId.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            
            if (doctorId.isEmpty()) {
                showError("请输入医生ID");
                return;
            }
            
            if (password.isEmpty()) {
                showError("请输入密码");
                return;
            }
            
            // 开始登录过程
            login(doctorId, password);
        });
    }
    
    private void login(String doctorId, String password) {
        showLoading(true);
        hideError();
        
        executor.execute(() -> {
            try {
                // 使用DBHelper验证医生身份
                Doctor doctor = DBHelper.verifyDoctorLogin(doctorId, password);
                
                if (doctor != null) {
                    // 登录成功
                    // 保存医生信息
                    saveLoginInfo(
                        String.valueOf(doctor.getDoctorId()), 
                        doctor.getName(), 
                        doctor.getDepartment(), 
                        doctor.getPosition()
                    );
                    
                    // 导航到ScheduleActivity
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this, 
                                "欢迎，" + doctor.getName() + " " + doctor.getPosition(), 
                                Toast.LENGTH_SHORT).show();
                        navigateToHome();
                    });
                } else {
                    // 登录失败
                    runOnUiThread(() -> {
                        showLoading(false);
                        showError("医生ID或密码错误");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "登录失败: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    showLoading(false);
                    showError("登录失败: " + e.getMessage());
                });
            }
        });
    }
    
    private void saveLoginInfo(String doctorId, String name, String department, String position) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString("doctor_id", doctorId);
        editor.putString("name", name);
        editor.putString("department", department);
        editor.putString("position", position);
        editor.putBoolean("logged_in", true);
        
        editor.apply();
        
        Log.d(TAG, "已保存医生信息: ID=" + doctorId + ", 姓名=" + name);
    }
    
    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("logged_in", false);
        
        if (isLoggedIn) {
            String doctorId = prefs.getString("doctor_id", "");
            String name = prefs.getString("name", "");
            Log.d(TAG, "发现已登录用户: ID=" + doctorId + ", 姓名=" + name);
        }
        
        return isLoggedIn;
    }
    
    private void navigateToHome() {
        Intent intent = new Intent(this, DoctorHomeActivity.class);
        startActivity(intent);
        finish(); // 结束登录活动，防止用户按返回键回到登录页面
    }
    
    private void showLoading(boolean show) {
        loginProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
    }
    
    private void showError(String message) {
        tvLoginStatus.setText(message);
        tvLoginStatus.setVisibility(View.VISIBLE);
    }
    
    private void hideError() {
        tvLoginStatus.setVisibility(View.GONE);
    }
} 