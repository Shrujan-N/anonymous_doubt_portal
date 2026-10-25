package com.doubtportal.repository;

import com.doubtportal.model.Doubt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;

public interface DoubtRepository extends JpaRepository<Doubt, Long> {
    @Procedure(procedureName = "sp_CloseDoubt")
    void closeDoubtProcedure(@Param("p_doubt_id") Integer doubtId);
}