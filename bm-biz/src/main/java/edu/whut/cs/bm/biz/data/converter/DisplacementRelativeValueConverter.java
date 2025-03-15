package edu.whut.cs.bm.biz.data.converter;

/**
 * @author qixin on 2021/10/30.
 * @version 1.0
 * 位移相对值 转换器
 */
public class DisplacementRelativeValueConverter extends BaseConverter{
    @Override
    public Double convert() {
        double displacement = Double.parseDouble(this.argArray[0]);
        double distance = Double.parseDouble((String) this.paramMap.get("Distance"));
        return displacement / distance;
    }
}
