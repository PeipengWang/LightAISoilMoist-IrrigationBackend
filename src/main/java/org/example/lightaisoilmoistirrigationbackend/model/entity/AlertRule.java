package org.example.lightaisoilmoistirrigationbackend.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "alert_rule")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String alertName;

    @Column(nullable = false, length = 32)
    private String alertType;

    @Column(length = 16)
    private String propertyIdentifier;

    @Column(length = 32)
    private String deviceName;

    @Column(nullable = false, length = 512)
    private String triggerCondition;

    @Column(length = 16)
    private String severity;

    @Column(length = 512)
    private String messageTemplate;

    private Integer cooldownMinutes = 30;

    @Column(nullable = false)
    private Boolean isActive = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAlertName() { return alertName; }
    public void setAlertName(String alertName) { this.alertName = alertName; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getPropertyIdentifier() { return propertyIdentifier; }
    public void setPropertyIdentifier(String propertyIdentifier) { this.propertyIdentifier = propertyIdentifier; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getTriggerCondition() { return triggerCondition; }
    public void setTriggerCondition(String triggerCondition) { this.triggerCondition = triggerCondition; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessageTemplate() { return messageTemplate; }
    public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }

    public Integer getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(Integer cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}