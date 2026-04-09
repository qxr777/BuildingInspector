package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Package;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.PackageMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IDiseaseService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.IProjectService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import edu.whut.cs.bi.biz.service.ITaskService;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageServiceImplTest {

    @InjectMocks
    private PackageServiceImpl packageService;

    @Mock
    private PackageMapper packageMapper;

    @Mock
    private FileMapServiceImpl fileMapServiceImpl;

    @Mock
    private MinioConfig minioConfig;

    @Mock
    private MinioClient minioClient;

    @Mock
    private ITaskService taskService;

    @Mock
    private IProjectService projectService;

    @Mock
    private IPropertyService propertyService;

    @Mock
    private IBuildingService buildingService;

    @Mock
    private IBiObjectService biObjectService;

    @Mock
    private IDiseaseService diseaseService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private com.ruoyi.system.service.ISysUserService userService;

    @Mock
    private IFileMapService fileMapService;

    /**
     * 测试 selectPackageById：根据主键正常查询数据包。
     */
    @Test
    void testSelectPackageById_Success() {
        Package pkg = new Package();
        pkg.setId(1L);
        when(packageMapper.selectPackageById(1L)).thenReturn(pkg);

        Package result = packageService.selectPackageById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(packageMapper, times(1)).selectPackageById(1L);
    }

    /**
     * 测试 selectPackageById：底层查询异常时向上抛出运行时异常。
     */
    @Test
    void testSelectPackageById_Exception() {
        when(packageMapper.selectPackageById(1L)).thenThrow(new RuntimeException("db error"));

        assertThrows(RuntimeException.class, () -> packageService.selectPackageById(1L));
    }

    /**
     * 测试 updatePackage：正常更新时自动补齐更新时间并调用 mapper。
     */
    @Test
    void testUpdatePackage_Success() {
        Package pkg = new Package();
        pkg.setId(10L);
        when(packageMapper.updatePackage(pkg)).thenReturn(1);

        int result = packageService.updatePackage(pkg);

        assertEquals(1, result);
        assertNotNull(pkg.getUpdateTime());
        verify(packageMapper, times(1)).updatePackage(pkg);
    }

    /**
     * 测试 updatePackage：传入空对象触发空指针异常。
     */
    @Test
    void testUpdatePackage_NullPackage() {
        assertThrows(NullPointerException.class, () -> packageService.updatePackage(null));
    }

    /**
     * 测试 createProjectData：正常生成项目与任务 json 并写入 zip。
     */
    @Test
    void testCreateProjectData_Success() throws Exception {
        Long userId = 100L;
        Project project = new Project();
        project.setId(200L);
        Task task = new Task();
        task.setProjectId(200L);

        when(projectService.selectProjectListByUserIdAndRole(any(Project.class), anyLong(), any(String.class)))
                .thenReturn(Collections.singletonList(project));
        when(taskService.selectTaskVOList(any(Task.class))).thenReturn(Collections.singletonList(task));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(bos);

        packageService.createProjectData(zipOut, "UD-20260101010101-user", userId);
        zipOut.close();

        verify(projectService, times(1)).selectProjectListByUserIdAndRole(any(Project.class), anyLong(), any(String.class));
        verify(taskService, times(1)).selectTaskVOList(any(Task.class));
    }

    /**
     * 测试 createProjectData：zip 输出流为空时抛出空指针异常。
     */
    @Test
    void testCreateProjectData_NullZipOutputStream() {
        when(projectService.selectProjectListByUserIdAndRole(any(Project.class), anyLong(), any(String.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(NullPointerException.class,
                () -> packageService.createProjectData(null, "UD-20260101010101-user", 100L));
    }

    /**
     * 测试 createBuildingData：正常收集结构树、属性和近三年病害数据流程。
     */
    @Test
    void testCreateBuildingData_Success() throws Exception {
        Long userId = 101L;
        Project project = new Project();
        project.setId(300L);

        Task task = new Task();
        task.setProjectId(300L);
        task.setBuildingId(400L);

        Building building = new Building();
        building.setId(400L);
        building.setRootObjectId(500L);
        building.setRootPropertyId(600L);

        Property property = new Property();
        property.setId(600L);

        when(projectService.selectProjectListByUserIdAndRole(any(Project.class), anyLong(), any(String.class)))
                .thenReturn(Collections.singletonList(project));
        when(taskService.selectTaskVOList(any(Task.class))).thenReturn(Collections.singletonList(task));
        when(buildingService.selectBuildingById(400L)).thenReturn(building);
        when(biObjectService.bridgeStructureJson(500L)).thenReturn("{\"id\":500}");
        when(attachmentService.getAttachmentBySubjectId(400L)).thenReturn(Collections.emptyList());
        when(propertyService.selectPropertyTree(600L)).thenReturn(property);
        when(diseaseService.selectDiseaseListForZip(any(Disease.class))).thenReturn(Collections.emptyList());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(bos);

        packageService.createBuildingData(zipOut, "UD-20260101010101-user", userId);
        zipOut.close();

        verify(buildingService, times(1)).selectBuildingById(400L);
        verify(propertyService, times(1)).selectPropertyTree(600L);
        verify(diseaseService, times(3)).selectDiseaseListForZip(any(Disease.class));
    }

    /**
     * 测试 createBuildingData：查询用户项目失败时抛出运行时异常。
     */
    @Test
    void testCreateBuildingData_QueryProjectException() throws Exception {
        when(projectService.selectProjectListByUserIdAndRole(any(Project.class), anyLong(), any(String.class)))
                .thenThrow(new RuntimeException("query project failed"));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(bos);

        assertThrows(RuntimeException.class,
                () -> packageService.createBuildingData(zipOut, "UD-20260101010101-user", 101L));
    }

    /**
     * 测试 getFrontAndSide：正常分组照片并写入 zip。
     */
    @Test
    void testGetFrontAndSide_Success() throws Exception {
        Attachment attachment = new Attachment();
        attachment.setId(700L);
        attachment.setMinioId(800L);
        attachment.setName("0_newfront_demo.jpg");

        FileMap fileMap = new FileMap();
        fileMap.setId(800);
        fileMap.setOldName("demo.jpg");
        fileMap.setNewName("ab123456.jpg");

        when(fileMapServiceImpl.selectFileMapByIds(anyList())).thenReturn(Collections.singletonList(fileMap));

        PackageServiceImpl spyPackageService = org.mockito.Mockito.spy(packageService);
        org.mockito.Mockito.doReturn(new ByteArrayInputStream("fake-image".getBytes()))
                .when(spyPackageService).openObjectStream("ab123456.jpg");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(bos);

        Map<String, List<String>> result = spyPackageService.getFrontAndSide(
                Collections.singletonList(attachment), zipOut, 400L, "UD-20260101010101-user");

        zipOut.close();

        assertEquals(1, result.get("frontLeft").size());
        assertEquals("400/images/demo.jpg", result.get("frontLeft").get(0));
        verify(fileMapServiceImpl, times(1)).selectFileMapByIds(anyList());
        verify(spyPackageService, times(1)).openObjectStream("ab123456.jpg");
    }

    /**
     * 测试 getFrontAndSide：附件列表为空对象时抛出空指针异常。
     */
    @Test
    void testGetFrontAndSide_NullAttachments() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(bos);

        assertThrows(NullPointerException.class,
                () -> packageService.getFrontAndSide(null, zipOut, 400L, "UD-20260101010101-user"));
    }
}
