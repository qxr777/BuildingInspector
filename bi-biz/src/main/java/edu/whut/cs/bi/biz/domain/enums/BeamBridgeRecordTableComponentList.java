package edu.whut.cs.bi.biz.domain.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 定期检查 记录表 的构件字段，要求固定格式。
 */
public enum BeamBridgeRecordTableComponentList {
    COMPONENT_1("桥面铺装", 1),
    COMPONENT_2("伸缩缝装置", 2),
    COMPONENT_3("排水系统", 3),
    COMPONENT_4("人行道", 4),
    COMPONENT_5("栏杆、护栏", 5),
    COMPONENT_6("照明、标志", 6),
    COMPONENT_7("路桥连接处", 7),
    COMPONENT_8("主要承重构件", 8),
    COMPONENT_9("一般构件", 9),
    COMPONENT_10("桥墩及基础", 10),
    COMPONENT_11("桥台及基础", 11),
    COMPONENT_12("翼墙、耳墙", 12),
    COMPONENT_13("锥坡、护坡", 13),
    COMPONENT_14("支座", 14),
    COMPONENT_15("防撞设施", 15),
    COMPONENT_16("防雷设施", 16),
    COMPONENT_17("防抛网、声屏障", 17),
    COMPONENT_18("检修设施", 18),
    COMPONENT_19("监控系统、永久观测点", 19),
    COMPONENT_20("调治构造物", 20);
    private final String ComponentName;
    private final Integer index;
    // index 表示 构件的顺序 ，从 1开始 ， 1 的 类型的占位符 为 ${类型1}

    public static List<String> getComponentNameList() {
        return Arrays.stream(BeamBridgeRecordTableComponentList.values()).map(a -> a.getComponentName()).toList();
    }

    BeamBridgeRecordTableComponentList(String componentName, Integer index) {
        ComponentName = componentName;
        this.index = index;
    }

    public String getComponentName() {
        return ComponentName;
    }

    public Integer getIndex() {
        return index;
    }
}
