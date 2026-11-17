package com.doubtportal.service;

import com.doubtportal.dto.LoginRequest;
import com.doubtportal.dto.LoginResponse;
import com.doubtportal.model.Admin;
import com.doubtportal.model.Student;
import com.doubtportal.model.Teacher;
import com.doubtportal.repository.AdminRepository;
import com.doubtportal.repository.StudentRepository;
import com.doubtportal.repository.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public static final String SESSION_USER_KEY = "PORTAL_AUTH_USER";
    public static final String TOKEN_HEADER = "X-Auth-Token";

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AdminRepository adminRepository;
    private final Map<String, AuthenticatedUser> tokenStore = new ConcurrentHashMap<>();

    public AuthService(StudentRepository studentRepository,
                       TeacherRepository teacherRepository,
                       AdminRepository adminRepository) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.adminRepository = adminRepository;
    }

    public record AuthenticatedUser(Long userId, String name, String email, String role, String detail, String token) {}

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }

    public LoginResponse authenticate(LoginRequest request, HttpSession session) {
        if (request == null || request.getRole() == null || request.getEmail() == null
                || request.getPassword() == null || request.getEmail().isBlank() || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Email, password, and role are required.");
        }

        String email = request.getEmail().trim();
        String password = request.getPassword();
        String role = request.getRole().trim().toUpperCase(Locale.ROOT);

        AuthenticatedUser authenticated = switch (role) {
            case "STUDENT" -> studentRepository.findByEmail(email)
                    .filter(u -> u.getPassword().equals(password))
                    .map(u -> issueSessionUser(u.getStudentId(), u.getName(), u.getEmail(), "STUDENT",
                            u.getBranch() + " • Sem " + u.getSemester(), session))
                    .orElseThrow(() -> new UnauthorizedException("Invalid student email or password."));
            case "TEACHER" -> teacherRepository.findByEmail(email)
                    .filter(u -> u.getPassword().equals(password))
                    .map(u -> issueSessionUser(u.getTeacherId(), u.getName(), u.getEmail(), "TEACHER",
                            u.getDepartment() + " Faculty", session))
                    .orElseThrow(() -> new UnauthorizedException("Invalid teacher email or password."));
            case "ADMIN" -> adminRepository.findByEmail(email)
                    .filter(u -> u.getPassword().equals(password))
                    .map(u -> issueSessionUser(u.getAdminId(), u.getName(), u.getEmail(), "ADMIN",
                            "Portal Moderation", session))
                    .orElseThrow(() -> new UnauthorizedException("Invalid admin email or password."));
            default -> throw new IllegalArgumentException("Invalid role specified. Expected STUDENT, TEACHER, or ADMIN.");
        };

        return toLoginResponse(authenticated, "Authentication Successful");
    }

    private AuthenticatedUser issueSessionUser(Long userId, String name, String email, String role,
                                               String detail, HttpSession session) {
        if (session != null && session.getAttribute(SESSION_USER_KEY) instanceof AuthenticatedUser previous
                && previous.token() != null) {
            tokenStore.remove(previous.token());
        }
        String token = UUID.randomUUID().toString();
        AuthenticatedUser user = new AuthenticatedUser(userId, name, email, role, detail, token);
        tokenStore.put(token, user);
        if (session != null) {
            session.setAttribute(SESSION_USER_KEY, user);
        }
        return user;
    }

    public Optional<AuthenticatedUser> getAuthenticatedUser(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            }
        }
        if (token != null && !token.isBlank()) {
            return Optional.ofNullable(tokenStore.get(token));
        }
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_USER_KEY) instanceof AuthenticatedUser sessionUser) {
            return Optional.of(sessionUser);
        }
        return Optional.empty();
    }

    public AuthenticatedUser requireAuthenticated(HttpServletRequest request) {
        return getAuthenticatedUser(request)
                .orElseThrow(() -> new UnauthorizedException("Authentication required. Please log in first."));
    }

    public AuthenticatedUser requireRole(HttpServletRequest request, String... allowedRoles) {
        AuthenticatedUser user = requireAuthenticated(request);
        boolean permitted = Arrays.stream(allowedRoles)
                .anyMatch(r -> r.equalsIgnoreCase(user.role()));
        if (!permitted) {
            throw new ForbiddenException("Access denied for role " + user.role()
                    + ". Required role: " + String.join(" or ", allowedRoles) + ".");
        }
        return user;
    }

    public void logout(HttpServletRequest request) {
        if (request == null) return;
        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            }
        }
        if (token != null && !token.isBlank()) {
            tokenStore.remove(token);
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object attr = session.getAttribute(SESSION_USER_KEY);
            if (attr instanceof AuthenticatedUser user && user.token() != null) {
                tokenStore.remove(user.token());
            }
            session.invalidate();
        }
    }

    public LoginResponse toLoginResponse(AuthenticatedUser user, String message) {
        return new LoginResponse(
                user.userId(),
                user.name(),
                user.email(),
                user.role(),
                user.detail(),
                user.token(),
                message
        );
    }
}
