package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BiObjectControllerTest {

    @Mock
    private IBiObjectService biObjectService;

    private BiObjectController controller;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new BiObjectController();
        ReflectionTestUtils.setField(controller, "biObjectService", biObjectService);
    }

    @Test
    public void repairCountsReturnsSuccessWhenNoRowsNeedUpdate() {
        when(biObjectService.repairCountsFromSubtree(1L)).thenReturn(0);

        AjaxResult result = controller.repairCounts(1L);

        assertEquals(AjaxResult.Type.SUCCESS.value(), result.get(AjaxResult.CODE_TAG));
        assertEquals("构件数量修正完成，共更新0条记录", result.get(AjaxResult.MSG_TAG));
        verify(biObjectService).repairCountsFromSubtree(1L);
    }

    @Test
    public void repairCountsRejectsMissingRootObjectId() {
        AjaxResult result = controller.repairCounts(null);

        assertEquals(AjaxResult.Type.ERROR.value(), result.get(AjaxResult.CODE_TAG));
        assertEquals("未找到根节点", result.get(AjaxResult.MSG_TAG));
        verify(biObjectService, never()).repairCountsFromSubtree(null);
    }

    @Test
    public void repairCountsReturnsConflictMessage() {
        when(biObjectService.repairCountsFromSubtree(1L))
                .thenThrow(new ServiceException("count数量冲突：对象上部承重构件(id=2) count=5，子树合计=7"));

        AjaxResult result = controller.repairCounts(1L);

        assertEquals(AjaxResult.Type.ERROR.value(), result.get(AjaxResult.CODE_TAG));
        assertEquals("count数量冲突：对象上部承重构件(id=2) count=5，子树合计=7", result.get(AjaxResult.MSG_TAG));
    }
    @Test
    public void correctWeightsRepairsCountsBeforeCorrectingWeights() {
        when(biObjectService.repairCountsFromSubtree(1L)).thenReturn(2);
        when(biObjectService.correctAllWeights(1L)).thenReturn(3);

        AjaxResult result = controller.correctWeights(1L);

        assertEquals(AjaxResult.Type.SUCCESS.value(), result.get(AjaxResult.CODE_TAG));

        InOrder inOrder = inOrder(biObjectService);
        inOrder.verify(biObjectService).repairCountsFromSubtree(1L);
        inOrder.verify(biObjectService).correctAllWeights(1L);
    }
}
