package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.V2AbsentMark;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseDetailMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.IdMappingMapper;
import edu.whut.cs.bi.biz.mapper.SpanComponentPartMapper;
import edu.whut.cs.bi.biz.mapper.SyncLogMapper;
import edu.whut.cs.bi.biz.mapper.V2AbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.V2ComponentInputMapper;
import edu.whut.cs.bi.biz.mapper.V2SingleControlMarkMapper;
import edu.whut.cs.bi.biz.service.v2.V2InputRowValidator;
import edu.whut.cs.bi.biz.service.v2.V2TaskContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncUploadServiceImplTest {

    private static final Long TASK_ID = 1L;
    private static final Long COMPONENT_ID = 400L;
    private static final Long PART_ID = 300L;
    private static final String COMP_UUID = "comp-uuid";
    private static final String PART_UUID = "part-uuid";
    private static final String CI_UUID = "ci-uuid";
    private static final String AM_UUID = "am-uuid";
    private static final String SC_UUID = "sc-uuid";

    @Mock private BuildingMapper buildingMapper;
    @Mock private BiObjectMapper biObjectMapper;
    @Mock private ComponentMapper componentMapper;
    @Mock private DiseaseMapper diseaseMapper;
    @Mock private DiseaseDetailMapper diseaseDetailMapper;
    @Mock private AttachmentMapper attachmentMapper;
    @Mock private IdMappingMapper idMappingMapper;
    @Mock private SyncLogMapper syncLogMapper;
    @Mock private SpanComponentPartMapper spanComponentPartMapper;
    @Mock private V2ComponentInputMapper v2ComponentInputMapper;
    @Mock private V2AbsentMarkMapper v2AbsentMarkMapper;
    @Mock private V2SingleControlMarkMapper v2SingleControlMarkMapper;
    @Mock private V2InputRowValidator v2InputRowValidator;
    @Mock private V2TaskContextResolver v2TaskContextResolver;

    @InjectMocks
    private SyncUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        Task task = new Task();
        task.setId(TASK_ID);
        lenient().when(v2TaskContextResolver.require5230Task(TASK_ID)).thenReturn(task);
        lenient().when(v2TaskContextResolver.bridgeTypeOf(TASK_ID)).thenReturn("B03");

        // 校验器默认放行；component 校验模拟 componentId 必填规则
        lenient().when(v2InputRowValidator.validateComponent(any())).thenAnswer(inv -> {
            V2ComponentInput row = inv.getArgument(0);
            return (row == null || row.getComponentId() == null)
                    ? List.of("构件输入缺少 componentId") : new ArrayList<String>();
        });
        lenient().when(v2InputRowValidator.validateAbsent(any(), eq("B03")))
                .thenReturn(new ArrayList<>());
        lenient().when(v2InputRowValidator.validateControl(any()))
                .thenReturn(new ArrayList<>());
    }

    // ---------- ComponentInput ----------

    @Test
    void componentInputInsertSavesMapping() {
        stubComponentAnchor();
        when(v2ComponentInputMapper.selectByOfflineUuid(CI_UUID)).thenReturn(null);
        when(v2ComponentInputMapper.insert(any())).thenAnswer(inv -> {
            inv.<V2ComponentInput>getArgument(0).setId(900L);
            return 1;
        });

        SyncResultVo result = service.syncUpload(componentPayload(CI_UUID, COMP_UUID, 0));

        ArgumentCaptor<V2ComponentInput> captor = ArgumentCaptor.forClass(V2ComponentInput.class);
        verify(v2ComponentInputMapper).insert(captor.capture());
        assertEquals(COMPONENT_ID, captor.getValue().getComponentId());
        assertEquals(0, result.getFailureCount());
        assertTrue(result.getIdMappings().stream()
                .anyMatch(m -> "V2ComponentInput".equals(m.getEntityType()) && m.getServerId() == 900L));
    }

    @Test
    void componentInputExistingIsUpdated() {
        stubComponentAnchor();
        V2ComponentInput existing = new V2ComponentInput();
        existing.setId(500L);
        when(v2ComponentInputMapper.selectByOfflineUuid(CI_UUID)).thenReturn(existing);

        SyncResultVo result = service.syncUpload(componentPayload(CI_UUID, COMP_UUID, 0));

        verify(v2ComponentInputMapper).update(any());
        verify(v2ComponentInputMapper, never()).insert(any());
        assertEquals(0, result.getFailureCount());
    }

    @Test
    void componentInputDeletedExistingIsPhysicallyDeleted() {
        stubComponentAnchor();
        V2ComponentInput existing = new V2ComponentInput();
        existing.setId(500L);
        when(v2ComponentInputMapper.selectByOfflineUuid(CI_UUID)).thenReturn(existing);

        service.syncUpload(componentPayload(CI_UUID, COMP_UUID, 1));

        verify(v2ComponentInputMapper).deleteById(500L);
        verify(v2ComponentInputMapper, never()).update(any());
    }

    @Test
    void componentInputDeletedMissingIsNoOp() {
        stubComponentAnchor();
        when(v2ComponentInputMapper.selectByOfflineUuid(CI_UUID)).thenReturn(null);

        service.syncUpload(componentPayload(CI_UUID, COMP_UUID, 1));

        verify(v2ComponentInputMapper, never()).deleteById(any());
        verify(v2ComponentInputMapper, never()).insert(any());
        verify(v2ComponentInputMapper, never()).update(any());
    }

    @Test
    void componentInputUnresolvedComponentUuidsReported() {
        SyncResultVo result = service.syncUpload(componentPayload(CI_UUID, "missing", 0));

        assertEquals(1, result.getFailureCount());
        verify(v2ComponentInputMapper, never()).insert(any());
    }

    @Test
    void componentInputValidatorErrorsSkipRow() {
        stubComponentAnchor();
        when(v2InputRowValidator.validateComponent(any())).thenReturn(List.of("eddi 越界"));

        SyncResultVo result = service.syncUpload(componentPayload(CI_UUID, COMP_UUID, 0));

        assertEquals(1, result.getFailureCount());
        verify(v2ComponentInputMapper, never()).insert(any());
    }

    @Test
    void componentInputDuplicateInSameBatchReported() {
        stubComponentAnchor();
        Map<String, Object> payload = basePayload();
        Map<String, Object> anchorComponent = new HashMap<>();
        anchorComponent.put("offlineUuid", COMP_UUID);
        payload.put("components", List.of(anchorComponent));
        payload.put("componentInputs", List.of(
                componentRow("ci-1", COMP_UUID, 0),
                componentRow("ci-2", COMP_UUID, 0)));

        SyncResultVo result = service.syncUpload(payload);

        assertEquals(1, result.getFailureCount());
        verify(v2ComponentInputMapper).insert(any());
    }

    // ---------- AbsentMark ----------

    @Test
    void absentMarkNullSpanInsertsWithNullSpanId() {
        stubPartAnchor();
        when(v2AbsentMarkMapper.selectByOfflineUuid(AM_UUID)).thenReturn(null);
        when(v2AbsentMarkMapper.insert(any())).thenAnswer(inv -> {
            inv.<V2AbsentMark>getArgument(0).setId(901L);
            return 1;
        });

        Map<String, Object> payload = basePayload();
        Map<String, Object> anchorObject = new HashMap<>();
        anchorObject.put("offlineUuid", PART_UUID);
        payload.put("objects", List.of(anchorObject));
        Map<String, Object> row = absentRow(AM_UUID, null, PART_UUID, 0);
        payload.put("absentMarks", List.of(row));

        SyncResultVo result = service.syncUpload(payload);

        ArgumentCaptor<V2AbsentMark> captor = ArgumentCaptor.forClass(V2AbsentMark.class);
        verify(v2AbsentMarkMapper).insert(captor.capture());
        assertNull(captor.getValue().getSpanId());
        assertEquals(PART_ID, captor.getValue().getPartId());
        assertEquals(0, result.getFailureCount());
    }

    @Test
    void absentMarkGivenUnresolvedSpanIsReported() {
        Map<String, Object> payload = basePayload();
        payload.put("absentMarks", List.of(absentRow(AM_UUID, "missing-span", PART_UUID, 0)));

        SyncResultVo result = service.syncUpload(payload);

        assertEquals(1, result.getFailureCount());
        verify(v2AbsentMarkMapper, never()).insert(any());
    }

    @Test
    void absentMarkValidatorErrorsSkipRow() {
        stubPartAnchor();
        when(v2InputRowValidator.validateAbsent(any(), eq("B03"))).thenReturn(List.of("非模板部件"));

        Map<String, Object> payload = basePayload();
        Map<String, Object> anchorObject = new HashMap<>();
        anchorObject.put("offlineUuid", PART_UUID);
        payload.put("objects", List.of(anchorObject));
        payload.put("absentMarks", List.of(absentRow(AM_UUID, null, PART_UUID, 0)));

        SyncResultVo result = service.syncUpload(payload);

        assertEquals(1, result.getFailureCount());
        verify(v2AbsentMarkMapper, never()).insert(any());
    }

    // ---------- SingleControlMark ----------

    @Test
    void singleControlNullUuidsInserts() {
        when(v2SingleControlMarkMapper.selectByOfflineUuid(SC_UUID)).thenReturn(null);
        when(v2SingleControlMarkMapper.insert(any())).thenAnswer(inv -> {
            inv.<V2SingleControlMark>getArgument(0).setId(902L);
            return 1;
        });

        Map<String, Object> payload = basePayload();
        Map<String, Object> row = new HashMap<>();
        row.put("offlineUuid", SC_UUID);
        row.put("taskId", TASK_ID);
        row.put("indicatorNo", 1);
        row.put("hit", 1);
        payload.put("singleControlMarks", List.of(row));

        SyncResultVo result = service.syncUpload(payload);

        ArgumentCaptor<V2SingleControlMark> captor = ArgumentCaptor.forClass(V2SingleControlMark.class);
        verify(v2SingleControlMarkMapper).insert(captor.capture());
        assertNull(captor.getValue().getComponentId());
        assertNull(captor.getValue().getSpanId());
        assertEquals(0, result.getFailureCount());
    }

    @Test
    void singleControlGivenUnresolvedComponentIsReported() {
        Map<String, Object> payload = basePayload();
        Map<String, Object> row = new HashMap<>();
        row.put("offlineUuid", SC_UUID);
        row.put("taskId", TASK_ID);
        row.put("indicatorNo", 1);
        row.put("hit", 1);
        row.put("componentUuid", "missing-comp");
        payload.put("singleControlMarks", List.of(row));

        SyncResultVo result = service.syncUpload(payload);

        assertEquals(1, result.getFailureCount());
        verify(v2SingleControlMarkMapper, never()).insert(any());
    }

    // ---------- helpers ----------

    private void stubComponentAnchor() {
        Component component = new Component();
        component.setId(COMPONENT_ID);
        when(componentMapper.selectByOfflineUuid(COMP_UUID)).thenReturn(component);
    }

    private void stubPartAnchor() {
        BiObject part = new BiObject();
        part.setId(PART_ID);
        when(biObjectMapper.selectByOfflineUuid(PART_UUID)).thenReturn(part);
    }

    private Map<String, Object> componentPayload(String uuid, String componentUuid, int deleted) {
        Map<String, Object> payload = basePayload();
        // 构件锚定行：先走 Component 同步，把 componentUuid → serverId 放进 uuidMap
        Map<String, Object> anchorComponent = new HashMap<>();
        anchorComponent.put("offlineUuid", componentUuid);
        payload.put("components", List.of(anchorComponent));
        payload.put("componentInputs", List.of(componentRow(uuid, componentUuid, deleted)));
        return payload;
    }

    private Map<String, Object> componentRow(String uuid, String componentUuid, int deleted) {
        Map<String, Object> row = new HashMap<>();
        row.put("offlineUuid", uuid);
        row.put("taskId", TASK_ID);
        row.put("componentUuid", componentUuid);
        row.put("offlineDeleted", deleted);
        return row;
    }

    private Map<String, Object> absentRow(String uuid, String spanUuid, String partUuid, int deleted) {
        Map<String, Object> row = new HashMap<>();
        row.put("offlineUuid", uuid);
        row.put("taskId", TASK_ID);
        row.put("partUuid", partUuid);
        row.put("offlineDeleted", deleted);
        if (spanUuid != null) {
            row.put("spanUuid", spanUuid);
        }
        return row;
    }

    private Map<String, Object> basePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", java.util.UUID.randomUUID().toString());
        return payload;
    }
}
