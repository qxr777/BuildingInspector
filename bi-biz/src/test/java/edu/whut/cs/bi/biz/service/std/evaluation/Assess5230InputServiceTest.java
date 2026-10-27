package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Element;
import edu.whut.cs.bi.biz.mapper.AssessAbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.AssessManualInputMapper;
import edu.whut.cs.bi.biz.mapper.AssessSingleControlMarkMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Assess5230InputService} 直连录入 upsert 单测：
 * 自然键去重、UUID 反查失败语义、服务端 offline_uuid 生成与 is_offline_data=0。
 */
@ExtendWith(MockitoExtension.class)
class Assess5230InputServiceTest {

    @InjectMocks
    private Assess5230InputService service;

    @Mock
    private AssessManualInputMapper manualInputMapper;
    @Mock
    private AssessAbsentMarkMapper absentMarkMapper;
    @Mock
    private AssessSingleControlMarkMapper singleControlMarkMapper;
    @Mock
    private ElementMapper elementMapper;
    @Mock
    private BiObjectMapper biObjectMapper;

    private static final String LOGIN = "inspector01";

    @BeforeEach
    void setUp() {
        // 行内校验走真实实现（值域规则另有 AssessInputRowValidatorTest 覆盖）
        ReflectionTestUtils.setField(service, "rowValidator", new AssessInputRowValidator());
    }

    // ---------------- 构件级人工判定 ----------------

    @Test
    void upsertManualInput_newRow_generatesUuidAndInsertsWithCloudOrigin() {
        AssessManualInput row = manualRow();
        when(elementMapper.selectByOfflineUuid("elem-uuid-1")).thenReturn(element(201L, "elem-uuid-1"));
        when(manualInputMapper.selectByTaskAndElement(10L, "elem-uuid-1")).thenReturn(null);

        AssessManualInput saved = service.upsertManualInput(row, LOGIN);

        ArgumentCaptor<AssessManualInput> captor = ArgumentCaptor.forClass(AssessManualInput.class);
        verify(manualInputMapper).insertAssessManualInput(captor.capture());
        AssessManualInput inserted = captor.getValue();
        assertEquals(201L, inserted.getElementId());
        assertEquals(0, inserted.getIsOfflineData());
        assertEquals(0, inserted.getOfflineDeleted());
        assertEquals(LOGIN, inserted.getCreateBy());
        assertNotNull(inserted.getOfflineUuid());
        assertEquals(32, inserted.getOfflineUuid().length());
        assertEquals(0, saved.getSafetyAffected());
    }

    @Test
    void upsertManualInput_existingNaturalKey_updatesKeepingOriginalUuid() {
        AssessManualInput row = manualRow();
        row.setEai(1);
        AssessManualInput existing = new AssessManualInput();
        existing.setId(77L);
        existing.setOfflineUuid("origin-uuid-77");
        existing.setIsOfflineData(1); // 离线通道首录
        when(elementMapper.selectByOfflineUuid("elem-uuid-1")).thenReturn(element(201L, "elem-uuid-1"));
        when(manualInputMapper.selectByTaskAndElement(10L, "elem-uuid-1")).thenReturn(existing);

        AssessManualInput saved = service.upsertManualInput(row, LOGIN);

        assertEquals(77L, saved.getId());
        assertEquals("origin-uuid-77", saved.getOfflineUuid());
        assertEquals(1, saved.getIsOfflineData());
        assertEquals(LOGIN, saved.getUpdateBy());
        verify(manualInputMapper).updateAssessManualInput(any(AssessManualInput.class));
        verify(manualInputMapper, never()).insertAssessManualInput(any());
    }

    @Test
    void upsertManualInput_elementUuidUnresolved_throwsAndNoWrite() {
        AssessManualInput row = manualRow();
        when(elementMapper.selectByOfflineUuid("elem-uuid-1")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.upsertManualInput(row, LOGIN));
        assertTrue(ex.getMessage().contains("反查 bi_element 失败"));
        verify(manualInputMapper, never()).insertAssessManualInput(any());
        verify(manualInputMapper, never()).updateAssessManualInput(any());
    }

