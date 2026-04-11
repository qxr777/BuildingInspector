package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.system.service.ISysDictDataService;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.*;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;

@ExtendWith(MockitoExtension.class)
class ReadFileServiceImplTest {

    @InjectMocks
    private ReadFileServiceImpl readFileService;

    @Mock
    private ITaskService taskService;
    @Mock
    private IComponentService componentService;
    @Mock
    private DiseaseTypeMapper diseaseTypeMapper;
    @Mock
    private BiObjectMapper biObjectMapper;
    @Mock
    private BuildingMapper buildingMapper;
    @Mock
    private IDiseaseTypeService diseaseTypeService;
    @Mock
    private DiseaseMapper diseaseMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private DiseaseDetailMapper diseaseDetailMapper;
    @Mock
    private IDiseaseService diseaseService;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private Executor thumbPhotoExecutor;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private IFileMapService fileMapService;
    @Mock
    private ISysDictDataService sysDictDataService;
    @Mock
    private IBuildingService buildingService;

    @Test
    void testSplitPhotoName_Success() {
        // 中文注释：图片编号包含中文顿号时应正确拆分为列表
        List<String> result = readFileService.splitPhotoName("1982、1983、1984");
        assertEquals(Arrays.asList("1982", "1983", "1984"), result);
    }

    @Test
    void testSplitPhotoName_InvalidRange() {
        // 中文注释：图片编号范围格式非法时应抛出数字转换异常
        assertThrows(NumberFormatException.class, () -> readFileService.splitPhotoName("A~B"));
    }

    @Test
    void testCreateThumbnail_Success() throws Exception {
        // 中文注释：从 MinIO 获取原图并生成缩略图成功
        XSSFWorkbook wb = new XSSFWorkbook();
        wb.createSheet("tmp");
        wb.close();

        // 1. 生成真实的图片数据，并存入 byte 数组
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1200, 800, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream imageOut = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpg", imageOut);
        byte[] imageBytes = imageOut.toByteArray();

        // 2. 将真实的字节数据装入 Java 原生的 ByteArrayInputStream
        ByteArrayInputStream realInputStream = new ByteArrayInputStream(imageBytes);

        // 3. Mock MinIO 的 GetObjectResponse
        GetObjectResponse inputStream = mock(GetObjectResponse.class);

        // 4. ✨ 核心魔法：把假 inputStream 的所有读取动作，全部“委托”给真实的 realInputStream！
        // 这样当程序读取时，不仅能读到真实的图片字节，而且读完后能正确返回 -1 跳出循环！
        lenient().when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> {
            byte[] b = invocation.getArgument(0);
            int off = invocation.getArgument(1);
            int len = invocation.getArgument(2);
            return realInputStream.read(b, off, len);
        });
        lenient().when(inputStream.read(any(byte[].class))).thenAnswer(invocation -> {
            byte[] b = invocation.getArgument(0);
            return realInputStream.read(b);
        });
        lenient().when(inputStream.read()).thenAnswer(invocation -> realInputStream.read());
        lenient().when(inputStream.available()).thenAnswer(invocation -> realInputStream.available());

        // 5. 正常的 Mock 注入
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(inputStream);

        // 6. 执行核心业务代码
        MultipartFile thumbnail = readFileService.createThumbnail("ab123.jpg", "origin.jpg", 1024, 768, 0.5f);

