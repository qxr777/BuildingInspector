package edu.whut.cs.bm.biz.data.converter;

/**
 * @author qixin on 2021/10/31.
 * @version 1.0
 * 张力损失率 转换器
 */
public class TensionLossPercentageConverter extends BaseConverter{
    @Override
    public Double convert() {
        double tension = Double.parseDouble(this.argArray[0]);
        double initialTension = Double.parseDouble((String) this.paramMap.get("InitialTension"));
        return Math.abs((tension / initialTension - 1) * 10000) / 100;
    }
}
