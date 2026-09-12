package org.example.lightaisoilmoistirrigationbackend.image.dto;

import java.util.List;

/**
 * 图片批量操作请求：删除 / 归档共用。
 * ids 为必填的图片ID集合；folderId 仅在归档(batch-move)时使用，
 * 设为 null 表示将图片移出目录、回到未分类。
 */
public class BatchImageRequest {

    private List<Long> ids;
    private Long folderId;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
}
