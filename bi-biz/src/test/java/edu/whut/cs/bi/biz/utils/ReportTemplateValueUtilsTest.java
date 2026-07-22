package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.Property;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReportTemplateValueUtilsTest {

    @Test
    public void oldPropertiesPopulateCurrentExcelTemplateAliases() {
        List<Property> properties = properties(
                "路线等级", "一级公路",
                "桥梁编号", "OLD-001",
                "桥位桩号", "K12+300",
                "设计荷载", "公路-I级",
                "桥梁全长(m)", "213.34",
                "桥面总宽(m)", "8.5",
                "车道宽度", "7",
                "结构体系", "空心板梁",
                "左幅", "3*20",
                "右幅", "4*20"
        );

        ReportTemplateValueUtils.addAliasProperties(properties);

        assertEquals("一级公路", value(properties, "路线技术等级"));
        assertEquals("OLD-001", value(properties, "桥梁代码"));
        assertEquals("K12+300", value(properties, "桥梁中心桩号"));
        assertEquals("公路-I级", value(properties, "设计荷载等级"));
        assertEquals("213.34", value(properties, "桥梁全长"));
        assertEquals("8.5", value(properties, "桥梁全宽"));
        assertEquals("7", value(properties, "车行道宽"));
        assertEquals("空心板梁", value(properties, "桥型"));
        assertEquals("左幅：3×20；右幅：4×20", value(properties, "跨径组合"));
    }

    @Test
    public void excelPropertiesPopulateLegacyAndCombinedPlaceholders() {
        List<Property> properties = properties(
                "路线技术等级", "高速公路",
                "桥梁代码", "G50422801R2170",
                "是否公铁两用桥梁", "公路",
                "管理单位名称", "管理公司",
                "养护单位名称", "养护公司",
                "通航等级（内河）", "三级",
                "桥下通航标准净空", "5.0",
                "引道线形", "直线",
                "引道曲线半径", "1200",
                "设计洪水频率", "1/100",
                "设计洪水位", "42.6",
                "主桥上部构造结构形式", "连续箱梁",
                "主桥上部构造材料", "预应力混凝土",
                "基础类型", "桩基础",
                "基础材料", "钢筋混凝土",
                "墩台防撞设施类型", "防撞护舷",
                "防船撞设施类型", "警示标志",
                "排水类型", "集中排水"
        );

        ReportTemplateValueUtils.addAliasProperties(properties);

        assertEquals("高速公路", value(properties, "路线等级"));
        assertEquals("G50422801R2170", value(properties, "桥梁编号"));
        assertEquals("公路", value(properties, "功能类型"));
        assertEquals("管理：管理公司；养护：养护公司", value(properties, "管养单位"));
        assertEquals("等级：三级；净空：5.0", value(properties, "桥下通航等级及标准净空"));
        assertEquals("线形：直线；半径：1200", value(properties, "引道线形或曲线半径"));
        assertEquals("频率：1/100；水位：42.6", value(properties, "设计洪水频率及其水位"));
        assertEquals("连续箱梁；预应力混凝土", value(properties, "主梁"));
        assertEquals("桩基础；钢筋混凝土", value(properties, "基础"));
        assertEquals("墩台：防撞护舷；船撞：警示标志", value(properties, "桥梁防撞设施"));
        assertEquals("排水：集中排水", value(properties, "航标及排水系统"));
    }

    @Test
    public void explicitValuesWinAndSlashValuesCanBeBackfilled() {
        List<Property> properties = properties(
                "路线技术等级", "高速公路",
                "路线等级", "一级公路",
                "桥梁代码", "/",
                "桥梁编号", "OLD-002",
                "管养单位", "原管养单位",
                "管理单位名称", "新管理单位",
                "养护单位名称", "新养护单位",
                "跨径组合", "2×30",
                "左幅", "3×20",
                "右幅", "4×20"
        );

        ReportTemplateValueUtils.addAliasProperties(properties);

        assertEquals("高速公路", value(properties, "路线技术等级"));
        assertEquals("一级公路", value(properties, "路线等级"));
        assertEquals("OLD-002", value(properties, "桥梁代码"));
        assertEquals("原管养单位", value(properties, "管养单位"));
        assertEquals("2×30", value(properties, "跨径组合"));
    }

    private static List<Property> properties(String... namesAndValues) {
        List<Property> properties = new ArrayList<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            Property property = new Property();
            property.setName(namesAndValues[i]);
            property.setValue(namesAndValues[i + 1]);
            properties.add(property);
        }
        return properties;
    }

    private static String value(List<Property> properties, String name) {
        return properties.stream()
                .filter(property -> name.equals(property.getName()))
                .map(Property::getValue)
                .findFirst()
                .orElse(null);
    }
}
