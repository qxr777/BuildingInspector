package edu.whut.cs.bi.biz.domain.enums;

/**
 * 报告生成 的 处置建议 字段 。 根据不同的 评定类别 ， 固定话术。
 */
public enum DisposalSuggestionEnums {
    LEVEL_1(1, "正常保养"),
    LEVEL_2(2, "修复养护、预防养护"),
    LEVEL_3(3, "修复养护、加固"),
    LEVEL_4(4, "修复养护、加固或改造"),
    LEVEL_5(5, "及时封闭交通，改建或重建"),
    ;
    // 桥梁 评定 等级
    private Integer systemLevel;
    // 具体的 处置建议 文本。
    private String content;


    public static String getContentByLevel(Integer systemLevel) {
        for (DisposalSuggestionEnums item : DisposalSuggestionEnums.values()) {
            if (item.systemLevel.equals(systemLevel)) {
                return item.getContent();
            }
        }
        return null;
    }

    public static DisposalSuggestionEnums getEnumByLevel(Integer systemLevel) {
        for (DisposalSuggestionEnums item : DisposalSuggestionEnums.values()) {
            if (item.systemLevel.equals(systemLevel)) {
                return item;
            }
        }
        return null;
    }


    DisposalSuggestionEnums(Integer systemLevel, String content) {
        this.systemLevel = systemLevel;
        this.content = content;
    }

    public Integer getSystemLevel() {
        return systemLevel;
    }

    public String getContent() {
        return content;
    }
}
