package com.example.demo.service;

import com.example.demo.entity.Alarm;
import com.example.demo.mapper.AlarmMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 告警人工处置流程。
 *
 * 状态机（严格顺序，禁止跳级）：
 * NEW → ACKNOWLEDGED → ON_SITE → RESOLVED → CLOSED
 *
 * 每个步骤只能从前一个状态迁移：
 * - 确认告警：NEW → ACKNOWLEDGED
 * - 标记到场：ACKNOWLEDGED → ON_SITE
 * - 填写处置结果：ON_SITE → RESOLVED
 * - 关闭事件：RESOLVED → CLOSED，且必须 recoverTime != null
 *
 * 非法跳转（如 ACKNOWLEDGED → RESOLVED、ON_SITE → CLOSED）
 * 一律返回 HTTP 400。
 *
 * 核心原则：
 * 人工处置状态（disposalState）与"环境是否恢复"（recoverTime）
 * 是两个完全独立的维度。
 *
 * - 管理员确认 / 到场 / 填写结果 / 关闭，
 *   只修改处置状态，绝不代替环境恢复判断。
 * - 环境是否恢复正常，仍然由系统根据烟雾浓度
 *   在 AlarmService 中自动写入 recoverTime。
 */
@Service
public class AlarmDisposalService {

    private static final Logger log =
            LoggerFactory.getLogger(AlarmDisposalService.class);

    /* ========== 处置状态 ========== */
    public static final String STATE_NEW = "NEW";
    public static final String STATE_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATE_ON_SITE = "ON_SITE";
    public static final String STATE_RESOLVED = "RESOLVED";
    public static final String STATE_CLOSED = "CLOSED";

    /* ========== 事件日志类型 ========== */
    public static final String EV_ALARM_CREATED = "ALARM_CREATED";
    public static final String EV_CONFIRMED = "CONFIRMED";
    public static final String EV_ON_SITE = "ON_SITE";
    public static final String EV_RESOLVED = "RESOLVED";
    public static final String EV_SMOKE_RECOVERED = "SMOKE_RECOVERED";
    public static final String EV_CLOSED = "CLOSED";

    private final AlarmMapper alarmMapper;
    private final AlarmEventLogService eventLogService;
    private final EcologicalFollowupService ecologicalFollowupService;

    /**
     * 兼容构造（测试场景）：不注入生态回访服务，
     * 森林闭环约束自动跳过。
     */
    public AlarmDisposalService(AlarmMapper alarmMapper,
                                AlarmEventLogService eventLogService) {
        this(alarmMapper, eventLogService, null);
    }

    @Autowired
    public AlarmDisposalService(AlarmMapper alarmMapper,
                                AlarmEventLogService eventLogService,
                                EcologicalFollowupService ecologicalFollowupService) {
        this.alarmMapper = alarmMapper;
        this.eventLogService = eventLogService;
        this.ecologicalFollowupService = ecologicalFollowupService;
    }


