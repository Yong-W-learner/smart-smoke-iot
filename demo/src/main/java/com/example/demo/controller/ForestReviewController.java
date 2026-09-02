package com.example.demo.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.ForestReviewUploadDTO;
import com.example.demo.entity.ForestCameraReview;
import com.example.demo.mapper.ForestCameraReviewMapper;
import com.example.demo.service.AiReviewService;
import com.example.demo.vo.ForestReviewVO;
import com.example.demo.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/review")
public class ForestReviewController {

    private static final Logger log = LoggerFactory.getLogger(ForestReviewController.class);

    private final ForestCameraReviewMapper reviewMapper;
    private final AiReviewService aiReviewService;

    public ForestReviewController(ForestCameraReviewMapper reviewMapper, AiReviewService aiReviewService) {
        this.reviewMapper = reviewMapper;
        this.aiReviewService = aiReviewService;
    }

    @PostMapping("/upload")
    public Result<String> upload(@RequestBody ForestReviewUploadDTO dto) {
        if (dto.getImageBase64() == null || dto.getImageBase64().isEmpty()) {
            return Result.fail("图片不能为空");
        }
        ForestCameraReview review = new ForestCameraReview();
        review.setSensorId(dto.getSensorId());
        review.setZone(dto.getZone());
        review.setLevel(dto.getLevel());
        review.setSmokeConcentration(dto.getSmokeConcentration());
        review.setImageBase64(ensureDataUri(dto.getImageBase64()));
        review.setCreateTime(LocalDateTime.now());
        reviewMapper.insert(review);

        try {
            AiReviewService.AiResult ai = aiReviewService.analyze(review);
            review.setAiVerdict(ai.verdict);
            review.setAiBasis(ai.basis);
            review.setAiDetections(JSON.toJSONString(ai.detections));
            review.setAiBoxes(JSON.toJSONString(ai.boxes));
            reviewMapper.updateById(review);
        } catch (Exception e) {
            log.warn("森林摄像头 AI 复核失败：{}", e.getMessage());
        }
        return Result.ok("上传成功");
    }

    @GetMapping("/latest")
    public ForestReviewVO latest() {
        LambdaQueryWrapper<ForestCameraReview> query = new LambdaQueryWrapper<>();
        query.orderByDesc(ForestCameraReview::getCreateTime).last("LIMIT 1");
        ForestCameraReview review = reviewMapper.selectOne(query);
        if (review == null) return null;

        ForestReviewVO result = new ForestReviewVO();
        result.setId(review.getId());
        result.setSensorId(review.getSensorId());
        result.setZone(review.getZone());
        result.setLevel(review.getLevel());
        result.setSmokeConcentration(review.getSmokeConcentration());
        result.setImageBase64(ensureDataUri(review.getImageBase64()));
        result.setCreateTime(review.getCreateTime());
        result.setAiVerdict(review.getAiVerdict());
        result.setAiBasis(review.getAiBasis());
        if (review.getAiDetections() != null) result.setAiDetections(JSON.parseArray(review.getAiDetections()));
        if (review.getAiBoxes() != null) result.setAiBoxes(JSON.parseArray(review.getAiBoxes()));
        return result;
    }

    private String ensureDataUri(String base64) {
        if (base64 == null || base64.isEmpty() || base64.startsWith("data:")) return base64;
        return "data:image/jpeg;base64," + base64;
    }
}
