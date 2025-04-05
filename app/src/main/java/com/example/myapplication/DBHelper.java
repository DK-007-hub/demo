package com.example.myapplication;

import android.util.Log;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import com.example.myapplication.model.Doctor;

// DBHelper.java
public class DBHelper {
    private static final String TAG = "DBHelper";
    // 修改数据库连接URL，添加中文支持和连接超时设置
    private static final String DB_URL = "jdbc:mysql://192.168.168.177:3306/db1?useUnicode=true&characterEncoding=UTF-8&connectTimeout=5000&socketTimeout=30000";
    private static final String USER = "dk";
    private static final String PASS = "123123";

    static {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            Log.d(TAG, "MySQL JDBC Driver注册成功");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "MySQL JDBC Driver注册失败: " + e.getMessage(), e);
        }
    }

    public static Connection getConnection() throws SQLException {
        try {
            Log.d(TAG, "尝试连接数据库: " + DB_URL);
            Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
            Log.d(TAG, "数据库连接成功");
            return conn;
        } catch (SQLException e) {
            Log.e(TAG, "数据库连接失败: " + e.getMessage(), e);
            throw e;
        }
    }

    public static int getUserSize() {
        String cls = "com.mysql.jdbc.Driver";
        // 确保连接字符串一致
        String url = "jdbc:mysql://192.168.188.177:3306/db1?useUnicode=true&characterEncoding=UTF-8";
        String user = "dk";
        String password = "123123";

        int count = 2;
        try {
            Class.forName(cls);
            Connection connection = DriverManager.getConnection(url, user, password);
            String sql = "select count(1) as s1 from user";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                count = resultSet.getInt("s1");
            }
            // 关闭资源
            resultSet.close();
            statement.close();
            connection.close();
        } catch (Exception e) {
            Log.e(TAG, "getUserSize失败: " + e.getMessage(), e);
            e.printStackTrace();
        }
        return count;
    }
    
    // 添加测试连接方法
    public static boolean testConnection() {
        try {
            Connection conn = getConnection();
            if (conn != null && !conn.isClosed()) {
                conn.close();
                return true;
            }
            return false;
        } catch (SQLException e) {
            Log.e(TAG, "测试连接失败: " + e.getMessage(), e);
            return false;
        }
    }
    
    // 验证医生登录
    public static Doctor verifyDoctorLogin(String doctorId, String password) {
        Doctor doctor = null;
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM doctors WHERE doctor_id = ? AND password = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, doctorId);
            pstmt.setString(2, password);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                doctor = new Doctor();
                doctor.setDoctorId(rs.getInt("doctor_id"));
                doctor.setName(rs.getString("name"));
                doctor.setDepartment(rs.getString("department"));
                doctor.setPosition(rs.getString("position"));
                doctor.setPhone(rs.getString("phone"));
                
                // 尝试获取可能不存在的字段
                try {
                    doctor.setImgUrl(rs.getString("img_url"));
                } catch (SQLException e) {
                    Log.w(TAG, "img_url列不存在: " + e.getMessage());
                }
                
                try {
                    doctor.setStatus(rs.getString("status"));
                } catch (SQLException e) {
                    Log.w(TAG, "status列不存在: " + e.getMessage());
                }
                
                Log.d(TAG, "医生验证成功: ID=" + doctorId + ", 姓名=" + doctor.getName());
            } else {
                Log.w(TAG, "医生验证失败: ID=" + doctorId);
            }
            
            rs.close();
            pstmt.close();
        } catch (SQLException e) {
            Log.e(TAG, "验证医生登录时出错: " + e.getMessage(), e);
        }
        return doctor;
    }
}
