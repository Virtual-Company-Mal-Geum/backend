package com.malgeum.geo.domain.domain.analysisjob.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
  List<AnalysisJob> findAllByOrder_IdIn(List<Long> orderIds);

  @Query(value = """
      SELECT *
      FROM analysis_job
      WHERE status IN ('PENDING', 'RETRY_WAIT')
        AND next_run_at <= now()
      ORDER BY created_at ASC
      LIMIT 1
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  Optional<AnalysisJob> findNextJobForUpdate();

  Optional<AnalysisJob> findByOrderId(Long orderId);
}
