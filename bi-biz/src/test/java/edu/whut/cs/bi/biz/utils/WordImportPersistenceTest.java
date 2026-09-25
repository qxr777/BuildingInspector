package edu.whut.cs.bi.biz.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import edu.whut.cs.bi.biz.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class WordImportPersistenceTest {
    @Test
    void storesSourceFileAndTypedReferenceForGeneration() throws Exception {
        IFileMapService files = mock(IFileMapService.class);
        ILineMultiBridgeReportDataService data = mock(ILineMultiBridgeReportDataService.class);
        LineReportWordImportService service = persistenceOnlyService();
        ReflectionTestUtils.setField(service, "files", files); ReflectionTestUtils.setField(service, "dataService", data);
        FileMap uploaded = new FileMap(); uploaded.setId(42); when(files.handleFileUpload(any())).thenReturn(uploaded);
        var parsed = parsed(); var selection = selection();
        int count = service.apply(9L, new MockMultipartFile("file", "来源.docx", "application/octet-stream", new byte[]{1}), parsed, List.of(selection));
        assertEquals(1, count);
        ArgumentCaptor<List<LineReportData>> rows = ArgumentCaptor.forClass(List.class);
        verify(data).saveWordImports(eq(9L), rows.capture(), anyMap());
        LineReportData saved = rows.getValue().get(0);
        assertEquals(2, saved.getType()); assertEquals("g1", saved.getGroupId()); assertEquals("mainMaterial", saved.getKey());
        var reference = WordImportSupport.reference(saved);
        assertEquals(42L, reference.getFileId()); assertEquals(2, reference.getStart()); assertEquals(4, reference.getEnd());
        verify(files, never()).deleteFileMapById(anyLong());
    }

    @Test
    void failedSaveCleansNewUploadAndDoesNotPretendSuccess() throws Exception {
        IFileMapService files = mock(IFileMapService.class);
        ILineMultiBridgeReportDataService data = mock(ILineMultiBridgeReportDataService.class);
        LineReportWordImportService service = persistenceOnlyService();
        ReflectionTestUtils.setField(service, "files", files); ReflectionTestUtils.setField(service, "dataService", data);
        FileMap uploaded = new FileMap(); uploaded.setId(42); when(files.handleFileUpload(any())).thenReturn(uploaded);
        when(data.saveWordImports(anyLong(), anyList(), anyMap())).thenThrow(new IllegalArgumentException("conflict"));
        assertThrows(IllegalArgumentException.class, () -> service.apply(9L,
                new MockMultipartFile("file", "来源.docx", "application/octet-stream", new byte[]{1}), parsed(), List.of(selection())));
        verify(files).deleteFileMapById(42L);
    }

    @Test
    void autosaveCannotOverwriteWordAndStaleImportIsRejectedUnderReportLock() {
        ReportDataMapper mapper = mock(ReportDataMapper.class); ReportMapper reports = mock(ReportMapper.class);
        LineMultiBridgeReportDataServiceImpl service = new LineMultiBridgeReportDataServiceImpl();
        ReflectionTestUtils.setField(service, "reportDataMapper", mapper); ReflectionTestUtils.setField(service, "reportMapper", reports);
        when(reports.lockForDataUpdate(9L)).thenReturn(9L);
        ReportData existing = new ReportData(); existing.setId(1L); existing.setKey("__group_g1__mainMaterial"); existing.setType(2); existing.setValue("source-reference");
        when(mapper.selectReportDataByReportId(9L)).thenReturn(List.of(existing));
        LineReportData text = new LineReportData(); text.setGroupId("g1"); text.setKey("mainMaterial"); text.setType(0); text.setValue("");
        assertEquals(0, service.saveBatch(9L, List.of(text)));
        text.setType(2);
        assertThrows(IllegalArgumentException.class, () -> service.saveWordImports(9L, List.of(text),
                Map.of(WordImportSupport.scopeKey("g1", "mainMaterial"), "stale")));
        verify(mapper, never()).updateReportData(any()); verify(mapper, never()).batchInsertReportData(anyList());
    }

    private LineReportWordMatcher.Result parsed() {
        var parsed = new LineReportWordMatcher.Result();
        var source = new LineReportWordMatcher.Section(); source.setId("s1"); source.setKey("mainMaterial"); source.setStart(2); source.setEnd(4); source.setText("材料");
        var target = new LineReportWordMatcher.Target(); target.setId("t1"); target.setGroupId("g1"); target.setKey("mainMaterial"); target.setVersion("v1");
        parsed.getSections().add(source); parsed.getTargets().add(target); return parsed;
    }
    private LineReportWordImportService persistenceOnlyService() {
        // OOXML validation is exercised by WordImportWorkflowTest and the real-source verification.
        return new LineReportWordImportService() {
            @Override protected void validateSource(org.springframework.web.multipart.MultipartFile file, List<LineReportData> rows) { }
        };
    }
    private LineReportWordImportService.Selection selection() {
        var s = new LineReportWordImportService.Selection(); s.setSourceId("s1"); s.setGroupId("g1"); s.setKey("mainMaterial"); s.setExpectedVersion("v1"); return s;
    }
}
