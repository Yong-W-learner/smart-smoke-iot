package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.AncientTree;
import com.example.demo.entity.Device;
import com.example.demo.entity.EcologicalFollowup;
import com.example.demo.entity.WildlifeHabitat;
import com.example.demo.mapper.AncientTreeMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.EcologicalFollowupMapper;
import com.example.demo.mapper.WildlifeHabitatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生态回访任务服务测试。
 *
 * 覆盖：
 * - 仅对 FOREST 且环境已恢复的事件自动创建回访任务；
 * - 事件点 1km 内的古树 / 栖息地各生成一条回访任务（幂等）；
 * - 非森林 / 未恢复事件不创建；
 * - start / complete 状态迁移；
 * - hasPendingFollowups 判定。
 */
public class EcologicalFollowupServiceTest {

    private EcologicalFollowupMapper followupMapper;
    private AncientTreeMapper ancientTreeMapper;
    private WildlifeHabitatMapper habitatMapper;
    private DeviceMapper deviceMapper;
    private EcologicalFollowupService service;

    @BeforeEach
    void setUp() {
        followupMapper = mock(EcologicalFollowupMapper.class);
        ancientTreeMapper = mock(AncientTreeMapper.class);
        habitatMapper = mock(WildlifeHabitatMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        service = new EcologicalFollowupService(
                followupMapper, ancientTreeMapper, habitatMapper, deviceMapper);
    }

    private Alarm forestAlarmRecovered() {
        Alarm alarm = new Alarm();
        alarm.setId(7L);
        alarm.setDeviceId(1001L);
        alarm.setSceneType(AlarmService.SCENE_FOREST);
        alarm.setZoneId(1L);
        alarm.setRecoverTime(LocalDateTime.now());
        return alarm;
    }

    private Device deviceAt(double lat, double lng) {
        Device device = new Device();
        device.setDeviceId(1001L);
        device.setLatitude(new BigDecimal(lat));
        device.setLongitude(new BigDecimal(lng));
        return device;
    }

    private AncientTree treeAt(String code, double lat, double lng) {
        AncientTree tree = new AncientTree();
        tree.setId(10L);
        tree.setTreeCode(code);
        tree.setTreeName("北部古松");
        tree.setLatitude(new BigDecimal(lat));
        tree.setLongitude(new BigDecimal(lng));
        return tree;
    }

    private WildlifeHabitat habitatAt(String code, double lat, double lng) {
        WildlifeHabitat habitat = new WildlifeHabitat();
        habitat.setId(20L);
        habitat.setHabitatCode(code);
        habitat.setHabitatName("北部林冠鸟类栖息地");
        habitat.setLatitude(new BigDecimal(lat));
        habitat.setLongitude(new BigDecimal(lng));
        return habitat;
    }

    @Test
    void autoCreateCreatesFollowupsForNearbyResources() {

        Alarm alarm = forestAlarmRecovered();
        when(deviceMapper.selectById(1001L)).thenReturn(deviceAt(28.181, 113.100));

        // 树距事件点约 111m < 1km；栖息地相距较远（>1km）
        when(ancientTreeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(treeAt("AT-001", 28.180, 113.100)));
        when(habitatMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(habitatAt("WH-001", 28.220, 113.160)));

        // 尚无重复回访任务
        when(followupMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.autoCreateForAlarm(alarm);

        verify(followupMapper, org.mockito.Mockito.times(1)).insert(
                org.mockito.ArgumentMatchers.argThat(f ->
                        f.getAssetType().equals(EcologicalFollowupService.ASSET_ANCIENT_TREE)
                                && f.getAssetCode().equals("AT-001")));
        verify(followupMapper, org.mockito.Mockito.never()).insert(
                org.mockito.ArgumentMatchers.argThat(f ->
                        f.getAssetType().equals(EcologicalFollowupService.ASSET_WILDLIFE_HABITAT)));
    }

    @Test
    void autoCreateSkipsNonForestOrNotRecovered() {

        Alarm dorm = new Alarm();
        dorm.setId(8L);
        dorm.setDeviceId(1001L);
        dorm.setSceneType(AlarmService.SCENE_DORM_LEGACY);
        dorm.setRecoverTime(LocalDateTime.now());

        service.autoCreateForAlarm(dorm);
        verify(followupMapper, never()).insert(any());

        Alarm forestNotRecovered = forestAlarmRecovered();
        forestNotRecovered.setRecoverTime(null);
        service.autoCreateForAlarm(forestNotRecovered);
        verify(followupMapper, never()).insert(any());
    }

    @Test
    void autoCreateIsIdempotentOnExistingFollowup() {

        Alarm alarm = forestAlarmRecovered();
        when(deviceMapper.selectById(1001L)).thenReturn(deviceAt(28.180, 113.100));
        when(ancientTreeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(treeAt("AT-001", 28.180, 113.100)));
        when(habitatMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        // 已存在该回访任务
        when(followupMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        service.autoCreateForAlarm(alarm);

        verify(followupMapper, never()).insert(any());
    }

    @Test
    void completeMovesPendingToCompleted() {

        EcologicalFollowup followup = new EcologicalFollowup();
        followup.setId(1L);
        followup.setState(EcologicalFollowupService.STATE_PENDING);
        when(followupMapper.selectById(1L)).thenReturn(followup);

        EcologicalFollowup result =
                service.complete(1L, "王巡护", "古树状态良好，无烧伤痕迹");

        assertEquals(EcologicalFollowupService.STATE_COMPLETED, result.getState());
        assertEquals("王巡护", result.getHandler());
        assertEquals("古树状态良好，无烧伤痕迹", result.getFollowupNote());
        assertTrue(result.getCompletedTime() != null);
        verify(followupMapper).updateById(followup);
    }

    @Test
    void startMovesPendingToInProgress() {

        EcologicalFollowup followup = new EcologicalFollowup();
        followup.setId(1L);
        followup.setState(EcologicalFollowupService.STATE_PENDING);
        when(followupMapper.selectById(1L)).thenReturn(followup);

        EcologicalFollowup result = service.start(1L, "王巡护");

        assertEquals(EcologicalFollowupService.STATE_IN_PROGRESS, result.getState());
        assertEquals("王巡护", result.getHandler());
    }

    @Test
    void hasPendingFollowupsDetectsOpenTasks() {

        when(followupMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertTrue(service.hasPendingFollowups(7L));

        when(followupMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertFalse(service.hasPendingFollowups(8L));

        assertFalse(service.hasPendingFollowups(null));
    }
}
