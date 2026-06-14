package edu.whut.cs.bi.biz.service.sheet;

import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bi.biz.utils.WordSheetPoiUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static edu.whut.cs.bi.biz.utils.WordSheetPoiUtils.setCellText;

/**
 * 桥梁结构位移试验检测记录表（水准仪，JGLP05001b-2）JSON → Word 渲染。
 */
@Component
public class DisplacementLevelJsonSheetRenderer extends AbstractStandardJsonSheetRenderer {

    public DisplacementLevelJsonSheetRenderer() {
        super("displacement_level",
                "桥梁结构位移试验检测记录表（水准仪）.docx",
                "桥梁结构位移试验检测记录表（水准仪）",
                "桥梁结构位移试验检测记录表",
                "JGLP05001b-2",
                Arrays.asList("测点", "后视", "前视", "位移"),
                Arrays.asList("测点", "后视", "前视", "位移"),
                Arrays.asList("station", "point", "backSightReading", "foreSightReading", "elevation", "initialElevation", "displacement", "note"));
    }

    @Override
    protected int minDataCellCount() {
        return 2;
    }

    @Override
    protected String valueAt(JSONObject record, int index) {
        if (record == null) {
            return "";
        }
        switch (index) {
            case 0:
                return record.getString("station");
            case 1:
                return record.getString("point");
            case 2:
                return record.getString("backSightReading");
            case 3:
                return record.getString("foreSightReading");
            case 4:
                return record.getString("elevation");
            case 5:
                return valueOf(record, "note", "initialElevation");
            case 6:
                return record.getString("displacement");
            case 7:
                return record.getString("note");
            default:
                return "";
        }
    }

    @Override
    protected void fillInfoTable(XWPFDocument document, JSONObject header) {
        super.fillInfoTable(document, header);
        fillWorkCondition(document, header.getString("workCondition"));
    }

    private void fillWorkCondition(XWPFDocument document, String workCondition) {
        if (workCondition == null || workCondition.isEmpty()) {
            return;
        }
        XWPFTable table = WordSheetPoiUtils.findTableContaining(document, "工程名称");
        if (table == null) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                if ("工况".equals(cells.get(i).getText().replaceAll("\\s+", ""))) {
                    if (i + 1 < cells.size()) {
                        setCellText(cells.get(i + 1), workCondition);
                    }
                    return;
                }
            }
        }
    }

    private String valueOf(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            String value = object.getString(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