    @Test
    void upsertManualInput_invalidRange_throws() {
        AssessManualInput row = manualRow();
        row.setEddi(9);
        assertThrows(ServiceException.class, () -> service.upsertManualInput(row, LOGIN));
        verify(elementMapper, never()).selectByOfflineUuid(any());
    }

    // ---------------- 应设未设标记 ----------------

    @Test
    void saveAbsentMark_newRow_insertsWithResolvedScopeIds() {
        AssessAbsentMark row = new AssessAbsentMark();
        row.setTaskId(10L);
        row.setPartKey("bearing");
        row.setLayerKey("superstructure");
        row.setUnitUuid("unit-uuid-1");
        row.setSpanUuid("span-uuid-2");
        when(biObjectMapper.selectByOfflineUuid("unit-uuid-1")).thenReturn(object(301L));
        when(biObjectMapper.selectByOfflineUuid("span-uuid-2")).thenReturn(object(302L));
        when(absentMarkMapper.selectByTaskId(10L)).thenReturn(Collections.emptyList());

        AssessAbsentMark saved = service.saveAbsentMark(row, LOGIN);

        assertEquals(301L, saved.getUnitId());
        assertEquals(302L, saved.getSpanId());
        assertEquals(0, saved.getIsOfflineData());
        assertNotNull(saved.getOfflineUuid());
        verify(absentMarkMapper).insertAssessAbsentMark(any(AssessAbsentMark.class));
    }

    @Test
    void saveAbsentMark_sameNaturalScopePart_convertsToUpdate() {
        AssessAbsentMark row = new AssessAbsentMark();
        row.setTaskId(10L);
        row.setPartKey("bearing");
        row.setSpanId(302L);

        AssessAbsentMark other = new AssessAbsentMark();
        other.setId(80L);
        other.setTaskId(10L);
        other.setPartKey("expansionJoint");
        AssessAbsentMark same = new AssessAbsentMark();
        same.setId(81L);
        same.setTaskId(10L);
        same.setPartKey("bearing");
        same.setSpanId(302L);
        when(absentMarkMapper.selectByTaskId(10L)).thenReturn(Arrays.asList(other, same));

        AssessAbsentMark saved = service.saveAbsentMark(row, LOGIN);

        assertEquals(81L, saved.getId());
        verify(absentMarkMapper).updateAssessAbsentMark(any(AssessAbsentMark.class));
        verify(absentMarkMapper, never()).insertAssessAbsentMark(any());
    }

