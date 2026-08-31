package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.dto.AlarmActionDTO;
import com.example.demo.dto.AlarmHandleDTO;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.vo.AlarmHistoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.demo.vo.Result;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class AlarmController {
    @Autowired
    private AlarmMapper alarmMapper;

    @Autowired
    private SmokeRecordMapper smokeRecordMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private UserMapper userMapper;

    //TODO JWT完成后，过滤当前登录居民的设备告警
    @GetMapping("/alarm/list")
    public List<Alarm> alarmList(){
        return alarmMapper.selectList(null);
    }

    // 管理员：警情事件处置列表。status 可选：0待处置、1已处置。
    @GetMapping("/alarm/incidents")
    public List<Alarm> alarmIncidents(@RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Alarm> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(Alarm::getStatus, status);
        }
        w.orderByDesc(Alarm::getAlarmTime);
        return alarmMapper.selectList(w);
    }

    // 管理员：确认警情并将任务送入应急消防员任务池。
    @PostMapping({"/alarm/incidents/{id}/confirm", "/alarm/incidents/{id}/respond"})
    public Result<Alarm> confirmIncident(@PathVariable Long id, @RequestBody AlarmActionDTO dto) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) return Result.fail("警情事件不存在");
        User operator = validateRole(dto == null ? null : dto.getOperatorId(), "admin");
        if (operator == null) return Result.fail("仅管理员可以确认警情");
        if (Integer.valueOf(1).equals(alarm.getStatus())) return Result.fail("警情事件已完成处置");
        if (!"pending".equals(alarm.getProcessStatus()) && alarm.getProcessStatus() != null) {
            return Result.fail("警情已经确认，请等待应急消防人员处置");
        }

        LocalDateTime now = LocalDateTime.now();
        alarm.setConfirmerId(operator.getId());
        alarm.setConfirmerName(operator.getUsername());
        alarm.setConfirmTime(now);
        alarm.setResponseTime(now);
        alarm.setProcessStatus("confirmed");
        alarmMapper.updateById(alarm);
        return Result.ok(alarm);
    }

    // 应急消防员：本人到达现场后确认到场。
    @PostMapping("/alarm/incidents/{id}/arrive")
    public Result<Alarm> arriveIncident(@PathVariable Long id, @RequestBody AlarmActionDTO dto) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) return Result.fail("警情事件不存在");
        User operator = validateRole(dto == null ? null : dto.getOperatorId(), "responder");
        if (operator == null) return Result.fail("仅应急消防人员可以确认到场");
        if (Integer.valueOf(1).equals(alarm.getStatus())) return Result.fail("警情事件已完成处置");
        if (!"confirmed".equals(alarm.getProcessStatus())) return Result.fail("请等待管理员确认警情");

        alarm.setArrivalTime(LocalDateTime.now());
        alarm.setHandlerId(operator.getId());
        alarm.setHandlerName(operator.getUsername());
        alarm.setProcessStatus("arrived");
        alarmMapper.updateById(alarm);
        return Result.ok(alarm);
    }

    // 应急消防员：到场处置后填写现场结论并上报。
    @PostMapping("/alarm/incidents/{id}/handle")
    public Result<Alarm> handleIncident(@PathVariable Long id,
                                        @RequestBody(required = false) AlarmHandleDTO dto) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            return Result.fail("警情事件不存在");
        }
        if (Integer.valueOf(1).equals(alarm.getStatus())) {
            return Result.ok(alarm);
        }
        if (dto == null || dto.getOperatorId() == null) return Result.fail("处置人不能为空");
        User operator = validateRole(dto.getOperatorId(), "responder");
        if (operator == null) return Result.fail("仅应急消防人员可以上报处置结果");
        if (!"arrived".equals(alarm.getProcessStatus())) return Result.fail("请先由应急消防人员确认到场");
        if (dto.getHandleResult() == null || dto.getHandleResult().trim().isEmpty()) {
            return Result.fail("请选择现场处置结果");
        }

        LocalDateTime now = LocalDateTime.now();
        alarm.setStatus(1);
        alarm.setProcessStatus("handled");
        alarm.setHandlerId(operator.getId());
        alarm.setHandlerName(operator.getUsername());
        alarm.setHandleTime(now);
        alarm.setHandleResult(dto.getHandleResult().trim());
        alarm.setHandleRemark(dto.getHandleRemark());
        alarmMapper.updateById(alarm);
        return Result.ok(alarm);
    }

    private User validateRole(Long operatorId, String role) {
        if (operatorId == null) return null;
        User operator = userMapper.selectById(operatorId);
        return operator != null && role.equals(operator.getRole()) ? operator : null;
    }

    // 管理员：查看所有历史告警事件（把连续的告警采集点聚合为一次事件）
    @GetMapping("/alarm/history")
    public List<AlarmHistoryVO> alarmHistory() {
        LambdaQueryWrapper<SmokeRecord> w = new LambdaQueryWrapper<>();
        w.orderByAsc(SmokeRecord::getDeviceId).orderByAsc(SmokeRecord::getCollectTime);
        List<SmokeRecord> records = smokeRecordMapper.selectList(w);

        List<AlarmHistoryVO> events = new ArrayList<>();
        AlarmHistoryVO cur = null;

        for (SmokeRecord r : records) {
            boolean alarming = r.getAlarm() != null && r.getAlarm() > 0;
            if (!alarming) {
                cur = null;
                continue;
            }
            // 同一设备，且与上一条告警记录间隔 < 5 分钟 → 并入当前事件
            boolean sameEvent = cur != null
                    && cur.getDeviceId().equals(r.getDeviceId())
                    && Duration.between(cur.getEndTime(), r.getCollectTime()).toMinutes() < 5;

            if (!sameEvent) {
                cur = new AlarmHistoryVO();
                cur.setDeviceId(r.getDeviceId());
                cur.setStartTime(r.getCollectTime());
                cur.setEndTime(r.getCollectTime());
                cur.setPeakConcentration(r.getSmokeConcentration());
                cur.setMaxLevel(r.getAlarm());
                cur.setRecordCount(1);
                Device d = deviceMapper.selectById(r.getDeviceId());
                if (d != null) {
                    cur.setBuilding(d.getBuilding());
                    cur.setFloor(d.getFloor());
                    cur.setRoom(d.getRoom());
                }
                events.add(cur);
            } else {
                cur.setEndTime(r.getCollectTime());
                if (r.getSmokeConcentration() != null
                        && (cur.getPeakConcentration() == null
                            || r.getSmokeConcentration() > cur.getPeakConcentration())) {
                    cur.setPeakConcentration(r.getSmokeConcentration());
                }
                if (r.getAlarm() > cur.getMaxLevel()) {
                    cur.setMaxLevel(r.getAlarm());
                }
                cur.setRecordCount(cur.getRecordCount() + 1);
            }
        }
        // 最新告警在前
        Collections.reverse(events);
        return events;
    }

    // 某次告警的详情：时间段内浓度变化 + 同时段报警的其他设备
    @GetMapping("/alarm/detail")
    public Map<String, Object> alarmDetail(
            @RequestParam Long deviceId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        Map<String, Object> result = new HashMap<>();

        // 告警前后各扩 3 分钟，展示浓度变化趋势
        LocalDateTime from = startTime.minusMinutes(3);
        LocalDateTime to = endTime.plusMinutes(3);
        LambdaQueryWrapper<SmokeRecord> w = new LambdaQueryWrapper<>();
        w.eq(SmokeRecord::getDeviceId, deviceId)
         .ge(SmokeRecord::getCollectTime, from)
         .le(SmokeRecord::getCollectTime, to)
         .orderByAsc(SmokeRecord::getCollectTime);
        result.put("points", smokeRecordMapper.selectList(w));

        // 同时段报警的其他设备（去重）
        LambdaQueryWrapper<SmokeRecord> w2 = new LambdaQueryWrapper<>();
        w2.ge(SmokeRecord::getCollectTime, startTime)
          .le(SmokeRecord::getCollectTime, endTime)
          .gt(SmokeRecord::getAlarm, 0);
        Set<Long> ids = new LinkedHashSet<>();
        for (SmokeRecord s : smokeRecordMapper.selectList(w2)) {
            if (!s.getDeviceId().equals(deviceId)) {
                ids.add(s.getDeviceId());
            }
        }
        List<Map<String, Object>> devices = new ArrayList<>();
        for (Long id : ids) {
            Device d = deviceMapper.selectById(id);
            Map<String, Object> m = new HashMap<>();
            m.put("deviceId", id);
            m.put("building", d != null ? d.getBuilding() : null);
            m.put("floor", d != null ? d.getFloor() : null);
            m.put("room", d != null ? d.getRoom() : null);
            devices.add(m);
        }
        result.put("simultaneousDevices", devices);

        // 预留：日后接入 AI 模型，对本次告警做智能分析并给出建议
        result.put("aiSuggestion", "待接入 AI 模型后，此处展示智能分析建议");
        return result;
    }

    // 告警统计分析：等级分布（饼图）、近7天趋势（柱状图）、各设备告警次数（柱状图）
    // 无真实告警数据时返回演示数据（demo=true），便于仿真展示
    @GetMapping("/alarm/stats")
    public Map<String, Object> alarmStats() {
        Map<String, Object> result = new LinkedHashMap<>();

        LambdaQueryWrapper<SmokeRecord> w = new LambdaQueryWrapper<>();
        w.gt(SmokeRecord::getAlarm, 0);
        List<SmokeRecord> alarms = smokeRecordMapper.selectList(w);

        int level1 = 0, level2 = 0, level3 = 0;
        Map<Long, Integer> deviceCount = new HashMap<>();
        Map<String, Integer> dayCount = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            dayCount.put(today.minusDays(i).toString().substring(5), 0); // MM-dd
        }

        for (SmokeRecord r : alarms) {
            int a = r.getAlarm();
            if (a == 1) level1++;
            else if (a == 2) level2++;
            else level3++;
            deviceCount.merge(r.getDeviceId(), 1, Integer::sum);
            if (r.getCollectTime() != null) {
                String key = r.getCollectTime().toLocalDate().toString().substring(5);
                if (dayCount.containsKey(key)) {
                    dayCount.merge(key, 1, Integer::sum);
                }
            }
        }
        int total = level1 + level2 + level3;

        // 无真实告警数据 → 返回仿真演示数据
        if (total == 0) {
            result.put("demo", true);
            result.put("totalAlarms", 25);
            result.put("levelDist", demoLevelDist());
            result.put("trend7d", demoTrend(today));
            result.put("deviceDist", demoDeviceDist());
            return result;
        }

        List<Map<String, Object>> levelDist = new ArrayList<>();
        levelDist.add(item("三级警情", level3));
        levelDist.add(item("二级警情", level2));
        levelDist.add(item("一级警情", level1));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, Integer> e : dayCount.entrySet()) {
            trend.add(item(e.getKey(), e.getValue()));
        }

        List<Map<String, Object>> deviceDist = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : deviceCount.entrySet()) {
            Device d = deviceMapper.selectById(e.getKey());
            String label = d != null ? (d.getBuilding() + "栋" + d.getFloor() + "层" + d.getRoom() + "户")
                    : ("设备#" + e.getKey());
            deviceDist.add(item(label, e.getValue()));
        }

        result.put("demo", false);
        result.put("totalAlarms", total);
        result.put("levelDist", levelDist);
        result.put("trend7d", trend);
        result.put("deviceDist", deviceDist);
        return result;
    }

    private Map<String, Object> item(String name, int value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private List<Map<String, Object>> demoLevelDist() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(item("三级警情", 3));
        list.add(item("二级警情", 8));
        list.add(item("一级警情", 14));
        return list;
    }

    private List<Map<String, Object>> demoTrend(LocalDate today) {
        int[] counts = {2, 1, 5, 0, 4, 3, 2};
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            list.add(item(today.minusDays(i).toString().substring(5), counts[6 - i]));
        }
        return list;
    }

    private List<Map<String, Object>> demoDeviceDist() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(item("3栋8层2户", 12));
        list.add(item("5栋3层1户", 7));
        list.add(item("2栋1层6户", 6));
        return list;
    }
}
