package org.example.lightaisoilmoistirrigationbackend.image.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageAnalysis;
import org.example.lightaisoilmoistirrigationbackend.image.service.AgentAnalysisService;
import org.example.lightaisoilmoistirrigationbackend.model.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 图片智能分析接口（阶段四）：选择某张图片调用本地部署的 AI 智能体进行分析。
 */
@RestController
@RequestMapping("/api/images")
@Tag(name = "图片智能分析", description = "选择图片调用 AI 智能体，流式/同步返回分析结论并落库")
@Slf4j
public class ImageAnalysisController {

    private final AgentAnalysisService analysisService;

    public ImageAnalysisController(AgentAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "流式分析（SSE）",
            description = "选择图片调用智能体，以 SSE 实时返回分析过程与最终结论，事件格式与智能体一致：" +
                    " data:{\"chunk\":\"...\"} / data:{\"done\":true,\"full_response\":\"...\"} / data:{\"error\":\"...\"}")
    @PostMapping(value = "/{id}/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(
            @Parameter(description = "图片ID") @PathVariable Long id,
            @Parameter(description = "分析提示词，不传则用默认（并自动附带拍摄上下文）") @RequestParam(required = false) String prompt,
            @Parameter(description = "智能体会话名，不传则用默认") @RequestParam(required = false) String sessionName) {
        return analysisService.analyzeStream(id, prompt, sessionName);
    }

    @Operation(summary = "同步分析（阻塞取结论）",
            description = "调用智能体并等待分析完成，把完整结论落库 image_analysis，返回分析记录")
    @PostMapping("/{id}/analyze")
    public Result<ImageAnalysis> analyze(
            @Parameter(description = "图片ID") @PathVariable Long id,
            @Parameter(description = "分析提示词，不传则用默认") @RequestParam(required = false) String prompt,
            @Parameter(description = "智能体会话名，不传则用默认") @RequestParam(required = false) String sessionName) {
        try {
            ImageAnalysis result = analysisService.analyzeSync(id, prompt, sessionName);
            return Result.success("分析完成", result);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            log.error("图片[{}]智能分析失败", id, e);
            return Result.error(500, e.getMessage());
        }
    }

    @Operation(summary = "分析历史", description = "查询某张图片的全部分析记录（按时间倒序）")
    @GetMapping("/{id}/analyses")
    public Result<List<ImageAnalysis>> analyses(
            @Parameter(description = "图片ID") @PathVariable Long id) {
        try {
            return Result.success(analysisService.listAnalyses(id));
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