    @Test
    void saveAbsentMark_updateByIdFromOtherTask_throws() {
        AssessAbsentMark row = new AssessAbsentMark();
        row.setId(99L);
        row.setTaskId(10L);
        row.setPartKey("bearing");
        AssessAbsentMark owned = new AssessAbsentMark();
        owned.setId(100L);
        owned.setTaskId(10L);
        owned.setPartKey("bearing");
        when(absentMarkMapper.selectByTaskId(10L)).thenReturn(Collections.singletonList(owned));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.saveAbsentMark(row, LOGIN));
        assertTrue(ex.getMessage().contains("不存在或不属于该任务"));
        verify(absentMarkMapper, never()).updateAssessAbsentMark(any());
    }

    @Test
    void saveAbsentMark_unitUuidUnresolved_throws() {
        AssessAbsentMark row = new AssessAbsentMark();
        row.setTaskId(10L);
        row.setPartKey("bearing");
        row.setUnitUuid("unit-uuid-x");
        when(biObjectMapper.selectByOfflineUuid("unit-uuid-x")).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.saveAbsentMark(row, LOGIN));
        verify(absentMarkMapper, never()).insertAssessAbsentMark(any());
    }

    // ---------------- 单项控制 ----------------

    @Test
    void upsertSingleControl_newRow_insertsAndDefaultsHit() {
        AssessSingleControlMark row = singleRow();
        when(singleControlMarkMapper.selectByTaskAndIndicator(10L, 3)).thenReturn(null);

        AssessSingleControlMark saved = service.upsertSingleControl(row, LOGIN);

        assertEquals(1, saved.getHit());
        assertEquals(0, saved.getIsOfflineData());
        assertNotNull(saved.getOfflineUuid());
        assertNull(saved.getSpanId());
        assertNull(saved.getElementId());
        verify(singleControlMarkMapper).insertAssessSingleControlMark(any(AssessSingleControlMark.class));
    }

    @Test
    void upsertSingleControl_evidenceUuidUnresolved_bestEffortStillInserts() {
        AssessSingleControlMark row = singleRow();
        row.setSpanUuid("span-uuid-2");
        row.setElementUuid("elem-uuid-9");
        when(biObjectMapper.selectByOfflineUuid("span-uuid-2")).thenReturn(null);
        when(elementMapper.selectByOfflineUuid("elem-uuid-9")).thenReturn(null);
        when(singleControlMarkMapper.selectByTaskAndIndicator(10L, 3)).thenReturn(null);

        AssessSingleControlMark saved = service.upsertSingleControl(row, LOGIN);

        assertNull(saved.getSpanId());
        assertNull(saved.getElementId());
        verify(singleControlMarkMapper).insertAssessSingleControlMark(any(AssessSingleControlMark.class));
    }

    @Test
    void upsertSingleControl_existingNaturalKey_updates() {
        AssessSingleControlMark row = singleRow();
        AssessSingleControlMark existing = new AssessSingleControlMark();
        existing.setId(55L);
        existing.setOfflineUuid("sc-uuid-55");
        existing.setIsOfflineData(1);
        when(singleControlMarkMapper.selectByTaskAndIndicator(10L, 3)).thenReturn(existing);

        AssessSingleControlMark saved = service.upsertSingleControl(row, LOGIN);

        assertEquals(55L, saved.getId());
        assertEquals("sc-uuid-55", saved.getOfflineUuid());
        verify(singleControlMarkMapper).updateAssessSingleControlMark(any(AssessSingleControlMark.class));
        verify(singleControlMarkMapper, never()).insertAssessSingleControlMark(any());
    }

    @Test
    void upsertSingleControl_invalidIndicator_throws() {
        AssessSingleControlMark row = singleRow();
        row.setIndicatorNo(20);
        assertThrows(ServiceException.class, () -> service.upsertSingleControl(row, LOGIN));
        verify(singleControlMarkMapper, never()).insertAssessSingleControlMark(any());
    }

    // ---------------- 删除委托 ----------------

    @Test
    void deleteMethods_delegateToMappers() {
        service.deleteManualInput(1L);
        service.deleteAbsentMark(2L);
        service.deleteSingleControlMark(3L);
        verify(manualInputMapper).deleteAssessManualInputById(1L);
        verify(absentMarkMapper).deleteAssessAbsentMarkById(2L);
        verify(singleControlMarkMapper).deleteAssessSingleControlMarkById(3L);
    }

    private AssessManualInput manualRow() {
        AssessManualInput row = new AssessManualInput();
        row.setTaskId(10L);
        row.setElementUuid("elem-uuid-1");
        return row;
    }

    private AssessSingleControlMark singleRow() {
        AssessSingleControlMark row = new AssessSingleControlMark();
        row.setTaskId(10L);
        row.setIndicatorNo(3);
        row.setEvidence("现场可见落梁板断裂");
        return row;
    }

    private Element element(Long id, String uuid) {
        Element e = new Element();
        e.setId(id);
        e.setOfflineUuid(uuid);
        return e;
    }

    private BiObject object(Long id) {
        BiObject o = new BiObject();
        o.setId(id);
        return o;
    }
}
