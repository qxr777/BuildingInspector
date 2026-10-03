package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 桥梁结构检测与监测索力试验检测记录表（测力传感器法，JGLP05012b）JSON → Word 渲染。
 */
@Component
public class CableForceSensorJsonSheetRenderer extends AbstractStandardJsonSheetRenderer {

    public CableForceSensorJsonSheetRenderer() {
        super("cable_force_sensor",
                "桥梁结构检测与监测索力试验检测记录表（测力传感器法）.docx",
                "桥梁结构检测与监测索力试验检测记录表（测力传感器法）",
                "桥梁结构检测与监测索力试验检测记录表",
                "JGLP05012b",
                Arrays.asList("索号", "测点", "传感器", "索力"),
                Arrays.asList("索号", "传感器编号", "测试温度"),
                Arrays.asList("cableNo", "sensorNumber", "testTemperature", "frequency",
                        "measuredCableForce", "theoreticalCableForce", "note"));
    }

    @Override
    protected int minDataCellCount() {
        return 2;
    }

    @Override
    protected String valueAt(JSONObject record, int index) {
        switch (index) {
            case 0:
                return valueOf(record, "cableNo");
            case 1:
                return valueOf(record, "sensorNumber", "sensorNo", "point");
            case 2:
                return valueOf(record, "testTemperature", "temperature");
            case 3:
                return valueOf(record, "frequency", "value1");
            case 4:
                return valueOf(record, "measuredCableForce", "value2");
            case 5:
                return valueOf(record, "theoreticalCableForce", "value3", "average");
            case 6:
                return valueOf(record, "note");
            default:
                return "";
        }
    }

    private String valueOf(JSONObject record, String... keys) {
        if (record == null) {
            return null;
        }
        for (String key : keys) {
            String value = record.getString(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
