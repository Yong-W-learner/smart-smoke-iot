package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户对安全事件的事后反馈。
 *
 * 该表用于把“误报抑制”从算法概念延伸为用户可参与的闭环体验：
 * 用户可以在事件结束后标记这次异常到底是真实烟雾、烹饪、蒸汽等。
 */
@Data
@TableName("alarm_feedback")
public class AlarmFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alarmId;

    private Long userId;

    /**
     * REAL_SMOKE / COOKING / SMOKING / STEAM / FALSE_ALARM / UNKNOWN
     */
    private String feedbackType;

    private String feedbackNote;

    private LocalDateTime feedbackTime;
}
