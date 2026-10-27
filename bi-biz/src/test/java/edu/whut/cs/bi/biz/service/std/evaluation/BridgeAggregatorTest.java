package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.service.std.evaluation.model.BridgeAggregate;
import edu.whut.cs.bi.biz.service.std.evaluation.model.UnitEval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeAggregatorTest {

    private BridgeAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new BridgeAggregator(new EvaluationMath());
    }

    private UnitEval unit(long id, String bci, int bcl) {
        UnitEval eval = new UnitEval();
        eval.setUnitId(id);
        eval.setBci(new BigDecimal(bci));
        eval.setBcl(bcl);
        return eval;
    }

    @Test
    void singleUnitPassesThrough() {
        BridgeAggregate agg = aggregator.aggregate(
                new ArrayList<>(Collections.singletonList(unit(10L, "85.3", 2))), null);

        assertEquals(BridgeAggregator.MODE_SINGLE_UNIT, agg.getCombineMode());
        assertEquals(new BigDecimal("85.3"), agg.getLayeredBci());
        assertEquals(2, agg.getLayeredBcl());
        assertEquals(new BigDecimal("85.3"), agg.getBci());
        assertEquals(2, agg.getBcl());
        assertNull(agg.getWorstUnitId());
        assertFalse(agg.isSingleControlHit());
    }

    @Test
    void worstUnitByLevel_344() {
        // 主桥 3 类 70.3 + 引桥 2 类 84.5 → 以主桥（最差单元）为全桥结果
        List<UnitEval> units = new ArrayList<>(Arrays.asList(unit(10L, "70.3", 3), unit(11L, "84.5", 2)));

        BridgeAggregate agg = aggregator.aggregate(units, null);

        assertEquals(BridgeAggregator.MODE_WORST_UNIT, agg.getCombineMode());
        assertEquals(10L, agg.getWorstUnitId());
        assertEquals(3, agg.getLayeredBcl());
        assertEquals(new BigDecimal("70.3"), agg.getLayeredBci());
        assertEquals(3, agg.getBcl());
        assertEquals(new BigDecimal("70.3"), agg.getBci());
    }

    @Test
    void sameWorstLevelTakesLowerBci() {
        // 同为 3 类的两个单元，取 BCI 更低者
        List<UnitEval> units = new ArrayList<>(Arrays.asList(unit(10L, "70.3", 3), unit(11L, "66.7", 3)));

        BridgeAggregate agg = aggregator.aggregate(units, null);

        assertEquals(11L, agg.getWorstUnitId());
        assertEquals(new BigDecimal("66.7"), agg.getBci());
    }

    @Test
    void singleControlHitForcesClass5AndCapsAt40() {
        List<UnitEval> units = new ArrayList<>(Collections.singletonList(unit(10L, "70.3", 3)));

        BridgeAggregate agg = aggregator.aggregate(units, Arrays.asList(7, 3));

        assertTrue(agg.isSingleControlHit());
        assertEquals("3,7", agg.getSingleControlNos());
        assertEquals(Arrays.asList(3, 7), agg.getHitIndicatorNos());
        assertEquals(3, agg.getLayeredBcl());
        assertEquals(new BigDecimal("70.3"), agg.getLayeredBci());
        assertEquals(5, agg.getBcl());
        assertEquals(new BigDecimal("40.0"), agg.getBci());
    }

    @Test
    void singleControlKeepsBciWhenLayeredAlreadyBelow40() {
        // 分层本就是 5 类 35 分：min(35,40)=35
        List<UnitEval> units = new ArrayList<>(Collections.singletonList(unit(10L, "35.0", 5)));

        BridgeAggregate agg = aggregator.aggregate(units, Collections.singletonList(1));

        assertEquals(5, agg.getBcl());
        assertEquals(new BigDecimal("35.0"), agg.getBci());
    }

    @Test
    void invalidIndicatorNoThrows() {
        List<UnitEval> units = new ArrayList<>(Collections.singletonList(unit(10L, "85.3", 2)));
        assertThrows(IllegalArgumentException.class,
                () -> aggregator.aggregate(units, Collections.singletonList(17)));
    }

    @Test
    void emptyUnitsThrow() {
        assertThrows(ServiceException.class, () -> aggregator.aggregate(new ArrayList<>(), null));
    }
}
