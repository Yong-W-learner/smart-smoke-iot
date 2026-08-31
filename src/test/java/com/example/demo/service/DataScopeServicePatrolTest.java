package com.example.demo.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.demo.entity.Device;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataScopeService 巡护员分区数据权限测试。
 *
 * 巡护员内部复用 resident 角色，以 sys_user.zone_id 表示巡护分区：
 * - resident + zone_id → 识别为巡护员（isPatrol），设备查询按 zone_id 过滤；
 * - resident 未绑定 zone_id → 普通居民（房间/user_id 语义），不做分区过滤；
 * - admin 全量。
 *
 * 覆盖：
 * - 巡护员识别（isPatrol / isAdmin）；
 * - 巡护员 getVisibleDevices 只查询本人绑定的分区（zone_id 过滤）；
 * - 未绑定分区的 resident 不被当作巡护员（不做 zone 过滤，不抛异常）；
 * - canAccessDevice：同分区设备可访问、异分区设备拒绝、admin 全量；
 * - resident 房间数据权限保持不回归。
 */
@ExtendWith(MockitoExtension.class)
public class DataScopeServicePatrolTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @InjectMocks
    private DataScopeService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, Device.class);
    }

    private User user(String role, Long zoneId) {
        User user = new User();
        user.setId(1L);
        user.setUsername("patrol");
        user.setRole(role);
        user.setZoneId(zoneId);
        return user;
    }

    private Device node(Long deviceId, Long zoneId) {
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setZoneId(zoneId);
        return device;
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username, "pw", java.util.Collections.emptyList()));
    }

    @Test
    void patrolRoleDetected() {

        // resident + zone_id → 巡护员；未绑定分区 → 普通居民
        assertTrue(service.isPatrol(user("resident", 1L)),
                "resident+zone_id 必须识别为巡护员");
        assertFalse(service.isPatrol(user("resident", null)),
                "resident 未绑定分区不是巡护员");
        assertFalse(service.isPatrol(user("admin", null)));
        assertTrue(service.isAdmin(user("admin", null)));
        assertFalse(service.isAdmin(user("resident", 1L)));
    }

    @Test
    void patrolVisibleDevicesFilteredByZone() {

        loginAs("patrol");
        User patrol = user("resident", 1L);
        when(userMapper.selectOne(any())).thenReturn(patrol);

        List<Device> nodes = List.of(node(1002L, 1L), node(1003L, 1L));
        when(deviceMapper.selectList(any())).thenReturn(nodes);

        List<Device> visible = service.getVisibleDevices();

        assertEquals(2, visible.size());

        ArgumentCaptor<LambdaQueryWrapper<Device>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deviceMapper).selectList(captor.capture());

        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("zone_id"),
                "巡护员设备查询必须按 zone_id 过滤，实际：" + sql);
    }

    @Test
    void residentWithoutZoneNotTreatedAsPatrol() {

        loginAs("resident01");
        User resident = user("resident", null);
        when(userMapper.selectOne(any())).thenReturn(resident);

        when(deviceMapper.selectList(any())).thenReturn(List.of());

        // 未绑定分区的 resident 按普通居民处理：不抛异常，也不做分区过滤
        List<Device> visible = service.getVisibleDevices();

        assertEquals(0, visible.size());

        ArgumentCaptor<LambdaQueryWrapper<Device>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deviceMapper).selectList(captor.capture());

        String sql = captor.getValue().getSqlSegment();
        assertFalse(sql.contains("zone_id"),
                "普通居民设备查询不得包含 zone_id 过滤，实际：" + sql);
    }

    @Test
    void patrolCanAccessOnlyOwnZoneDevice() {

        loginAs("patrol");
        when(userMapper.selectOne(any())).thenReturn(user("resident", 1L));

        when(deviceMapper.selectById(1002L)).thenReturn(node(1002L, 1L));
        when(deviceMapper.selectById(1026L)).thenReturn(node(1026L, 2L));

        assertTrue(service.canAccessDevice(1002L),
                "同分区设备应可访问");
        assertFalse(service.canAccessDevice(1026L),
                "异分区设备必须拒绝");
    }

    @Test
    void adminCanAccessAnyExistingDevice() {

        loginAs("admin");
        when(userMapper.selectOne(any())).thenReturn(user("admin", null));

        when(deviceMapper.selectById(1090L)).thenReturn(node(1090L, 4L));

        assertTrue(service.canAccessDevice(1090L),
                "admin 可访问全部设备");
    }

    @Test
    void residentRoomScopePreserved() {

        User resident = user("resident", null);
        resident.setId(1L);
        resident.setBuilding(1);
        resident.setFloor(1);
        resident.setRoom(101);

        loginAs("resident01");
        when(userMapper.selectOne(any())).thenReturn(resident);

        when(deviceMapper.selectById(1001L))
                .thenReturn(nodeWithRoom(1001L, 1, 1, 101));
        when(deviceMapper.selectById(1083L))
                .thenReturn(nodeWithRoom(1083L, 3, 5, 503));

        assertTrue(service.canAccessDevice(1001L),
                "resident 可访问自己房间设备");
        assertFalse(service.canAccessDevice(1083L),
                "resident 不可访问他人房间设备");
    }

    private Device nodeWithRoom(Long deviceId,
                                int building, int floor, int room) {
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setBuilding(building);
        device.setFloor(floor);
        device.setRoom(room);
        return device;
    }
}
