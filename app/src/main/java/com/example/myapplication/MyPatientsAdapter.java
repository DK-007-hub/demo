package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MyPatientsAdapter extends RecyclerView.Adapter<MyPatientsAdapter.PatientViewHolder> {
    
    private List<MyPatientsActivity.Patient> patientList;
    private OnPatientClickListener listener;
    
    // 点击监听器接口
    public interface OnPatientClickListener {
        void onPatientClick(MyPatientsActivity.Patient patient);
    }
    
    public MyPatientsAdapter(List<MyPatientsActivity.Patient> patientList) {
        this.patientList = patientList;
    }
    
    public void setOnPatientClickListener(OnPatientClickListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_patient, parent, false);
        return new PatientViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        MyPatientsActivity.Patient patient = patientList.get(position);
        
        // 设置患者信息
        holder.tvName.setText(patient.getName());
        holder.tvGender.setText(patient.getGender());
        holder.tvAge.setText(String.valueOf(patient.getAge()) + "岁");
        holder.tvPhone.setText(patient.getPhone());
        
        // 身份证可能为空，需要检查
        if (patient.getIdCard() != null && !patient.getIdCard().isEmpty()) {
            holder.tvIdCard.setText(patient.getIdCard());
        } else {
            holder.tvIdCard.setText("未知");
        }
        
        // 设置点击事件
        if (listener != null) {
            holder.itemView.setOnClickListener(v -> listener.onPatientClick(patient));
        }
    }
    
    @Override
    public int getItemCount() {
        return patientList == null ? 0 : patientList.size();
    }
    
    static class PatientViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvGender;
        TextView tvAge;
        TextView tvPhone;
        TextView tvIdCard;
        
        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_patient_name);
            tvGender = itemView.findViewById(R.id.tv_patient_gender);
            tvAge = itemView.findViewById(R.id.tv_patient_age);
            tvPhone = itemView.findViewById(R.id.tv_patient_phone);
            tvIdCard = itemView.findViewById(R.id.tv_patient_id_card);
        }
    }
} 