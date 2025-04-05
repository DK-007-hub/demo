package com.example.myapplication.model;

import java.sql.Date;
import java.sql.Time;

public class Schedule {
    private int scheduleId;
    private int doctorId;
    private Date scheduleDate;
    private Time startTime;
    private Time endTime;
    private int maxPatients;
    private int currentPatients;
    private String status;
    
    // 添加医生相关信息
    private String doctorName;
    private String department;
    private String doctorTitle;

    // Getters and Setters
    public int getScheduleId() { return scheduleId; }
    public void setScheduleId(int scheduleId) { this.scheduleId = scheduleId; }
    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }
    public Date getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(Date scheduleDate) { this.scheduleDate = scheduleDate; }
    public Time getStartTime() { return startTime; }
    public void setStartTime(Time startTime) { this.startTime = startTime; }
    public void setStartTime(String startTime) { 
        try {
            this.startTime = Time.valueOf(startTime); 
        } catch (Exception e) {
            this.startTime = null;
        }
    }
    public Time getEndTime() { return endTime; }
    public void setEndTime(Time endTime) { this.endTime = endTime; }
    public void setEndTime(String endTime) { 
        try {
            this.endTime = Time.valueOf(endTime); 
        } catch (Exception e) {
            this.endTime = null;
        }
    }
    public int getMaxPatients() { return maxPatients; }
    public void setMaxPatients(int maxPatients) { this.maxPatients = maxPatients; }
    public int getCurrentPatients() { return currentPatients; }
    public void setCurrentPatients(int currentPatients) { this.currentPatients = currentPatients; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    // 添加医生信息的getter和setter
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getDoctorTitle() { return doctorTitle; }
    public void setDoctorTitle(String doctorTitle) { this.doctorTitle = doctorTitle; }
    
    // 格式化时间段的方法
    public String getFormattedTimeSlot() {
        if (startTime == null || endTime == null) return "";
        return startTime.toString().substring(0, 5) + " - " + endTime.toString().substring(0, 5);
    }
} 