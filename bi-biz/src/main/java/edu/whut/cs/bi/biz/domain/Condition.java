package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 部件的技术状况评定
 */
public class Condition extends BaseEntity {

    static Map<Integer, Double> tMap = new LinkedHashMap<>();

    static {
        // 录入H21规范中，表4.1.2 t值数据，key为构件数量，value为t值
        tMap.put(1, Double.POSITIVE_INFINITY);
        tMap.put(2, 10.0);
        tMap.put(3, 9.7);
        tMap.put(4, 9.5);
        tMap.put(5, 9.2);
        tMap.put(6, 8.9);
        tMap.put(7, 8.7);
        tMap.put(8, 8.5);
        tMap.put(9, 8.3);
        tMap.put(10, 8.1);
        tMap.put(11, 7.9);
        tMap.put(12, 7.7);
        tMap.put(13, 7.5);
        tMap.put(14, 7.3);
        tMap.put(15, 7.2);
        tMap.put(16, 7.08);
        tMap.put(17, 6.96);
        tMap.put(18, 6.84);
        tMap.put(19, 6.72);
        tMap.put(20, 6.6);
        tMap.put(21, 6.48);
        tMap.put(22, 6.36);
        tMap.put(23, 6.24);
        tMap.put(24, 6.12);
        tMap.put(25, 6.00);
        tMap.put(26, 5.88);
        tMap.put(27, 5.76);
        tMap.put(28, 5.64);
        tMap.put(29, 5.52);
        tMap.put(30, 5.4);
        tMap.put(40, 4.9);
        tMap.put(50, 4.4);
        tMap.put(60, 4.0);
        tMap.put(70, 3.6);
        tMap.put(80, 3.2);
        tMap.put(90, 2.8);
        tMap.put(100, 2.5);
        tMap.put(200, 2.3);
    }

    public static Double getT(Integer componentsCount) {
        /**
         * 根据给定的等级，返回对应的t值。
         * 表中未列出的 t 值采用内插法计算。
         */
        // 当componentsCount大于等于200时，直接返回key为200的value值
        if (componentsCount >= 200) {
            return tMap.get(200);
        }
        
        if (tMap.get(componentsCount) == null) {
            int key =0, key1 = 0;
            for (int i = 0; i < tMap.size(); i++) {
                if (componentsCount > (Integer) tMap.keySet().toArray()[i]
                        && componentsCount < (Integer) tMap.keySet().toArray()[i + 1]) {
                    key = (int) tMap.keySet().toArray()[i];
                    key1 = (int) tMap.keySet().toArray()[i + 1];
                    break;
                }
            }
            double t = tMap.get(key);
            double t1 = tMap.get(key1);
            return Math.round((t + (componentsCount - key) * (t1 - t) / (key1 - key)) * 1000.0) / 1000.0; // 点斜式，保留小数点后三位
        }
        return tMap.get(componentsCount);
    }

    /**
     * 实体的唯一标识符，用于唯一标识该实体。
     */
    private Long Id;

    /**
     * 权重值，用于表示该部件的权重。
     * 该值通常用于计算或评估部件的重要性。
     */
    private BigDecimal weight;

    /**
     * 评分值，用于表示该部件的评分。
     * 该值通常用于评估部件的质量或状态。
     */
    private BigDecimal score;

    /**
     * 等级值，用于表示该部件的等级。
     * 该值通常用于描述部件的严重程度或优先级。
     */
    private Integer level;

    /**
     * 构件数量，用于表示该部件的构件数量。
     */
    private Integer componentsCount;

    /**
     * 部件，关联的部件实体。
     */
    private BiObject biObject;
    private Long biObjectId;

    /**
     * 技术状况评定，关联的桥幅技术状况评定。
     */
    private BiEvaluation biEvaluation;
    private Long biEvaluationId;

    public static void main(String[] args) {
        for (int i = 1; i <= 300; i++) {
            System.out.println(i + ":" + getT(i));
        }
    }
}