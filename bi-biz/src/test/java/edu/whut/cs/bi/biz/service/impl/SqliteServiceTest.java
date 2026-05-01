package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import edu.whut.cs.bi.biz.domain.DiseasePosition;
import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.UserSqlite;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.domain.BiObjectComponent;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectComponentMapper;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseDetailMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseasePositionMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseScaleMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.mapper.ProjectMapper;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.mapper.TODiseasePositionMapper;
import edu.whut.cs.bi.biz.mapper.TODiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.mapper.UserSqliteMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SqliteServiceTest {

    @InjectMocks
    private SqliteService sqliteService;

    @Mock
    private TaskMapper taskMapper;
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
    private AttachmentService attachmentService;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private FileMapMapper fileMapMapper;
    @Mock
    private UserSqliteMapper userSqliteMapper;
    @Mock
    private BiTemplateObjectMapper biTemplateObjectMapper;
    @Mock
    private TODiseaseTypeMapper toDiseaseTypeMapper;
    @Mock
    private DiseaseTypeMapper diseaseTypeMapper;
    @Mock
    private DiseaseScaleMapper diseaseScaleMapper;
    @Mock
    private DiseasePositionMapper diseasePositionMapper;
    @Mock
    private TODiseasePositionMapper toDiseasePositionMapper;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private BiObjectComponentMapper biObjectComponentMapper;
    @Mock
    private PropertyMapper propertyMapper;

    private final List<File> generatedFiles = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (File file : generatedFiles) {
            if (file != null && file.exists()) {
                file.delete();
            }
        }
    }

    /**
     * 测试场景：结构物数据完整时，生成 building SQLite 成功。
     * Mock 内容：所有 Mapper/Service 均返回有效结构、构件、病害、附件、文件映射数据。
     * 预期结果：返回临时 db 文件，且核心业务表中数据条数符合预期。
     */
    @Test
    void testDoGenerateBuildingSqlite_HappyPath() throws Exception {
        Long buildingId = 1L;

        Building building = new Building();
        building.setId(buildingId);
        building.setRootObjectId(100L);

        BiObject rootObj = new BiObject();
        rootObj.setId(100L);
        rootObj.setName("root");

        BiObject childObj = new BiObject();
        childObj.setId(101L);
        childObj.setParentId(100L);
        childObj.setName("child");

        Component component = new Component();
        component.setId(201L);
        component.setBiObjectId(101L);
        component.setName("component-1");

        Disease disease = new Disease();
        disease.setId(301L);
        disease.setBuildingId(buildingId);
        disease.setBiObjectId(101L);
        disease.setComponentId(201L);
        disease.setLevel(1);
        disease.setQuantity(1);

        DiseaseDetail detail = new DiseaseDetail();
        detail.setId(401L);
        detail.setDiseaseId(301L);

        Attachment diseaseAttachment = new Attachment();
        diseaseAttachment.setId(501L);
        diseaseAttachment.setSubjectId(301L);
        diseaseAttachment.setType(1);
        diseaseAttachment.setMinioId(701L);
        diseaseAttachment.setThumbMinioId(702L);

        Attachment buildingAttachment = new Attachment();
        buildingAttachment.setId(502L);
        buildingAttachment.setSubjectId(buildingId);
        buildingAttachment.setType(6);
        buildingAttachment.setMinioId(703L);

        FileMap map1 = new FileMap();
        map1.setId(701);
        map1.setOldName("old1");
        map1.setNewName("abcdef.db");

        doReturn(building).when(buildingMapper).selectBuildingById(buildingId);
        doReturn(Collections.singletonList(building)).when(buildingMapper).selectBuildingsByIds(Collections.singletonList(buildingId));
        doReturn(Collections.singletonList(rootObj)).when(biObjectMapper).selectBiObjectsByIds(Collections.singletonList(100L));
        doReturn(Collections.singletonList(childObj)).when(biObjectMapper).selectChildrenById(100L);
        doReturn(Collections.singletonList(component)).when(componentMapper).selectComponentsByBiObjectIds(Arrays.asList(100L, 101L));
        doReturn(Collections.singletonList(disease)).when(diseaseMapper).selectDiseasesByBuildingIds(Collections.singletonList(buildingId));
        doReturn(Collections.singletonList(detail)).when(diseaseDetailMapper).selectDiseaseDetailsByDiseaseIds(Collections.singletonList(301L));
        doReturn(Collections.singletonList(diseaseAttachment), Collections.singletonList(buildingAttachment))
                .when(attachmentService).getAttachmentBySubjectIds(anyList());
        doReturn(Collections.singletonList(map1)).when(fileMapMapper).selectFileMapByIds(Arrays.asList(701L, 702L, 703L));
        doReturn(new ArrayList<>()).when(biObjectComponentMapper).selectBiObjectComponentList(any());

        File result = sqliteService.doGenerateBuildingSqlite(buildingId);
        generatedFiles.add(result);

        assertNotNull(result);
        assertTrue(result.exists());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + result.getAbsolutePath());
             Statement statement = conn.createStatement()) {
            assertEquals(2, queryCount(statement, "select count(1) from bi_object"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_component"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_disease"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_disease_detail"));
            assertEquals(2, queryCount(statement, "select count(1) from bi_attachment"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_file_map"));
        }
    }

    /**
     * 测试场景：结构物不存在。
     * Mock 内容：buildingMapper 返回 null。
     * 预期结果：直接返回 null，不创建 SQLite 文件。
     */
    @Test
    void testDoGenerateBuildingSqlite_EdgeCase_BuildingNotFound() throws Exception {
        doReturn(null).when(buildingMapper).selectBuildingById(999L);

        File result = sqliteService.doGenerateBuildingSqlite(999L);

        assertNull(result);
        verify(buildingMapper, times(1)).selectBuildingById(999L);
    }

    /**
     * 测试场景：用户维度同步打包流程全部成功。
     * Mock 内容：项目/任务/桥梁查询正常，MinIO 上传成功，fileMap 插入后回填 id，用户打包记录不存在走 insert 分支。
     * 预期结果：方法正常执行且写入用户 SQLite 关联信息。
     */
    @Test
    void testGenerateUserSqliteSync_HappyPath() throws Exception {
        Long userId = 99L;

        Project project = new Project();
        project.setId(1001L);

        Task task = new Task();
        task.setId(2001L);
        task.setBuildingId(3001L);

        Building building = new Building();
        building.setId(3001L);
        building.setRootPropertyId(88L);
        
        Property property = new Property();
        property.setId(88L);
        property.setName("桥梁属性");

        doReturn(Collections.singletonList(project)).when(projectMapper).selectProjectList(any(Project.class), eq(userId), eq(null));
        doReturn(Collections.singletonList(task)).when(taskMapper).selectFullTaskListByProjectId(1001L);
        doReturn(Collections.singletonList(building)).when(buildingMapper).selectBuildingsByIds(Collections.singletonList(3001L));
        doReturn(Collections.singletonList(property)).when(propertyMapper).selectAllChildrenById(88L);

        doReturn("bucket-test").when(minioConfig).getBucketName();
        doReturn(null).when(minioClient).putObject(any());
        doAnswer(invocation -> {
            FileMap arg = invocation.getArgument(0);
            arg.setId(777);
            return 1;
        }).when(fileMapMapper).insertFileMap(any(FileMap.class));

        doReturn(null).when(userSqliteMapper).selectUserSqliteByUserId(userId);

        sqliteService.generateUserSqliteSync(userId);

        verify(fileMapMapper, times(1)).insertFileMap(any(FileMap.class));
        verify(userSqliteMapper, times(1)).insertUserSqlite(any(UserSqlite.class));
        verify(userSqliteMapper, never()).updateUserSqlite(any(UserSqlite.class));
    }

    /**
     * 测试场景：用户维度同步打包时上游依赖抛异常。
     * Mock 内容：projectMapper 查询项目时抛 RuntimeException。
     * 预期结果：内部捕获异常并返回 null。
     */
    @Test
    void testGenerateUserSqliteSync_ExceptionPath() throws Exception {
        Long userId = 100L;
        doThrow(new RuntimeException("db error"))
                .when(projectMapper).selectProjectList(any(Project.class), eq(userId), eq(null));

        SqliteVo vo = sqliteService.generateUserSqliteSync(userId);
        assertNull(vo);

        verify(minioClient, never()).putObject(any());
        verify(userSqliteMapper, never()).insertUserSqlite(any(UserSqlite.class));
        verify(userSqliteMapper, never()).updateUserSqlite(any(UserSqlite.class));
    }

    /**
     * 测试场景：通用基础数据打包成功并生成可下载地址。
     * Mock 内容：模板对象/病害类型/标度均返回有效数据，MinIO 上传成功，fileMap 可查询。
     * 预期结果：返回 SqliteVo，url/timestamp/size 字段正确填充。
     */
    @Test
    void testGenerateCommonBaseSqlite_HappyPath() throws Exception {
        BiTemplateObject templateObject = new BiTemplateObject();
        templateObject.setId(1L);
        templateObject.setName("模板结构物");

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("template_object_id", 1L);
        mapping.put("disease_type_id", 11L);

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setId(11L);
        diseaseType.setCode("DT-11");
        diseaseType.setName("裂缝");

        DiseaseScale scale = new DiseaseScale();
        scale.setId(21L);
        scale.setTypeCode("DT-11");
        scale.setScale(1);

        FileMap stored = new FileMap();
        stored.setId(888);
        stored.setNewName("ab1234567890.db");
        stored.setCreateTime(new Date());

        doReturn(Collections.singletonList(templateObject)).when(biTemplateObjectMapper)
                .selectBiTemplateObjectList(any(BiTemplateObject.class));
        doReturn(Collections.singletonList(mapping)).when(toDiseaseTypeMapper)
                .selectAllTemplateObjectDiseaseTypeMappings();
        doReturn(Collections.singletonList(diseaseType)).when(diseaseTypeMapper)
                .selectDiseaseTypeList(any(DiseaseType.class));
        doReturn(Collections.singletonList(scale)).when(diseaseScaleMapper)
                .selectDiseaseScaleList(any(DiseaseScale.class));
        doReturn(Collections.emptyList()).when(diseasePositionMapper)
                .selectDiseasePositionList(any());
        doReturn(Collections.emptyList()).when(toDiseasePositionMapper)
                .selectAllMappings();

        doReturn("bucket-test").when(minioConfig).getBucketName();
        doReturn("http://minio.local").when(minioConfig).getUrl();
        doReturn(null).when(minioClient).putObject(any());
        doAnswer(invocation -> {
            FileMap arg = invocation.getArgument(0);
            arg.setId(888);
            return 1;
        }).when(fileMapMapper).insertFileMap(any(FileMap.class));
        doReturn(stored).when(fileMapMapper).selectFileMapById(888L);

        SqliteVo vo = sqliteService.generateCommonBaseSqlite();

        assertNotNull(vo);
        assertNotNull(vo.getTimestamp());
        assertNotNull(vo.getSize());
        assertEquals("http://minio.local/bucket-test/ab/ab1234567890.db", vo.getUrl());
    }

    /**
     * 测试场景：通用基础数据查询阶段失败。
     * Mock 内容：模板对象查询直接抛异常。
     * 预期结果：方法捕获异常并返回 null。
     */
    @Test
    void testGenerateCommonBaseSqlite_ExceptionPath() throws Exception {
        doThrow(new RuntimeException("query failed"))
                .when(biTemplateObjectMapper).selectBiTemplateObjectList(any(BiTemplateObject.class));

        SqliteVo vo = sqliteService.generateCommonBaseSqlite();

        assertNull(vo);
        verify(minioClient, never()).putObject(any());
    }

    /**
     * 测试场景：项目 SQLite 下载地址查询成功。
     * Mock 内容：projectMapper 返回 sqliteMinioId，fileMapMapper 返回文件映射，MinioConfig 提供访问前缀。
     * 预期结果：返回包含正确 url 的 SqliteVo。
     */
    @Test
    void testGetProjectSqliteUrl_HappyPath() {
        Project project = new Project();
        project.setId(1L);
        project.setSqliteMinioId(123L);

        FileMap fm = new FileMap();
        fm.setId(123);
        fm.setNewName("aa_file.db");
        fm.setCreateTime(new Date());

        doReturn(project).when(projectMapper).selectProjectById(1L);
        doReturn(fm).when(fileMapMapper).selectFileMapById(123L);
        doReturn("http://localhost:9000").when(minioConfig).getUrl();
        doReturn("bucket-test").when(minioConfig).getBucketName();

        SqliteVo vo = sqliteService.getProjectSqliteUrl(1L);

        assertNotNull(vo);
        assertEquals("http://localhost:9000/bucket-test/aa/aa_file.db", vo.getUrl());
    }

    /**
     * 测试场景：项目不存在或未关联 SQLite。
     * Mock 内容：projectMapper 返回 null。
     * 预期结果：返回 null。
     */
    @Test
    void testGetProjectSqliteUrl_EdgeCase_ProjectNotFound() {
        doReturn(null).when(projectMapper).selectProjectById(999L);

        SqliteVo vo = sqliteService.getProjectSqliteUrl(999L);

        assertNull(vo);
    }

    /**
     * 测试场景：通过 spy 识别可测性问题（硬编码连接/私有方法耦合）。
     * Mock 内容：对服务创建 spy，说明当前 private connect/uploadToMinio + DriverManager 静态调用不易替身。
     * 预期结果：给出重构建议（将连接与上传提取为可覆写协作者），便于后续用 doReturn(...).when(spy) 精准隔离。
     */
    @Test
    void testRefactorSuggestion_WithSpyForTestability() {
        SqliteService spyService = Mockito.spy(sqliteService);
        assertNotNull(spyService);
        // 重构建议：将 connect/uploadToMinio 抽到独立 Collaborator（如 SqliteConnectionFactory / MinioUploader），
        // 再通过依赖注入后即可在测试中 doReturn(...).when(spyService/协作者) 做更细粒度隔离。
    }

    private int queryCount(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
}
