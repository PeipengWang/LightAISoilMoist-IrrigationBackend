package org.example.lightaisoilmoistirrigationbackend.image.repository;

import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImageFolderRepository extends JpaRepository<ImageFolder, Long> {

    /** 查询某父目录下的直接子目录 */
    List<ImageFolder> findByParentId(Long parentId);
}