        // 7. 验收断言
        assertNotNull(thumbnail);
        assertTrue(thumbnail.getOriginalFilename().contains("thumbnail"));
        assertTrue(thumbnail.getSize() > 0);
        verify(minioClient, times(1)).getObject(any(GetObjectArgs.class));
    }

    @Test
    void testCreateThumbnail_EmptyName() {
        // 中文注释：文件新名称为空时应抛出参数异常
        assertThrows(IllegalArgumentException.class,
                () -> readFileService.createThumbnail("", "origin.jpg", 1024, 768, 0.5f));
    }

    @Test
    void testUploadPictures_Success() {
        // 中文注释：上传图片能按编号匹配病害并更新附件计数
        Long taskId = 1L;
        Task task = new Task();
        task.setId(taskId);
        task.setBuildingId(10L);
        task.setProjectId(20L);

        Disease disease = new Disease();
        disease.setId(100L);
        disease.setImgNoExp("[\"1001\"]");

        Disease dbDisease = new Disease();
        dbDisease.setId(100L);
        dbDisease.setAttachmentCount(2);

        MockMultipartFile matchedPhoto = new MockMultipartFile("file", "IMG_1001.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile unmatchedPhoto = new MockMultipartFile("file", "IMG_9999.jpg", "image/jpeg", new byte[]{4, 5, 6});

        when(taskService.selectTaskById(taskId)).thenReturn(task);
        when(diseaseMapper.selectDiseaseList(any(Disease.class))).thenReturn(Collections.singletonList(disease));
        doNothing().when(diseaseService).handleDiseaseAttachment(any(MultipartFile[].class), eq(100L), eq(1));
        when(diseaseService.selectDiseaseById(100L)).thenReturn(dbDisease);
        when(diseaseMapper.updateDisease(any(Disease.class))).thenReturn(1);

        List<String> unmatched = readFileService.uploadPictures(Arrays.asList(matchedPhoto, unmatchedPhoto), taskId);

        assertEquals(1, unmatched.size());
        assertEquals("IMG_9999.jpg", unmatched.get(0));
        verify(diseaseService, times(1)).handleDiseaseAttachment(any(MultipartFile[].class), eq(100L), eq(1));

        ArgumentCaptor<Disease> captor = ArgumentCaptor.forClass(Disease.class);
        verify(diseaseMapper, times(1)).updateDisease(captor.capture());
        assertEquals(3, captor.getValue().getAttachmentCount());
    }

    @Test
    void testUploadPictures_DiseaseMissing() {
        // 中文注释：匹配到病害后查询病害为空时应抛出空指针异常
        Long taskId = 2L;
        Task task = new Task();
        task.setId(taskId);
        task.setBuildingId(10L);
        task.setProjectId(20L);

        Disease disease = new Disease();
        disease.setId(200L);
        disease.setImgNoExp("[\"2002\"]");

        MockMultipartFile matchedPhoto = new MockMultipartFile("file", "PIC_2002.jpg", "image/jpeg", new byte[]{7, 8});

        when(taskService.selectTaskById(taskId)).thenReturn(task);
        when(diseaseMapper.selectDiseaseList(any(Disease.class))).thenReturn(Collections.singletonList(disease));
        doNothing().when(diseaseService).handleDiseaseAttachment(any(MultipartFile[].class), eq(200L), eq(1));
        when(diseaseService.selectDiseaseById(200L)).thenReturn(null);

        assertThrows(NullPointerException.class, () -> readFileService.uploadPictures(Collections.singletonList(matchedPhoto), taskId));
    }

    @Test
    void testReadBuildingFile_Success() throws Exception {
        // 1. 制造一个包含真实内容的假 Excel
        XSSFWorkbook wb = new XSSFWorkbook();
        // 必须建满 7 个 sheet，匹配业务代码的 j < 7
        for (int j = 0; j < 7; j++) {
            Sheet sheet = wb.createSheet("Sheet" + j);
            Row row0 = sheet.createRow(0); // 表头
            Row row1 = sheet.createRow(1); // 真实数据行
            Row row2 = sheet.createRow(2); // 垫底行，为了让 i < getLastRowNum() 成立

            // 我们只在第一个 sheet 塞入触发代码的数据
            if(j == 0) {
                row1.createCell(0).setCellValue("测试桥幅");
                row1.createCell(1).setCellValue("桥幅"); // 触发桥幅分支
                row1.createCell(2).setCellValue("测试父桥");
                row1.createCell(3).setCellValue("洪山区");
                row1.createCell(4).setCellValue("1号线");
                row1.createCell(5).setCellValue("梁式桥");
            }
        }

        // 将写好的 workbook 转成文件流
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        // 2. 模拟字典查询成功（必须有，否则会报异常中断）
        SysDictData mockDict = new SysDictData();
        mockDict.setDictValue("100");
        when(sysDictDataService.selectDictDataList(any(SysDictData.class))).thenReturn(Collections.singletonList(mockDict));

        // 3. 模拟在数据库中查不到同名桥梁，这样代码才会走入 insert 分支
        when(buildingMapper.selectBuildingList(any(Building.class))).thenReturn(Collections.emptyList());

        // 4. 模拟插入方法正常返回
        when(buildingService.insertBuilding(any(Building.class))).thenReturn(1);

        // 5. 执行你要测试的业务代码！
        readFileService.ReadBuildingFile(mockFile, 1L);

        // 6. 验收时刻：验证 insertBuilding 至少被成功调用了 1 次！
        verify(buildingService, atLeastOnce()).insertBuilding(any(Building.class));
    }

    @Test
    void testReadBuildingFile_DictMissing() throws Exception {
        // 1. 制造一个包含真实内容的假 Excel
        XSSFWorkbook wb = new XSSFWorkbook();
        // 必须建满 7 个 sheet，因为你的业务代码写死了 for (int j = 0; j < 7; j++)
        for (int j = 0; j < 7; j++) {
            Sheet sheet = wb.createSheet("Sheet" + j);
            Row row0 = sheet.createRow(0); // 表头
            Row row1 = sheet.createRow(1); // 数据行（必须有，才能进循环）
            Row row2 = sheet.createRow(2); // 因为代码是 i < getLastRowNum()，所以得多造一行才能让 i=1 成立

            if(j == 0) { // 在第一个 sheet 塞点触发报错的数据
                row1.createCell(0).setCellValue("测试桥");
                row1.createCell(1).setCellValue("组合桥");
                row1.createCell(2).setCellValue("父桥");
                row1.createCell(3).setCellValue("不存在的片区"); // 关键：引发异常的数据
                row1.createCell(4).setCellValue("测试线");
                row1.createCell(5).setCellValue("梁式桥");
            }
        }

        // 把 workbook 转成 MultipartFile
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        // 2. Mock 字典查询，故意让它查不到（返回空列表）
        when(sysDictDataService.selectDictDataList(any())).thenReturn(Collections.emptyList());

        // 3. 断言它一定会抛出异常！
        assertThrows(RuntimeException.class, () -> {
            readFileService.ReadBuildingFile(mockFile, 1L);
        });
    }

    @Test
    void testReadCBMSDiseaseExcel_Success() throws Exception {
        // 中文注释：CBMS 病害导入文件结构正常时主流程可执行完成
        MockMultipartFile excel = createSimpleDiseaseExcel();

        Task task = new Task();
        task.setId(1L);
        task.setBuildingId(10L);
        task.setProjectId(20L);

        Building building = new Building();
        building.setId(10L);
        building.setRootObjectId(100L);

        DiseaseType otherType = new DiseaseType();
        otherType.setId(5L);

        when(taskService.selectTaskById(1L)).thenReturn(task);
        when(componentService.selectComponentList(any(Component.class))).thenReturn(Collections.emptyList());
        when(buildingMapper.selectBuildingById(10L)).thenReturn(building);
        when(diseaseTypeMapper.selectDiseaseTypeByCode("0.0.0.0-5")).thenReturn(otherType);
        when(biObjectMapper.selectBiObjectAndChildrenThreeLevel(100L)).thenReturn(Collections.emptyList());
        when(biObjectMapper.selectBiObjectAndChildren(100L)).thenReturn(Collections.emptyList());

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");
            readFileService.readCBMSDiseaseExcel(excel, 1L);
        }

        verify(taskService, times(1)).selectTaskById(1L);
        verify(buildingMapper, times(1)).selectBuildingById(10L);
    }

    @Test
    void testReadCBMSDiseaseExcel_ReadError() {
        // 中文注释：读取 CBMS 病害文件 IO 异常时应抛出运行时异常
        MultipartFile badFile = mock(MultipartFile.class);
        Task task = new Task();
        task.setId(1L);
        task.setBuildingId(10L);

        Building building = new Building();
        building.setId(10L);

        when(taskService.selectTaskById(1L)).thenReturn(task);
        when(componentService.selectComponentList(any(Component.class))).thenReturn(Collections.emptyList());
        when(buildingMapper.selectBuildingById(10L)).thenReturn(building);
        when(diseaseTypeMapper.selectDiseaseTypeByCode("0.0.0.0-5")).thenReturn(new DiseaseType());

        try (MockedStatic<ShiroUtils> shiroMock = mockStatic(ShiroUtils.class)) {
            shiroMock.when(ShiroUtils::getLoginName).thenReturn("tester");
            when(badFile.getInputStream()).thenThrow(new java.io.IOException("io error"));
            assertThrows(RuntimeException.class, () -> readFileService.readCBMSDiseaseExcel(badFile, 1L));
        } catch (Exception e) {
            fail(e);
        }
    }

    private MockMultipartFile createBuildingExcel(String name, String type, String father, String area, String line, String template) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        for (int i = 0; i < 7; i++) {
            Sheet sheet = workbook.createSheet("sheet" + i);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("buildingName");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(name);
            row.createCell(1).setCellValue(type);
            row.createCell(2).setCellValue(father);
            row.createCell(3).setCellValue(area);
            row.createCell(4).setCellValue(line);
            row.createCell(5).setCellValue(template);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new MockMultipartFile("file", "building.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private MockMultipartFile createSimpleDiseaseExcel() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("disease").createRow(0).createCell(0).setCellValue("header");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new MockMultipartFile("file", "disease.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }
}
