package edu.whut.cs.bi.biz.domain.enums;

public enum AreaIdentifierEnums {
    COMMON(0, "普通"),
    AVERAGE(1, "平均"),
    COUNT(2, "总计");
    private int code;
    private String desc;

    AreaIdentifierEnums(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
