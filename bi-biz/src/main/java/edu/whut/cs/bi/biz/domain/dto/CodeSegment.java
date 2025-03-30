package edu.whut.cs.bi.biz.domain.dto;

/**
 * @Author:wanzheng
 * @Date:2025/3/28 14:29
 * @Description:
 **/
/**
 * 编号片段类
 */
public class CodeSegment {
    /** 片段类型（1：固定值，2：序号） */
    private int type;

    /** 固定值 */
    private String value;

    /** 序号最小值 */
    private int minValue;

    /** 序号最大值 */
    private int maxValue;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getMinValue() {
        return minValue;
    }

    public void setMinValue(int minValue) {
        this.minValue = minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
    }
}