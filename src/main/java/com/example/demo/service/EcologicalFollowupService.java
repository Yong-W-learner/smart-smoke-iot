package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.AncientTree;
import com.example.demo.entity.Device;
import com.example.demo.entity.EcologicalFollowup;
import com.example.demo.entity.WildlifeHabitat;
import com.example.demo.mapper.AncientTreeMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.EcologicalFollowupMapper;
import com.example.demo.mapper.WildlifeHabitatMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生态回访任务服务。
 *
 * 森林火险事件处置到 RESOLVED（且已写入 recover_time）时，
 * 若事件点附近存在古树 / 野生动物栖息地（Haversine ≤1km），
 * 自动创建生态回访任务（PENDING）。
 *
 * 状态机：PENDING → IN_PROGRESS → COMPLETED
 *
 * 闭环约束：存在 PENDING/IN_PROGRESS 回访任务的森林事件，
 * 不允许关闭（由 AlarmDisposalService 强制）。
 */
@Service
public class EcologicalFollowupService {

    private static final Logger log =
            LoggerFactory.getLogger(EcologicalFollowupService.class);

    public static final String ASSET_ANCIENT_TREE = "ANCIENT_TREE";
    public static final String ASSET_WILDLIFE_HABITAT = "WILDLIFE_HABITAT";

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATE_COMPLETED = "COMPLETED";

    /** 生态回访触发半径（米），与影响评分有效半径一致 */
    public static final double FOLLOWUP_RADIUS_METERS = 1000.0;

    private final EcologicalFollowupMapper followupMapper;
    private final AncientTreeMapper ancientTreeMapper;
    private final WildlifeHabitatMapper wildlifeHabitatMapper;
    private final DeviceMapper deviceMapper;

    public EcologicalFollowupService(EcologicalFollowupMapper followupMapper,
                                     AncientTreeMapper ancientTreeMapper,
                                     WildlifeHabitatMapper wildlifeHabitatMapper,
                                     DeviceMapper deviceMapper) {
        this.followupMapper = followupMapper;
        this.ancientTreeMapper = ancientTreeMapper;
        this.wildlifeHabitatMapper = wildlifeHabitatMapper;
        this.deviceMapper = deviceMapper;
    }


    /**
     * 森林事件处置到 RESOLVED 且环境已恢复时自动创建回访任务。
     *
     * 仅对 scene_type=FOREST 且 recoverTime != null 的事件生效；
     * 事件点附近 1km 内的古树 / 栖息地各生成一条回访任务（幂等）。
     */
    public void autoCreateForAlarm(Alarm alarm) {

        if (alarm == null
                || !AlarmService.SCENE_FOREST.equals(alarm.getSceneType())
                || alarm.getRecoverTime() == null) {
            return;
        }

        Device device = alarm.getDeviceId() == null
                ? null : deviceMapper.selectById(alarm.getDeviceId());

        if (device == null
                || device.getLatitude() == null
                || device.getLongitude() == null) {
            return;
        }

        double lat = device.getLatitude().doubleValue();
        double lng = device.getLongitude().doubleValue();

        int created = 0;

        created += createTreeFollowups(alarm, lat, lng);
        created += createHabitatFollowups(alarm, lat, lng);

        if (created > 0) {
            log.info(
                    "已为森林事件自动创建生态回访任务：alarmId={}，数量={}",
                    alarm.getId(), created
            );
        }
    }

    /**
     * 事件附近的古树回访任务。
     */
    private int createTreeFollowups(Alarm alarm, double lat, double lng) {

        List<AncientTree> trees =
                ancientTreeMapper.selectList(
                        new LambdaQueryWrapper<AncientTree>()
                                .eq(alarm.getZoneId() != null,
                                        AncientTree::getZoneId, alarm.getZoneId())
                );

        int created = 0;

        for (AncientTree tree : trees) {

            if (tree.getLatitude() == null
                    || tree.getLongitude() == null) {
                continue;
            }

            double distance = ForestEventPriorityService.haversineMeters(
                    lat, lng,
                    tree.getLatitude().doubleValue(),
                    tree.getLongitude().doubleValue()
            );

            if (distance <= FOLLOWUP_RADIUS_METERS
                    && !exists(alarm.getId(), ASSET_ANCIENT_TREE, tree.getId())) {

                insertFollowup(alarm, ASSET_ANCIENT_TREE,
                        tree.getId(), tree.getTreeCode(), tree.getTreeName());
                created++;
            }
        }

        return created;
    }

