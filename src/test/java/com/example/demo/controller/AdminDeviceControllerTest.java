package com.example.demo.controller;

import com.example.demo.entity.Device;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.EventPriorityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备位置唯一性测试。
 *
 * 关键约束：
 * - 一个寝室只能对应一台设备；
 * - 修改设备位置时，若目标位置已被其他设备占用必须拒绝，
 *   彻底阻止 REAL 与 DEMO 占用同一寝室。
 */
@ExtendWith(MockitoExtension.class)
public class AdminDeviceControllerTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SmokeRecordMapper smokeRecordMapper;

    @Mock
    private EventPriorityService priorityService;

    @InjectMocks
    private AdminDeviceController controller;

    private Device deviceAt(Integer building, Integer floor, Integer room) {
        Device device = new Device();
        device.setDeviceId(1L);
        device.setBuilding(building);
        device.setFloor(floor);
        device.setRoom(room);
        return device;
    }

    private Map<String, Object> positionBody(Integer building,
                                             Integer floor,
                                             Integer room) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", null);
        body.put("building", building);
        body.put("floor", floor);
        body.put("room", room);
        return body;
    }

    @Test
    void positionAlreadyOccupiedByAnotherDeviceRejected() {

        when(deviceMapper.selectById(1L))
                .thenReturn(deviceAt(1, 2, 3));
        // 另一台设备已占用 1栋2层3室
        when(deviceMapper.selectCount(any())).thenReturn(1L);

        ResponseStatusException e = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateDevice(1L, positionBody(1, 2, 3))
        );

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(e.getReason().contains("占用"));

        verify(deviceMapper, never()).updateById(any());
    }

    @Test
    void uniquePositionAccepted() {

        when(deviceMapper.selectById(1L))
                .thenReturn(deviceAt(1, 2, 3));
        when(deviceMapper.selectCount(any())).thenReturn(0L);
        when(deviceMapper.updateById(any())).thenReturn(1);

        Map<String, Object> result =
                controller.updateDevice(1L, positionBody(1, 2, 3));

        assertEquals(Boolean.TRUE, result.get("success"));
        verify(deviceMapper).updateById(any());
    }
}
