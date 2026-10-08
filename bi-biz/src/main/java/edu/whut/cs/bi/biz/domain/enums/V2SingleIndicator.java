package edu.whut.cs.bi.biz.domain.enums;

/**
 * 单项控制指标（JTG/T 5230—2026 第 3.4.5 条，共 16 项）。
 *
 * <p>名称摘自正式标准原文；任一指标命中，全桥技术状况等级直接定为 5 类。</p>
 */
public enum V2SingleIndicator {

    I01(1, "落梁或梁板断裂"),
    I02(2, "主梁控制截面全截面开裂"),
    I03(3, "组合主梁结合面贯通开裂、组合作用失效"),
    I04(4, "主梁明显永久变形伴严重开裂"),
    I05(5, "主拱圈严重变形"),
    I06(6, "圬工拱圈砌体大范围断裂脱落"),
    I07(7, "拱脚固结失效、明显变位"),
    I08(8, "腹拱圈、桥面板塌陷断裂"),
    I09(9, "主要构件混凝土压溃或杆件失稳趋势"),
    I10(10, "上部主要构件严重异常位移"),
    I11(11, "主缆、拉吊索、系杆索严重锈蚀断裂"),
    I12(12, "墩台严重倾斜"),
    I13(13, "扩大基础冲空面积大于20%"),
    I14(14, "桩柱连接钢筋失效或严重错位"),
    I15(15, "桩基核心混凝土大范围缺失"),
    I16(16, "墩台基础锚碇明显位移、下沉、倾斜");

    private final int no;
    private final String name;

    V2SingleIndicator(int no, String name) {
        this.no = no;
        this.name = name;
    }

    public int getNo() {
        return no;
    }

    public String getName() {
        return name;
    }

    /**
     * 按序号 1~16 查找枚举。
     *
     * @param no 指标序号
     * @return 对应枚举项
     * @throws IllegalArgumentException 序号超出 1~16
     */
    public static V2SingleIndicator valueOfNo(int no) {
        for (V2SingleIndicator indicator : values()) {
            if (indicator.no == no) {
                return indicator;
            }
        }
        throw new IllegalArgumentException("无效的单项控制指标序号: " + no);
    }
}
