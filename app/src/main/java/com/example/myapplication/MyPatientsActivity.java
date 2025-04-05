package com.example.myapplication;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DividerItemDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MyPatientsActivity extends AppCompatActivity {
    private static final String TAG = "MyPatientsActivity";
    private static final String PREF_NAME = "DoctorLogin";
    
    private RecyclerView recyclerView;
    private TextView tvEmptyList;
    private EditText etSearch;
    
    private MyPatientsAdapter adapter;
    private List<Patient> patientList;
    private List<Patient> filteredPatientList;
    
    private int currentDoctorId = -1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_patients);
        
        // 初始化列表
        patientList = new ArrayList<>();
        filteredPatientList = new ArrayList<>();
        
        // 加载医生信息
        loadDoctorInfo();
        
        // 初始化视图
        initViews();
        
        // 设置搜索功能
        setupSearch();
        
        // 加载患者数据
        loadPatientData();
    }
    
    private void loadDoctorInfo() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String doctorIdStr = prefs.getString("doctor_id", "-1");
        try {
            currentDoctorId = Integer.parseInt(doctorIdStr);
        } catch (NumberFormatException e) {
            currentDoctorId = -1;
        }
        
        if (currentDoctorId <= 0) {
            Toast.makeText(this, "获取医生信息失败，请重新登录", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initViews() {
        // 设置标题栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("我的患者");
        
        // 初始化RecyclerView
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 添加分割线
        DividerItemDecoration divider = new DividerItemDecoration(
                this, DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(divider);
        
        // 初始化空状态提示
        tvEmptyList = findViewById(R.id.tv_empty_list);
        
        // 初始化搜索框
        etSearch = findViewById(R.id.et_search);
    }
    
    private void loadPatientData() {
        // 显示加载状态
        tvEmptyList.setVisibility(View.GONE);
        
        // 创建一个新线程来执行数据库操作
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 使用MySQL连接
                try (Connection conn = DBHelper.getConnection()) {
                    // 查询与当前医生相关的患者
                    String sql = "SELECT DISTINCT p.patient_id, p.patient_name, p.gender, p.age, p.phone, " +
                            "p.id_card, p.address, p.medical_history, p.allergies " +
                            "FROM patients p " +
                            "INNER JOIN appointments a ON p.patient_id = a.patient_id " +
                            "WHERE a.doctor_id = ? ORDER BY p.patient_name";
                    
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, currentDoctorId);
                        
                        try (ResultSet rs = stmt.executeQuery()) {
                            patientList.clear();
                            
                            while (rs.next()) {
                                int id = rs.getInt("patient_id");
                                String name = rs.getString("patient_name");
                                String gender = rs.getString("gender");
                                int age = rs.getInt("age");
                                String phone = rs.getString("phone");
                                String idCard = rs.getString("id_card");
                                String address = rs.getString("address");
                                String medicalHistory = rs.getString("medical_history");
                                String allergies = rs.getString("allergies");
                                
                                Patient patient = new Patient(id, name, gender, age, phone, 
                                        idCard, address, medicalHistory, allergies);
                                patientList.add(patient);
                            }
                        }
                    }
                }
                
                // 更新UI
                runOnUiThread(() -> {
                    if (patientList.isEmpty()) {
                        tvEmptyList.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        tvEmptyList.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        filteredPatientList = new ArrayList<>(patientList);
                        adapter = new MyPatientsAdapter(filteredPatientList);
                        
                        // 设置患者点击事件
                        adapter.setOnPatientClickListener(patient -> {
                            showPatientDetails(patient);
                        });
                        
                        recyclerView.setAdapter(adapter);
                    }
                });
                
            } catch (SQLException e) {
                Log.e(TAG, "加载患者数据出错: " + e.getMessage(), e);
                
                // 显示错误信息
                runOnUiThread(() -> {
                    tvEmptyList.setText("加载数据失败: " + e.getMessage());
                    tvEmptyList.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
            }
        });
    }
    
    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要实现
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 不需要实现
            }
            
            @Override
            public void afterTextChanged(Editable s) {
                filterPatients(s.toString().trim());
            }
        });
    }
    
    private void filterPatients(String query) {
        if (patientList == null) return;
        
        if (query.isEmpty()) {
            // 如果查询为空，显示所有患者
            filteredPatientList.clear();
            filteredPatientList.addAll(patientList);
        } else {
            // 过滤匹配的患者
            filteredPatientList.clear();
            String lowerQuery = query.toLowerCase();
            
            for (Patient patient : patientList) {
                if (patient.getName().toLowerCase().contains(lowerQuery) ||
                    (patient.getIdCard() != null && patient.getIdCard().contains(lowerQuery)) ||
                    patient.getPhone().contains(lowerQuery)) {
                    filteredPatientList.add(patient);
                }
            }
        }
        
        // 更新适配器
        if (adapter != null) {
            adapter = new MyPatientsAdapter(filteredPatientList);
            
            // 设置患者点击事件
            adapter.setOnPatientClickListener(patient -> {
                showPatientDetails(patient);
            });
            
            recyclerView.setAdapter(adapter);
        }
        
        // 更新空状态提示
        if (filteredPatientList.isEmpty()) {
            tvEmptyList.setText("没有找到匹配的患者");
            tvEmptyList.setVisibility(View.VISIBLE);
        } else {
            tvEmptyList.setVisibility(View.GONE);
        }
    }
    
    // 显示患者详情
    private void showPatientDetails(MyPatientsActivity.Patient patient) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_patient_details, null);
        
        TextView tvPatientName = dialogView.findViewById(R.id.tv_patient_name);
        TextView tvPatientGender = dialogView.findViewById(R.id.tv_patient_gender);
        TextView tvPatientAge = dialogView.findViewById(R.id.tv_patient_age);
        TextView tvPatientPhone = dialogView.findViewById(R.id.tv_patient_phone);
        TextView tvPatientIdCard = dialogView.findViewById(R.id.tv_patient_id_card);
        TextView tvPatientAddress = dialogView.findViewById(R.id.tv_patient_address);
        TextView tvMedicalHistory = dialogView.findViewById(R.id.tv_medical_history);
        TextView tvAllergies = dialogView.findViewById(R.id.tv_allergies);
        
        // 设置基本信息
        tvPatientName.setText(patient.getName());
        tvPatientGender.setText(patient.getGender());
        tvPatientAge.setText(String.valueOf(patient.getAge()) + "岁");
        tvPatientPhone.setText(patient.getPhone());
        
        // 设置身份证信息
        if (patient.getIdCard() != null && !patient.getIdCard().isEmpty()) {
            tvPatientIdCard.setText(patient.getIdCard());
        } else {
            tvPatientIdCard.setText("暂无");
        }
        
        // 设置地址信息
        if (patient.getAddress() != null && !patient.getAddress().isEmpty()) {
            tvPatientAddress.setText(patient.getAddress());
        } else {
            tvPatientAddress.setText("暂无地址信息");
        }
        
        // 设置病史信息
        if (patient.getMedicalHistory() != null && !patient.getMedicalHistory().isEmpty()) {
            tvMedicalHistory.setText(patient.getMedicalHistory());
        } else {
            tvMedicalHistory.setText("无");
        }
        
        // 设置过敏史信息
        if (patient.getAllergies() != null && !patient.getAllergies().isEmpty()) {
            tvAllergies.setText(patient.getAllergies());
        } else {
            tvAllergies.setText("无");
        }
        
        // 设置对话框
        builder.setView(dialogView)
               .setPositiveButton("关闭", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    // 患者数据模型类
    public static class Patient {
        private int patientId;
        private String name;
        private String gender;
        private int age;
        private String phone;
        private String idCard;
        private String address;
        private String medicalHistory;
        private String allergies;
        
        public Patient(int patientId, String name, String gender, int age, String phone, String idCard) {
            this.patientId = patientId;
            this.name = name;
            this.gender = gender;
            this.age = age;
            this.phone = phone;
            this.idCard = idCard;
            this.address = null;
            this.medicalHistory = null;
            this.allergies = null;
        }
        
        // 添加包含所有字段的构造函数
        public Patient(int patientId, String name, String gender, int age, String phone, 
                      String idCard, String address, String medicalHistory, String allergies) {
            this.patientId = patientId;
            this.name = name;
            this.gender = gender;
            this.age = age;
            this.phone = phone;
            this.idCard = idCard;
            this.address = address;
            this.medicalHistory = medicalHistory;
            this.allergies = allergies;
        }
        
        public int getId() {
            return patientId;
        }
        
        public String getName() {
            return name;
        }
        
        public String getGender() {
            return gender;
        }
        
        public int getAge() {
            return age;
        }
        
        public String getPhone() {
            return phone;
        }
        
        public String getIdCard() {
            return idCard;
        }
        
        public String getAddress() {
            return address;
        }
        
        public void setAddress(String address) {
            this.address = address;
        }
        
        public String getMedicalHistory() {
            return medicalHistory;
        }
        
        public void setMedicalHistory(String medicalHistory) {
            this.medicalHistory = medicalHistory;
        }
        
        public String getAllergies() {
            return allergies;
        }
        
        public void setAllergies(String allergies) {
            this.allergies = allergies;
        }
    }
} 