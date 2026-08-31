package com.example.demo.service;

import com.example.demo.entity.AncientTree;
import com.example.demo.entity.WildlifeHabitat;
import com.example.demo.mapper.AncientTreeMapper;
import com.example.demo.mapper.WildlifeHabitatMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 森林火险事件优先级评分。
 *
 * 透明规则，非AI：
 * 事件优先级 = 火险可信度 × 0.40
 *            + 火险气象评分 × 0.25
 *            + 古树影响评分（最近古树分档）
 *            + 野生动物栖息地影响评分（最近栖息地分档）
 * 总分封顶 100。
 *
 * 无人机确认火点后，优先级强制提高到 ≥95（RED）。
 *
 * 影响评分基于 Haversine 球面距离（米），按保护等级分档（冻结规格）：
 * - 古树：≤100m  一级20 / 二级15 / 三级10
 *         100~250m 一级12 / 二级8 / 三级5
 *         &gt;250m 记 0
 * - 栖息地：≤200m  CORE15 / HIGH10 / MEDIUM5
 *          &gt;200m 记 0
 *
 * 多个资源命中时，取最高分作为事件影响分，同时返回全部受影响资源列表。
 * 距离不做分区限制：邻近分区的古树 / 栖息地按真实距离参与计分。
 */
@Service
public class ForestEventPriorityService {

    public static final double FIRE_WEIGHT = 0.40;
    public static final double WEATHER_WEIGHT = 0.25;

    /** 无人机确认火点后的强制最低优先级 */
    public static final int DRONE_CONFIRMED_MIN_PRIORITY = 95;

    /** 古树影响分档阈值（米） */
    public static final double TREE_IMPACT_BAND_NEAR_METERS = 100.0;
    public static final double TREE_IMPACT_BAND_FAR_METERS = 250.0;

    /** 栖息地影响分档阈值（米） */
    public static final double HABITAT_IMPACT_BAND_METERS = 200.0;

    public static final String RESOURCE_TYPE_TREE = "TREE";
    public static final String RESOURCE_TYPE_HABITAT = "HABITAT";

    public static final String LEVEL_RED = "RED";
    public static final String LEVEL_ORANGE = "ORANGE";
    public static final String LEVEL_YELLOW = "YELLOW";
    public static final String LEVEL_LOW = "LOW";

    private final AncientTreeMapper ancientTreeMapper;
    private final WildlifeHabitatMapper wildlifeHabitatMapper;

    public ForestEventPriorityService(AncientTreeMapper ancientTreeMapper,
                                      WildlifeHabitatMapper wildlifeHabitatMapper) {
        this.ancientTreeMapper = ancientTreeMapper;
        this.wildlifeHabitatMapper = wildlifeHabitatMapper;
    }

    /**
     * 单个受影响生态资源（事件点附近命中分档的资源）。
     *
     * @param type 资源类型：TREE（古树）/ HABITAT（栖息地）
     */
    public record ResourceImpact(String type,
                                 String code,
                                 String name,
                                 String protectionLevel,
                                 double distanceMeters,
                                 int impactScore) {
    }

    /**
     * 生态资源影响评分结果。
     */
    public record ImpactScores(int ancientTreeImpactScore,
                               int wildlifeImpactScore,
                               List<ResourceImpact> treeImpacts,
                               List<ResourceImpact> habitatImpacts) {

        /** 兼容便捷构造：仅用于固定分值场景（无受影响资源详情）。 */
        public ImpactScores(int ancientTreeImpactScore,
                            int wildlifeImpactScore) {
            this(ancientTreeImpactScore, wildlifeImpactScore,
                    List.of(), List.of());
        }

        /** 全部受影响生态资源（古树 + 栖息地）。 */
        public List<ResourceImpact> affectedResources() {
            List<ResourceImpact> all = new ArrayList<>(treeImpacts);
            all.addAll(habitatImpacts);
            return all;
        }
    }

    /**
     * 计算事件点对古树 / 野生动物栖息地的影响评分。
     *
     * @param zoneId 分区ID（保留签名兼容，分档按真实球面距离，不做分区过滤）
     * @param lat    事件点纬度（可空）
     * @param lng    事件点经度（可空）
     */
    public ImpactScores impactScores(Long zoneId,
                                     BigDecimal lat,
                                     BigDecimal lng) {

        if (lat == null || lng == null) {
            return new ImpactScores(0, 0);
        }

        return impactScoresAt(lat.doubleValue(), lng.doubleValue());
    }

    /**
     * 计算事件优先级总分（0~100）。
     */
    public int priorityScore(int fireConfidenceScore,
                             int fireWeatherScore,
                             int ancientTreeImpactScore,
                             int wildlifeImpactScore,
                             boolean droneConfirmed) {

        double score = fireConfidenceScore * FIRE_WEIGHT
                + fireWeatherScore * WEATHER_WEIGHT
                + ancientTreeImpactScore
                + wildlifeImpactScore;

        score = clamp(score, 0, 100);

        if (droneConfirmed) {
            score = Math.max(score, DRONE_CONFIRMED_MIN_PRIORITY);
        }

        return (int) Math.round(score);
    }