    /**
     * 确认告警。
     *
     * NEW → ACKNOWLEDGED。
     * 已确认 / 已到场 / 已填结果时幂等返回，不重复处理。
     * 已关闭的事件不允许再确认。
     */
    public Alarm confirm(Alarm alarm, String operator) {

        requireAlarm(alarm);

        String state = normalize(alarm);

        if (STATE_CLOSED.equals(state)) {
            throw badRequest("事件已关闭，不能再确认");
        }

        if (isConfirmed(alarm)) {

            /*
             * 兼容修复（"确认按钮点击后无变化"Bug）：
             *
             * 历史数据可能存在 acknowledged=1 但 disposal_state 为空。
             * 此时 isConfirmed() 会因 acknowledged=1 直接认为已确认并 return，
             * 导致 disposal_state 永远保持 null，
             * 前端每次刷新都把该告警归一化为 NEW 并重新显示确认按钮。
             *
             * 因此只要 acknowledged=1 且 disposalState 为空，
             * 就不允许直接 return：
             * 必须把 disposalState 补写为 ACKNOWLEDGED，
             * 并用 ackTime 回填 confirmedAt（必要时）后持久化，
             * 保证"确认"真正落库为 NEW → ACKNOWLEDGED。
             */
            boolean emptyDisposalState =
                    alarm.getDisposalState() == null
                            || alarm.getDisposalState().trim().isEmpty();

            if (Integer.valueOf(1).equals(alarm.getAcknowledged())
                    && emptyDisposalState) {

                alarm.setDisposalState(STATE_ACKNOWLEDGED);

                if (alarm.getConfirmedAt() == null) {
                    alarm.setConfirmedAt(
                            alarm.getAckTime() != null
                                    ? alarm.getAckTime()
                                    : LocalDateTime.now());
                }

                if (alarm.getHandledBy() == null
                        || alarm.getHandledBy().trim().isEmpty()) {
                    alarm.setHandledBy(operator);
                }

                persist(alarm);

                log.info(
                        "兼容回填：legacy acknowledged=1 告警补写 "
                                + "disposalState=ACKNOWLEDGED，alarmId={}，operator={}",
                        alarm.getId(),
                        operator
                );
            }

            return alarm;
        }

        LocalDateTime now = LocalDateTime.now();

        alarm.setAcknowledged(1);
        alarm.setAckTime(now);
        alarm.setConfirmedAt(now);
        alarm.setHandledBy(operator);
        alarm.setDisposalState(STATE_ACKNOWLEDGED);

        persist(alarm);

        eventLogService.record(
                alarm.getId(),
                alarm.getDeviceId(),
                EV_CONFIRMED,
                "管理员确认告警",
                "已确认并记录人工响应",
                operator
        );

        log.info("告警已确认：alarmId={}，operator={}", alarm.getId(), operator);

        return alarm;
    }


    /**
     * 标记到场，开始现场处理。
     *
     * 前置：ACKNOWLEDGED → ON_SITE。
     * 已到场的幂等返回。
     */
    public Alarm markOnSite(Alarm alarm, String operator) {

        requireAlarm(alarm);

        String state = normalize(alarm);

        if (STATE_CLOSED.equals(state)) {
            throw badRequest("事件已关闭，不能再标记到场");
        }

        if (STATE_ON_SITE.equals(state)) {
            return alarm;
        }

        if (!STATE_ACKNOWLEDGED.equals(state)) {
            throw badRequest("请先确认告警，再标记到场（当前状态：" + state + "）");
        }

        alarm.setOnSiteAt(LocalDateTime.now());
        alarm.setHandledBy(operator);
        alarm.setDisposalState(STATE_ON_SITE);

        persist(alarm);

        eventLogService.record(
                alarm.getId(),
                alarm.getDeviceId(),
                EV_ON_SITE,
                "开始现场处理",
                "管理员标记到场",
                operator
        );

        log.info("告警已标记到场：alarmId={}，operator={}", alarm.getId(), operator);

        return alarm;
    }


    /**
     * 填写处置结果。
     *
     * 严格状态机：仅允许 ON_SITE → RESOLVED。
     * 禁止 ACKNOWLEDGED → RESOLVED（未到场就填写结果）。
     * 备注为自由文本，仅记录人工描述，
     * 不自动推断烟雾来源（吸烟 / 电器 / 火灾等）。
     */
    public Alarm resolve(Alarm alarm, String operator, String remark) {

        requireAlarm(alarm);

        String state = normalize(alarm);

        if (STATE_CLOSED.equals(state)) {
            throw badRequest("事件已关闭，不能再填写处置结果");
        }

        if (STATE_NEW.equals(state)) {
            throw badRequest("请先确认告警，再填写处置结果（当前状态：" + state + "）");
        }

        if (STATE_ACKNOWLEDGED.equals(state)) {
            throw badRequest("请先标记到场，再填写处置结果（当前状态：已确认）");
        }

        if (STATE_RESOLVED.equals(state)) {
            return alarm;
        }

        String trimmed = remark == null ? null : remark.trim();

        alarm.setDisposalRemark(trimmed);
        alarm.setHandledBy(operator);
        alarm.setDisposalState(STATE_RESOLVED);

        persist(alarm);

        eventLogService.record(
                alarm.getId(),
                alarm.getDeviceId(),
                EV_RESOLVED,
                "填写处置结果",
                trimmed == null || trimmed.isEmpty()
                        ? "已填写处置结果"
                        : trimmed,
                operator
        );

        /*
         * 森林事件处置到 RESOLVED 且环境已恢复时，
         * 自动为事件点附近古树 / 栖息地创建生态回访任务。
         */
        if (ecologicalFollowupService != null) {
            ecologicalFollowupService.autoCreateForAlarm(alarm);
        }

        log.info("告警已填写处置结果：alarmId={}，remark={}", alarm.getId(), trimmed);

        return alarm;
    }


