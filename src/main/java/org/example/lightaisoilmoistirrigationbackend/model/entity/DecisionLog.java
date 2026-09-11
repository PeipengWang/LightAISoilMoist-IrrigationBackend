package org.example.lightaisoilmoistirrigationbackend.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "decision_log")
public class DecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32, nullable = false)
    private String deviceName;

    @Column(length = 16, nullable = false)
    private String identifier;

    @Column(length = 32)
    private String propertyName;

    @Column(name = "property_value", length = 64)
    private String value;

    @Column(name = "value_desc", length = 64)
    private String valueDesc;

    @Column(length = 16, nullable = false)
    private String decisionType;

    @Column(nullable = false)
    private Boolean success = false;

    @Column(name = "result_msg", length = 256)
    private String resultMsg;

    @Column(name = "operator_name", length = 64)
    private String operator = "api";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getValueDesc() { return valueDesc; }
    public void setValueDesc(String valueDesc) { this.valueDesc = valueDesc; }

    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getResultMsg() { return resultMsg; }
    public void setResultMsg(String resultMsg) { this.resultMsg = resultMsg; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
