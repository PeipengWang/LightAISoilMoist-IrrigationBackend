package org.example.lightaisoilmoistirrigationbackend.repository;

import org.example.lightaisoilmoistirrigationbackend.model.entity.DecisionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class DecisionLogRepositoryTest {

    @Autowired
    private DecisionLogRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // 插入测试数据：2条水泵控制 + 2条阈值设置
        DecisionLog log1 = new DecisionLog();
        log1.setDeviceName("device");
        log1.setIdentifier("F");
        log1.setPropertyName("水泵状态");
        log1.setValue("1");
        log1.setValueDesc("开启");
        log1.setDecisionType("PUMP_CONTROL");
        log1.setSuccess(true);
        log1.setResultMsg("SUCCESS");
        log1.setOperator("api");
        log1.setCreatedAt(LocalDateTime.now().minusHours(3));
        repository.save(log1);

        DecisionLog log2 = new DecisionLog();
        log2.setDeviceName("device");
        log2.setIdentifier("F");
        log2.setPropertyName("水泵状态");
        log2.setValue("0");
        log2.setValueDesc("关闭");
        log2.setDecisionType("PUMP_CONTROL");
        log2.setSuccess(true);
        log2.setResultMsg("SUCCESS");
        log2.setOperator("api");
        log2.setCreatedAt(LocalDateTime.now().minusHours(2));
        repository.save(log2);

        DecisionLog log3 = new DecisionLog();
        log3.setDeviceName("device");
        log3.setIdentifier("K");
        log3.setPropertyName("土壤湿度阈值低");
        log3.setValue("30");
        log3.setDecisionType("THRESHOLD_SET");
        log3.setSuccess(true);
        log3.setResultMsg("SUCCESS");
        log3.setOperator("api");
        log3.setCreatedAt(LocalDateTime.now().minusHours(1));
        repository.save(log3);

        DecisionLog log4 = new DecisionLog();
        log4.setDeviceName("device");
        log4.setIdentifier("L");
        log4.setPropertyName("土壤湿度阈值高");
        log4.setValue("70");
        log4.setDecisionType("THRESHOLD_SET");
        log4.setSuccess(false);
        log4.setResultMsg("设备离线");
        log4.setOperator("api");
        log4.setCreatedAt(LocalDateTime.now());
        repository.save(log4);
    }

    @Test
    void shouldSaveAndCountAll() {
        assertEquals(4, repository.count());
    }

    @Test
    void shouldFindByDecisionType() {
        Page<DecisionLog> pumpLogs = repository.findByDecisionTypeOrderByCreatedAtDesc(
                "PUMP_CONTROL", PageRequest.of(0, 10));
        assertEquals(2, pumpLogs.getTotalElements());
        pumpLogs.forEach(log -> assertEquals("PUMP_CONTROL", log.getDecisionType()));

        Page<DecisionLog> thresholdLogs = repository.findByDecisionTypeOrderByCreatedAtDesc(
                "THRESHOLD_SET", PageRequest.of(0, 10));
        assertEquals(2, thresholdLogs.getTotalElements());
    }

    @Test
    void shouldCountByDecisionType() {
        assertEquals(2, repository.countByDecisionType("PUMP_CONTROL"));
        assertEquals(2, repository.countByDecisionType("THRESHOLD_SET"));
    }

    @Test
    void shouldCountBySuccess() {
        assertEquals(3, repository.countBySuccessTrue());
    }

    @Test
    void shouldPaginateCorrectly() {
        // 每页2条，共4条 → 2页
        Page<DecisionLog> page1 = repository.findAll(
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(4, page1.getTotalElements());
        assertEquals(2, page1.getTotalPages());
        assertEquals(0, page1.getNumber());
        assertEquals(2, page1.getContent().size());

        Page<DecisionLog> page2 = repository.findAll(
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertEquals(1, page2.getNumber());
        assertEquals(2, page2.getContent().size());
    }

    @Test
    void shouldFilterByTimeRange() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(90);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);

        Page<DecisionLog> result = repository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                start, end, PageRequest.of(0, 10));

        // 应返回最近2条（1小时内的阈值设置）
        assertTrue(result.getTotalElements() >= 2);
    }

    @Test
    void shouldFilterByDeviceName() {
        Page<DecisionLog> result = repository.findByDeviceNameOrderByCreatedAtDesc(
                "device", PageRequest.of(0, 10));
        assertEquals(4, result.getTotalElements());
    }

    @Test
    void shouldFilterBySpecification() {
        Specification<DecisionLog> spec = (root, query, cb) ->
                cb.equal(root.get("decisionType"), "PUMP_CONTROL");

        Page<DecisionLog> result = repository.findAll(spec,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertEquals(2, result.getTotalElements());
        result.forEach(log -> assertEquals("F", log.getIdentifier()));
    }

    @Test
    void shouldCombineSpecificationFilters() {
        Specification<DecisionLog> spec = (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("deviceName"), "device"),
                        cb.equal(root.get("decisionType"), "THRESHOLD_SET"),
                        cb.equal(root.get("success"), false)
                );

        List<DecisionLog> results = repository.findAll(spec);
        assertEquals(1, results.size());
        assertEquals("L", results.get(0).getIdentifier());
        assertEquals("设备离线", results.get(0).getResultMsg());
    }
}
