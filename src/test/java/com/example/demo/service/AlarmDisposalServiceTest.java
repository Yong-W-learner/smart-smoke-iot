package com.example.demo.service;

import com.example.demo.entity.Alarm;
import com.example.demo.mapper.AlarmMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 告警人工处置状态机测试。
 *
 * 关键约束：
 * - NEW → ACKNOWLEDGED → ON_SITE → RESOLVED → CLOSED
 * - 处置状态与 recoverTime（环境恢复）相互独立，
 *   管理员确认不能代替环境恢复判定。
 * - 非法跳转（如未确认直接到场、未处置直接关闭）必须被拒绝。
 * - 处置备注为自由文本，仅原样记录。
 */
@ExtendWith(MockitoExtension.class)
public class AlarmDisposalServiceTest {

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private AlarmEventLogService eventLogService;

    private AlarmDisposalService service;

    @BeforeEach
    void setUp() {
        service = new AlarmDisposalService(alarmMapper, eventLogService);
    }

    private Alarm newAlarm() {
        Alarm alarm = new Alarm();
        alarm.setId(7L);
        alarm.setDeviceId(101L);
        alarm.setAlarmTime(java.time.LocalDateTime.now());
        alarm.setAcknowledged(0);
        alarm.setDisposalState(AlarmDisposalService.STATE_NEW);
        return alarm;
    }

    /** 将告警推进到 RESOLVED（严格顺序：确认 → 到场 → 填结果）。 */
    private void runToResolved(Alarm alarm) {
        service.confirm(alarm, "admin01");
        service.markOnSite(alarm, "admin01");
        service.resolve(alarm, "admin01", "已完成现场排查");
    }

    /** 模拟系统自动判定烟雾环境已恢复。 */
    private void markEnvironmentRecovered(Alarm alarm) {
        alarm.setRecoverTime(java.time.LocalDateTime.now());
    }

    /* ---------- 确认 ---------- */