    /**
     * 优先级等级：RED ≥80 / ORANGE ≥60 / YELLOW ≥40 / LOW &lt;40。
     */
    public String priorityLevel(int priorityScore) {

        if (priorityScore >= 80) return LEVEL_RED;
        if (priorityScore >= 60) return LEVEL_ORANGE;
        if (priorityScore >= 40) return LEVEL_YELLOW;
        return LEVEL_LOW;
    }

    /**
     * 生成优先级判定依据说明（透明规则，不含AI描述）。
     */
    public String priorityReason(int fireConfidenceScore,
                                 int fireWeatherScore,
                                 int ancientTreeImpactScore,
                                 int wildlifeImpactScore,
                                 int priorityScore,
                                 boolean droneConfirmed) {

        String droneText = droneConfirmed
                ? "无人机已确认火点，优先级强制≥95"
                : "无人机复核未执行";

        return "火险可信度" + fireConfidenceScore + "×0.40"
                + " + 气象评分" + fireWeatherScore + "×0.25"
                + " + 古树影响" + ancientTreeImpactScore
                + " + 栖息地影响" + wildlifeImpactScore
                + " = 优先级" + priorityScore
                + "(" + priorityLevel(priorityScore) + ")；" + droneText;
    }

    /**
     * 球面距离（米），Haversine 公式。
     */
    public static double haversineMeters(double lat1, double lng1,
                                         double lat2, double lng2) {

        double earthRadius = 6371000;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }

    /**
     * 古树影响分档：距离与保护等级共同决定（冻结规格）。
     */
    static int treeImpactBand(double meters, String protectionLevel) {

        if (meters > TREE_IMPACT_BAND_FAR_METERS) {
            return 0;
        }

        if (meters <= TREE_IMPACT_BAND_NEAR_METERS) {
            return bandScore(protectionLevel,
                    20, 15, 10);
        }

        return bandScore(protectionLevel,
                12, 8, 5);
    }

    /**
     * 栖息地影响分档：距离与保护等级共同决定（冻结规格）。
     */
    static int habitatImpactBand(double meters, String protectionLevel) {

        if (meters > HABITAT_IMPACT_BAND_METERS) {
            return 0;
        }

        if ("CORE".equalsIgnoreCase(protectionLevel)) return 15;
        if ("HIGH".equalsIgnoreCase(protectionLevel)) return 10;
        if ("MEDIUM".equalsIgnoreCase(protectionLevel)) return 5;
        return 0;
    }

    private static int bandScore(String protectionLevel,
                                 int first, int second, int third) {

        if ("一级".equals(protectionLevel)) return first;
        if ("二级".equals(protectionLevel)) return second;
        if ("三级".equals(protectionLevel)) return third;
        return 0;
    }

    private ImpactScores impactScoresAt(double lat, double lng) {

        List<ResourceImpact> treeImpacts = new ArrayList<>();
        int treeMax = 0;

        List<AncientTree> trees = ancientTreeMapper.selectList(null);

        for (AncientTree tree : trees) {

            if (tree.getLatitude() == null
                    || tree.getLongitude() == null) {
                continue;
            }

            double meters = haversineMeters(
                    lat, lng,
                    tree.getLatitude().doubleValue(),
                    tree.getLongitude().doubleValue()
            );

            int score = treeImpactBand(meters, tree.getProtectionLevel());

            if (score > 0) {
                treeImpacts.add(new ResourceImpact(
                        RESOURCE_TYPE_TREE,
                        tree.getTreeCode(),
                        tree.getTreeName(),
                        tree.getProtectionLevel(),
                        roundToMeter(meters),
                        score
                ));
                treeMax = Math.max(treeMax, score);
            }
        }

        List<ResourceImpact> habitatImpacts = new ArrayList<>();
        int habitatMax = 0;

        List<WildlifeHabitat> habitats = wildlifeHabitatMapper.selectList(null);

        for (WildlifeHabitat habitat : habitats) {

            if (habitat.getLatitude() == null
                    || habitat.getLongitude() == null) {
                continue;
            }

            double meters = haversineMeters(
                    lat, lng,
                    habitat.getLatitude().doubleValue(),
                    habitat.getLongitude().doubleValue()
            );

            int score = habitatImpactBand(meters, habitat.getProtectionLevel());

            if (score > 0) {
                habitatImpacts.add(new ResourceImpact(
                        RESOURCE_TYPE_HABITAT,
                        habitat.getHabitatCode(),
                        habitat.getHabitatName(),
                        habitat.getProtectionLevel(),
                        roundToMeter(meters),
                        score
                ));
                habitatMax = Math.max(habitatMax, score);
            }
        }

        return new ImpactScores(treeMax, habitatMax,
                treeImpacts, habitatImpacts);
    }

    private static double roundToMeter(double meters) {
        return Math.round(meters);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
