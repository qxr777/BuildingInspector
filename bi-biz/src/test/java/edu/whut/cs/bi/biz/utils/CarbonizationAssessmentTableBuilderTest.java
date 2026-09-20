package edu.whut.cs.bi.biz.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CarbonizationAssessmentTableBuilderTest {

    @Test
    void averagesCarbonPointMeansAndTenCoverMeans() {
        String carbonJson = """
                {
                  "sheetId": "carbon_depth",
                  "pages": [{
                    "header": { "projectName": "东荆河4号大桥" },
                    "records": [
                      {"componentName": "L-2-10#变截面箱梁节段内腹板", "point": "1", "average": "0.50"},
                      {"componentName": "L-2-10#变截面箱梁节段内腹板", "point": "2", "average": "0.50"},
                      {"componentName": "L-2-10#变截面箱梁节段内腹板", "point": "3", "average": "0.50"}
                    ]
                  }]
                }
                """;
        String coverJson = """
                {
                  "sheetId": "rebar_cover",
                  "pages": [{
                    "records": [{
                      "componentName": "L-2-10#变截面箱梁节段",
                      "testPosition": "内腹板",
                      "rebarCoverItems": [
                        {"average": "35.5"}, {"average": "35.0"}, {"average": "34.5"},
                        {"average": "33.5"}, {"average": "35.5"}, {"average": "36.5"},
                        {"average": "36.0"}, {"average": "35.5"}, {"average": "35.5"},
                        {"average": "35.0"}
                      ]
                    }]
                  }]
                }
                """;

        List<CarbonizationAssessmentTableBuilder.Row> rows =
                CarbonizationAssessmentTableBuilder.build("东荆河4号大桥（小箱梁）左幅", carbonJson, coverJson);

        assertEquals(1, rows.size());
        CarbonizationAssessmentTableBuilder.Row row = rows.get(0);
        assertEquals("东荆河4号大桥（小箱梁）左幅", row.bridgeName);
        assertEquals("L-2-10#变截面箱梁节段内腹板", row.componentName);
        assertEquals("0.5", row.carbonAverageText);
        assertEquals("35.25", row.coverAverageText);
        assertEquals("0.014", row.kcText);
        assertEquals("", row.scaleText);
    }

    @Test
    void leavesCoverAndKcBlankWhenNoMatchingCoverSheet() {
        String carbonJson = """
                {"pages":[{"header":{"projectName":"主桥"},"records":[{"componentName":"L10#箱梁左腹板","average":"2.0"}]}]}
                """;

        List<CarbonizationAssessmentTableBuilder.Row> rows =
                CarbonizationAssessmentTableBuilder.build("主桥", carbonJson, null);
        assertEquals("主桥", rows.get(0).bridgeName);
        assertEquals("L10#箱梁左腹板", rows.get(0).componentName);

        assertEquals(1, rows.size());
        assertEquals("2.0", rows.get(0).carbonAverageText);
        assertEquals("", rows.get(0).coverAverageText);
        assertEquals("", rows.get(0).kcText);
        assertEquals("", rows.get(0).scaleText);
    }

    @Test
    void skipsBlankOrSlashCoverMeans() {
        String carbonJson = """
                {"pages":[{"header":{"projectName":"引桥"},"records":[{"componentName":"盖梁","average":"1.0"}]}]}
                """;
        String coverJson = """
                {"pages":[{"records":[{
                  "componentName":"盖梁",
                  "rebarCoverItems":[
                    {"average":"40.0"},{"average":"/"},{"average":"-"},{"average":"40.0"}
                  ]
                }]}]}
                """;

        List<CarbonizationAssessmentTableBuilder.Row> rows =
                CarbonizationAssessmentTableBuilder.build("引桥", carbonJson, coverJson);

        assertEquals("40.0", rows.get(0).coverAverageText);
        assertEquals("0.025", rows.get(0).kcText);
    }
}
