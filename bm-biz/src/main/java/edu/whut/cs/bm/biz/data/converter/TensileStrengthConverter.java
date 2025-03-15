package edu.whut.cs.bm.biz.data.converter;

/**
 * @author qixin on 2023/4/14.
 * @version 1.0
 * 钢筋抗压强度，由钢筋应力指标值计算得到
 */
public class TensileStrengthConverter extends BaseConverter{
    @Override
    public Double convert() {
        double currentValue = Double.parseDouble(this.argArray[0]);
        double baseValue = Double.parseDouble((String) this.paramMap.get("BaseValue"));  // Kn
        double barDiameter = Double.parseDouble((String) this.paramMap.get("BarDiameter"));   // mm
        double tensileStrength = (currentValue - baseValue) * 1000 / Math.pow(barDiameter / (2 * 1000), 2) * Math.PI / Math.pow(10, 6);
        return tensileStrength;  // 单位：兆帕
    }
}
