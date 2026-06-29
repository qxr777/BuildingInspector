package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.vo.BatchBridgeCardImportResult;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReadFileServiceImplTest {

    @Mock
    private BuildingMapper buildingMapper;

    @Mock
    private IPropertyService propertyService;

    @InjectMocks
    private ReadFileServiceImpl readFileService;

    @Test
    public void batchImportBridgeCardsExtractsBridgeNameAndImportsMatchingWord() throws Exception {
        Building building = new Building();
        building.setId(99L);
        building.setName("吊羊岩桥");
        when(buildingMapper.selectBuildingExactList(any(Building.class))).thenReturn(Collections.singletonList(building));
        when(propertyService.readWordFile(any(MultipartFile.class), any(Property.class), eq(99L))).thenReturn(true);

        MockMultipartFile zip = zipFile("cards/桥梁基本状况卡片2021-吊羊岩桥.docx", "word-content");

        BatchBridgeCardImportResult result = readFileService.batchImportBridgeCards(zip, null);

        assertEquals(1, result.getSuccessCount());
        assertTrue(result.getFailures().isEmpty());

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(propertyService).readWordFile(fileCaptor.capture(), any(Property.class), eq(99L));
        assertEquals("桥梁基本状况卡片2021-吊羊岩桥.docx", fileCaptor.getValue().getOriginalFilename());
    }

    @Test
    public void batchImportBridgeCardsRecordsFailureWhenBridgeNameDoesNotMatch() throws Exception {
        when(buildingMapper.selectBuildingExactList(any(Building.class))).thenReturn(Collections.emptyList());

        MockMultipartFile zip = zipFile("桥梁基本状况卡片2021-不存在桥.docx", "word-content");

        BatchBridgeCardImportResult result = readFileService.batchImportBridgeCards(zip, null);

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailures().size());
        assertEquals("桥梁基本状况卡片2021-不存在桥.docx", result.getFailures().get(0).getFileName());
        assertTrue(result.getFailures().get(0).getReason().contains("未找到桥梁"));
        verify(propertyService, never()).readWordFile(any(MultipartFile.class), any(Property.class), any());
    }

    @Test
    public void batchImportBridgeCardsSupportsGbkEncodedZipEntryNames() throws Exception {
        Building building = new Building();
        building.setId(100L);
        building.setName("吊羊岩桥");
        when(buildingMapper.selectBuildingExactList(any(Building.class))).thenReturn(Collections.singletonList(building));
        when(propertyService.readWordFile(any(MultipartFile.class), any(Property.class), eq(100L))).thenReturn(true);

        MockMultipartFile zip = zipFile("子目录/桥梁基本状况卡片2021-吊羊岩桥.docx", "word-content", Charset.forName("GBK"));

        BatchBridgeCardImportResult result = readFileService.batchImportBridgeCards(zip, null);

        assertEquals(1, result.getSuccessCount());
        assertTrue(result.getFailures().isEmpty());
    }

    @Test
    public void batchImportBridgeCardsSkipsBridgeThatAlreadyHasCard() throws Exception {
        Building building = new Building();
        building.setId(101L);
        building.setName("吊羊岩桥");
        building.setRootPropertyId(2001L);
        when(buildingMapper.selectBuildingExactList(any(Building.class))).thenReturn(Collections.singletonList(building));

        MockMultipartFile zip = zipFile("桥梁基本状况卡片2021-吊羊岩桥.docx", "word-content");

        BatchBridgeCardImportResult result = readFileService.batchImportBridgeCards(zip, null);

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals("桥梁基本状况卡片2021-吊羊岩桥.docx", result.getSkipped().get(0).getFileName());
        assertEquals("吊羊岩桥", result.getSkipped().get(0).getBridgeName());
        assertTrue(result.getSkipped().get(0).getReason().contains("已存在桥梁卡片"));
        verify(propertyService, never()).readWordFile(any(MultipartFile.class), any(Property.class), any());
    }

    private MockMultipartFile zipFile(String entryName, String content) throws IOException {
        return zipFile(entryName, content, null);
    }

    private MockMultipartFile zipFile(String entryName, String content, Charset charset) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOut = charset == null
                ? new ZipOutputStream(outputStream)
                : new ZipOutputStream(outputStream, charset);
        try (zipOut) {
            zipOut.putNextEntry(new ZipEntry(entryName));
            zipOut.write(content.getBytes());
            zipOut.closeEntry();
        }
        return new MockMultipartFile("file", "bridge-cards.zip", "application/zip", outputStream.toByteArray());
    }
}
