package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.AcceptWorkOrderDTO;
import com.example.demo.dto.CloseWorkOrderDTO;
import com.example.demo.dto.CreateWorkOrderDTO;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkOrder;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.WorkOrderMapper;
import com.example.demo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备运维接口：居民报修、维修员接单/确认关闭、管理员查看/兜底关闭。
 * 警情事件由 AlarmController 独立管理，不再混入维修工单。
 */
@RestController
@RequestMapping("/api/work-order")
public class WorkOrderController {

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private UserMapper userMapper;

    // 居民报修：创建工单
    @PostMapping("/create")
    public Result<String> create(@RequestBody CreateWorkOrderDTO dto) {
        if (dto.getReporterId() == null) {
            return Result.fail("报修人不能为空");
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            return Result.fail("报修标题不能为空");
        }
        User user = userMapper.selectById(dto.getReporterId());
        if (user == null) {
            return Result.fail("报修人不存在");
        }

        WorkOrder wo = new WorkOrder();
        wo.setOrderNo("WO" + System.currentTimeMillis());
        wo.setType("repair");
        wo.setTitle(dto.getTitle().trim());
        wo.setDescription(dto.getDescription());
        wo.setBuilding(user.getBuilding());
        wo.setFloor(user.getFloor());
        wo.setRoom(user.getRoom());
        wo.setReporterId(user.getId());
        wo.setReporterName(user.getUsername());
        wo.setStatus("pending");
        wo.setCreateTime(LocalDateTime.now());
        workOrderMapper.insert(wo);
        return Result.ok("报修工单已提交");
    }

    // 我的工单（居民查看自己报修的工单）
    @GetMapping("/mine")
    public List<WorkOrder> mine(@RequestParam Long userId) {
        LambdaQueryWrapper<WorkOrder> w = new LambdaQueryWrapper<>();
        w.eq(WorkOrder::getReporterId, userId)
         .eq(WorkOrder::getType, "repair")
         .orderByDesc(WorkOrder::getCreateTime);
        return workOrderMapper.selectList(w);
    }

    // 全部设备运维工单（管理员 + 维修员共用）
    @GetMapping("/list")
    public List<WorkOrder> list() {
        LambdaQueryWrapper<WorkOrder> w = new LambdaQueryWrapper<>();
        w.eq(WorkOrder::getType, "repair").orderByDesc(WorkOrder::getCreateTime);
        return workOrderMapper.selectList(w);
    }

    // 维修员接单
    @PostMapping("/accept")
    public Result<String> accept(@RequestBody AcceptWorkOrderDTO dto) {
        if (dto.getId() == null || dto.getRepairerId() == null) {
            return Result.fail("参数不完整");
        }
        WorkOrder wo = workOrderMapper.selectById(dto.getId());
        if (wo == null) {
            return Result.fail("工单不存在");
        }
        if (!"repair".equals(wo.getType())) {
            return Result.fail("警情事件不能作为设备运维工单接取");
        }
        if (!"pending".equals(wo.getStatus())) {
            return Result.fail("该工单已被接单或已关闭");
        }
        User repairer = userMapper.selectById(dto.getRepairerId());
        if (repairer == null) {
            return Result.fail("维修员不存在");
        }
        wo.setStatus("accepted");
        wo.setRepairerId(repairer.getId());
        wo.setRepairerName(repairer.getUsername());
        wo.setAcceptTime(LocalDateTime.now());
        workOrderMapper.updateById(wo);
        return Result.ok("接单成功");
    }

    // 关闭工单：维修员确认完成（仅能关自己已接单的）/ 管理员兜底关闭（可关任意未关闭）
    @PostMapping("/close")
    public Result<String> close(@RequestBody CloseWorkOrderDTO dto) {
        if (dto.getId() == null || dto.getOperatorId() == null) {
            return Result.fail("参数不完整");
        }
        WorkOrder wo = workOrderMapper.selectById(dto.getId());
        if (wo == null) {
            return Result.fail("工单不存在");
        }
        if (!"repair".equals(wo.getType())) {
            return Result.fail("警情事件请在警情事件页面处置");
        }
        if ("closed".equals(wo.getStatus())) {
            return Result.fail("工单已关闭");
        }
        User operator = userMapper.selectById(dto.getOperatorId());
        if (operator == null) {
            return Result.fail("操作者不存在");
        }

        boolean isAdmin = "admin".equals(operator.getRole());
        boolean isOwnerRepairer = "repairer".equals(operator.getRole())
                && "accepted".equals(wo.getStatus())
                && dto.getOperatorId().equals(wo.getRepairerId());
        if (!isAdmin && !isOwnerRepairer) {
            return Result.fail("无权关闭该工单");
        }

        wo.setStatus("closed");
        wo.setRemark(dto.getRemark());
        wo.setCloseTime(LocalDateTime.now());
        workOrderMapper.updateById(wo);
        return Result.ok("工单已关闭");
    }
}
