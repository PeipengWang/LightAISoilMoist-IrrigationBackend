package org.example.lightaisoilmoistirrigationbackend.image.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 图片智能分析结论记录（阶段四：调用 AI 智能体分析后落库）。
 * 与 image_record 为「一对多」关系：同一张图片可多次分析，保留历史。
 */
@Entity
@Table(name = "image_analysis")
public class ImageAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_id", nullable = false)
    private Long imageId;

    @Column(name = "session_name", length = 128)
    private String sessionName;

    @Column(name = "prompt", length = 2000)
    private String prompt;

    /** 智能体返回的完整分析结论（阶段四的 D 类核心产出） */
    @Column(name = "conclusion", columnDefinition = "TEXT")
    private String conclusion;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }

    public String getSessionName() { return sessionName; }
    public void setSessionName(String sessionName) { this.sessionName = sessionName; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
