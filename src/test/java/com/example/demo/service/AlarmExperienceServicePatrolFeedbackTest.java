package com.example.demo.service;

import com.example.demo.entity.Alarm;
import com.example.demo.entity.AlarmFeedback;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmFeedbackMapper;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 巡护员现场反馈服务测试。
 *
 * 覆盖：
 * - FOREST 事件在环境未恢复（recoverTime 为空）时即可反馈现场复核结果；
 * - 历史宿舍事件仍要求恢复后才能反馈（不回归）；
 * - 森林现场复核类型 FIRE_CONFIRMED / NO_ABNORMALITY / SMOKE_UNCERTAIN / OTHER
 *   均被接受；
 * - 非法反馈类型被拒绝。
 */
@ExtendWith(MockitoExtension.class)
public class AlarmExperienceServicePatrolFeedbackTest {

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private AlarmFeedbackMapper feedbackMapper;

    @Mock
    private SmokeRecordMapper smokeRecordMapper;

    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private AlarmExperienceService service;

    private Alarm alarm(String sceneType, LocalDateTime recoverTime) {
        Alarm alarm = new Alarm();
        alarm.setId(7L);
        alarm.setDeviceId(1002L);
        alarm.setSceneType(sceneType);
        alarm.setRecoverTime(recoverTime);
        return alarm;
    }

    private void stubForestAlarm() {
        Alarm alarm = alarm(AlarmService.SCENE_FOREST, null);
        when(alarmMapper.selectById(7L)).thenReturn(alarm);
        when(feedbackMapper.selectOne(any())).thenReturn(null);
        when(dataScopeService.getCurrentUser()).thenReturn(patrolUser());
    }

    private User patrolUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("patrol");
        user.setRole("resident");
        user.setZoneId(1L);
        return user;
    }

    @Test
    void forestEventAllowsFeedbackBeforeRecovery() {

        stubForestAlarm();

        service.saveFeedback(7L, "FIRE_CONFIRMED", "现场确认明火，已有烟柱");

        ArgumentCaptor<AlarmFeedback> captor =
                ArgumentCaptor.forClass(AlarmFeedback.class);
        verify(feedbackMapper).insert(captor.capture());

        assertEquals("FIRE_CONFIRMED", captor.getValue().getFeedbackType());
        assertEquals("现场确认明火，已有烟柱", captor.getValue().getFeedbackNote());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    void allForestReviewTypesAccepted() {

        List<String> types = List.of(
                "FIRE_CONFIRMED", "NO_ABNORMALITY", "SMOKE_UNCERTAIN", "OTHER");

        for (String type : types) {
            stubForestAlarm();
            service.saveFeedback(7L, type, null);
        }

        ArgumentCaptor<AlarmFeedback> captor =
                ArgumentCaptor.forClass(AlarmFeedback.class);
        verify(feedbackMapper, org.mockito.Mockito.times(4))
                .insert(captor.capture());

        List<String> stored = captor.getAllValues().stream()
                .map(AlarmFeedback::getFeedbackType)
                .collect(java.util.stream.Collectors.toList());

        assertTrue(stored.containsAll(types),
                "四种森林复核类型必须全部被接受，实际：" + stored);
    }

    @Test
    void dormEventRejectsFeedbackBeforeRecovery() {

        when(alarmMapper.selectById(7L))
                .thenReturn(alarm(AlarmService.SCENE_DORM_LEGACY, null));

        assertThrows(ResponseStatusException.class,
                () -> service.saveFeedback(7L, "FIRE_CONFIRMED", null));

        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    void recoveredDormEventStillAcceptsFeedback() {

        Alarm dorm = alarm(AlarmService.SCENE_DORM_LEGACY,
                LocalDateTime.now());
        when(alarmMapper.selectById(7L)).thenReturn(dorm);
        when(feedbackMapper.selectOne(any())).thenReturn(null);
        when(dataScopeService.getCurrentUser()).thenReturn(patrolUser());

        service.saveFeedback(7L, "COOKING", "做饭引起误报");

        ArgumentCaptor<AlarmFeedback> captor =
                ArgumentCaptor.forClass(AlarmFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertEquals("COOKING", captor.getValue().getFeedbackType());
    }

    @Test
    void invalidTypeRejected() {

        when(alarmMapper.selectById(7L))
                .thenReturn(alarm(AlarmService.SCENE_FOREST, null));

        assertThrows(ResponseStatusException.class,
                () -> service.saveFeedback(7L, "HACKER", null));

        verify(feedbackMapper, never()).insert(any());
    }
}
