package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.DeviceDTO;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.vo.DeviceVO;
import com.example.demo.vo.Result;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceRequest;
import com.huaweicloud.sdk.iotda.v5.model.ShowDeviceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DeviceController {
    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private SmokeRecordMapper smokeRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IoTDAClient ioTDAClient;

    @Value("${huawei.iot.deviceId}")
    private String huaweiDeviceId;

    // 查看全部传感器：设备信息 + 最新烟雾 + 归属居民
    @GetMapping("/device/list")
    public List<DeviceVO> getDeviceList(){
        List<Device> devices = deviceMapper.selectList(null);
        // 华为云唯一真机的真实在线状态（采集任务写死 device_id=1）
        boolean realOnline = queryRealOnline();
        List<DeviceVO> result = new ArrayList<>();
        for (Device d : devices) {
            DeviceVO vo = new DeviceVO();
            vo.setDeviceId(d.getDeviceId());
            vo.setBuilding(d.getBuilding());
            vo.setFloor(d.getFloor());
            vo.setRoom(d.getRoom());

            // 在线状态：device_id=1 是真机，用华为云实时状态；其余设备用本地 status
            boolean online = (d.getDeviceId() != null && d.getDeviceId() == 1L)
                    ? realOnline
                    : (d.getStatus() != null && d.getStatus() == 1);
            vo.setStatus(online ? 1 : 0);
            vo.setOnline(online);
            vo.setSimulated(d.getDeviceId() == null || d.getDeviceId() != 1L);

            // 最新一条烟雾记录
            LambdaQueryWrapper<SmokeRecord> w = new LambdaQueryWrapper<>();
            w.eq(SmokeRecord::getDeviceId, d.getDeviceId());
            w.orderByDesc(SmokeRecord::getCollectTime).last("LIMIT 1");
            SmokeRecord latest = smokeRecordMapper.selectOne(w);
            if (latest != null) {
                vo.setSmokeConcentration(latest.getSmokeConcentration());
                vo.setAlarm(latest.getAlarm());
            }

            // 归属居民：同位置（栋/层/户）的所有居民，一户多账号共用一台设备
            LambdaQueryWrapper<User> uw = new LambdaQueryWrapper<>();
            uw.eq(User::getBuilding, d.getBuilding())
              .eq(User::getFloor, d.getFloor())
              .eq(User::getRoom, d.getRoom())
              .eq(User::getRole, "resident");
            List<User> owners = userMapper.selectList(uw);
            if (!owners.isEmpty()) {
                List<String> names = new ArrayList<>();
                List<String> phones = new ArrayList<>();
                for (User o : owners) {
                    names.add(o.getUsername());
                    if (o.getPhone() != null) phones.add(o.getPhone());
                }
                vo.setUserName(String.join("、", names));
                vo.setPhone(String.join("、", phones));
            }
            result.add(vo);
        }
        return result;
    }

    // 居民端：查当前住户绑定的传感器（含位置、在线状态、最新烟雾）
    @GetMapping("/device/mine")
    public DeviceVO mine(@RequestParam Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) return null;
        // 按住户位置找本户设备：一户多账号共用同一台设备
        LambdaQueryWrapper<Device> w = new LambdaQueryWrapper<>();
        w.eq(Device::getBuilding, u.getBuilding())
         .eq(Device::getFloor, u.getFloor())
         .eq(Device::getRoom, u.getRoom())
         .last("LIMIT 1");
        Device d = deviceMapper.selectOne(w);
        if (d == null) return null;

        DeviceVO vo = new DeviceVO();
        vo.setDeviceId(d.getDeviceId());
        vo.setBuilding(d.getBuilding());
        vo.setFloor(d.getFloor());
        vo.setRoom(d.getRoom());

        boolean online = (d.getDeviceId() != null && d.getDeviceId() == 1L)
                ? queryRealOnline()
                : (d.getStatus() != null && d.getStatus() == 1);
        vo.setStatus(online ? 1 : 0);
        vo.setOnline(online);
        vo.setSimulated(d.getDeviceId() == null || d.getDeviceId() != 1L);

        LambdaQueryWrapper<SmokeRecord> sw = new LambdaQueryWrapper<>();
        sw.eq(SmokeRecord::getDeviceId, d.getDeviceId())
          .orderByDesc(SmokeRecord::getCollectTime).last("LIMIT 1");
        SmokeRecord latest = smokeRecordMapper.selectOne(sw);
        if (latest != null) {
            vo.setSmokeConcentration(latest.getSmokeConcentration());
            vo.setAlarm(latest.getAlarm());
            vo.setCollectTime(latest.getCollectTime());
        }
        return vo;
    }

    // 查询华为云真实在线状态（唯一真机 smoke001）
    private boolean queryRealOnline() {
        try {
            ShowDeviceRequest request = new ShowDeviceRequest();
            request.setDeviceId(huaweiDeviceId);
            ShowDeviceResponse resp = ioTDAClient.showDevice(request);
            return "ONLINE".equals(resp.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    // 新增传感器：写入 device 表，按手机号关联居民
    @PostMapping("/device/add")
    public Result<String> addDevice(@RequestBody DeviceDTO dto){
        if (dto.getDeviceId() == null || dto.getBuilding() == null
                || dto.getFloor() == null || dto.getRoom() == null) {
            return Result.fail("设备编号和位置（栋/层/户）不能为空");
        }
        Device exist = deviceMapper.selectById(dto.getDeviceId());
        if (exist != null) {
            return Result.fail("设备编号已存在");
        }

        // 按手机号找居民（选填，填写则必须为11位数字）
        Long userId = null;
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            if (!dto.getPhone().matches("\\d{11}")) {
                return Result.fail("居民手机号需为11位数字");
            }
            LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
            w.eq(User::getPhone, dto.getPhone());
            User user = userMapper.selectOne(w);
            userId = user != null ? user.getId() : null;
        }

        Device device = new Device();
        device.setDeviceId(dto.getDeviceId());
        device.setBuilding(dto.getBuilding());
        device.setFloor(dto.getFloor());
        device.setRoom(dto.getRoom());
        device.setStatus(0); // 新设备初始离线
        device.setUserId(userId);
        deviceMapper.insert(device);
        return Result.ok("新增设备成功");
    }

    // 删除传感器：同时清理其烟雾采集记录
    @DeleteMapping("/device/{deviceId}")
    public Result<String> deleteDevice(@PathVariable Long deviceId) {
        Device exist = deviceMapper.selectById(deviceId);
        if (exist == null) {
            return Result.fail("设备不存在");
        }
        LambdaQueryWrapper<SmokeRecord> w = new LambdaQueryWrapper<>();
        w.eq(SmokeRecord::getDeviceId, deviceId);
        smokeRecordMapper.delete(w);
        deviceMapper.deleteById(deviceId);
        return Result.ok("删除成功");
    }
}
