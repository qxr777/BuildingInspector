package edu.whut.cs.bm.biz.data.converter;

/**
 * @author qixin on 2023/4/17.
 * @version 1.0
 * 偏移百分率 转换器
 */
public class OffsetPercentConverter extends BaseConverter{
    @Override
    public Double convert() {
        double currentValue = Double.parseDouble(this.argArray[0]);
        double baseValue = Double.parseDouble((String) this.paramMap.get("BaseValue"));
        return Math.abs(currentValue - baseValue) / baseValue * 100;
    }
}
