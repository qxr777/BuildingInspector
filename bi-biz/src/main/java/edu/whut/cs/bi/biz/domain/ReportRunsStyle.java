package edu.whut.cs.bi.biz.domain;

import lombok.Data;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;

@Data
public class ReportRunsStyle {


    /**
     * 用于在 Apache POI 操作 Word 时，对 XWPFRun 的样式进行快照/还原。
     * 仅保存最常用、肉眼可见的字符级属性，不含文本内容。
     *
     * 所有字段均给默认值，避免空指针；与 Word 默认值保持一致。
     */


    // ================= 字体与字号 =================
    /**
     * 字体中文名或英文名，如 "宋体"、"Times New Roman"
     */
    public String fontFamily = "宋体";

    /**
     * 字号（磅值）。Word 小四=12，五号=10.5，小五=9
     */
    public double fontSize = 10.5;

    // ================= 加粗/倾斜/删除线 =================
    public boolean bold = false;
    public boolean italic = false;
    /**
     * 单/双删除线
     */
    public boolean strike = false;
    public boolean doubleStrike = false;

    // ================= 颜色 =================
    /**
     * 16 进制 RGB，如 "FF0000"=红；null 表示自动（默认黑）
     */
    public String color = null;

    // ================= 下划线 =================
    /**
     * 无下划线时保存 {@link UnderlinePatterns#NONE}
     */
    public UnderlinePatterns underline = UnderlinePatterns.NONE;
    /**
     * 下划线颜色，null 同字体颜色
     */
    public String underlineColor = null;

    // ================= 上下标 =================
    /**
     * 上标、下标二者只能存一；0=正常，+1=上标，-1=下标
     */
    public int verticalAlign = 0;

    // ================= 字符间距/缩放 =================
    /**
     * 字符间距，单位磅；>0 加宽，<0 紧缩
     */
    public int characterSpacing = 0;

    /**
     * 水平缩放百分比，100=不缩放
     */
    public int scale = 100;

    // ================= 突出显示（荧光笔） =================
    /**
     * 16 进制颜色，如 "FFFF00"=黄色；null=无高亮
     */
    public String highlightColor = null;

    // ================= 浮雕/阴影/镂空等特效 =================
    public boolean embossed = false;
    public boolean imprinted = false;
    public boolean shadow = false;
    public boolean outline = false;

    // ================= 隐藏文字 =================
    public boolean vanish = false;

    // ================= 快速构造 =================
    public ReportRunsStyle() {
    }

    /**
     * 基于现有 XWPFRun 抓取快照
     */
    public static ReportRunsStyle snapshot(org.apache.poi.xwpf.usermodel.XWPFRun run) {
        ReportRunsStyle s = new ReportRunsStyle();
        if (run == null) return s;

        s.fontFamily = run.getFontFamily();
        if (s.fontFamily == null) s.fontFamily = "宋体";

        int size = run.getFontSize();
        s.fontSize = (size == -1 ? 10.5 : size);

        s.bold = run.isBold();
        s.italic = run.isItalic();
        s.strike = run.isStrikeThrough();
        s.doubleStrike = run.isDoubleStrikeThrough();
        s.color = run.getColor();
        s.underline = run.getUnderline();
        s.underlineColor = run.getUnderlineColor();

        s.characterSpacing = run.getCharacterSpacing();
        s.scale = run.getTextScale();
        s.embossed = run.isEmbossed();
        s.imprinted = run.isImprinted();
        s.shadow = run.isShadowed();
        s.vanish = run.isVanish();

        return s;
    }

    /**
     * 将本样式一次性写回指定 run（已做 null / 空值保护）
     */
    public void apply(org.apache.poi.xwpf.usermodel.XWPFRun run) {
        if (run == null) return;

        /* 1. 字体：禁止 null / 空 */
        if (fontFamily != null && !fontFamily.isEmpty()) {
            run.setFontFamily(fontFamily);   // 中英分开
        }

        /* 2. 字号：-1 表示未设置，用默认 10.5 */
        if (fontSize > 0) {
            run.setFontSize(fontSize);
        }

        /* 3. 布尔属性 */
        run.setBold(bold);
        run.setItalic(italic);
        run.setStrikeThrough(strike);
        run.setDoubleStrikethrough(doubleStrike);
        run.setEmbossed(embossed);
        run.setImprinted(imprinted);
        run.setShadow(shadow);
        run.setVanish(vanish);

        /* 4. 颜色：null 或空串都不写 */
        if (color != null && !color.isEmpty()) {
            run.setColor(color);
        }

        /* 5. 下划线 */
        if (underline != null && underline != UnderlinePatterns.NONE) {
            run.setUnderline(underline);
            if (underlineColor != null && !underlineColor.isEmpty()) {
                run.setUnderlineColor(underlineColor);
            }
        }

        /* 6. 上下标：0 表示正常，其余才写 */
        if (verticalAlign == 1) {
            run.setVerticalAlignment("superscript");
        } else if (verticalAlign == -1) {
            run.setVerticalAlignment("subscript");
        }
        // verticalAlign == 0 时 **不调用** setVerticalAlignment，避免 null

        /* 7. 字符间距 & 缩放 */
        if (characterSpacing != 0) {
            run.setCharacterSpacing(characterSpacing);
        }
        if (scale != 100) {          // 100 表示不缩放
            run.setTextScale(scale);
        }

    }
}

