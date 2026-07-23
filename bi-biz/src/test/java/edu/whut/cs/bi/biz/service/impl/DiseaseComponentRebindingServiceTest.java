package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseDetailMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.IComponentService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DiseaseComponentRebindingServiceTest {

    @Mock
    private DiseaseMapper diseaseMapper;

    @Mock
    private DiseaseDetailMapper diseaseDetailMapper;

    @Mock
    private IComponentService componentService;

    @Mock
    private ComponentMapper componentMapper;

    @Mock
    private BiObjectMapper biObjectMapper;

    @InjectMocks
    private DiseaseServiceImpl diseaseService;

    @Test
    public void newUpdateDiseaseRebindsToExistingTargetComponent() {
        Disease oldDisease = oldDisease();
        Disease editedDisease = editedDisease();
        Component oldComponent = component(10L, 20L, "OLD");
        Component targetComponent = component(11L, 30L, "NEW");
        Component duplicateTargetComponent = component(13L, 30L, "NEW");

        when(diseaseMapper.selectDiseaseById(1L)).thenReturn(oldDisease);
        when(componentService.selectComponentById(10L)).thenReturn(oldComponent);
        when(componentMapper.selectComponentList(any(Component.class)))
                .thenReturn(Arrays.asList(duplicateTargetComponent, targetComponent));
        when(diseaseMapper.updateDisease(editedDisease)).thenReturn(1);

        int updated = diseaseService.newUpdateDisease(editedDisease);

        assertEquals(1, updated);
        assertEquals(Long.valueOf(11L), editedDisease.getComponentId());
        ArgumentCaptor<Component> queryCaptor = ArgumentCaptor.forClass(Component.class);
        verify(componentMapper).selectComponentList(queryCaptor.capture());
        assertEquals(Long.valueOf(30L), queryCaptor.getValue().getBiObjectId());
        assertEquals("NEW", queryCaptor.getValue().getCode());
        verify(componentService, never()).insertComponent(any(Component.class));
        verify(componentService, never()).updateComponent(any(Component.class));
    }

    @Test
    public void newUpdateDiseaseCreatesTargetAndDoesNotMutateSharedOldComponent() {
        Disease oldDisease = oldDisease();
        Disease editedDisease = editedDisease();
        Component oldComponent = component(10L, 20L, "OLD");

        when(diseaseMapper.selectDiseaseById(1L)).thenReturn(oldDisease);
        when(componentService.selectComponentById(10L)).thenReturn(oldComponent);
        when(componentMapper.selectComponentList(any(Component.class))).thenReturn(Collections.emptyList());
        BiObject targetBiObject = new BiObject();
        targetBiObject.setId(30L);
        targetBiObject.setName("桥墩");
        when(biObjectMapper.selectBiObjectById(30L)).thenReturn(targetBiObject);
        doAnswer(invocation -> {
            Component inserted = invocation.getArgument(0);
            inserted.setId(12L);
            return 1;
        }).when(componentService).insertComponent(any(Component.class));
        when(diseaseMapper.updateDisease(editedDisease)).thenReturn(1);

        int updated = diseaseService.newUpdateDisease(editedDisease);

        assertEquals(1, updated);
        assertEquals(Long.valueOf(12L), editedDisease.getComponentId());
        ArgumentCaptor<Component> insertedCaptor = ArgumentCaptor.forClass(Component.class);
        verify(componentService).insertComponent(insertedCaptor.capture());
        Component inserted = insertedCaptor.getValue();
        assertEquals(Long.valueOf(30L), inserted.getBiObjectId());
        assertEquals("NEW", inserted.getCode());
        assertEquals("NEW#桥墩", inserted.getName());
        assertEquals("0", inserted.getStatus());
        assertEquals("0", inserted.getDelFlag());
        verify(componentService, never()).updateComponent(any(Component.class));
    }

    @Test
    public void updateDiseaseUsesTheSameSafeRebindingLogic() {
        Disease oldDisease = oldDisease();
        Disease editedDisease = editedDisease();
        Component oldComponent = component(10L, 20L, "OLD");
        Component targetComponent = component(21L, 30L, "NEW");

        when(diseaseMapper.selectDiseaseById(1L)).thenReturn(oldDisease);
        when(componentService.selectComponentById(10L)).thenReturn(oldComponent);
        when(componentMapper.selectComponentList(any(Component.class)))
                .thenReturn(Collections.singletonList(targetComponent));
        when(diseaseMapper.updateDisease(editedDisease)).thenReturn(1);

        int updated = diseaseService.updateDisease(editedDisease);

        assertEquals(1, updated);
        assertEquals(Long.valueOf(21L), editedDisease.getComponentId());
        verify(componentService, never()).updateComponent(any(Component.class));
    }

    private Disease oldDisease() {
        Disease disease = new Disease();
        disease.setId(1L);
        disease.setDiseaseTypeId(1L);
        disease.setBiObjectId(20L);
        disease.setComponentId(10L);
        return disease;
    }

    private Disease editedDisease() {
        Disease disease = new Disease();
        disease.setId(1L);
        disease.setDiseaseTypeId(2L);
        disease.setBiObjectId(30L);
        disease.setBiObjectName("桥墩");
        disease.setComponentId(10L);
        disease.setUpdateBy("tester");
        Component component = new Component();
        component.setCode("NEW");
        disease.setComponent(component);
        return disease;
    }

    private Component component(Long id, Long biObjectId, String code) {
        Component component = new Component();
        component.setId(id);
        component.setBiObjectId(biObjectId);
        component.setCode(code);
        return component;
    }
}
