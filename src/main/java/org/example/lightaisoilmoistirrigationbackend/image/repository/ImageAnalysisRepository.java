package org.example.lightaisoilmoistirrigationbackend.image.repository;

import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ImageAnalysisRepository extends JpaRepository<ImageAnalysis, Long> {

    /** 查询某张图片的全部分析历史（按时间倒序） */
    List<ImageAnalysis> findByImageIdOrderByCreatedAtDesc(Long imageId);

    /** 删除某张图片的全部分析记录（删除图片前级联清理用） */
    @Transactional
    void deleteByImageId(Long imageId);
}
