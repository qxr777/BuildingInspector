package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;

/**
 * App 上传 JSON 检测记录表 → Word 渲染器。
 */
public interface JsonSheetWordRenderer {

    /** 表格类型，对应 bi_task_sheets.type / JSON sheetId */
    String sheetType();

    /** classpath 模板路径，如 word.biz/碳化深度检测记录表.docx */
    String templateClasspath();

    /** 下载时的默认文件名（不含扩展名） */
    String defaultDownloadBaseName();

    /** 将 JSON 渲染为 DOCX 字节流（预览：页码在正文） */
    byte[] render(JSONObject sheetJson);

    /** 将 JSON 渲染为 DOCX 字节流，指定页码位置 */
    byte[] render(JSONObject sheetJson, WordSheetPoiUtils.PageNumberPlacement pageNumberPlacement);

    /** 填充单页 Word 内容 */
    void fillPage(org.apache.poi.xwpf.usermodel.XWPFDocument document, JSONObject pageJson);
}
