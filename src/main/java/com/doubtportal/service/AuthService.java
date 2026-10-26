package com.doubtportal.service;

import com.doubtportal.dto.LoginRequest;
import com.doubtportal.dto.LoginResponse;
import com.doubtportal.model.Admin;
import com.doubtportal.model.Student;
import com.doubtportal.model.Teacher;
import com.doubtportal.repository.AdminRepository;
import com.doubtportal.repository.StudentRepository;
import com.doubtportal.repository.TeacherRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AdminRepository adminRepository;

    public AuthService(StudentRepository studentRepository, TeacherRepository teacherRepository,
                       AdminRepository adminRepository) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.adminRepository = adminRepository;
    }

    public LoginResponse authenticate(LoginRequest request) {
        if (request == null || request.getRole() == null || request.getEmail() == null
                || request.getPassword() == null) {
            throw new IllegalArgumentException("Email, password, and role are required");
        }

        String role = request.getRole().trim().toUpperCase();
        return switch (role) {
            case "STUDENT" -> studentRepository.findByEmail(request.getEmail())
                    .filter(user -> user.getPassword().equals(request.getPassword()))
                    .map(user -> studentResponse(user))
                    .orElseThrow(() -> invalidCredentials());
            case "TEACHER" -> teacherRepository.findByEmail(request.getEmail())
                    .filter(user -> user.getPassword().equals(request.getPassword()))
                    .map(user -> teacherResponse(user))
                    .orElseThrow(() -> invalidCredentials());
            case "ADMIN" -> adminRepository.findByEmail(request.getEmail())
                    .filter(user -> user.getPassword().equals(request.getPassword()))
                    .map(user -> adminResponse(user))
                    .orElseThrow(() -> invalidCredentials());
            default -> throw new IllegalArgumentException("Invalid role specified");
        };
    }

    private LoginResponse studentResponse(Student user) {
        return new LoginResponse(user.getStudentId(), user.getName(), user.getEmail(),
                "STUDENT", "Authentication Successful");
    }

    private LoginResponse teacherResponse(Teacher user) {
        return new LoginResponse(user.getTeacherId(), user.getName(), user.getEmail(),
                "TEACHER", "Authentication Successful");
    }

    private LoginResponse adminResponse(Admin user) {
        return new LoginResponse(user.getAdminId(), user.getName(), user.getEmail(),
                "ADMIN", "Authentication Successful");
    }

    private RuntimeException invalidCredentials() {
        return new IllegalArgumentException("Invalid credentials or role specified");
    }
}
