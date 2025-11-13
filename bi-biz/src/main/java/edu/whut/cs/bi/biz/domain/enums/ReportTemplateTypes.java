package edu.whut.cs.bi.biz.domain.enums;

public enum ReportTemplateTypes {
    COMBINED_BRIDGE(0, "组合桥"),
    BEAM_BRIDGE(1, "梁桥"),
    ARCH_BRIDGE(2, "拱桥");

    private Integer type;
    private String desc;

    ReportTemplateTypes(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    public static boolean isSigleBridge(Integer type) {
        if (type.equals(BEAM_BRIDGE.getType())
                || type.equals(ARCH_BRIDGE.getType())
        ) {
            return true;
        }
        return false;
    }

    // 根据桥梁模板名 获取 桥梁模板类型。
    public static ReportTemplateTypes getEnumByDesc(String templateName) {
        for (ReportTemplateTypes item : ReportTemplateTypes.values()) {
            if (templateName.contains(item.getDesc())) {
                return item;
            }
        }
        return null;
    }

    public static String getDescByType(Integer type) {
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