    /**
     * 关闭事件。
     *
     * 严格状态机：仅允许 RESOLVED → CLOSED。
     * 关闭必须同时满足两个条件：
     * 1. 处置状态为 RESOLVED（已填写处置结果）；
     * 2. 环境已恢复（recoverTime != null，烟雾已恢复）。
     *
     * 禁止 ACKNOWLEDGED → CLOSED、ON_SITE → CLOSED 等跳级操作；
     * 环境尚未恢复时返回明确提示。
     */
    public Alarm close(Alarm alarm, String operator, String remark) {

        requireAlarm(alarm);

        String state = normalize(alarm);

        if (STATE_CLOSED.equals(state)) {
            return alarm;
        }

        if (STATE_NEW.equals(state)) {
            throw badRequest("事件尚未处置，不能直接关闭（请先确认告警）");
        }

        if (STATE_ACKNOWLEDGED.equals(state)) {
            throw badRequest("尚未填写处置结果，不能关闭事件（请先标记到场并填写处置结果）");
        }

        if (STATE_ON_SITE.equals(state)) {
            throw badRequest("尚未填写处置结果，不能关闭事件（当前状态：现场处理中）");
        }

        /*
         * 到这里处置状态必为 RESOLVED。
         * 关闭前必须确认烟雾环境已恢复。
         */
        if (alarm.getRecoverTime() == null) {
            throw badRequest("环境尚未恢复，暂不能关闭事件");
        }

        /*
         * 森林事件闭环约束：
         * 存在未完成生态回访任务（古树 / 栖息地）时禁止关闭，
         * 保证生态影响评估闭环后才结束事件。
         */
        boolean forestEvent = AlarmService.SCENE_FOREST
                .equals(alarm.getSceneType());

        if (forestEvent
                && ecologicalFollowupService != null
                && ecologicalFollowupService.hasPendingFollowups(
                        alarm.getId())) {

            throw badRequest("存在未完成的生态回访任务，暂不能关闭事件");
        }

        String trimmed = remark == null ? null : remark.trim();

        if (trimmed != null && !trimmed.isEmpty()) {
            alarm.setDisposalRemark(trimmed);
        }

        alarm.setCloseAt(LocalDateTime.now());
        alarm.setHandledBy(operator);
        alarm.setDisposalState(STATE_CLOSED);

        persist(alarm);

        eventLogService.record(
                alarm.getId(),
                alarm.getDeviceId(),
                EV_CLOSED,
                "事件关闭",
                "管理员关闭安全事件",
                operator
        );

        log.info("安全事件已关闭：alarmId={}，operator={}", alarm.getId(), operator);

        return alarm;
    }


    /* ========== 内部工具 ========== */

    private void requireAlarm(Alarm alarm) {
        if (alarm == null || alarm.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "告警记录不存在"
            );
        }
    }

    private String normalize(Alarm alarm) {
        String state = alarm.getDisposalState();
        return state == null || state.trim().isEmpty()
                ? STATE_NEW
                : state.trim().toUpperCase();
    }

    private boolean isConfirmed(Alarm alarm) {
        String state = normalize(alarm);
        return Integer.valueOf(1).equals(alarm.getAcknowledged())
                || STATE_ACKNOWLEDGED.equals(state)
                || STATE_ON_SITE.equals(state)
                || STATE_RESOLVED.equals(state);
    }

    private void persist(Alarm alarm) {
        int rows = alarmMapper.updateById(alarm);
        if (rows != 1) {
            log.warn("告警处置状态更新异常：alarmId={}，rows={}", alarm.getId(), rows);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