    @Test
    void confirmMovesNewToAcknowledged() {
        Alarm alarm = newAlarm();

        service.confirm(alarm, "admin01");

        assertEquals(AlarmDisposalService.STATE_ACKNOWLEDGED, alarm.getDisposalState());
        assertEquals(1, alarm.getAcknowledged());
        assertNotNull(alarm.getAckTime());
        assertNotNull(alarm.getConfirmedAt());
        assertEquals("admin01", alarm.getHandledBy());
        assertNull(alarm.getRecoverTime(), "确认不能代替环境恢复判定");

        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_CONFIRMED),
                anyString(), anyString(), eq("admin01"));
    }

    @Test
    void confirmIsIdempotentWhenAlreadyConfirmed() {
        Alarm alarm = newAlarm();
        service.confirm(alarm, "admin01");

        Alarm before = alarm;
        service.confirm(alarm, "admin02");

        assertEquals("admin01", before.getHandledBy(), "重复确认不应覆盖首次处理人");

        verify(eventLogService, times(1)).record(anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void confirmOnClosedEventRejected() {
        Alarm alarm = newAlarm();
        alarm.setDisposalState(AlarmDisposalService.STATE_CLOSED);

        assertThrows(ResponseStatusException.class,
                () -> service.confirm(alarm, "admin01"));
    }

    /* ---------- 兼容修复：legacy acknowledged=1 但 disposal_state 为空 ---------- */

    /**
     * "确认按钮点击后无变化"Bug 的回归测试。
     *
     * 历史数据存在 acknowledged=1 但 disposal_state 为空的告警，
     * 直接点确认不得再原样返回：
     * 必须把 disposalState 补写为 ACKNOWLEDGED 并持久化，
     * 让前端立即把确认按钮切换为"继续处置"。
     */
    @Test
    void legacyAcknowledgedWithoutDisposalStateBackfillsAcknowledged() {
        Alarm alarm = newAlarm();
        alarm.setAcknowledged(1);
        alarm.setDisposalState(null);

        service.confirm(alarm, "admin01");

        assertEquals(AlarmDisposalService.STATE_ACKNOWLEDGED,
                alarm.getDisposalState(),
                "legacy acknowledged=1 但处置状态为空时，确认必须补写 ACKNOWLEDGED");
        assertNotNull(alarm.getConfirmedAt(),
                "确认后必须回填 confirmedAt，否则前端仍判为未确认");

        verify(alarmMapper).updateById(alarm);
        verify(eventLogService, never()).record(
                anyLong(), anyLong(), anyString(),
                anyString(), anyString(), anyString());
    }

    /**
     * legacy 回填 confirmedAt 优先复用 ackTime：
     * 历史告警已有 ack_time 时，confirmedAt 直接取 ackTime，
     * 不产生新的时间戳。
     */
    @Test
    void legacyAcknowledgedBackfillsConfirmedAtFromAckTime() {
        Alarm alarm = newAlarm();
        alarm.setAcknowledged(1);
        alarm.setDisposalState(null);
        java.time.LocalDateTime ackTime = java.time.LocalDateTime.of(2026, 8, 1, 9, 30);
        alarm.setAckTime(ackTime);

        service.confirm(alarm, "admin01");

        assertEquals(ackTime, alarm.getConfirmedAt(),
                "有 ack_time 时 confirmedAt 应回填为 ackTime");
        assertEquals(AlarmDisposalService.STATE_ACKNOWLEDGED,
                alarm.getDisposalState());
    }

    /**
     * acknowledged=0 且 disposal_state 为空时走正常 NEW → ACKNOWLEDGED 流程：
     * 写入事件日志，不与兼容分支混淆。
     */
    @Test
    void newAlarmWithDisposalStateNullGoesToAcknowledged() {
        Alarm alarm = newAlarm();
        alarm.setAcknowledged(0);
        alarm.setDisposalState(null);

        service.confirm(alarm, "admin01");

        assertEquals(AlarmDisposalService.STATE_ACKNOWLEDGED, alarm.getDisposalState());
        assertEquals(1, alarm.getAcknowledged());

        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_CONFIRMED),
                anyString(), anyString(), eq("admin01"));
    }

    /* ---------- 到场 ---------- */

    @Test
    void onSiteRequiresAcknowledged() {
        Alarm alarm = newAlarm();

        assertThrows(ResponseStatusException.class,
                () -> service.markOnSite(alarm, "admin01"),
                "未确认的告警不能直接标记到场");

        assertNull(alarm.getOnSiteAt());
    }

    @Test
    void onSiteMovesAcknowledgedToOnSite() {
        Alarm alarm = newAlarm();
        service.confirm(alarm, "admin01");

        service.markOnSite(alarm, "admin01");

        assertEquals(AlarmDisposalService.STATE_ON_SITE, alarm.getDisposalState());
        assertNotNull(alarm.getOnSiteAt());

        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_ON_SITE),
                anyString(), anyString(), eq("admin01"));
    }

    /* ---------- 处置结果 ---------- */

    @Test
    void resolveRequiresProgressBeyondNew() {
        Alarm alarm = newAlarm();

        assertThrows(ResponseStatusException.class,
                () -> service.resolve(alarm, "admin01", "现场无明显烟雾"),
                "未确认的告警不能直接填写处置结果");
    }

    @Test
    void resolveStoresFreeTextRemark() {
        Alarm alarm = newAlarm();
        service.confirm(alarm, "admin01");
        service.markOnSite(alarm, "admin01");

        service.resolve(alarm, "admin01", "  现场无明显烟雾，已排查  ");

        assertEquals(AlarmDisposalService.STATE_RESOLVED, alarm.getDisposalState());
        assertEquals("现场无明显烟雾，已排查", alarm.getDisposalRemark(),
                "备注应去除首尾空白并原样保存");

        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_RESOLVED),
                anyString(), eq("现场无明显烟雾，已排查"), eq("admin01"));
    }

    /* ---------- 关闭 ---------- */

    @Test
    void closeRequiresDisposalProgress() {
        Alarm alarm = newAlarm();

        assertThrows(ResponseStatusException.class,
                () -> service.close(alarm, "admin01", null),
                "尚未处置的告警不能直接关闭");
    }

    @Test
    void closeMovesResolvedToClosed() {
        Alarm alarm = newAlarm();
        service.confirm(alarm, "admin01");
        service.markOnSite(alarm, "admin01");
        service.resolve(alarm, "admin01", "已完成现场排查");
        markEnvironmentRecovered(alarm);

        service.close(alarm, "admin01", "事件已关闭");

        assertEquals(AlarmDisposalService.STATE_CLOSED, alarm.getDisposalState());
        assertNotNull(alarm.getCloseAt());
        assertEquals("事件已关闭", alarm.getDisposalRemark());

        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_CLOSED),
                anyString(), anyString(), eq("admin01"));
    }

    /* ---------- 严格状态机：禁止跳级 ---------- */

    @Test
    void resolveRequiresOnSiteBeforeFillResult() {
        Alarm alarm = newAlarm();
        service.confirm(alarm, "admin01");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.resolve(alarm, "admin01", "现场无明显烟雾"),
                "已确认但未到场的告警不能直接填写处置结果");

        assertEquals("请先标记到场，再填写处置结果（当前状态：已确认）",
                e.getReason());
        assertEquals(AlarmDisposalService.STATE_ACKNOWLEDGED, alarm.getDisposalState());
    }

    @Test
    void closeRequiresResolvedState() {
        Alarm acked = newAlarm();
        service.confirm(acked, "admin01");
        assertThrows(ResponseStatusException.class,
                () -> service.close(acked, "admin01", null),
                "ACKNOWLEDGED 不能直接关闭（跳级）");

        Alarm onSite = newAlarm();
        service.confirm(onSite, "admin01");
        service.markOnSite(onSite, "admin01");
        assertThrows(ResponseStatusException.class,
                () -> service.close(onSite, "admin01", null),
                "ON_SITE 不能直接关闭（跳级）");
    }

    @Test
    void closeRequiresRecoveredEnvironment() {
        Alarm alarm = newAlarm();
        runToResolved(alarm); // RESOLVED 但烟雾环境尚未恢复

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.close(alarm, "admin01", null),
                "烟雾未恢复前禁止关闭");

        assertEquals("环境尚未恢复，暂不能关闭事件", e.getReason());
        assertEquals(AlarmDisposalService.STATE_RESOLVED, alarm.getDisposalState());
        assertNull(alarm.getCloseAt());
    }

    @Test
    void disposalCanContinueAfterEnvironmentRecovered() {
        Alarm alarm = newAlarm();
        markEnvironmentRecovered(alarm); // 环境已恢复，但人工处置尚未完成

        service.confirm(alarm, "admin01");
        service.markOnSite(alarm, "admin01");
        service.resolve(alarm, "admin01", "短时烟雾已消散");
        service.close(alarm, "admin01", "事件已关闭");

        assertEquals(AlarmDisposalService.STATE_CLOSED, alarm.getDisposalState(),
                "环境恢复不阻断人工处置，恢复后仍可继续处置并关闭");
        assertNotNull(alarm.getCloseAt());
    }

    /**
     * "环境已恢复 · 待完成处置"语义：
     * recoverTime 已有（环境维度已恢复）但 disposalState != CLOSED 时，
     * 事件不得被当作"已闭环"，必须保持处置流程打开。
     * 只有 disposalState=CLOSED 才代表真正闭环。
     */
    @Test
    void recoveredButNotClosedMustNotBeTreatedAsClosedLoop() {
        Alarm alarm = newAlarm();
        markEnvironmentRecovered(alarm); // 环境已恢复
        service.confirm(alarm, "admin01"); // 人工处置仍在进行

        assertNotNull(alarm.getRecoverTime());
        assertFalse(AlarmDisposalService.STATE_CLOSED
                        .equals(alarm.getDisposalState()),
                "环境已恢复但处置未关闭，不得显示为已闭环");
        assertNull(alarm.getCloseAt());

        // 继续处置并关闭后，才真正闭环。
        service.markOnSite(alarm, "admin01");
        service.resolve(alarm, "admin01", "短时烟雾已消散");
        service.close(alarm, "admin01", "事件已关闭");

        assertEquals(AlarmDisposalService.STATE_CLOSED, alarm.getDisposalState());
        assertNotNull(alarm.getCloseAt());
    }

    /* ---------- 独立性与完整闭环 ---------- */

    @Test
    void fullClosedLoopRecordsEveryStage() {
        Alarm alarm = newAlarm();

        service.confirm(alarm, "admin01");
        service.markOnSite(alarm, "admin01");
        service.resolve(alarm, "admin01", "已完成现场排查");
        markEnvironmentRecovered(alarm);
        service.close(alarm, "admin01", null);

        assertEquals(AlarmDisposalService.STATE_CLOSED, alarm.getDisposalState());

        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_CONFIRMED),
                anyString(), anyString(), eq("admin01"));
        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_ON_SITE),
                anyString(), anyString(), eq("admin01"));
        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_RESOLVED),
                anyString(), anyString(), eq("admin01"));
        verify(eventLogService).record(
                eq(7L), eq(101L), eq(AlarmDisposalService.EV_CLOSED),
                anyString(), anyString(), eq("admin01"));
    }

    @Test
    void recoveryTimeIsNeverTouchedByDisposal() {
        Alarm alarm = newAlarm();

        service.confirm(alarm, "admin01");
        service.markOnSite(alarm, "admin01");
        service.resolve(alarm, "admin01", "短时烟雾已消散");

        assertNull(alarm.getRecoverTime(),
                "人工处置全程不得写入 recoverTime");

        verify(alarmMapper, times(3)).updateById(alarm);
    }
}