    /**
     * 事件附近的栖息地回访任务。
     */
    private int createHabitatFollowups(Alarm alarm, double lat, double lng) {

        List<WildlifeHabitat> habitats =
                wildlifeHabitatMapper.selectList(
                        new LambdaQueryWrapper<WildlifeHabitat>()
                                .eq(alarm.getZoneId() != null,
                                        WildlifeHabitat::getZoneId, alarm.getZoneId())
                );

        int created = 0;

        for (WildlifeHabitat habitat : habitats) {

            if (habitat.getLatitude() == null
                    || habitat.getLongitude() == null) {
                continue;
            }

            double distance = ForestEventPriorityService.haversineMeters(
                    lat, lng,
                    habitat.getLatitude().doubleValue(),
                    habitat.getLongitude().doubleValue()
            );

            if (distance <= FOLLOWUP_RADIUS_METERS
                    && !exists(alarm.getId(), ASSET_WILDLIFE_HABITAT, habitat.getId())) {

                insertFollowup(alarm, ASSET_WILDLIFE_HABITAT,
                        habitat.getId(), habitat.getHabitatCode(),
                        habitat.getHabitatName());
                created++;
            }
        }

        return created;
    }

    private void insertFollowup(Alarm alarm, String assetType,
                                Long assetId, String assetCode,
                                String assetName) {

        EcologicalFollowup followup = new EcologicalFollowup();
        followup.setAlarmId(alarm.getId());
        followup.setAssetType(assetType);
        followup.setAssetId(assetId);
        followup.setAssetCode(assetCode);
        followup.setAssetName(assetName);
        followup.setZoneId(alarm.getZoneId());
        followup.setState(STATE_PENDING);
        followup.setDueTime(LocalDateTime.now().plusDays(7));
        followup.setCreatedAt(LocalDateTime.now());

        followupMapper.insert(followup);
    }

    private boolean exists(Long alarmId, String assetType, Long assetId) {

        Long count = followupMapper.selectCount(
                new LambdaQueryWrapper<EcologicalFollowup>()
                        .eq(EcologicalFollowup::getAlarmId, alarmId)
                        .eq(EcologicalFollowup::getAssetType, assetType)
                        .eq(EcologicalFollowup::getAssetId, assetId)
        );

        return count != null && count > 0;
    }


    /**
     * 某事件的全部回访任务。
     */
    public List<EcologicalFollowup> listByAlarm(Long alarmId) {

        return followupMapper.selectList(
                new LambdaQueryWrapper<EcologicalFollowup>()
                        .eq(EcologicalFollowup::getAlarmId, alarmId)
                        .orderByAsc(EcologicalFollowup::getId)
        );
    }


    /**
     * 开始回访：PENDING → IN_PROGRESS。
     */
    public EcologicalFollowup start(Long followupId, String operator) {

        EcologicalFollowup followup = requireFollowup(followupId);

        if (STATE_COMPLETED.equals(followup.getState())) {
            return followup;
        }

        followup.setState(STATE_IN_PROGRESS);
        followup.setHandler(operator);
        followupMapper.updateById(followup);

        return followup;
    }


    /**
     * 完成回访：PENDING/IN_PROGRESS → COMPLETED。
     */
    public EcologicalFollowup complete(Long followupId, String operator,
                                       String note) {

        EcologicalFollowup followup = requireFollowup(followupId);

        if (STATE_COMPLETED.equals(followup.getState())) {
            return followup;
        }

        followup.setState(STATE_COMPLETED);
        followup.setHandler(operator);
        followup.setFollowupNote(note);
        followup.setCompletedTime(LocalDateTime.now());
        followupMapper.updateById(followup);

        log.info("生态回访任务已完成：followupId={}，asset={}，operator={}",
                followupId, followup.getAssetCode(), operator);

        return followup;
    }


    /**
     * 该事件是否存在未完成的回访任务（PENDING / IN_PROGRESS）。
     */
    public boolean hasPendingFollowups(Long alarmId) {

        if (alarmId == null) {
            return false;
        }

        Long count = followupMapper.selectCount(
                new LambdaQueryWrapper<EcologicalFollowup>()
                        .eq(EcologicalFollowup::getAlarmId, alarmId)
                        .in(EcologicalFollowup::getState,
                                STATE_PENDING, STATE_IN_PROGRESS)
        );

        return count != null && count > 0;
    }

    private EcologicalFollowup requireFollowup(Long followupId) {

        EcologicalFollowup followup = followupId == null
                ? null : followupMapper.selectById(followupId);

        if (followup == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "生态回访任务不存在"
            );
        }

        return followup;
    }
}
