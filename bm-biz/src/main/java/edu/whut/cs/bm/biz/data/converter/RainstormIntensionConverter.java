package edu.whut.cs.bm.biz.data.converter;

/**
 * @author qixin on 2021/10/30.
 * @version 1.0
 * 暴雨强度指标
 */
public class RainstormIntensionConverter extends BaseConverter {
    @Override
    public Double convert() {
        double h24 = Double.parseDouble(this.argArray[0]);
        double h1 = Double.parseDouble(this.argArray[1]);
        double h1_6 = Double.parseDouble(this.argArray[2]);
        double K = Double.parseDouble((String) paramMap.get("K"));
        double h24_d = Double.parseDouble((String) paramMap.get("H24_D"));
        double h1_d = Double.parseDouble((String) paramMap.get("H1_D"));
        double h1_6_d = Double.parseDouble((String) paramMap.get("H1_6_D"));
        double result = Math.round(K * (h24 / h24_d + h1 / h1_d + h1_6 / h1_6_d) * 100) / 100;
        return result;
    }
}
