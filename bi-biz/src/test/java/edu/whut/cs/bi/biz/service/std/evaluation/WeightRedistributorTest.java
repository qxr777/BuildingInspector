package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedPart;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedSpan;
import edu.whut.cs.bi.biz.service.std.evaluation.model.ResolvedUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeightRedistributorTest {

    private WeightRedistributor redistributor;
    private EvalWarnings warnings;

    @BeforeEach
    void setUp() {
        redistributor = new WeightRedistributor();
        warnings = new EvalWarnings();
    }

    private ResolvedPart part(String key, String layer, int omega) {
        return part(key, layer, omega, false);
    }

    private ResolvedPart part(String key, String layer, int omega, boolean absent) {
        ResolvedPart p = new ResolvedPart();
        p.setPartKey(key);
        p.setLayerKey(layer);
        p.setOmega(omega);
        p.setOmegaEffective(BigDecimal.valueOf(omega));
        p.setGamma(new BigDecimal("0.42"));
        p.setAbsent(absent);
        if (absent) {
            p.setCi(3);
        }
        return p;
    }

    /** 两跨同构上部部件（各跨独立实例）。 */
    private ResolvedUnit twoSpanSuperUnit(boolean bearingInstance,
                                          boolean bearingAbsent,
                                          boolean bearingPresentS1,
                                          boolean bearingPresentS2) {
        ResolvedUnit unit = new ResolvedUnit();
        unit.setBridgeType("B03");

        ResolvedSpan s1 = new ResolvedSpan();
        s1.setSpanId(1L);
        s1.getParts().add(part("mainGirder", "superstructure", 55));
        s1.getParts().add(part("diaphragm", "superstructure", 20));
        ResolvedSpan s2 = new ResolvedSpan();
        s2.setSpanId(2L);
        s2.getParts().add(part("mainGirder", "superstructure", 55));
        s2.getParts().add(part("diaphragm", "superstructure", 20));

        if (bearingInstance) {
            s1.getParts().add(part("bearing", "superstructure", 25, bearingAbsent));
            s2.getParts().add(part("bearing", "superstructure", 25, bearingAbsent));
        }
        unit.getSpans().add(s1);
        unit.getSpans().add(s2);

        Set<String> present = new HashSet<>();
        present.add(WeightRedistributor.key(1L, "mainGirder"));
        present.add(WeightRedistributor.key(2L, "mainGirder"));
        present.add(WeightRedistributor.key(1L, "diaphragm"));
        present.add(WeightRedistributor.key(2L, "diaphragm"));
        if (bearingPresentS1) {
            present.add(WeightRedistributor.key(1L, "bearing"));
        }
        if (bearingPresentS2) {
            present.add(WeightRedistributor.key(2L, "bearing"));
        }
        return unit;
    }

    private Set<String> runAndReturnPresent(ResolvedUnit unit) {
        Set<String> present = new HashSet<>();
        // present 集合由入参带入；此处用测试构造时的一致集合：全部非 absent 部件均设有
        for (ResolvedSpan span : unit.getSpans()) {
            for (ResolvedPart p : span.getParts()) {
                if (!p.isAbsent()) {
                    present.add(WeightRedistributor.key(span.getSpanId(), p.getPartKey()));
                }
            }
        }
        return present;
    }

    @Test
    void baselineARedistributesWhenPartUnnecessaryInAllSpans() {
        // bearing 节点存在但全桥无构件、无 absent 标记 → 全桥无须设置
        ResolvedUnit unit = twoSpanSuperUnit(true, false, false, false);
        redistributor.applyB13(unit, runAndReturnPresentButBearing(unit), warnings);

        for (ResolvedSpan span : unit.getSpans()) {
            assertEquals(new BigDecimal("73.3333"), effective(span, "mainGirder"));
            assertEquals(new BigDecimal("26.6667"), effective(span, "diaphragm"));
            assertEquals(new BigDecimal("0.0000"), effective(span, "bearing"));
        }
    }

    /** 全部非 absent 部件设有，但 bearing 实际无构件：从 present 集合排除 bearing。 */
    private Set<String> runAndReturnPresentButBearing(ResolvedUnit unit) {
        Set<String> present = runAndReturnPresent(unit);
        present.remove(WeightRedistributor.key(1L, "bearing"));
        present.remove(WeightRedistributor.key(2L, "bearing"));
        return present;
    }

    private BigDecimal effective(ResolvedSpan span, String partKey) {
        for (ResolvedPart p : span.getParts()) {
            if (p.getPartKey().equals(partKey)) {
                return p.getOmegaEffective();
            }
        }
        throw new IllegalStateException("部件不存在: " + partKey);
    }

    @Test
    void gammaUntouchedAndWeightsUniformAcrossSpans() {
        ResolvedUnit unit = twoSpanSuperUnit(true, false, false, false);
        redistributor.applyB13(unit, runAndReturnPresentButBearing(unit), warnings);

        ResolvedSpan s1 = unit.getSpans().get(0);
        ResolvedSpan s2 = unit.getSpans().get(1);
        for (String key : new String[]{"mainGirder", "diaphragm", "bearing"}) {
            assertEquals(effective(s1, key), effective(s2, key));
            assertEquals(new BigDecimal("0.42"), gamma(s1, key));
        }
    }

    private BigDecimal gamma(ResolvedSpan span, String partKey) {
        for (ResolvedPart p : span.getParts()) {
            if (p.getPartKey().equals(partKey)) {
                return p.getGamma();
            }
        }
        throw new IllegalStateException("部件不存在: " + partKey);
    }

    @Test
    void noRedistributionWhenPartOnlyPartiallyMissing() {
        // bearing 仅在跨1设有 → 不重分配，全部权重原样
        ResolvedUnit unit = twoSpanSuperUnit(true, false, true, false);
        Set<String> present = runAndReturnPresent(unit);
        present.remove(WeightRedistributor.key(2L, "bearing"));
        redistributor.applyB13(unit, present, warnings);

        for (ResolvedSpan span : unit.getSpans()) {
            assertEquals(new BigDecimal("55.0000"), effective(span, "mainGirder"));
            assertEquals(new BigDecimal("20.0000"), effective(span, "diaphragm"));
            assertEquals(new BigDecimal("25.0000"), effective(span, "bearing"));
        }
    }

    @Test
    void absentPartKeepsWeightAndStaysOutOfR() {
        // bearing 标记应设未设：保权重 25，R=75，其余按比例
        ResolvedUnit unit = twoSpanSuperUnit(true, true, false, false);
        redistributor.applyB13(unit, runAndReturnPresent(unit), warnings);

        for (ResolvedSpan span : unit.getSpans()) {
            assertEquals(new BigDecimal("73.3333"), effective(span, "mainGirder"));
            assertEquals(new BigDecimal("26.6667"), effective(span, "diaphragm"));
            assertEquals(new BigDecimal("25.0000"), effective(span, "bearing"));
        }
    }

    @Test
    void throwsWhenREmptyWithoutAbsentMark() {
        ResolvedUnit unit = new ResolvedUnit();
        ResolvedSpan s1 = new ResolvedSpan();
        s1.setSpanId(1L);
        s1.getParts().add(part("otherAncillary", "ancillary", 30));
        ResolvedSpan s2 = new ResolvedSpan();
        s2.setSpanId(2L);
        s2.getParts().add(part("otherAncillary", "ancillary", 30));
        unit.getSpans().add(s1);
        unit.getSpans().add(s2);

        Set<String> present = new HashSet<>();
        assertThrows(ServiceException.class,
                () -> redistributor.applyB13(unit, present, warnings));
    }

    @Test
    void keepsWeightsWhenLayerOnlyHasAbsentParts() {
        ResolvedUnit unit = new ResolvedUnit();
        ResolvedSpan s1 = new ResolvedSpan();
        s1.setSpanId(1L);
        s1.getParts().add(part("otherAncillary", "ancillary", 30, true));
        ResolvedSpan s2 = new ResolvedSpan();
        s2.setSpanId(2L);
        s2.getParts().add(part("otherAncillary", "ancillary", 30, true));
        unit.getSpans().add(s1);
        unit.getSpans().add(s2);

        Set<String> present = new HashSet<>();
        redistributor.applyB13(unit, present, warnings);
        assertEquals(new BigDecimal("30.0000"), effective(s1, "otherAncillary"));
        assertEquals(new BigDecimal("30.0000"), effective(s2, "otherAncillary"));
    }
}
