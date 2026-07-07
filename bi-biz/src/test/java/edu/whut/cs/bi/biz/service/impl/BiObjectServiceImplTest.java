package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BiObjectServiceImplTest {

    @Mock
    private BiObjectMapper biObjectMapper;

    @InjectMocks
    private BiObjectServiceImpl biObjectService;

    @Test
    public void repairCountsFromSubtreeSumsChildCountsUpToRoot() {
        BiObject root = biObject(1L, 0L, 0);
        BiObject parent = biObject(2L, 1L, 0);
        BiObject leafA = biObject(3L, 2L, 3);
        BiObject leafB = biObject(4L, 2L, 4);
        BiObject leafC = biObject(5L, 1L, 2);

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(root);
        when(biObjectMapper.selectChildrenById(1L)).thenReturn(Arrays.asList(parent, leafA, leafB, leafC));
        when(biObjectMapper.updateBiObject(any(BiObject.class))).thenReturn(1);

        int updated = biObjectService.repairCountsFromSubtree(1L);

        assertEquals(2, updated);

        ArgumentCaptor<BiObject> captor = ArgumentCaptor.forClass(BiObject.class);
        verify(biObjectMapper, times(2)).updateBiObject(captor.capture());

        Map<Long, Integer> countsById = captor.getAllValues().stream()
                .collect(Collectors.toMap(BiObject::getId, BiObject::getCount));
        assertEquals(Integer.valueOf(7), countsById.get(2L));
        assertEquals(Integer.valueOf(9), countsById.get(1L));
    }

    @Test
    public void repairCountsFromSubtreeClearsExistingParentCountWhenChildrenAreZero() {
        BiObject root = biObject(1L, 0L, 0);
        BiObject parent = biObject(2L, 1L, 5);
        BiObject child = biObject(3L, 2L, 0);

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(root);
        when(biObjectMapper.selectChildrenById(1L)).thenReturn(Arrays.asList(parent, child));
        when(biObjectMapper.updateBiObject(any(BiObject.class))).thenReturn(1);

        int updated = biObjectService.repairCountsFromSubtree(1L);

        assertEquals(1, updated);

        ArgumentCaptor<BiObject> captor = ArgumentCaptor.forClass(BiObject.class);
        verify(biObjectMapper).updateBiObject(captor.capture());

        BiObject updatedObject = captor.getValue();
        assertEquals(Long.valueOf(2L), updatedObject.getId());
        assertEquals(Integer.valueOf(0), updatedObject.getCount());
    }

    @Test
    public void repairCountsFromSubtreeUsesChildTotalWhenParentCountConflicts() {
        BiObject root = biObject(1L, 0L, 0);
        BiObject parent = biObject(2L, 1L, 5);
        BiObject leafA = biObject(3L, 2L, 3);
        BiObject leafB = biObject(4L, 2L, 4);

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(root);
        when(biObjectMapper.selectChildrenById(1L)).thenReturn(Arrays.asList(parent, leafA, leafB));
        when(biObjectMapper.updateBiObject(any(BiObject.class))).thenReturn(1);

        int updated = biObjectService.repairCountsFromSubtree(1L);

        assertEquals(2, updated);

        ArgumentCaptor<BiObject> captor = ArgumentCaptor.forClass(BiObject.class);
        verify(biObjectMapper, times(2)).updateBiObject(captor.capture());

        Map<Long, Integer> countsById = captor.getAllValues().stream()
                .collect(Collectors.toMap(BiObject::getId, BiObject::getCount));
        assertEquals(Integer.valueOf(7), countsById.get(2L));
        assertEquals(Integer.valueOf(7), countsById.get(1L));
    }

    @Test
    public void repairCountsFromSubtreeKeepsFourthLevelBusinessLeafCount() {
        BiObject root = biObject(1L, 0L, 0);
        BiObject superstructure = biObject(2L, 1L, 0);
        BiObject bearingGroup = biObject(3L, 2L, 0);
        BiObject hollowSlab = biObject(4L, 3L, 99);
        BiObject finerNode = biObject(5L, 4L, 0);

        when(biObjectMapper.selectBiObjectById(1L)).thenReturn(root);
        when(biObjectMapper.selectChildrenById(1L))
                .thenReturn(Arrays.asList(superstructure, bearingGroup, hollowSlab, finerNode));
        when(biObjectMapper.updateBiObject(any(BiObject.class))).thenReturn(1);

        int updated = biObjectService.repairCountsFromSubtree(1L);

        assertEquals(3, updated);

        ArgumentCaptor<BiObject> captor = ArgumentCaptor.forClass(BiObject.class);
        verify(biObjectMapper, times(3)).updateBiObject(captor.capture());

        Map<Long, Integer> countsById = captor.getAllValues().stream()
                .collect(Collectors.toMap(BiObject::getId, BiObject::getCount));
        assertEquals(Integer.valueOf(99), countsById.get(3L));
        assertEquals(Integer.valueOf(99), countsById.get(2L));
        assertEquals(Integer.valueOf(99), countsById.get(1L));
    }

    private BiObject biObject(Long id, Long parentId, Integer count) {
        BiObject biObject = new BiObject();
        biObject.setId(id);
        biObject.setParentId(parentId);
        biObject.setCount(count);
        return biObject;
    }
}
