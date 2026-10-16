package edu.whut.cs.bi.biz.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiseaseCauseKnowledgeBaseTest {
    private final DiseaseCauseKnowledgeBase kb = DiseaseCauseKnowledgeBase.getDefault();

    @Test
    void tBeamCrackRoutesToRibBeamEntries() {
        List<DiseaseCauseKnowledgeBase.Entry> candidates = kb.candidates(new DiseaseCauseKnowledgeBase.Query(
                "梁式桥", "上部结构", "上部承重构件", List.of("T梁"), "5.1.1-1-2#裂缝", "竖向"));

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.size() <= DiseaseCauseKnowledgeBase.MAX_CANDIDATES);
        for (DiseaseCauseKnowledgeBase.Entry entry : candidates) {
            assertEquals("上部结构-梁桥", entry.sheet());
            assertTrue(entry.component().contains("肋梁"), entry.component());
        }
    }

    @Test
    void doubleCurvedArchRingUsesTemplateSpecificRule() {
        List<DiseaseCauseKnowledgeBase.Entry> candidates = kb.candidates(new DiseaseCauseKnowledgeBase.Query(
                "双曲拱桥", "上部结构", "主拱圈", List.of("拱肋"), "裂缝", "纵向"));

        assertFalse(candidates.isEmpty());
        for (DiseaseCauseKnowledgeBase.Entry entry : candidates) {
            assertEquals("上部结构-拱桥", entry.sheet());
            assertTrue(entry.component().contains("双曲拱圈"), entry.component());
        }
    }

    @Test
    void capBeamRoutesToSubstructureCapBeam() {
        List<DiseaseCauseKnowledgeBase.Entry> candidates = kb.candidates(new DiseaseCauseKnowledgeBase.Query(
                "梁式桥", "下部结构", "桥墩", List.of("盖梁"), "裂缝", "竖向"));

        assertFalse(candidates.isEmpty());
        for (DiseaseCauseKnowledgeBase.Entry entry : candidates) {
            assertEquals("下部结构", entry.sheet());
            assertTrue(entry.component().contains("盖梁") || entry.component().contains("墩帽"), entry.component());
        }
    }

    @Test
    void bearingOffsetUsesTypeSynonyms() {
        List<DiseaseCauseKnowledgeBase.Entry> candidates = kb.candidates(new DiseaseCauseKnowledgeBase.Query(
                "梁式桥", "上部结构", "支座", List.of("支座"), "偏位", "纵向"));

        assertTrue(candidates.stream().anyMatch(entry -> entry.defectType().contains("串动")));
        assertTrue(candidates.stream().allMatch(entry -> entry.category().contains("支座")));
    }

    @Test
    void boxGirderUnderOtherPartUsesComponentName() {
        List<DiseaseCauseKnowledgeBase.Entry> candidates = kb.candidates(new DiseaseCauseKnowledgeBase.Query(
                "梁式桥", "上部结构", "", List.of("箱形梁"), "裂缝", "纵向"));

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().allMatch(entry -> entry.component().contains("箱梁")));
    }

    @Test
    void suspensionPartRoutesEvenWithUnrelatedChildName() {
        List<DiseaseCauseKnowledgeBase.Entry> candidates = kb.candidates(new DiseaseCauseKnowledgeBase.Query(
                "钢箱梁悬索桥", "上部结构", "主缆", List.of("钢护筒"), "锈蚀", null));

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().allMatch(entry -> entry.category().equals("主缆")));
    }

    @Test
    void unsupportedTemplateHasNoCandidates() {
        assertFalse(kb.supportsTemplate("2026普通桥梁定期检查报告模板"));
        assertTrue(kb.candidates(new DiseaseCauseKnowledgeBase.Query(
                "不存在的模板", "上部结构", "上部承重构件", List.of("T梁"), "裂缝", "竖向")).isEmpty());
    }

    @Test
    void queryTextAddsCrackDirectionOnlyForCracks() {
        assertEquals("竖向裂缝", DiseaseCauseKnowledgeBase.queryText("5.1.1-1-2#裂缝", "竖向"));
        assertEquals("剥落露筋", DiseaseCauseKnowledgeBase.queryText("5.1.1-2-3#剥落露筋", "纵向"));
        assertTrue(DiseaseCauseKnowledgeBase.bigrams("钢构件裂纹").contains("裂缝"));
    }

    @Test
    void suggestionForLevelPicksMatchingSegment() {
        String suggestion = "标度2：加强观测。\n标度3~4：封闭裂缝。\n标度5：加固。";

        assertEquals("加强观测。", DiseaseCauseKnowledgeBase.suggestionForLevel(suggestion, 2));
        assertEquals("封闭裂缝。", DiseaseCauseKnowledgeBase.suggestionForLevel(suggestion, 4));
        assertEquals("加固。", DiseaseCauseKnowledgeBase.suggestionForLevel("标度2：加强观测。\n标度3：加固。", 5));
        assertEquals(suggestion, DiseaseCauseKnowledgeBase.suggestionForLevel(suggestion, 1));
        assertEquals(suggestion, DiseaseCauseKnowledgeBase.suggestionForLevel(suggestion, null));
        assertEquals("定期观测。", DiseaseCauseKnowledgeBase.suggestionForLevel("定期观测。", 3));
    }
}
