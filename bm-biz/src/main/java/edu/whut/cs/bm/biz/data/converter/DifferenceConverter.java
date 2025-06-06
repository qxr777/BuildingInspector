package edu.whut.cs.bm.biz.data.converter;

/**
 * @author qixin on 2023/4/13.
 * @version 1.0
 * 相对基准数值转换器
 */
public class DifferenceConverter extends BaseConverter{
    @Override
    public Double convert() {
        double currentValue = Double.parseDouble(this.argArray[0]);
        double baseValue = Double.parseDouble((String) this.paramMap.get("BaseValue"));
        return currentValue -  baseValue;
    }
}
