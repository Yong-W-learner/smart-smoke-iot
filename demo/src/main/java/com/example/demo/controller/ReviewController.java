package com.example.demo.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.ReviewUploadDTO;
import com.example.demo.entity.AlarmReview;
import com.example.demo.entity.Device;
import com.example.demo.mapper.AlarmReviewMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.service.AiReviewService;
import com.example.demo.vo.Result;
import com.example.demo.vo.ReviewVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private AlarmReviewMapper alarmReviewMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private AiReviewService aiReviewService;

    // 上传现场画面（居民端报警时抓拍），并同步做 AI 复核
    @PostMapping("/upload")
    public Result<String> upload(@RequestBody ReviewUploadDTO dto) {
        if (dto.getImageBase64() == null || dto.getImageBase64().isEmpty()) {
            return Result.fail("图片不能为空");
        }
        AlarmReview r = new AlarmReview();
        r.setDeviceId(dto.getDeviceId());
        r.setAlarmLevel(dto.getAlarmLevel());
        r.setSmokeConcentration(dto.getSmokeConcentration());
        // 统一补全 data:image 前缀，保证前端 <img :src> 能直接显示
        r.setImageBase64(ensureDataUri(dto.getImageBase64()));
        r.setCreateTime(LocalDateTime.now());
        alarmReviewMapper.insert(r);

        // AI 复核：YOLO 视觉识别 + 浓度融合，结果写回数据库
        try {
            AiReviewService.AiResult ai = aiReviewService.analyze(r);
            r.setAiVerdict(ai.verdict);
            r.setAiBasis(ai.basis);
            r.setAiDetections(JSON.toJSONString(ai.detections));
            r.setAiBoxes(JSON.toJSONString(ai.boxes));
            alarmReviewMapper.updateById(r);
        } catch (Exception e) {
            log.warn("AI 复核失败：{}", e.getMessage());
        }
        return Result.ok("上传成功");
    }

    // 最新一条现场画面（管理员主页展示，含设备地址）
    @GetMapping("/latest")
    public ReviewVO latest() {
        LambdaQueryWrapper<AlarmReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AlarmReview::getCreateTime).last("LIMIT 1");
        AlarmReview r = alarmReviewMapper.selectOne(wrapper);
        if (r == null) return null;
        ReviewVO vo = new ReviewVO();
        vo.setId(r.getId());
        vo.setDeviceId(r.getDeviceId());
        vo.setAlarmLevel(r.getAlarmLevel());
        vo.setSmokeConcentration(r.getSmokeConcentration());
        vo.setImageBase64(ensureDataUri(r.getImageBase64()));
        vo.setCreateTime(r.getCreateTime());
        vo.setAiVerdict(r.getAiVerdict());
        vo.setAiBasis(r.getAiBasis());
        // JSON 字符串 -> 数组，前端直接遍历
        if (r.getAiDetections() != null) {
            vo.setAiDetections(JSON.parseArray(r.getAiDetections()));
        }
        if (r.getAiBoxes() != null) {
            vo.setAiBoxes(JSON.parseArray(r.getAiBoxes()));
        }
        // 补设备地址
        if (r.getDeviceId() != null) {
            Device d = deviceMapper.selectById(r.getDeviceId());
            if (d != null) {
                vo.setBuilding(d.getBuilding());
                vo.setFloor(d.getFloor());
                vo.setRoom(d.getRoom());
            }
        }
        return vo;
    }

    // 补全 data:image 前缀，保证前端 <img :src> 能直接显示（兼容裸 base64）
    private String ensureDataUri(String base64) {
        if (base64 == null || base64.isEmpty() || base64.startsWith("data:")) {
            return base64;
        }
        return "data:image/jpeg;base64," + base64;
    }
}
