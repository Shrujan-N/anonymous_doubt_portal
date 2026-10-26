package com.doubtportal.repository;

import com.doubtportal.model.TeacherAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeacherAnswerRepository extends JpaRepository<TeacherAnswer, Long> {
    List<TeacherAnswer> findByDoubtId(Long doubtId);
}
