package edu.whut.cs.bi.api.service.impl;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IDiseaseService;
import edu.whut.cs.bi.biz.service.impl.DiseaseServiceImpl;
import edu.whut.cs.bi.biz.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiServiceImplTest {

    @InjectMocks
    private ApiServiceImpl apiService;

    @Mock
    private IBuildingService buildingService;

    @Mock
    private IBiObjectService biObjectService;

    @Mock
    private IDiseaseService diseaseService;

    @Mock
    private FileMapController fileMapController;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private DiseaseServiceImpl diseaseServiceImpl;

    @Mock
    private TaskServiceImpl taskServiceImpl;

    @Mock
    private TaskMapper taskMapper;

    /**
     * 测试 uploadBridgeData：上传合法 ZIP 且建筑存在时，流程正常返回成功。
     */
    @Test
    void testUploadBridgeData_Success() throws IOException {
        long buildingId = 1001L;
        byte[] zipBytes = createZipBytes();
        MockMultipartFile zipFile = new MockMultipartFile(
                "file",
                buildingId + ".zip",
                "application/zip",
                zipBytes
        );

        Building building = new Building();
        building.setId(buildingId);
        when(buildingService.selectBuildingById(buildingId)).thenReturn(building);

        AjaxResult result = apiService.uploadBridgeData(zipFile);

        assertEquals("桥梁数据上传成功", result.get("msg"));
        verify(buildingService, times(1)).selectBuildingById(buildingId);
    }

    /**
     * 测试 uploadBridgeData：上传非 ZIP 文件时，抛出业务异常。
     */
    @Test
    void testUploadBridgeData_InvalidExtension() {
        MockMultipartFile nonZipFile = new MockMultipartFile(
                "file",
                "1001.txt",
                "text/plain",
                "not-zip".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(ServiceException.class, () -> apiService.uploadBridgeData(nonZipFile));
    }

    /**
     * 测试 uploadBridgeDataImage：前视图与侧视图图片均上传时，调用附件上传接口。
     */
    @Test
    void testUploadBridgeDataImage_Success() {
        long buildingId = 2002L;
        MultipartFile[] frontFiles = new MultipartFile[]{
                new MockMultipartFile("front", "front1.jpg", "image/jpeg", "f1".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("front", "front2.jpg", "image/jpeg", "f2".getBytes(StandardCharsets.UTF_8))
        };
        MultipartFile[] sideFiles = new MultipartFile[]{
                new MockMultipartFile("side", "side1.jpg", "image/jpeg", "s1".getBytes(StandardCharsets.UTF_8))
        };

        apiService.uploadBridgeDataImage(buildingId, frontFiles, sideFiles);

        verify(fileMapController, times(3)).uploadAttachment(anyLong(), any(MultipartFile.class), anyString(), anyInt());
        verify(fileMapController, times(1)).uploadAttachment(buildingId, sideFiles[0], "newside", 0);
        verify(fileMapController, times(1)).uploadAttachment(buildingId, frontFiles[0], "newfront", 0);
        verify(fileMapController, times(1)).uploadAttachment(buildingId, frontFiles[1], "newfront", 1);
    }

    /**
     * 测试 uploadBridgeDataImage：上传附件过程中下游异常时，向上抛出运行时异常。
     */
    @Test
    void testUploadBridgeDataImage_UploadAttachmentException() {
        long buildingId = 3003L;
        MultipartFile[] frontFiles = new MultipartFile[]{
                new MockMultipartFile("front", "front1.jpg", "image/jpeg", "f1".getBytes(StandardCharsets.UTF_8))
        };
        MultipartFile[] sideFiles = new MultipartFile[0];

        doThrow(new RuntimeException("upload failed"))
                .when(fileMapController)
                .uploadAttachment(buildingId, frontFiles[0], "newfront", 0);

        assertThrows(RuntimeException.class, () -> apiService.uploadBridgeDataImage(buildingId, frontFiles, sideFiles));
    }

    private byte[] createZipBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("placeholder.txt");
            zos.putNextEntry(entry);
            zos.write("ok".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
