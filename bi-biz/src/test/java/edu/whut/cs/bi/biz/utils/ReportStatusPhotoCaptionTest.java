package edu.whut.cs.bi.biz.utils;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.FileMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReportStatusPhotoCaptionTest {

    @Test
    public void subsetNameEndsWithXianzhuang() {
        BiObject deck = node(1L, "桥面系", null);
        BiObject pavement = node(2L, "桥面铺装", 1L);
        Map<Long, BiObject> nodes = Map.of(1L, deck, 2L, pavement);

        assertEquals("桥面铺装现状", ReportGenerateTools.statusPhotoCaption(photo(2L, null), deck, nodes));
        assertEquals("桥面铺装现状", ReportGenerateTools.statusPhotoCaption(photo(2L, "桥面铺装现状照片"), deck, nodes));
    }

    @Test
    public void sideRemarkKeepsNameAndAppendsXianzhuang() {
        BiObject deck = node(1L, "桥面系", null);
        BiObject walkway = node(2L, "人行道", 1L);
        BiObject left = node(3L, "左幅", 2L);
        Map<Long, BiObject> nodes = Map.of(1L, deck, 2L, walkway, 3L, left);

        assertEquals("L#人行道现状", ReportGenerateTools.statusPhotoCaption(photo(3L, "L#人行道"), deck, nodes));
        assertEquals("L#现状", ReportGenerateTools.statusPhotoCaption(photo(3L, "L#"), deck, nodes));
        assertEquals("R#现状", ReportGenerateTools.statusPhotoCaption(photo(3L, "R#"), deck, nodes));
        assertEquals("人行道现状", ReportGenerateTools.statusPhotoCaption(photo(3L, null), deck, nodes));
        assertEquals("人行道现状", ReportGenerateTools.statusPhotoCaption(photo(3L, "  "), deck, nodes));
    }

    private static BiObject node(Long id, String name, Long parentId) {
        BiObject object = new BiObject();
        object.setId(id);
        object.setName(name);
        object.setParentId(parentId);
        return object;
    }

    private static FileMap photo(Long subjectId, String remark) {
        FileMap fileMap = new FileMap();
        fileMap.setSubjectId(subjectId);
        fileMap.setAttachmentRemark(remark);
        return fileMap;
    }
}
