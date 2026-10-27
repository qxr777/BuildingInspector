package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Element;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.AssessAbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.AssessManualInputMapper;
import edu.whut.cs.bi.biz.mapper.AssessSingleControlMarkMapper;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseDetailMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import edu.whut.cs.bi.biz.mapper.IdMappingMapper;
import edu.whut.cs.bi.biz.mapper.SpanElementPartMapper;
import edu.whut.cs.bi.biz.mapper.SyncLogMapper;
import edu.whut.cs.bi.biz.service.std.evaluation.AssessInputRowValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * sync/upload 三数组（assessManualInputs/assessAbsentMarks/assessSingleControlMarks）
 * 处理单测：行校验、UUID 反查、offline_uuid 幂等 upsert 与 offlineDeleted 物理删除。
 */
@ExtendWith(MockitoExtension.class)
class SyncUploadAssessMarksTest {

    @InjectMocks
    private SyncUploadServiceImpl syncUploadService;

    @Mock
    private BuildingMapper buildingMapper;
    @Mock
    private BiObjectMapper biObjectMapper;
    @Mock
    private ComponentMapper componentMapper;
    @Mock
    private DiseaseMapper diseaseMapper;
    @Mock
    private DiseaseDetailMapper diseaseDetailMapper;
    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private IdMappingMapper idMappingMapper;
    @Mock
    private SyncLogMapper syncLogMapper;
    @Mock
    private ElementMapper elementMapper;
    @Mock
    private SpanElementPartMapper spanElementPartMapper;
    @Mock
    private AssessManualInputMapper assessManualInputMapper;
    @Mock
    private AssessAbsentMarkMapper assessAbsentMarkMapper;
    @Mock
    private AssessSingleControlMarkMapper assessSingleControlMarkMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(syncUploadService, "assessInputRowValidator", new AssessInputRowValidator());
    }

    @Test
    void manualInput_newRow_resolvesElementAndInsertsOfflineOrigin() {
        AssessManualInput row = new AssessManualInput();
        row.setOfflineUuid("mi-offline-1");
        row.setTaskId(10L);
        row.setElementUuid("elem-uuid-1");
        row.setEddi(1);
        row.setSafetyAffected(1);

        when(elementMapper.selectByOfflineUuid("elem-uuid-1")).thenReturn(element(201L));
        when(assessManualInputMapper.selectByOfflineUuid("mi-offline-1")).thenReturn(null);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessManualInputs",
                Collections.singletonList(row)));

        assertEquals(1, result.getSuccessCount());
        assertTrue(result.getErrors().isEmpty());
        ArgumentCaptor<AssessManualInput> captor = ArgumentCaptor.forClass(AssessManualInput.class);
        verify(assessManualInputMapper).insertAssessManualInput(captor.capture());
        AssessManualInput inserted = captor.getValue();
        assertEquals(201L, inserted.getElementId());
        assertEquals(1, inserted.getIsOfflineData());
        assertEquals(1, inserted.getEddi());
        assertEquals(1, inserted.getSafetyAffected());
        assertEquals("system", inserted.getCreateBy());
    }

    @Test
    void manualInput_elementUuidUnresolved_rowErrorNoInsert() {
        AssessManualInput row = new AssessManualInput();
        row.setOfflineUuid("mi-offline-2");
        row.setTaskId(10L);
        row.setElementUuid("elem-uuid-x");
        when(elementMapper.selectByOfflineUuid("elem-uuid-x")).thenReturn(null);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessManualInputs",
                Collections.singletonList(row)));

        assertEquals(1, result.getFailureCount());
        assertEquals("AssessManualInput", result.getErrors().get(0).getEntityType());
        assertTrue(result.getErrors().get(0).getMessage().contains("反查 bi_element 失败"));
        verify(assessManualInputMapper, never()).insertAssessManualInput(any());
    }

    @Test
    void manualInput_invalidScale_rowErrorNoInsert() {
        AssessManualInput row = new AssessManualInput();
        row.setOfflineUuid("mi-offline-3");
        row.setTaskId(10L);
        row.setElementUuid("elem-uuid-1");
        row.setEddi(5);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessManualInputs",
                Collections.singletonList(row)));

        assertEquals(1, result.getFailureCount());
        assertTrue(result.getErrors().get(0).getMessage().contains("eddi"));
        verify(elementMapper, never()).selectByOfflineUuid(any());
        verify(assessManualInputMapper, never()).insertAssessManualInput(any());
    }

    @Test
    void manualInput_existingOfflineUuid_updates() {
        AssessManualInput row = new AssessManualInput();
        row.setOfflineUuid("mi-offline-4");
        row.setTaskId(10L);
        row.setElementUuid("elem-uuid-1");
        row.setEfi(2);

        AssessManualInput existing = new AssessManualInput();
        existing.setId(77L);
        existing.setOfflineUuid("mi-offline-4");
        when(elementMapper.selectByOfflineUuid("elem-uuid-1")).thenReturn(element(201L));
        when(assessManualInputMapper.selectByOfflineUuid("mi-offline-4")).thenReturn(existing);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessManualInputs",
                Collections.singletonList(row)));

        assertEquals(1, result.getSuccessCount());
        verify(assessManualInputMapper).updateAssessManualInput(any(AssessManualInput.class));
        verify(assessManualInputMapper, never()).insertAssessManualInput(any());
    }

    @Test
    void manualInput_offlineDeleted_existingRowPhysicallyDeleted() {
        AssessManualInput row = new AssessManualInput();
        row.setOfflineUuid("mi-offline-5");
        row.setTaskId(10L);
        row.setElementUuid("elem-uuid-1");
        row.setOfflineDeleted(1);

        AssessManualInput existing = new AssessManualInput();
        existing.setId(78L);
        existing.setOfflineUuid("mi-offline-5");
        when(elementMapper.selectByOfflineUuid("elem-uuid-1")).thenReturn(element(201L));
        when(assessManualInputMapper.selectByOfflineUuid("mi-offline-5")).thenReturn(existing);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessManualInputs",
                Collections.singletonList(row)));

        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        verify(assessManualInputMapper).deleteAssessManualInputById(78L);
        verify(assessManualInputMapper, never()).updateAssessManualInput(any());
    }

    @Test
    void absentMark_newRow_resolvesSpanAndInserts() {
        AssessAbsentMark row = new AssessAbsentMark();
        row.setOfflineUuid("am-offline-1");
        row.setTaskId(10L);
        row.setPartKey("bearing");
        row.setLayerKey("superstructure");
        row.setSpanUuid("span-uuid-1");

        when(biObjectMapper.selectByOfflineUuid("span-uuid-1")).thenReturn(object(302L));
        when(assessAbsentMarkMapper.selectByOfflineUuid("am-offline-1")).thenReturn(null);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessAbsentMarks",
                Collections.singletonList(row)));

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<AssessAbsentMark> captor = ArgumentCaptor.forClass(AssessAbsentMark.class);
        verify(assessAbsentMarkMapper).insertAssessAbsentMark(captor.capture());
        assertEquals(302L, captor.getValue().getSpanId());
        assertEquals(1, captor.getValue().getIsOfflineData());
    }

    @Test
    void absentMark_unitUuidUnresolved_rowError() {
        AssessAbsentMark row = new AssessAbsentMark();
        row.setOfflineUuid("am-offline-2");
        row.setTaskId(10L);
        row.setPartKey("bearing");
        row.setUnitUuid("unit-uuid-x");
        when(biObjectMapper.selectByOfflineUuid("unit-uuid-x")).thenReturn(null);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessAbsentMarks",
                Collections.singletonList(row)));

        assertEquals(1, result.getFailureCount());
        assertTrue(result.getErrors().get(0).getMessage().contains("unitUuid"));
        verify(assessAbsentMarkMapper, never()).insertAssessAbsentMark(any());
    }

    @Test
    void singleControl_newRow_inserts() {
        AssessSingleControlMark row = new AssessSingleControlMark();
        row.setOfflineUuid("sc-offline-1");
        row.setTaskId(10L);
        row.setIndicatorNo(7);

        when(assessSingleControlMarkMapper.selectByOfflineUuid("sc-offline-1")).thenReturn(null);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessSingleControlMarks",
                Collections.singletonList(row)));

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<AssessSingleControlMark> captor = ArgumentCaptor.forClass(AssessSingleControlMark.class);
        verify(assessSingleControlMarkMapper).insertAssessSingleControlMark(captor.capture());
        // hit 缺省由校验器在 fastjson 解析出的对象上回填为 1
        assertEquals(1, captor.getValue().getHit());
    }

    @Test
    void singleControl_evidenceUuidUnresolved_bestEffortStillUpdates() {
        AssessSingleControlMark row = new AssessSingleControlMark();
        row.setOfflineUuid("sc-offline-2");
        row.setTaskId(10L);
        row.setIndicatorNo(7);
        row.setSpanUuid("span-uuid-x");
        row.setElementUuid("elem-uuid-x");

        AssessSingleControlMark existing = new AssessSingleControlMark();
        existing.setId(55L);
        existing.setOfflineUuid("sc-offline-2");
        when(biObjectMapper.selectByOfflineUuid("span-uuid-x")).thenReturn(null);
        when(elementMapper.selectByOfflineUuid("elem-uuid-x")).thenReturn(null);
        when(assessSingleControlMarkMapper.selectByOfflineUuid("sc-offline-2")).thenReturn(existing);

        SyncResultVo result = syncUploadService.syncUpload(payload("assessSingleControlMarks",
                Collections.singletonList(row)));

        assertEquals(1, result.getSuccessCount());
        verify(assessSingleControlMarkMapper).updateAssessSingleControlMark(any(AssessSingleControlMark.class));
    }

    @Test
    void emptyPayload_assessArraysAbsent_noErrorsNoWrites() {
        SyncResultVo result = syncUploadService.syncUpload(new HashMap<>());

        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertTrue(result.getErrors().isEmpty());
        verify(assessManualInputMapper, never()).insertAssessManualInput(any());
        verify(assessAbsentMarkMapper, never()).insertAssessAbsentMark(any());
        verify(assessSingleControlMarkMapper, never()).insertAssessSingleControlMark(any());
    }

    private Map<String, Object> payload(String key, Object list) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, list);
        return map;
    }

    private Element element(Long id) {
        Element e = new Element();
        e.setId(id);
        return e;
    }

    private BiObject object(Long id) {
        BiObject o = new BiObject();
        o.setId(id);
        return o;
    }
}
