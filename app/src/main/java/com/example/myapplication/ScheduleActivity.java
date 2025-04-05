package com.example.myapplication;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.DatePickerDialog;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.util.Log;
import android.widget.ImageButton;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import androidx.appcompat.widget.Toolbar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;
import java.sql.DatabaseMetaData;

import com.example.myapplication.model.Schedule;
import com.example.myapplication.model.Patient;
import com.google.android.material.button.MaterialButton;


// 排班表界面
public class ScheduleActivity extends AppCompatActivity {
    private RecyclerView rvSchedules;
    private TextView tvCurrentDate;
    private Spinner spinnerDepartment;
    private ProgressBar progressBar;
    private LocalDate currentDate;
    private ScheduleAdapter adapter;
    private ExecutorService executor = Executors.newFixedThreadPool(4);
    private EditText etDoctorSearch;
    private Button btnSearch;
    private Button btnClearSearch;
    private String searchQuery = null;
    private Toolbar toolbar;
    private ImageButton btnBack;
    private Button btnPrevDay;
    private Button btnNextDay;
    private List<String> departmentIds;
    private List<Schedule> schedules = new ArrayList<>();
    private View layoutEmptyState;
    private TextView tvScheduleCount;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final String PREF_NAME = "DoctorLogin";
    private int currentDoctorId = -1;
    private String currentDoctorName = "";
    private String currentDoctorDepartment = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        // 检查登录状态，获取当前医生信息
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("logged_in", false);

        if (!isLoggedIn) {
            // 如果未登录，跳转到登录页面
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
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

        Log.d("ScheduleActivity", "当前登录医生: ID=" + currentDoctorId +
                ", 姓名=" + currentDoctorName +
                ", 科室=" + currentDoctorDepartment);

        // 初始化当前日期
        currentDate = LocalDate.now();
        schedules = new ArrayList<>();

        // 测试数据库连接和医生数据
        testDatabaseConnection();

        // 检查医生表结构
        checkDoctorTableStructure(this);

        initViews();
        setupDateSelector();
        setupRecyclerView();
        loadDepartments();
        loadSchedules(currentDate);
    }

