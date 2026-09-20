package edu.whut.cs.bi.biz.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RebarCorrosionAssessmentTableBuilderTest {

    @Test
    void usesSubBridgeNameAndSheetComponentWithTwentyValues() {
        String json = """
                {
                  "sheetId": "rebar_corrosion",
                  "pages": [{
                    "header": { "projectName": "东荆河4号大桥" },
                    "records": [{
                      "componentName": "L-2-10#变截面箱梁节段内腹板",
                      "temperature": "18",
                      "value1": "-17.6", "value2": "-15.7", "value3": "-13.2",
                      "value4": "-18.3", "value5": "-20.1", "value6": "-25.4",
                      "value7": "-29.7", "value8": "-26.2", "value9": "-28.6",
                      "value10": "-32.1", "value11": "-23.5", "value12": "-25.4",
                      "value13": "-26.9", "value14": "-24.1", "value15": "-29.8",
                      "value16": "-31.6", "value17": "-24.2", "value18": "-26.7",
                      "value19": "-24.0", "value20": "-20.5"
                    }]
                  }]
                }
                """;

        List<RebarCorrosionAssessmentTableBuilder.Row> rows =
                RebarCorrosionAssessmentTableBuilder.build("东荆河4号大桥（小箱梁）左幅", json);

        assertEquals(1, rows.size());
        RebarCorrosionAssessmentTableBuilder.Row row = rows.get(0);
        assertEquals("东荆河4号大桥（小箱梁）左幅", row.bridgeName);
        assertEquals("L-2-10#变截面箱梁节段内腹板", row.location);
        assertEquals(20, row.values.size());
        assertEquals("-17.6", row.values.get(0));
        assertEquals("-32.1", row.values.get(9));
        assertEquals("-20.5", row.values.get(19));
        assertEquals("-32.1", row.minText);
        assertEquals("18", row.temperatureText);
        assertEquals("", row.correctionText);
        assertEquals("", row.correctedMinText);
        assertEquals("", row.scaleText);
    }

    @Test
    void skipsBlankComponentAndMissingValues() {
        String json = """
                {"pages":[{"records":[
                  {"componentName":"-","value1":"-10"},
                  {"componentName":"盖梁"}
                ]}]}
                """;
        assertEquals(0, RebarCorrosionAssessmentTableBuilder.build("主桥", json).size());
    }
}
