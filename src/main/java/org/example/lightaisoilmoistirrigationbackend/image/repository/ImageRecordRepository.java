package org.example.lightaisoilmoistirrigationbackend.image.repository;

import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImageRecordRepository extends JpaRepository<ImageRecord, Long> {

    /** 按目录查询该目录下全部图片 */
    List<ImageRecord> findByFolderId(Long folderId);

    /** 按目录分页查询 */
    Page<ImageRecord> findByFolderId(Long folderId, Pageable pageable);
}