    // 添加测试数据库连接的方法
    private void testDatabaseConnection() {
        new Thread(() -> {
            boolean isConnected = DBHelper.testConnection();

            // 测试doctors表是否有数据
            boolean hasDoctorData = false;
            try (Connection conn = DBHelper.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM doctors");
                         ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            hasDoctorData = count > 0;
                            Log.d("ScheduleActivity", "医生表记录数量: " + count);
                        }
                    } catch (SQLException e) {
                        Log.e("ScheduleActivity", "查询医生表失败: " + e.getMessage(), e);
                    }

                    // 获取一个医生记录作为示例
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM doctors LIMIT 1");
                         ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String doctorName = rs.getString("name");
                            String department = rs.getString("department");
                            String position = rs.getString("position");
                            Log.d("ScheduleActivity", "医生数据示例: 姓名=" + doctorName +
                                    ", 科室=" + department +
                                    ", 职称=" + position);
                        }
                    } catch (SQLException e) {
                        Log.e("ScheduleActivity", "查询示例医生失败: " + e.getMessage(), e);
                    }
                }
            } catch (SQLException e) {
                Log.e("ScheduleActivity", "测试医生数据时连接数据库失败: " + e.getMessage(), e);
            }

            final boolean finalHasDoctorData = hasDoctorData;
            runOnUiThread(() -> {
                if (isConnected) {
                    Log.d("ScheduleActivity", "数据库连接测试成功");
                    if (finalHasDoctorData) {
                        Toast.makeText(this, "数据库连接成功，医生数据正常", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "数据库连接成功，但医生表没有数据", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "数据库连接失败，请检查网络或服务器状态", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // 检查医生表结构
    private void checkDoctorTableStructure(Context context) {
        executor.execute(() -> {
            try (Connection conn = DBHelper.getConnection()) {
                if (conn != null) {
                    // 获取医生表的元数据
                    DatabaseMetaData dbMetaData = conn.getMetaData();
                    try (ResultSet columns = dbMetaData.getColumns(null, null, "doctors", null)) {
                        List<String> columnNames = new ArrayList<>();
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            columnNames.add(columnName);
                        }

                        Log.d("ScheduleActivity", "医生表结构: " + String.join(", ", columnNames));

                        // 检查必要的列是否存在
                        boolean hasName = columnNames.contains("name");
                        boolean hasPosition = columnNames.contains("position");
                        boolean hasPhone = columnNames.contains("phone");
                        boolean hasEmail = columnNames.contains("email");
                        boolean hasBio = columnNames.contains("bio");
                        boolean hasSpecialty = columnNames.contains("specialty");

                        Log.d("ScheduleActivity", "必要列检查: name=" + hasName +
                                ", position=" + hasPosition +
                                ", phone=" + hasPhone +
                                ", email=" + hasEmail +
                                ", bio=" + hasBio +
                                ", specialty=" + hasSpecialty);
                    }
                }
            } catch (SQLException e) {
                Log.e("ScheduleActivity", "检查医生表结构失败: " + e.getMessage(), e);
            }
        });
    }

    //初始化
    private void initViews() {
        // 设置标题栏
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        // 设置工具栏标题显示当前医生信息
        TextView tvToolbarTitle = findViewById(R.id.tv_toolbar_title);
        if (tvToolbarTitle != null && !currentDoctorName.isEmpty()) {
            tvToolbarTitle.setText(currentDoctorName + " 医生");
        }

        // 设置返回按钮
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            onBackPressed();
        });

        // 初始化日期选择器
        tvCurrentDate = findViewById(R.id.tv_current_date);
        btnPrevDay = findViewById(R.id.btn_prev_day);
        btnNextDay = findViewById(R.id.btn_next_day);

        btnPrevDay.setOnClickListener(v -> {
            currentDate = currentDate.minusDays(1);
            updateDateDisplay();
            loadSchedules(currentDate);
        });

        btnNextDay.setOnClickListener(v -> {
            currentDate = currentDate.plusDays(1);
            updateDateDisplay();
            loadSchedules(currentDate);
        });

        tvCurrentDate.setOnClickListener(v -> showDatePickerDialog());

        // 初始化科室下拉选择器
        spinnerDepartment = findViewById(R.id.spinner_department);

        spinnerDepartment.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(getResources().getColor(R.color.colorPrimary));
                    ((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                }
                // 增加防御性代码，避免越界异常
                if (departmentIds != null && position < departmentIds.size()) {
                    String departmentId = position > 0 ? departmentIds.get(position) : null;
                    Log.d("ScheduleActivity", "选择科室: 位置=" + position + ", ID=" + departmentId);
                    loadSchedules(currentDate);
                } else {
                    Log.e("ScheduleActivity", "科室选择错误: position=" + position +
                            ", departmentIds大小=" + (departmentIds != null ? departmentIds.size() : "null"));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做任何事情
            }
        });

        // 初始化搜索功能
        etDoctorSearch = findViewById(R.id.et_doctor_search);
        btnSearch = findViewById(R.id.btn_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);

        btnSearch.setOnClickListener(v -> {
            searchQuery = etDoctorSearch.getText().toString().trim();
            loadSchedules(currentDate);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(etDoctorSearch.getWindowToken(), 0);
        });

        btnClearSearch.setOnClickListener(v -> {
            etDoctorSearch.setText("");
            searchQuery = null;
            loadSchedules(currentDate);
        });

        etDoctorSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                btnSearch.performClick();
                return true;
            }
            return false;
        });

        // 初始化RecyclerView
        rvSchedules = findViewById(R.id.rv_schedules);
        rvSchedules.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScheduleAdapter();
        rvSchedules.setAdapter(adapter);

        progressBar = findViewById(R.id.progress_bar);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        tvScheduleCount = findViewById(R.id.tv_schedule_count);
    }

    //日期加一减一实现
    private void setupDateSelector() {
        updateDateDisplay();
    }

    // 添加日期选择器对话框方法
    private void showDatePickerDialog() {
        // 从当前选择的日期中获取年、月、日信息
        int year = currentDate.getYear();
        int month = currentDate.getMonthValue() - 1; // 月份从0开始计算
        int day = currentDate.getDayOfMonth();

        // 创建日期选择器对话框
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // 用户选择日期后的回调
                    currentDate = LocalDate.of(selectedYear, selectedMonth + 1, selectedDay);
                    updateDateDisplay();
                    loadSchedules(currentDate);
                },
                year, month, day);

        // 设置日期选择器的标题
        datePickerDialog.setTitle("选择日期");

        // 显示日期选择器
        datePickerDialog.show();
    }

    private void updateDateDisplay() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 E", Locale.CHINESE);
        String formattedDate = currentDate.format(formatter);
        tvCurrentDate.setText(formattedDate);
    }

    private void setupRecyclerView() {
        rvSchedules.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScheduleAdapter();
        rvSchedules.setAdapter(adapter);
    }

    private void loadDepartments() {
        Log.d("ScheduleActivity", "开始加载科室信息");

        // 先初始化默认科室数据，确保即使数据库查询失败也有数据可用
        List<String> departmentNames = new ArrayList<>();
        List<String> departmentIds = new ArrayList<>();

        // 添加"全部科室"选项作为第一个选项
        departmentNames.add("全部科室");
        departmentIds.add(null);

        // 添加默认科室数据
        departmentNames.add("内科");
        departmentNames.add("外科");
        departmentNames.add("儿科");
        departmentNames.add("妇产科");
        departmentIds.add("内科");
        departmentIds.add("外科");
        departmentIds.add("儿科");
        departmentIds.add("妇产科");

        // 直接设置科室数据到成员变量，确保不为空
        this.departmentIds = new ArrayList<>(departmentIds);

        // 设置适配器
        try {
            setupDepartmentAdapter(departmentNames);
            Log.d("ScheduleActivity", "已设置默认科室数据");
        } catch (Exception e) {
            Log.e("ScheduleActivity", "设置默认科室适配器失败", e);
        }

        // 尝试从数据库加载真实科室数据
        new Thread(() -> {
            Connection conn = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;

            try {
                conn = DBHelper.getConnection();
                if (conn == null) {
                    Log.e("ScheduleActivity", "数据库连接失败");
                    return;
                }

                // 直接从doctors表中获取去重的department列表
                String sql = "SELECT DISTINCT department FROM doctors ORDER BY department";
                Log.d("ScheduleActivity", "科室查询SQL: " + sql);

                try {
                    pstmt = conn.prepareStatement(sql);
                    rs = pstmt.executeQuery();

                    // 清空之前的列表，重新添加"全部科室"
                    List<String> dbDepartmentNames = new ArrayList<>();
                    List<String> dbDepartmentIds = new ArrayList<>();
                    dbDepartmentNames.add("全部科室");
                    dbDepartmentIds.add(null);

                    boolean hasData = false;
                    while (rs.next()) {
                        hasData = true;
                        String department = rs.getString("department");
                        dbDepartmentNames.add(department);
                        dbDepartmentIds.add(department); // 使用科室名称作为ID
                        Log.d("ScheduleActivity", "加载科室: " + department);
                    }

                    if (hasData) {
                        // 更新UI
                        final List<String> finalNames = dbDepartmentNames;
                        final List<String> finalIds = dbDepartmentIds;
                        runOnUiThread(() -> {
                            try {
                                if (isDestroyed() || isFinishing()) return;

                                // 更新成员变量
                                ScheduleActivity.this.departmentIds = new ArrayList<>(finalIds);

                                // 更新适配器
                                setupDepartmentAdapter(finalNames);
                                Log.d("ScheduleActivity", "成功加载数据库科室数据: " + finalNames.size() + "个科室");
                            } catch (Exception e) {
                                Log.e("ScheduleActivity", "设置数据库科室适配器失败", e);
                            }
                        });
                    } else {
                        Log.w("ScheduleActivity", "数据库中没有找到科室信息，使用默认数据");
                    }
                } catch (SQLException e) {
                    Log.e("ScheduleActivity", "查询科室失败: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                Log.e("ScheduleActivity", "加载科室失败", e);
            } finally {
                // 确保资源关闭
                try {
                    if (rs != null) rs.close();
                    if (pstmt != null) pstmt.close();
                    if (conn != null) conn.close();
                } catch (SQLException e) {
                    Log.e("ScheduleActivity", "关闭数据库资源出错", e);
                }
            }
        }).start();
    }

    // 抽取设置适配器的逻辑为单独的方法
    private void setupDepartmentAdapter(List<String> departmentNames) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                departmentNames) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setPadding(20, 16, 20, 16);

                if (position == spinnerDepartment.getSelectedItemPosition()) {
                    textView.setBackgroundColor(getResources().getColor(R.color.backgroundSecondary));
                    textView.setTextColor(getResources().getColor(R.color.colorPrimary));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                } else {
                    textView.setBackgroundColor(getResources().getColor(R.color.backgroundCard));
                    textView.setTextColor(getResources().getColor(R.color.textPrimary));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                }
                return view;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(getResources().getColor(R.color.textPrimary));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                return view;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDepartment.setAdapter(adapter);
    }

    private void loadSchedules(LocalDate date) {
        showLoading(true);
        executor.execute(() -> {
            try (Connection conn = DBHelper.getConnection()) {
                List<Schedule> newSchedules = new ArrayList<>();

                try {
                    // 获取当前选择的科室ID
                    final int selectedDeptPos = spinnerDepartment.getSelectedItemPosition();
                    final String selectedDeptId = (departmentIds != null && selectedDeptPos > 0 &&
                            selectedDeptPos < departmentIds.size()) ?
                            departmentIds.get(selectedDeptPos) : null;

                    Log.d("ScheduleActivity", "加载排班: 日期=" + date.toString() +
                            ", 科室位置=" + selectedDeptPos + ", 科室ID=" + selectedDeptId);

                    // 根据是否选择了科室构建SQL语句
                    StringBuilder sqlBuilder = new StringBuilder();
                    sqlBuilder.append("SELECT s.*, d.doctor_id, d.name AS doctor_name, d.department, d.position AS doctor_title, ");
                    sqlBuilder.append("(SELECT COUNT(*) FROM appointments a WHERE a.schedule_id = s.schedule_id) AS current_patients ");
                    sqlBuilder.append("FROM schedules s ");
                    sqlBuilder.append("JOIN doctors d ON s.doctor_id = d.doctor_id ");
                    sqlBuilder.append("WHERE s.schedule_date = ? ");

                    // 添加科室筛选条件（如果有）
                    if (selectedDeptPos > 0 && departmentIds != null && departmentIds.size() > selectedDeptPos) {
                        // 使用department名称而非department_id进行筛选
                        sqlBuilder.append("AND d.department = ? ");
                    }

                    // 添加搜索条件（如果有）
                    if (searchQuery != null && !searchQuery.isEmpty()) {
                        sqlBuilder.append("AND d.name LIKE ? ");
                    }

                    sqlBuilder.append("ORDER BY d.department, d.name, s.start_time");

                    String sql = sqlBuilder.toString();
                    Log.d("ScheduleActivity", "排班SQL: " + sql);

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, date.toString());

                        int paramIndex = 2;

                        // 设置科室参数（如果有）
                        if (selectedDeptPos > 0 && departmentIds != null && departmentIds.size() > selectedDeptPos) {
                            // 使用科室名称而非科室ID，根据spinnerDepartment中选择的项
                            String departmentName = spinnerDepartment.getSelectedItem().toString();
                            stmt.setString(paramIndex++, departmentName);
                            Log.d("ScheduleActivity", "设置科室参数: " + departmentName);
                        }

                        // 设置搜索参数（如果有）
                        if (searchQuery != null && !searchQuery.isEmpty()) {
                            stmt.setString(paramIndex, "%" + searchQuery + "%");
                            Log.d("ScheduleActivity", "设置搜索参数: " + searchQuery);
                        }

                        try (ResultSet rs = stmt.executeQuery()) {
                            Log.d("ScheduleActivity", "SQL查询执行成功，开始处理结果集");
                            int count = 0;
                            while (rs.next()) {
                                count++;
                                Schedule schedule = new Schedule();
                                schedule.setScheduleId(rs.getInt("schedule_id"));
                                schedule.setDoctorId(rs.getInt("doctor_id"));
                                schedule.setScheduleDate(rs.getDate("schedule_date"));
                                schedule.setStartTime(rs.getTime("start_time"));
                                schedule.setEndTime(rs.getTime("end_time"));
                                schedule.setMaxPatients(rs.getInt("max_patients"));
                                schedule.setCurrentPatients(rs.getInt("current_patients"));
                                schedule.setStatus(rs.getString("status"));

                                // 获取医生信息
                                String doctorName = rs.getString("doctor_name");
                                String department = rs.getString("department");
                                String doctorTitle = rs.getString("doctor_title");

                                // 设置医生信息
                                schedule.setDoctorName(doctorName);
                                schedule.setDepartment(department);
                                schedule.setDoctorTitle(doctorTitle);

                                Log.d("ScheduleActivity", "处理排班记录 #" + count +
                                        ": 医生=" + doctorName +
                                        ", 科室=" + department +
                                        ", 职称=" + doctorTitle +
                                        ", 日期=" + schedule.getScheduleDate() +
                                        ", 时间=" + schedule.getStartTime() + "-" + schedule.getEndTime());

                                // 修改排班状态文本和视觉表现，使其适合医生端
                                String status = schedule.getStatus();
                                if (status != null) {
                                    if (date.isBefore(LocalDate.now())) {
                                        // 过去的日期
                                        schedule.setStatus("已过期");
                                    } else if (date.isEqual(LocalDate.now())) {
                                        // 今天的排班
                                        if ("available".equalsIgnoreCase(status)) {
                                            schedule.setStatus("今日出诊");
                                        } else if ("full".equalsIgnoreCase(status)) {
                                            schedule.setStatus("已约满");
                                        } else if ("cancelled".equalsIgnoreCase(status)) {
                                            schedule.setStatus("已取消");
                                        } else {
                                            schedule.setStatus("待出诊");
                                        }
                                    } else {
                                        // 未来的排班
                                        if ("available".equalsIgnoreCase(status)) {
                                            schedule.setStatus("可出诊");
                                        } else if ("full".equalsIgnoreCase(status)) {
                                            schedule.setStatus("约满");
                                        } else if ("cancelled".equalsIgnoreCase(status)) {
                                            schedule.setStatus("已取消");
                                        } else {
                                            schedule.setStatus("待确认");
                                        }
                                    }
                                }

                                newSchedules.add(schedule);
                            }
                            Log.d("ScheduleActivity", "共找到 " + count + " 条排班记录");
                            if (count == 0) {
                                Log.w("ScheduleActivity", "数据库查询未返回任何排班记录");
                            }
                        }
                    }
                } catch (SQLException e) {
                    Log.e("ScheduleActivity", "SQL查询出错: " + e.getMessage(), e);
                    // 尝试备用查询或生成测试数据
                    if (newSchedules.isEmpty()) {
                        // 生成一些测试数据以便UI可以显示
                        Log.w("ScheduleActivity", "数据库连接失败，使用备用测试数据");

                        String[] doctorNames = {"张三", "李四", "王五", "赵六", "钱七"};
                        String[] doctorTitles = {"主任医师", "副主任医师", "主治医师", "住院医师", "实习医师"};
                        String[] departments = {"内科", "外科", "儿科", "妇产科", "眼科"};

                        for (int i = 1; i <= 5; i++) {
                            Schedule schedule = new Schedule();
                            schedule.setScheduleId(i);
                            schedule.setDoctorId(i);
                            schedule.setScheduleDate(new java.sql.Date(System.currentTimeMillis()));
                            schedule.setStartTime(java.sql.Time.valueOf((8 + i % 3) + ":00:00"));
                            schedule.setEndTime(java.sql.Time.valueOf((12 + i % 3) + ":00:00"));
                            schedule.setMaxPatients(20);
                            schedule.setCurrentPatients(i * 3);
                            schedule.setStatus("active");

                            // 使用不同的医生信息
                            schedule.setDoctorName(doctorNames[i - 1]);
                            schedule.setDepartment(departments[i - 1]);
                            schedule.setDoctorTitle(doctorTitles[i - 1]);

                            Log.d("ScheduleActivity", "添加备用测试数据: 医生=" + doctorNames[i - 1] +
                                    ", 科室=" + departments[i - 1] +
                                    ", 职称=" + doctorTitles[i - 1]);

                            newSchedules.add(schedule);
                        }
                    }
                }

                // 使用临时变量保存searchQuery的值，确保runOnUiThread中的值是一致的
                final String finalSearchQuery = searchQuery;
                final List<Schedule> finalSchedules = newSchedules;

                runOnUiThread(() -> {
                    try {
                        showLoading(false);
                        if (isDestroyed() || isFinishing()) {
                            Log.w("ScheduleActivity", "Activity已销毁，不再更新UI");
                            return;
                        }

                        ScheduleActivity.this.schedules = finalSchedules;
                        adapter.setSchedules(finalSchedules);
                        updateEmptyState();

                        // 使用finalSearchQuery代替直接使用searchQuery
                        if (finalSearchQuery != null && !finalSearchQuery.isEmpty()) {
                            Toast.makeText(ScheduleActivity.this,
                                    "找到 " + finalSchedules.size() + " 条排班记录",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e("ScheduleActivity", "更新UI时出错", e);
                    }
                });
            } catch (SQLException e) {
                Log.e("ScheduleActivity", "加载排班失败: " + e.getMessage(), e);
                showError("排班加载失败: " + e.getMessage());

                // 生成一些测试数据以便UI可以显示
                List<Schedule> testSchedules = new ArrayList<>();
                Log.w("ScheduleActivity", "数据库连接失败，使用备用测试数据");

                String[] doctorNames = {"张三", "李四", "王五", "赵六", "钱七"};
                String[] doctorTitles = {"主任医师", "副主任医师", "主治医师", "住院医师", "实习医师"};
                String[] departments = {"内科", "外科", "儿科", "妇产科", "眼科"};

                for (int i = 1; i <= 5; i++) {
                    Schedule schedule = new Schedule();
                    schedule.setScheduleId(i);
                    schedule.setDoctorId(i);
                    schedule.setScheduleDate(new java.sql.Date(System.currentTimeMillis()));
                    schedule.setStartTime(java.sql.Time.valueOf((8 + i % 3) + ":00:00"));
                    schedule.setEndTime(java.sql.Time.valueOf((12 + i % 3) + ":00:00"));
                    schedule.setMaxPatients(20);
                    schedule.setCurrentPatients(i * 3);
                    schedule.setStatus("active");

                    // 使用不同的医生信息
                    schedule.setDoctorName(doctorNames[i - 1]);
                    schedule.setDepartment(departments[i - 1]);
                    schedule.setDoctorTitle(doctorTitles[i - 1]);

                    Log.d("ScheduleActivity", "添加备用测试数据: 医生=" + doctorNames[i - 1] +
                            ", 科室=" + departments[i - 1] +
                            ", 职称=" + doctorTitles[i - 1]);

                    testSchedules.add(schedule);
                }

                final List<Schedule> finalTestSchedules = testSchedules;
                runOnUiThread(() -> {
                    try {
                        showLoading(false);
                        if (isDestroyed() || isFinishing()) {
                            return;
                        }
                        ScheduleActivity.this.schedules = finalTestSchedules;
                        adapter.setSchedules(finalTestSchedules);
                        updateEmptyState();
                        Toast.makeText(ScheduleActivity.this, "使用测试数据", Toast.LENGTH_SHORT).show();
                    } catch (Exception ex) {
                        Log.e("ScheduleActivity", "更新UI时出错", ex);
                    }
                });
            }
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        rvSchedules.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            showLoading(false);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    // 患者选择对话框
    private void showPatientSelectionDialog(Schedule schedule) {
        // 实现患者选择逻辑
        Toast.makeText(this, "这里应该显示患者选择对话框，预约医生: " + schedule.getDoctorName(), Toast.LENGTH_SHORT).show();
    }

    // Schedule Adapter
    private class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {
        private List<Schedule> schedules = new ArrayList<>();

        public void setSchedules(List<Schedule> schedules) {
            this.schedules = schedules;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_schedule, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Schedule schedule = schedules.get(position);

            // 设置医生信息
            holder.tvDoctorName.setText(schedule.getDoctorName());
            holder.tvDoctorTitle.setText(schedule.getDoctorTitle());
            holder.tvDepartment.setText(schedule.getDepartment());

            // 设置时间信息
            String scheduleTime = "";
            if (schedule.getScheduleDate() != null) {
                scheduleTime += dateFormat.format(schedule.getScheduleDate()) + " ";
            }
            scheduleTime += schedule.getFormattedTimeSlot();
            holder.tvScheduleTime.setText(scheduleTime);

            // 设置剩余名额信息
            int maxPatients = schedule.getMaxPatients();
            int currentPatients = schedule.getCurrentPatients();
            holder.tvRemainingQuota.setText("患者数量: " + currentPatients + "/" + maxPatients);

            // 根据患者数量设置颜色
            if (currentPatients >= maxPatients) {
                // 如果已经约满，使用浅灰色背景
                holder.tvRemainingQuota.setBackgroundResource(R.drawable.bg_pill_outline);
                holder.tvRemainingQuota.setTextColor(getResources().getColor(R.color.statusFull));
            } else if (currentPatients > 0) {
                // 有患者但未约满，使用蓝色背景
                holder.tvRemainingQuota.setBackgroundResource(R.drawable.bg_pill_outline);
                holder.tvRemainingQuota.setTextColor(getResources().getColor(R.color.colorPrimary));
            } else {
                // 没有患者，使用默认样式
                holder.tvRemainingQuota.setBackgroundResource(R.drawable.bg_pill_outline);
                holder.tvRemainingQuota.setTextColor(getResources().getColor(R.color.textSecondary));
            }

            // 设置状态
            String status = schedule.getStatus();
            int statusColor = 0;

            // 根据状态设置颜色
            switch (status.toLowerCase()) {
                case "可出诊":
                case "今日出诊":
                    statusColor = getResources().getColor(R.color.statusAvailable);
                    break;
                case "已约满":
                case "约满":
                    statusColor = getResources().getColor(R.color.statusFull);
                    break;
                case "已取消":
                    statusColor = getResources().getColor(R.color.statusCancelled);
                    break;
                case "待出诊":
                case "待确认":
                    statusColor = getResources().getColor(R.color.statusWaiting);
                    break;
                case "已过期":
                    statusColor = getResources().getColor(R.color.status_expired);
                    break;
                default:
                    statusColor = getResources().getColor(R.color.statusAvailable);
                    break;
            }

            holder.tvScheduleStatus.setText(status);

            // 设置状态背景
            GradientDrawable statusBackground = (GradientDrawable) holder.tvScheduleStatus.getBackground();
            statusBackground.setColor(statusColor);

            // 设置整个项的点击事件
            holder.itemView.setOnClickListener(v -> {
                showDoctorDetails(ScheduleActivity.this,
                        schedule.getDoctorId(),
                        schedule.getDoctorName(),
                        schedule.getDepartment());
            });
        }

        @Override
        public int getItemCount() {
            return schedules.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDoctorName, tvDoctorTitle, tvDepartment, tvScheduleTime, tvRemainingQuota, tvScheduleStatus;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDoctorName = itemView.findViewById(R.id.tv_doctor_name);
                tvDoctorTitle = itemView.findViewById(R.id.tv_doctor_title);
                tvDepartment = itemView.findViewById(R.id.tv_department);
                tvScheduleTime = itemView.findViewById(R.id.tv_schedule_time);
                tvRemainingQuota = itemView.findViewById(R.id.tv_remaining_quota);
                tvScheduleStatus = itemView.findViewById(R.id.tv_schedule_status);
            }
        }
    }

    // 测试查看患者功能
    private void testPatientListDialog(Context context) {
        Log.d("ScheduleActivity", "测试患者列表对话框功能");

        // 创建测试患者数据
        List<Patient> testPatients = new ArrayList<>();

        Patient patient1 = new Patient();
        patient1.setPatientId(1);
        patient1.setPatientName("张三");
        patient1.setGender("男");
        patient1.setAge(35);
        patient1.setPhone("13800138000");
        patient1.setAppointmentStatus("已预约");
        testPatients.add(patient1);

        Patient patient2 = new Patient();
        patient2.setPatientId(2);
        patient2.setPatientName("李四");
        patient2.setGender("女");
        patient2.setAge(28);
        patient2.setPhone("13900139000");
        patient2.setAppointmentStatus("待就诊");
        testPatients.add(patient2);

        // 显示测试对话框
        try {
            showPatientsListDialog(context, testPatients, "测试医生");
            Toast.makeText(context, "测试对话框显示成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("ScheduleActivity", "测试患者列表对话框失败", e);
            Toast.makeText(context, "测试失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 修改showDoctorDetails方法，添加权限检查
    private void showDoctorDetails(Context context, int doctorId, String doctorName, String department) {
        // 检查是否有权限查看该医生的患者信息
        boolean canViewPatients = (doctorId == currentDoctorId);

        Log.d("ScheduleActivity", "查看医生详情: 目标医生ID=" + doctorId +
                ", 当前医生ID=" + currentDoctorId +
                ", 可查看患者=" + canViewPatients);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_doctor_details, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // 获取对话框中的视图
        TextView tvDoctorName = dialogView.findViewById(R.id.tv_doctor_name);
        TextView tvDepartment = dialogView.findViewById(R.id.tv_department);
        TextView tvDoctorTitle = dialogView.findViewById(R.id.tv_doctor_title);
        TextView tvPhone = dialogView.findViewById(R.id.tv_phone);
        TextView tvEmail = dialogView.findViewById(R.id.tv_email);
        TextView tvBio = dialogView.findViewById(R.id.tv_bio);
        ImageButton btnCloseDetails = dialogView.findViewById(R.id.btn_close);
        MaterialButton btnPatientList = dialogView.findViewById(R.id.btn_patient_list);
        MaterialButton btnUpdateSchedule = dialogView.findViewById(R.id.btn_update_schedule);

        // 先设置已知的基本信息，确保即使数据库查询失败也有内容显示
        tvDoctorName.setText(doctorName);
        tvDepartment.setText(department);
        tvDoctorTitle.setText("医师"); // 默认值
        tvPhone.setText("联系电话：暂无");
        tvEmail.setText("电子邮件：暂无");
        tvBio.setText("暂无医生简介");

        // 根据权限控制按钮显示
        btnPatientList.setEnabled(canViewPatients);
        btnPatientList.setAlpha(canViewPatients ? 1.0f : 0.5f);

        if (!canViewPatients) {
            btnPatientList.setOnClickListener(v -> {
                Toast.makeText(context, "您只能查看自己的患者信息", Toast.LENGTH_SHORT).show();
            });
        } else {
            // 原有的长按测试功能保留
            btnPatientList.setOnLongClickListener(v -> {
                testPatientListDialog(context);
                return true;
            });

            // 提前设置患者列表按钮的事件，避免数据库查询失败时无法点击
            btnPatientList.setOnClickListener(v -> {
                Log.d("ScheduleActivity", "查看患者按钮被点击");
                testPatientListDialog(context);
            });
        }

        // 设置调整排班按钮点击事件
        btnUpdateSchedule.setOnClickListener(v -> {
            Toast.makeText(context, "正在准备调整 " + doctorName + " 的排班", Toast.LENGTH_SHORT).show();
            showUpdateScheduleDialog(context, doctorId, doctorName);
        });

        // 获取当前排班的scheduleId
        int currentScheduleId = -1;
        for (Schedule schedule : schedules) {
            if (schedule.getDoctorId() == doctorId) {
                currentScheduleId = schedule.getScheduleId();
                break;
            }
        }

        final int finalScheduleId = currentScheduleId;
        Log.d("ScheduleActivity", "当前排班ID: " + finalScheduleId);

        // 查询并填充详细信息
        executor.execute(() -> {
            try {
                Log.d("ScheduleActivity", "开始查询医生ID=" + doctorId + "的详细信息");
                Connection conn = DBHelper.getConnection();
                if (conn == null) {
                    Log.e("ScheduleActivity", "数据库连接失败");
                    return;
                }

                String query = "SELECT * FROM doctors WHERE doctor_id = ?";
                PreparedStatement pstmt = conn.prepareStatement(query);
                pstmt.setInt(1, doctorId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    try {
                        String position = rs.getString("position");
                        String phone = rs.getString("phone");

                        // 安全获取可能不存在的列
                        String email = "";
                        try {
                            email = rs.getString("email");
                            if (email == null) email = "未设置邮箱";
                        } catch (SQLException ex) {
                            Log.w("ScheduleActivity", "医生表中不存在email列: " + ex.getMessage());
                            email = "未设置邮箱";
                        }

                        // 安全获取可能不存在的列
                        String specialty = "";
                        try {
                            specialty = rs.getString("specialty");
                            if (specialty == null) specialty = "暂无专长信息";
                        } catch (SQLException ex) {
                            Log.w("ScheduleActivity", "医生表中不存在specialty列: " + ex.getMessage());
                            specialty = "暂无专长信息";
                        }

                        // 安全获取可能不存在的列
                        String bio = "";
                        try {
                            bio = rs.getString("bio");
                            if (bio == null) bio = "暂无医生简介";
                        } catch (SQLException ex) {
                            Log.w("ScheduleActivity", "医生表中不存在bio列: " + ex.getMessage());
                            bio = "暂无医生简介";
                        }

                        // 使用最终的字段值
                        final String finalEmail = email;
                        final String finalBio = bio;

                        runOnUiThread(() -> {
                            tvDoctorTitle.setText(position);
                            tvPhone.setText("联系电话：" + phone);
                            tvEmail.setText("电子邮件：" + finalEmail);
                            tvBio.setText(finalBio);
                        });
                    } catch (SQLException ex) {
                        Log.e("ScheduleActivity", "获取医生详情时出错: " + ex.getMessage(), ex);
                        runOnUiThread(() -> {
                            Toast.makeText(context, "获取医生详情出错，使用部分信息显示", Toast.LENGTH_SHORT).show();
                            // 确保UI仍然显示一些基本信息
                            tvPhone.setText("联系电话：暂无");
                            tvEmail.setText("电子邮件：暂无");
                            tvBio.setText("暂无医生简介");
                        });
                    }
                }

                // 查询该医生的患者列表
                try {
                    Log.d("ScheduleActivity", "开始查询医生ID=" + doctorId + "的患者列表，排班ID=" + finalScheduleId);

                    // 检查患者表结构
                    boolean hasNameColumn = true;
                    boolean hasGenderColumn = true;
                    boolean hasPhoneColumn = true;

                    try {
                        DatabaseMetaData dbMetaData = conn.getMetaData();
                        ResultSet columns = dbMetaData.getColumns(null, null, "patients", null);
                        List<String> columnNames = new ArrayList<>();
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            columnNames.add(columnName);
                        }
                        columns.close();

                        Log.d("ScheduleActivity", "患者表结构: " + String.join(", ", columnNames));

                        hasNameColumn = columnNames.contains("name");
                        hasGenderColumn = columnNames.contains("gender");
                        hasPhoneColumn = columnNames.contains("phone");

                        if (!hasNameColumn || !hasGenderColumn || !hasPhoneColumn) {
                            Log.w("ScheduleActivity", "患者表缺少必要列: name=" + hasNameColumn +
                                    ", gender=" + hasGenderColumn +
                                    ", phone=" + hasPhoneColumn);
                        }
                    } catch (SQLException e) {
                        Log.e("ScheduleActivity", "检查患者表结构失败: " + e.getMessage(), e);
                    }

                    // 修改查询，只查特定排班的患者
                    String patientQuery;
                    if (finalScheduleId > 0) {
                        // 如果有特定排班ID，则只查询该排班的患者
                        patientQuery = "SELECT p.* FROM patients p " +
                                "JOIN appointments a ON p.patient_id = a.patient_id " +
                                "WHERE a.schedule_id = ? " +
                                "GROUP BY p.patient_id";
                        pstmt = conn.prepareStatement(patientQuery);
                        pstmt.setInt(1, finalScheduleId);
                    } else {
                        // 如果没有特定排班ID，则查询该医生的所有患者
                        patientQuery = "SELECT p.* FROM patients p " +
                                "JOIN appointments a ON p.patient_id = a.patient_id " +
                                "WHERE a.doctor_id = ? " +
                                "GROUP BY p.patient_id";
                        pstmt = conn.prepareStatement(patientQuery);
                        pstmt.setInt(1, doctorId);
                    }
                    Log.d("ScheduleActivity", "患者查询SQL: " + patientQuery);

                    ResultSet patientRs = pstmt.executeQuery();

                    final List<Patient>[] patientsList = new List[1];
                    patientsList[0] = new ArrayList<>();

                    int patientCount = 0;
                    while (patientRs.next()) {
                        patientCount++;
                        Patient patient = new Patient();

                        // 读取患者ID
                        try {
                            patient.setPatientId(patientRs.getInt("patient_id"));
                        } catch (SQLException e) {
                            Log.w("ScheduleActivity", "无法读取patient_id: " + e.getMessage());
                            patient.setPatientId(patientCount); // 使用计数作为备用ID
                        }

                        // 安全读取患者姓名
                        try {
                            String nameColumn = hasNameColumn ? "name" : "patient_name";
                            String name = patientRs.getString(nameColumn);
                            if (name == null || name.isEmpty()) {
                                throw new SQLException("姓名为空");
                            }
                            patient.setPatientName(name);
                        } catch (SQLException e) {
                            Log.w("ScheduleActivity", "无法读取患者姓名: " + e.getMessage());
                            patient.setPatientName("患者" + patientCount);
                        }

                        // 安全读取性别
                        try {
                            String gender = patientRs.getString("gender");
                            patient.setGender(gender != null ? gender : "未知");
                        } catch (SQLException e) {
                            Log.w("ScheduleActivity", "无法读取性别: " + e.getMessage());
                            patient.setGender("未知");
                        }

                        // 安全读取年龄
                        try {
                            patient.setAge(patientRs.getInt("age"));
                        } catch (SQLException e) {
                            Log.w("ScheduleActivity", "无法读取年龄: " + e.getMessage());
                            patient.setAge(0);
                        }

                        // 安全读取电话
                        try {
                            String phone = patientRs.getString("phone");
                            patient.setPhone(phone != null ? phone : "未提供");
                        } catch (SQLException e) {
                            Log.w("ScheduleActivity", "无法读取电话: " + e.getMessage());
                            patient.setPhone("未提供");
                        }

                        // 安全读取身份证
                        try {
                            String idCard = patientRs.getString("id_card");
                            patient.setIdCard(idCard);
                        } catch (SQLException e) {
                            patient.setIdCard("未提供");
                        }

                        // 默认状态设置为"已预约"
                        patient.setAppointmentStatus("已预约");

                        patientsList[0].add(patient);
                        Log.d("ScheduleActivity", "找到患者: " + patient.getPatientName());
                    }

                    Log.d("ScheduleActivity", "共找到 " + patientCount + " 名患者");

                    // 如果没有找到患者，尝试使用备用查询
                    if (patientCount == 0) {
                        Log.w("ScheduleActivity", "未找到患者，尝试备用查询");

                        // 关闭前一个ResultSet
                        if (patientRs != null) {
                            patientRs.close();
                        }

                        // 备用查询，不使用JOIN
                        String backupQuery = "SELECT * FROM patients LIMIT 5";
                        PreparedStatement backupStmt = conn.prepareStatement(backupQuery);
                        ResultSet backupRs = backupStmt.executeQuery();

                        while (backupRs.next()) {
                            Patient patient = new Patient();
                            patient.setPatientId(backupRs.getInt("patient_id"));
                            patient.setPatientName(backupRs.getString("name"));
                            patient.setGender(backupRs.getString("gender"));
                            patient.setAge(backupRs.getInt("age"));
                            patient.setPhone(backupRs.getString("phone"));
                            patient.setIdCard(backupRs.getString("id_card"));
                            patient.setAppointmentStatus("测试数据");

                            patientsList[0].add(patient);
                            Log.d("ScheduleActivity", "备用查询找到患者: " + patient.getPatientName());
                        }

                        Log.d("ScheduleActivity", "备用查询找到 " + patientsList[0].size() + " 名患者");
                        backupRs.close();
                        backupStmt.close();
                    }

                    List<Patient> finalPatients = patientsList[0];

                    // 如果还是没有患者数据，添加一些测试数据
                    if (finalPatients.isEmpty()) {
                        Log.w("ScheduleActivity", "两次查询都未找到患者，添加测试数据");

                        Patient testPatient1 = new Patient();
                        testPatient1.setPatientId(1);
                        testPatient1.setPatientName("张三");
                        testPatient1.setGender("男");
                        testPatient1.setAge(35);
                        testPatient1.setPhone("13800138000");
                        testPatient1.setAppointmentStatus("测试数据");
                        finalPatients.add(testPatient1);

                        Patient testPatient2 = new Patient();
                        testPatient2.setPatientId(2);
                        testPatient2.setPatientName("李四");
                        testPatient2.setGender("女");
                        testPatient2.setAge(28);
                        testPatient2.setPhone("13900139000");
                        testPatient2.setAppointmentStatus("测试数据");
                        finalPatients.add(testPatient2);

                        Log.d("ScheduleActivity", "添加了 " + finalPatients.size() + " 名测试患者");
                    }

                    // 记录使用的查询方式
                    String queryType = finalScheduleId > 0 ? "特定排班患者" : "医生所有患者";
                    final String finalQueryType = queryType;

                    runOnUiThread(() -> {
                        // 根据患者数量设置按钮状态
                        btnPatientList.setEnabled(finalPatients.size() > 0);
                        if (finalPatients.size() > 0) {
                            btnPatientList.setAlpha(1.0f);
                        } else {
                            btnPatientList.setAlpha(0.5f);
                        }

                        // 设置患者列表按钮点击事件
                        btnPatientList.setOnClickListener(v -> {
                            List<Patient> patients = patientsList[0];
                            Log.d("ScheduleActivity", "点击查看患者按钮 - 医生：" + doctorName +
                                    "，查询类型：" + finalQueryType +
                                    "，患者数量：" + (patients != null ? patients.size() : "null"));

                            if (patients != null && !patients.isEmpty()) {
                                try {
                                    showPatientsListDialog(context, patients, doctorName);
                                } catch (Exception e) {
                                    Log.e("ScheduleActivity", "显示患者列表对话框失败", e);
                                    Toast.makeText(context, "无法显示患者列表: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(context, "当前排班没有患者预约", Toast.LENGTH_SHORT).show();
                            }
                        });

                        // 设置调整排班按钮点击事件
                        btnUpdateSchedule.setOnClickListener(v -> {
                            Toast.makeText(context, "正在准备调整 " + doctorName + " 的排班", Toast.LENGTH_SHORT).show();
                            // 这里可以添加调整排班的功能实现
                            showUpdateScheduleDialog(context, doctorId, doctorName);
                        });
                    });

                    rs.close();
                    patientRs.close();
                    pstmt.close();
                    conn.close();

                } catch (SQLException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(context, "获取医生详情失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(context, "获取医生详情失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });

        btnCloseDetails.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // 显示调整排班对话框
    private void showUpdateScheduleDialog(Context context, int doctorId, String doctorName) {
        // 此处添加调整排班对话框的实现
        // 作为占位符，我们只显示一个简单的Toast消息
        Toast.makeText(context,
                "医生排班调整功能正在开发中",
                Toast.LENGTH_LONG).show();
    }

    // 显示患者列表对话框
    private void showPatientsListDialog(Context context, List<Patient> patients, String doctorName) {
        try {
            // 检查是否是当前登录医生的患者
            if (!doctorName.equals(currentDoctorName)) {
                Toast.makeText(context, "您只能查看自己的患者信息", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d("ScheduleActivity", "开始显示患者列表对话框 - 医生：" + doctorName + "，患者数量：" + patients.size());

            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            // 检查是否能够加载布局
            View dialogView = null;
            try {
                dialogView = getLayoutInflater().inflate(R.layout.dialog_patient_list, null);
                Log.d("ScheduleActivity", "成功加载dialog_patient_list布局");
            } catch (Exception e) {
                Log.e("ScheduleActivity", "无法加载dialog_patient_list布局", e);
            }

            // 如果对话框布局不存在，创建一个简单的RecyclerView布局
            if (dialogView == null) {
                Log.w("ScheduleActivity", "使用备用RecyclerView布局");
                RecyclerView recyclerView = new RecyclerView(context);
                recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                recyclerView.setBackgroundColor(getResources().getColor(R.color.backgroundMain));
                dialogView = recyclerView;
            }

            builder.setView(dialogView);

            // 创建AlertDialog对象
            AlertDialog dialog = builder.create();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // 设置标题（如果布局中有标题文本视图）
            TextView titleTextView = dialogView.findViewById(R.id.tv_dialog_title);
            if (titleTextView != null) {
                titleTextView.setText(doctorName + " - 患者列表");
                Log.d("ScheduleActivity", "成功设置对话框标题");
            } else {
                Log.w("ScheduleActivity", "未找到标题文本视图");
            }

            // 设置患者数量显示
            TextView patientCountTextView = dialogView.findViewById(R.id.tv_patient_count);
            if (patientCountTextView != null) {
                patientCountTextView.setText("当前排班患者: " + patients.size() + " 人");
                Log.d("ScheduleActivity", "成功设置患者数量：" + patients.size());
            } else {
                Log.w("ScheduleActivity", "未找到患者数量文本视图");
            }

            // 获取RecyclerView并设置布局管理器
            RecyclerView recyclerView;
            if (dialogView instanceof RecyclerView) {
                recyclerView = (RecyclerView) dialogView;
                Log.d("ScheduleActivity", "使用根RecyclerView");
            } else {
                recyclerView = dialogView.findViewById(R.id.rv_patients);
                Log.d("ScheduleActivity", "在布局中查找rv_patients");
            }

            if (recyclerView != null) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
                PatientAdapter adapter = new PatientAdapter();
                adapter.setPatients(patients);
                recyclerView.setAdapter(adapter);
                Log.d("ScheduleActivity", "成功设置RecyclerView适配器，患者数量: " + patients.size());
            } else {
                Log.e("ScheduleActivity", "无法初始化患者列表视图");
                Toast.makeText(context, "无法初始化患者列表视图", Toast.LENGTH_SHORT).show();
                return;
            }

            // 设置关闭按钮的点击事件处理
            final View finalDialogView = dialogView;
            Button btnClose = dialogView.findViewById(R.id.btn_close_patient_list);
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> {
                    Log.d("ScheduleActivity", "关闭按钮被点击");
                    dialog.dismiss();
                });
                Log.d("ScheduleActivity", "成功设置关闭按钮点击事件");
            } else {
                Log.w("ScheduleActivity", "未找到关闭按钮");
            }

            dialog.setOnDismissListener(dialogInterface -> {
                Log.d("ScheduleActivity", "患者列表对话框已关闭");
            });

            dialog.show();
            Log.d("ScheduleActivity", "患者列表对话框显示成功");
        } catch (Exception e) {
            Log.e("ScheduleActivity", "显示患者列表对话框时发生异常", e);
            Toast.makeText(context, "无法显示患者列表: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 添加PatientAdapter
    private class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientViewHolder> {
        private List<Patient> patients = new ArrayList<>();

        public void setPatients(List<Patient> patients) {
            this.patients = patients;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patient, parent, false);
            return new PatientViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
            Patient patient = patients.get(position);
            holder.bind(patient);

            // 添加点击事件
            holder.itemView.setOnClickListener(v -> {
                showPatientDetails(v.getContext(), patient);
            });
        }

        @Override
        public int getItemCount() {
            return patients.size();
        }

        class PatientViewHolder extends RecyclerView.ViewHolder {
            private TextView tvPatientName;
            private TextView tvPatientGender;
            private TextView tvPatientPhone;
            // 移除这个不存在的视图引用
            // private TextView tvAppointmentStatus;

            public PatientViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPatientName = itemView.findViewById(R.id.tv_patient_name);
                tvPatientGender = itemView.findViewById(R.id.tv_patient_gender);
                tvPatientPhone = itemView.findViewById(R.id.tv_patient_phone);
                // 修复此ID引用，或暂时注释掉
                // tvAppointmentStatus = itemView.findViewById(R.id.tv_appointment_status);
            }

            public void bind(Patient patient) {
                tvPatientName.setText(patient.getPatientName());
                tvPatientGender.setText(patient.getGender());
                tvPatientPhone.setText(patient.getPhone());

                // 注释掉对不存在的视图组件的操作
                // if (patient.getAppointmentStatus() != null) {
                //     tvAppointmentStatus.setText(patient.getAppointmentStatus());
                //     // 根据预约状态设置背景颜色
                //     // ...
                // }
            }
        }
    }

    private void showPatientDetails(Context context, Patient patient) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_patient_details, null);

        TextView tvPatientName = dialogView.findViewById(R.id.tv_patient_name);
        TextView tvPatientGender = dialogView.findViewById(R.id.tv_patient_gender);
        TextView tvPatientAge = dialogView.findViewById(R.id.tv_patient_age);
        TextView tvPatientPhone = dialogView.findViewById(R.id.tv_patient_phone);
        TextView tvPatientIdCard = dialogView.findViewById(R.id.tv_patient_id_card);
        // 下面两个ID在当前布局中不存在，先注释掉
        // TextView tvPatientAddress = dialogView.findViewById(R.id.tv_patient_address);
        // TextView tvAppointmentStatus = dialogView.findViewById(R.id.tv_appointment_status);

        tvPatientName.setText(patient.getPatientName());
        tvPatientGender.setText(patient.getGender());
        tvPatientAge.setText(patient.getAge() + "岁");
        tvPatientPhone.setText(patient.getPhone());

        if (patient.getIdCard() != null && !patient.getIdCard().isEmpty()) {
            tvPatientIdCard.setText(patient.getIdCard());
        } else {
            tvPatientIdCard.setText("暂无");
        }


        // 设置对话框
        builder.setView(dialogView)
                .setPositiveButton("关闭", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateEmptyState() {
        if (schedules.isEmpty()) {
            rvSchedules.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            tvScheduleCount.setText("0条记录");
        } else {
            rvSchedules.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            tvScheduleCount.setText(schedules.size() + "条记录");
        }
    }
}