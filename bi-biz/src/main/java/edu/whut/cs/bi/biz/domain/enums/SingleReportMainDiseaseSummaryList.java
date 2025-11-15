package edu.whut.cs.bi.biz.domain.enums;

import edu.whut.cs.bi.biz.domain.BiObject;

import java.util.ArrayList;
import java.util.List;

public enum SingleReportMainDiseaseSummaryList {
    UPPER("上部结构"),
    LOWER("下部结构"),
    DECKSYSTEM("桥面系");
    private String StructureName;

    public static List<String> getAllStructureList() {
        List<String> result = new ArrayList<>();
        for (SingleReportMainDiseaseSummaryList item : SingleReportMainDiseaseSummaryList.values()) {
            result.add(item.getStructureName());
        }
        return result;
    }

    SingleReportMainDiseaseSummaryList(String structureName) {
        StructureName = structureName;
    }

    public String getStructureName() {
        return StructureName;
    }
}
