package com.taskflow.repository;

import com.taskflow.entity.ExecutionStatus;
import com.taskflow.entity.TaskExecutionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {

    Optional<TaskExecutionEntity> findByTaskId(String taskId);

    List<TaskExecutionEntity> findByStatus(ExecutionStatus status);

    Page<TaskExecutionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ExecutionStatus status);
}
