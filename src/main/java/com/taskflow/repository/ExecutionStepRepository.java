package com.taskflow.repository;

import com.taskflow.entity.ExecutionStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionStepRepository extends JpaRepository<ExecutionStepEntity, Long> {

    List<ExecutionStepEntity> findByTaskExecutionTaskIdOrderByStepIndexAsc(String taskId);

    void deleteByTaskExecutionTaskId(String taskId);
}
