package edu.whut.cs.bi.biz.domain.enums;

public enum ReportTemplateTypes {
    COMBINED_BRIDGE(0, new String[]{"组合桥"}),
    LEVEL_2_BEAM_BRIDGE(1, new String[]{"梁桥", "二级"}),
    LEVEL_2_ARCH_BRIDGE(2, new String[]{"拱桥", "二级"}),
    LEVEL_1_BEAM_BRIDGE(3, new String[]{"梁桥", "一级"});

    private Integer type;
    private String[] desc;

    ReportTemplateTypes(Integer type, String[] desc) {
        this.type = type;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }

    public String[] getDesc() {
        return desc;
    }

    public static boolean is2LevelSigleBridge(Integer type) {
        if (type.equals(LEVEL_2_BEAM_BRIDGE.getType())
                || type.equals(LEVEL_2_ARCH_BRIDGE.getType())
        ) {
            return true;
        }
        return false;
    }

    public static boolean is1LevelSigleBridge(Integer type) {
        if (type.equals(LEVEL_1_BEAM_BRIDGE.getType())

        ) {
            return true;
        }
        return false;
    }

    // 根据桥梁模板名 获取 桥梁模板类型。
    public static ReportTemplateTypes getEnumByDesc(String templateName) {
        for (ReportTemplateTypes item : ReportTemplateTypes.values()) {
            boolean flag = true;
            for (String desc : item.getDesc()) {
                if (!templateName.contains(desc)) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                return item;
            }
        }
        return null;
    }

    public static String[] getDescByType(Integer type) {
        for (ReportTemplateTypes item : ReportTemplateTypes.values()) {
            if (item.getType().equals(type)) {
                return item.getDesc();
            }
        }
        return null;
    }

    public static ReportTemplateTypes getEnumByType(Integer type) {
        for (ReportTemplateTypes item : ReportTemplateTypes.values()) {
            if (item.getType().equals(type)) {
                return item;
            }
        }
        return null;
    }
}
