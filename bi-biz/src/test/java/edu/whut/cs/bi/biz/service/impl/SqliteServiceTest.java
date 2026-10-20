package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.DiseaseDetail;
import edu.whut.cs.bi.biz.domain.DiseaseScale;
import edu.whut.cs.bi.biz.domain.DiseaseType;
import edu.whut.cs.bi.biz.domain.Element;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.LeafObject;
import edu.whut.cs.bi.biz.domain.LoDiseasePosition;
import edu.whut.cs.bi.biz.domain.LoDiseaseType;
import edu.whut.cs.bi.biz.domain.MaterialDurability;
import edu.whut.cs.bi.biz.domain.MdDiseaseType;
import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.domain.RepairStrengtheningStructure;
import edu.whut.cs.bi.biz.domain.RssDiseaseType;
import edu.whut.cs.bi.biz.domain.SpanElementPart;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.TemplateObjectLeafObject;
import edu.whut.cs.bi.biz.domain.UserSqlite;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BiTemplateObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseDetailMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseScaleMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.mapper.LeafObjectMapper;
import edu.whut.cs.bi.biz.mapper.LoDiseasePositionMapper;
import edu.whut.cs.bi.biz.mapper.LoDiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.MaterialDurabilityMapper;
import edu.whut.cs.bi.biz.mapper.MdDiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.ProjectMapper;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.mapper.RepairStrengtheningStructureMapper;
import edu.whut.cs.bi.biz.mapper.RssDiseaseTypeMapper;
import edu.whut.cs.bi.biz.mapper.SpanElementPartMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.mapper.TemplateObjectLeafObjectMapper;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    private ElementMapper elementMapper;
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
    private DiseaseTypeMapper diseaseTypeMapper;
    @Mock
    private DiseaseScaleMapper diseaseScaleMapper;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private PropertyMapper propertyMapper;
    @Mock
    private SpanElementPartMapper spanElementPartMapper;
    @Mock
    private LeafObjectMapper leafObjectMapper;
    @Mock
    private LoDiseaseTypeMapper loDiseaseTypeMapper;
    @Mock
    private LoDiseasePositionMapper loDiseasePositionMapper;
    @Mock
    private MaterialDurabilityMapper materialDurabilityMapper;
    @Mock
    private RepairStrengtheningStructureMapper repairStrengtheningStructureMapper;
    @Mock
    private MdDiseaseTypeMapper mdDiseaseTypeMapper;
    @Mock
    private RssDiseaseTypeMapper rssDiseaseTypeMapper;
    @Mock
    private TemplateObjectLeafObjectMapper templateObjectLeafObjectMapper;

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
     * 测试场景：结构物数据完整时，生成 building SQLite 成功（新标 bi_element 链）。
     * Mock 内容：所有 Mapper/Service 均返回有效结构、构件、病害、附件、文件映射数据。
     * 预期结果：返回临时 db 文件，bi_element 数据落库，旧 bi_component 两表不存在。
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

        Element element = new Element();
        element.setId(201L);
        element.setBiObjectId(101L);
        element.setName("element-1");
        element.setCode("E-201");
        element.setOfflineUuid("elem-uuid-201");

        Disease disease = new Disease();
        disease.setId(301L);
        disease.setBuildingId(buildingId);
        disease.setBiObjectId(101L);
        disease.setComponentId(201L);
        disease.setElementUuid("elem-uuid-201");
        disease.setLeafObjectId(901L);
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
        doReturn(Collections.singletonList(element)).when(elementMapper).selectElementsByBiObjectIds(Arrays.asList(100L, 101L));
        doReturn(Collections.singletonList(disease)).when(diseaseMapper).selectDiseasesByBuildingIds(Collections.singletonList(buildingId));
        doReturn(Collections.singletonList(detail)).when(diseaseDetailMapper).selectDiseaseDetailsByDiseaseIds(Collections.singletonList(301L));
        doReturn(Collections.singletonList(diseaseAttachment), Collections.singletonList(buildingAttachment))
                .when(attachmentService).getAttachmentBySubjectIds(anyList());
        doReturn(Collections.singletonList(map1)).when(fileMapMapper).selectFileMapByIds(Arrays.asList(701L, 702L, 703L));

        File result = sqliteService.doGenerateBuildingSqlite(buildingId);
        generatedFiles.add(result);

        assertNotNull(result);
        assertTrue(result.exists());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + result.getAbsolutePath());
             Statement statement = conn.createStatement()) {
            assertEquals(2, queryCount(statement, "select count(1) from bi_object"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_element"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_disease"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_disease_detail"));
            assertEquals(2, queryCount(statement, "select count(1) from bi_attachment"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_file_map"));
            // 新链两表存在
            assertEquals(2, queryCount(statement,
                    "select count(1) from sqlite_master where type='table' "
                            + "and name in ('bi_element','bi_span_element_part')"));
            // 旧链两表已不出包
            assertEquals(0, queryCount(statement,
                    "select count(1) from sqlite_master where type='table' "
                            + "and name in ('bi_component','bi_span_component_part')"));
            // 病害走新链 element_uuid：DDL 有 element_uuid、无 component_uuid，值正确落库
            assertEquals(1, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_disease') where name='element_uuid'"));
            assertEquals(0, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_disease') where name='component_uuid'"));
            // leaf_object_id 随包下发：PART 多叶子时病害的显式归属
            assertEquals(1, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_disease') where name='leaf_object_id'"));
            try (ResultSet rs = statement.executeQuery(
                    "select element_uuid, leaf_object_id from bi_disease where server_id = 301")) {
                assertTrue(rs.next());
                assertEquals("elem-uuid-201", rs.getString(1));
                assertEquals(901L, rs.getLong(2));
            }
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
     * 测试场景：通用基础数据打包成功并生成可下载地址（10 张 5230 字典/模板表）。
     * Mock 内容：模板/叶子部件/绑定/病害类型/标度等均返回有效数据，MinIO 上传成功，fileMap 可查询。
     * 预期结果：返回 SqliteVo，url/timestamp/size 字段正确填充。
     */
    @Test
    void testGenerateCommonBaseSqlite_HappyPath() throws Exception {
        BiTemplateObject templateObject = new BiTemplateObject();
        templateObject.setId(1L);
        templateObject.setName("模板结构物");

        LeafObject leafObject = new LeafObject();
        leafObject.setId(31L);
        leafObject.setCode("bearing");
        leafObject.setName("支座");

        LoDiseaseType loDiseaseType = new LoDiseaseType();
        loDiseaseType.setId(41L);
        loDiseaseType.setLeafObjectId(31L);
        loDiseaseType.setDiseaseTypeId(11L);

        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setId(11L);
        diseaseType.setCode("DT-11");
        diseaseType.setName("裂缝");

        DiseaseScale scale = new DiseaseScale();
        scale.setId(21L);
        scale.setTypeCode("DT-11");
        scale.setScale(1);

        LoDiseasePosition loDiseasePosition = new LoDiseasePosition();
        loDiseasePosition.setId(51L);
        loDiseasePosition.setLeafObjectId(31L);
        loDiseasePosition.setPosition("腹板");

        MaterialDurability material = new MaterialDurability();
        material.setId(61L);
        material.setCode("reinforcedConcrete");
        material.setName("配筋混凝土构件");

        RepairStrengtheningStructure rss = new RepairStrengtheningStructure();
        rss.setId(71L);
        rss.setCode("sectionEnlargement");
        rss.setName("增大截面加固");

        MdDiseaseType mdDiseaseType = new MdDiseaseType();
        mdDiseaseType.setId(81L);
        mdDiseaseType.setMaterialDurabilityId(61L);
        mdDiseaseType.setDiseaseTypeId(11L);

        RssDiseaseType rssDiseaseType = new RssDiseaseType();
        rssDiseaseType.setId(91L);
        rssDiseaseType.setRepairStrengtheningStructureId(71L);
        rssDiseaseType.setDiseaseTypeId(11L);

        doReturn(Collections.singletonList(templateObject)).when(biTemplateObjectMapper)
                .selectBiTemplateObjectList(any(BiTemplateObject.class));
        doReturn(Collections.singletonList(leafObject)).when(leafObjectMapper)
                .selectLeafObjectList(any(LeafObject.class));
        doReturn(Collections.singletonList(loDiseaseType)).when(loDiseaseTypeMapper)
                .selectLoDiseaseTypeList(any(LoDiseaseType.class));
        doReturn(Collections.singletonList(diseaseType)).when(diseaseTypeMapper)
                .selectDiseaseTypeList(any(DiseaseType.class));
        doReturn(Collections.singletonList(scale)).when(diseaseScaleMapper)
                .selectDiseaseScaleList(any(DiseaseScale.class));
        doReturn(Collections.singletonList(loDiseasePosition)).when(loDiseasePositionMapper)
                .selectLoDiseasePositionList(any(LoDiseasePosition.class));
        doReturn(Collections.singletonList(material)).when(materialDurabilityMapper)
                .selectMaterialDurabilityList(any(MaterialDurability.class));
        doReturn(Collections.singletonList(rss)).when(repairStrengtheningStructureMapper)
                .selectRepairStrengtheningStructureList(any(RepairStrengtheningStructure.class));
        doReturn(Collections.singletonList(mdDiseaseType)).when(mdDiseaseTypeMapper)
                .selectMdDiseaseTypeList(any(MdDiseaseType.class));
        doReturn(Collections.singletonList(rssDiseaseType)).when(rssDiseaseTypeMapper)
                .selectRssDiseaseTypeList(any(RssDiseaseType.class));

        doReturn("bucket-test").when(minioConfig).getBucketName();
        doReturn("http://minio.local").when(minioConfig).getUrl();
        doReturn(null).when(minioClient).putObject(any());
        doAnswer(invocation -> {
            FileMap arg = invocation.getArgument(0);
            arg.setId(888);
            return 1;
        }).when(fileMapMapper).insertFileMap(any(FileMap.class));
        doReturn(storedFileMap()).when(fileMapMapper).selectFileMapById(888L);

        SqliteVo vo = sqliteService.generateCommonBaseSqlite();

        assertNotNull(vo);
        assertNotNull(vo.getTimestamp());
        assertNotNull(vo.getSize());
        assertEquals("http://minio.local/bucket-test/ab/ab1234567890.db", vo.getUrl());
    }

    /**
     * 测试场景：通用基础包 SQLite 文件内容（私有 doGenerateCommonBaseSqlite 直调）。
     * 预期结果：恰为 11 张目标表（含模板 PART-叶子多对多绑定表）；每表 1 行；
     * 旧 bi_template_object_disease_type 表不存在；模板带 node_type/is_main_component、
     * 病害类型带 scale_type；6 张版本字典查询条件均为 std_version=5230-2026。
     */
    @Test
    void testDoGenerateCommonBaseSqlite_ExactlyElevenTables() throws Exception {
        BiTemplateObject templateObject = new BiTemplateObject();
        templateObject.setId(1L);
        templateObject.setNodeType("PART");
        templateObject.setIsMainComponent(1);
        LeafObject leafObject = new LeafObject();
        leafObject.setId(2L);
        LoDiseaseType loDiseaseType = new LoDiseaseType();
        loDiseaseType.setId(3L);
        DiseaseType diseaseType = new DiseaseType();
        diseaseType.setId(4L);
        diseaseType.setScaleType("EDI");
        DiseaseScale diseaseScale = new DiseaseScale();
        diseaseScale.setId(5L);
        LoDiseasePosition loDiseasePosition = new LoDiseasePosition();
        loDiseasePosition.setId(6L);
        MaterialDurability material = new MaterialDurability();
        material.setId(7L);
        RepairStrengtheningStructure rss = new RepairStrengtheningStructure();
        rss.setId(8L);
        MdDiseaseType mdDiseaseType = new MdDiseaseType();
        mdDiseaseType.setId(9L);
        RssDiseaseType rssDiseaseType = new RssDiseaseType();
        rssDiseaseType.setId(10L);
        TemplateObjectLeafObject templateLeaf = new TemplateObjectLeafObject();
        templateLeaf.setId(12L);
        templateLeaf.setTemplateObjectId(1L);
        templateLeaf.setLeafObjectId(2L);

        doReturn(Collections.singletonList(templateObject)).when(biTemplateObjectMapper)
                .selectBiTemplateObjectList(any(BiTemplateObject.class));
        doReturn(Collections.singletonList(leafObject)).when(leafObjectMapper)
                .selectLeafObjectList(any(LeafObject.class));
        doReturn(Collections.singletonList(loDiseaseType)).when(loDiseaseTypeMapper)
                .selectLoDiseaseTypeList(any(LoDiseaseType.class));
        doReturn(Collections.singletonList(diseaseType)).when(diseaseTypeMapper)
                .selectDiseaseTypeList(any(DiseaseType.class));
        doReturn(Collections.singletonList(diseaseScale)).when(diseaseScaleMapper)
                .selectDiseaseScaleList(any(DiseaseScale.class));
        doReturn(Collections.singletonList(loDiseasePosition)).when(loDiseasePositionMapper)
                .selectLoDiseasePositionList(any(LoDiseasePosition.class));
        doReturn(Collections.singletonList(material)).when(materialDurabilityMapper)
                .selectMaterialDurabilityList(any(MaterialDurability.class));
        doReturn(Collections.singletonList(rss)).when(repairStrengtheningStructureMapper)
                .selectRepairStrengtheningStructureList(any(RepairStrengtheningStructure.class));
        doReturn(Collections.singletonList(mdDiseaseType)).when(mdDiseaseTypeMapper)
                .selectMdDiseaseTypeList(any(MdDiseaseType.class));
        doReturn(Collections.singletonList(rssDiseaseType)).when(rssDiseaseTypeMapper)
                .selectRssDiseaseTypeList(any(RssDiseaseType.class));
        doReturn(Collections.singletonList(templateLeaf)).when(templateObjectLeafObjectMapper)
                .selectTemplateObjectLeafObjectList(any(TemplateObjectLeafObject.class));

        File result = (File) invokePrivate("doGenerateCommonBaseSqlite", new Class<?>[0]);
        generatedFiles.add(result);
        assertNotNull(result);

        // 6 张带版本的字典只下发 5230-2026（4 张绑定表无版本列，全量即 5230）
        verify(biTemplateObjectMapper).selectBiTemplateObjectList(argThat(
                q -> "5230-2026".equals(q.getStdVersion())));
        verify(leafObjectMapper).selectLeafObjectList(argThat(
                q -> "5230-2026".equals(q.getStdVersion())));
        verify(diseaseTypeMapper).selectDiseaseTypeList(argThat(
                q -> "5230-2026".equals(q.getStdVersion())));
        verify(diseaseScaleMapper).selectDiseaseScaleList(argThat(
                q -> "5230-2026".equals(q.getStdVersion())));
        verify(materialDurabilityMapper).selectMaterialDurabilityList(argThat(
                q -> "5230-2026".equals(q.getStdVersion())));
        verify(repairStrengtheningStructureMapper).selectRepairStrengtheningStructureList(argThat(
                q -> "5230-2026".equals(q.getStdVersion())));

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + result.getAbsolutePath());
             Statement statement = conn.createStatement()) {
            assertEquals(11, queryCount(statement,
                    "select count(1) from sqlite_master where type='table' and name not like 'sqlite_%'"));
            assertEquals(0, queryCount(statement,
                    "select count(1) from sqlite_master where type='table' "
                            + "and name='bi_template_object_disease_type'"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_template_object"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_template_object_leaf_object"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_leaf_object"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_lo_disease_type"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_disease_type"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_disease_scale"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_lo_disease_position"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_material_durability"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_repair_strengthening_structure"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_md_disease_type"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_rss_disease_type"));
            // 新列存在性
            assertEquals(1, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_template_object') where name='node_type'"));
            assertEquals(1, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_template_object') where name='is_main_component'"));
            assertEquals(1, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_disease_type') where name='scale_type'"));
            // 新列值
            assertEquals(1, queryCount(statement,
                    "select count(1) from bi_template_object where node_type='PART' and is_main_component=1"));
            assertEquals(1, queryCount(statement,
                    "select count(1) from bi_disease_type where scale_type='EDI'"));
            assertEquals(1, queryCount(statement,
                    "select count(1) from bi_template_object_leaf_object "
                            + "where template_object_id=1 and leaf_object_id=2"));
        }
    }

    /**
     * 测试场景：用户个人包 SQLite 文件内容（私有 doGenerateUserSqlite 直调）。
     * 预期结果：含新链 bi_element/bi_span_element_part 两表及数据；不含旧链两表。
     */
    @Test
    void testDoGenerateUserSqlite_NewChainTables() throws Exception {
        Long userId = 95L;

        Project project = new Project();
        project.setId(1002L);

        Task task = new Task();
        task.setId(2002L);
        task.setBuildingId(3002L);

        Building building = new Building();
        building.setId(3002L);
        building.setRootObjectId(100L);

        BiObject rootObj = new BiObject();
        rootObj.setId(100L);

        Element element = new Element();
        element.setId(202L);
        element.setBiObjectId(100L);
        element.setName("element-2");
        element.setCode("E-202");
        element.setOfflineUuid("elem-uuid-202");

        SpanElementPart rel = new SpanElementPart();
        rel.setId(402L);
        rel.setElementId(202L);
        rel.setSpanId(100L);
        rel.setPartId(100L);
        rel.setOfflineUuid("sep-uuid-402");

        doReturn(Collections.singletonList(project)).when(projectMapper)
                .selectProjectList(any(Project.class), eq(userId), eq(null));
        doReturn(Collections.singletonList(task)).when(taskMapper).selectFullTaskListByProjectId(1002L);
        doReturn(Collections.singletonList(building)).when(buildingMapper)
                .selectBuildingsByIds(Collections.singletonList(3002L));
        doReturn(Collections.singletonList(rootObj)).when(biObjectMapper)
                .selectBiObjectsByIds(Collections.singletonList(100L));
        doReturn(new ArrayList<BiObject>()).when(biObjectMapper).selectChildrenById(100L);
        doReturn(Collections.singletonList(element)).when(elementMapper)
                .selectElementsByBiObjectIds(Collections.singletonList(100L));
        doReturn(Collections.singletonList(rel)).when(spanElementPartMapper)
                .selectBySpanIds(Collections.singletonList(100L));
        doReturn(new ArrayList<Disease>()).when(diseaseMapper)
                .selectDiseasesByBuildingIds(Collections.singletonList(3002L));
        doReturn(new ArrayList<Attachment>()).when(attachmentService).getAttachmentBySubjectIds(anyList());

        File result = (File) invokePrivate("doGenerateUserSqlite",
                new Class<?>[]{Long.class}, userId);
        generatedFiles.add(result);
        assertNotNull(result);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + result.getAbsolutePath());
             Statement statement = conn.createStatement()) {
            // 11 张用户包表
            assertEquals(11, queryCount(statement,
                    "select count(1) from sqlite_master where type='table' and name not like 'sqlite_%'"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_project"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_task"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_building"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_object"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_element"));
            assertEquals(1, queryCount(statement, "select count(1) from bi_span_element_part"));
            // 旧链两表不存在
            assertEquals(0, queryCount(statement,
                    "select count(1) from sqlite_master where type='table' "
                            + "and name in ('bi_component','bi_span_component_part')"));
            // bi_object 带主要构件标识列（评定 3.3.3 用），但不下发已冻结的单值 leaf_object_id
            assertEquals(1, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_object') where name='is_main_component'"));
            assertEquals(0, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_object') where name='leaf_object_id'"));
            // bi_disease 携带 leaf_object_id（病害显式归属叶子），与 bi_object 冻结列互不影响
            assertEquals(1, queryCount(statement,
                    "select count(1) from pragma_table_info('bi_disease') where name='leaf_object_id'"));
        }
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

    private FileMap storedFileMap() {
        FileMap stored = new FileMap();
        stored.setId(888);
        stored.setNewName("ab1234567890.db");
        stored.setCreateTime(new Date());
        return stored;
    }

    private Object invokePrivate(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = SqliteService.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(sqliteService, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
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
