package com.malgeum.geo.domain.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisreport.entity.ReportResult.Score;
import com.malgeum.geo.domain.domain.order.entity.Order;

class ReevaluationPolicyTest {

    private static AnalysisJob succeeded() {
        AnalysisJob job = AnalysisJob.create(null);
        job.markSucceeded();
        return job;
    }

    private static AnalysisJob failed() {
        AnalysisJob job = AnalysisJob.create(null);
        job.markFailed("crawl blocked");
        return job;
    }

    @Test
    @DisplayName("실패한 재평가는 횟수에 넣지 않고, 진행 중·성공은 횟수를 쓴다")
    void remainingExcludesFailed() {
        assertThat(ReevaluationPolicy.calculateRemainingReevaluationCount(List.of())).isEqualTo(2);
        assertThat(ReevaluationPolicy.calculateRemainingReevaluationCount(List.of(failed(), failed()))).isEqualTo(2);
        assertThat(ReevaluationPolicy.calculateRemainingReevaluationCount(List.of(succeeded(), failed()))).isEqualTo(1);
        assertThat(ReevaluationPolicy.calculateRemainingReevaluationCount(List.of(succeeded(), AnalysisJob.create(null)))).isEqualTo(0);
        assertThat(ReevaluationPolicy.calculateRemainingReevaluationCount(List.of(succeeded(), succeeded(), succeeded()))).isEqualTo(0);
    }

    @Test
    @DisplayName("재평가 기한은 원 주문 완료 시각 + 30일")
    void expiresThirtyDaysAfterCompletion() {
        AnalysisJob baseline = succeeded();
        assertThat(ReevaluationPolicy.expiresAt(baseline)).isEqualTo(baseline.getCompletedAt().plusDays(30));
        assertThat(AnalysisJob.create(null).isInProgress()).isTrue();
        assertThat(baseline.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("재평가 주문 이름은 '원 주문 이름 - 재분석 N'이고, 255자를 넘으면 원래 이름 쪽을 자른다")
    void reevaluationNameHasSequenceSuffix() {
        Order baseline = Order.builder().siteName("JHW토익").targetUrl("https://www.jhwtoeic.com/").build();
        Order reevaluation = Order.createDuplicateForReevaluation(baseline, 2);
        assertThat(reevaluation.getSiteName()).isEqualTo("JHW토익 - 재분석 2");
        assertThat(reevaluation.getParentOrder()).isSameAs(baseline);
        assertThat(reevaluation.getTargetUrl()).isEqualTo(baseline.getTargetUrl());

        Order longName = Order.builder().siteName("가".repeat(255)).build();
        String name = Order.createDuplicateForReevaluation(longName, 1).getSiteName();
        assertThat(name).hasSize(255).endsWith(" - 재분석 1");
    }

    @Test
    @DisplayName("점수는 /diagnose의 analysis와 /evaluate의 result 모두에서 읽고, 미완료면 null")
    void scoreReadsBothShapes() {
        Map<String, Object> scores = Map.of("raw_score", 58, "total_score", 55.0);

        assertThat(Score.from(succeeded(), Map.of("result", scores))).isEqualTo(new Score(58.0, 55.0));
        assertThat(Score.from(succeeded(), Map.of("analysis", scores))).isEqualTo(new Score(58.0, 55.0));
        assertThat(Score.from(AnalysisJob.create(null), Map.of("result", scores))).isNull();
        assertThat(Score.from(succeeded(), null)).isNull();
    }
}
