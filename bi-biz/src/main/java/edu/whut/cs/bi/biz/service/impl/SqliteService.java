package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.vo.SqliteVo;
import edu.whut.cs.bi.biz.mapper.*;
import edu.whut.cs.bi.biz.service.AttachmentService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SQLite 数据库文件生成服务
 * 
 * 模块化支持：
 * 1. 项目级：全量数据
 * 2. 用户级：bi_project, bi_building, bi_task
 * 3. 结构物级：bi_object, bi_component, bi_attachment, bi_disease,
 * bi_disease_detail, bi_file_map
 *
 * @author Antigravity
 */
@Service
@Slf4j
public class SqliteService {

    @Resource
    private TaskMapper taskMapper;
    @Resource
    private BuildingMapper buildingMapper;
    @Resource
    private BiObjectMapper biObjectMapper;
    @Resource
    private ComponentMapper componentMapper;
    @Resource
    private DiseaseMapper diseaseMapper;
    @Resource
    private DiseaseDetailMapper diseaseDetailMapper;
    @Resource
    private AttachmentService attachmentService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private FileMapMapper fileMapMapper;
    @Resource
    private UserSqliteMapper userSqliteMapper;
    @Resource
    private BiTemplateObjectMapper biTemplateObjectMapper;
    @Resource
    private TODiseaseTypeMapper toDiseaseTypeMapper;
    @Resource
    private DiseaseTypeMapper diseaseTypeMapper;
    @Resource
    private DiseaseScaleMapper diseaseScaleMapper;
    @Resource
    private DiseasePositionMapper diseasePositionMapper;
    @Resource
    private TODiseasePositionMapper toDiseasePositionMapper;
    @Resource
    private BiObjectComponentMapper biObjectComponentMapper;

    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioConfig minioConfig;

    private final ConcurrentHashMap<String, Long> lastTriggerTime = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 5000;

    // ======================== API 入口 ========================

    /**
     * 按项目异步生成全量 SQLite
     */
    @Async("sqliteTaskExecutor")
    public void generateSqliteAsync(Long projectId) {
        if (projectId == null)
            return;
        if (isDebounced("project_" + projectId))
            return;

        log.info("[SQLite] 开始为项目 {} 生成全量 SQLite", projectId);
        File sqliteFile = null;
        try {
            sqliteFile = doGenerateProjectSqlite(projectId);
            if (sqliteFile != null && sqliteFile.exists()) {
                String fileMapId = uploadToMinio(sqliteFile, "project_" + projectId + ".db");
                updateProjectSqliteRef(projectId, fileMapId);
                log.info("[SQLite] 项目 {} 全量包上传成功", projectId);
            }
        } catch (Exception e) {
            log.error("[SQLite] 项目 {} 生成失败", projectId, e);
        } finally {
            cleanupTempFile(sqliteFile);
        }
    }

    /**
     * 按用户同步生成核心表 SQLite (供闭环强一致性使用)
     */
    public SqliteVo generateUserSqliteSync(Long userId) {
        if (userId == null)
            return null;

        log.info("[SQLite] 开始为用户 {} 生成核心表 SQLite (同步)", userId);
        File sqliteFile = null;
        try {
            sqliteFile = doGenerateUserSqlite(userId);
            if (sqliteFile != null && sqliteFile.exists()) {
                String fileMapId = uploadToMinio(sqliteFile, "user_" + userId + ".db");
                updateUserSqliteRef(userId, fileMapId, sqliteFile.length());
                log.info("[SQLite] 用户 {} 核心包上传成功 (同步)", userId);
                return getUserSqliteUrl(userId);
            }
        } catch (Exception e) {
            log.error("[SQLite] 用户 {} 生成失败 (同步)", userId, e);
        } finally {
            cleanupTempFile(sqliteFile);
        }
        return null;
    }

    /**
     * 按结构物异步生成检查数据 SQLite (bi_object, bi_component, bi_disease 等)
     */

    /**
     * 获取项目的 SQLite 文件同步信息
     */
    public SqliteVo getProjectSqliteUrl(Long projectId) {
        Project project = projectMapper.selectProjectById(projectId);
        if (project == null || project.getSqliteMinioId() == null)
            return null;
        return getVoFromFileMapId(project.getSqliteMinioId(), null);
    }

    /**
     * 获取用户的 SQLite 文件同步信息
     */
    public SqliteVo getUserSqliteUrl(Long userId) {
        UserSqlite us = userSqliteMapper.selectUserSqliteByUserId(userId);
        if (us == null || us.getMinioId() == null)
            return null;
        return getVoFromFileMapId(us.getMinioId(), us.getPackageSize());
    }

    /**
     * 生成通用基础核心数据 SQLite (模板、病害类型、标度)
     */
    public SqliteVo generateCommonBaseSqlite() {
        log.info("[SQLite] 开始生成通用基础核心表 SQLite");
        File sqliteFile = null;
        try {
            sqliteFile = doGenerateCommonBaseSqlite();
            if (sqliteFile != null && sqliteFile.exists()) {
                String fileMapId = uploadToMinio(sqliteFile, "common_base.db");
                log.info("[SQLite] 通用基础核心包上传成功");
                return getVoFromFileMapId(Long.valueOf(fileMapId), sqliteFile.length() / 1024 + " KB");
            }
        } catch (Exception e) {
            log.error("[SQLite] 通用基础核心包生成失败", e);
        } finally {
            cleanupTempFile(sqliteFile);
        }
        return null;
    }

    private SqliteVo getVoFromFileMapId(Long fileMapId, String size) {
        FileMap fm = fileMapMapper.selectFileMapById(fileMapId);
        if (fm == null)
            return null;
        String objName = fm.getNewName();
        String url = minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/"
                + objName.substring(0, 2) + "/" + objName;
        SqliteVo vo = new SqliteVo();
        vo.setUrl(url);
        vo.setTimestamp(fm.getCreateTime());
        vo.setSize(size);
        return vo;
    }

    // ======================== 核心生成逻辑 ========================

    private File doGenerateProjectSqlite(Long projectId) throws Exception {
        List<Task> tasks = taskMapper.selectFullTaskListByProjectId(projectId);
        if (tasks.isEmpty())
            return null;

        List<Long> bIds = tasks.stream().map(Task::getBuildingId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        List<Building> buildings = bIds.isEmpty() ? Collections.emptyList() : buildingMapper.selectBuildingsByIds(bIds);
        Project project = projectMapper.selectProjectById(projectId);

        File tempFile = File.createTempFile("project_" + projectId + "_", ".db");
        try (Connection conn = connect(tempFile)) {
            createAllTables(conn);
            if (project != null)
                insertProjects(conn, Collections.singletonList(project));
            insertTasks(conn, tasks);
            insertBuildings(conn, buildings);

            // 递归查询 Inspection Data (Objects -> Components -> Diseases)
            exportInspectionData(conn, bIds);
            conn.commit();
        }
        return tempFile;
    }

    private File doGenerateUserSqlite(Long userId) throws Exception {
        // 1. 获取关联项目
        List<Project> projects = projectMapper.selectProjectList(new Project(), userId, null);
        if (projects.isEmpty())
            return null;

        List<Long> pIds = projects.stream().map(Project::getId).collect(Collectors.toList());

        // 2. 获取关联任务
        List<Task> tasks = new ArrayList<>();
        for (Long pid : pIds) {
            tasks.addAll(taskMapper.selectFullTaskListByProjectId(pid));
        }

        // 3. 获取所有桥梁记录
        List<Long> bIds = tasks.stream().map(Task::getBuildingId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        List<Building> buildings = bIds.isEmpty() ? Collections.emptyList() : buildingMapper.selectBuildingsByIds(bIds);

        File tempFile = File.createTempFile("user_" + userId + "_", ".db");
        try (Connection conn = connect(tempFile)) {
            // 合并创建用户级与桥梁检查级相关的所有表
            createTables(conn, "bi_project", "bi_building", "bi_task", "bi_object", "bi_component", "bi_disease",
                    "bi_disease_detail", "bi_attachment", "bi_file_map", "bi_object_component");

            insertProjects(conn, projects);
            insertBuildings(conn, buildings);
            insertTasks(conn, tasks);

            // 4. 将所有关联桥梁的检查数据 (部件结构树、病害、照片等) 直接装填入此单一 DB
            if (!bIds.isEmpty()) {
                exportInspectionData(conn, bIds);
            }
            conn.commit();
        }
        return tempFile;
    }

    public File doGenerateBuildingSqlite(Long buildingId) throws Exception {
        Building building = buildingMapper.selectBuildingById(buildingId);
        if (building == null)
            return null;

        File tempFile = File.createTempFile("building_" + buildingId + "_", ".db");
        try (Connection conn = connect(tempFile)) {
            createTables(conn, "bi_object", "bi_component", "bi_attachment", "bi_disease", "bi_disease_detail",
                    "bi_file_map", "bi_object_component");
            exportInspectionData(conn, Collections.singletonList(buildingId));
            conn.commit();
        }
        return tempFile;
    }

    private File doGenerateCommonBaseSqlite() throws Exception {
        // 1. 获取全量基础数据
        List<BiTemplateObject> templateObjects = biTemplateObjectMapper
                .selectBiTemplateObjectList(new BiTemplateObject());
        List<Map<String, Object>> toMappings = toDiseaseTypeMapper.selectAllTemplateObjectDiseaseTypeMappings();
        List<DiseaseType> diseaseTypes = diseaseTypeMapper.selectDiseaseTypeList(new DiseaseType());
        List<DiseaseScale> diseaseScales = diseaseScaleMapper.selectDiseaseScaleList(new DiseaseScale());
        List<DiseasePosition> diseasePositions = diseasePositionMapper.selectDiseasePositionList(new DiseasePosition());
        List<Map<String, Object>> toDpMappings = toDiseasePositionMapper.selectAllMappings();

        File tempFile = File.createTempFile("common_base_", ".db");
        try (Connection conn = connect(tempFile)) {
            createTables(conn, "bi_template_object", "bi_template_object_disease_type", "bi_disease_type",
                    "bi_disease_scale", "bi_disease_position", "bi_template_object_disease_position");
            insertTemplateObjects(conn, templateObjects);
            insertTODiseaseTypeMappings(conn, toMappings);
            insertDiseaseTypes(conn, diseaseTypes);
            insertDiseaseScales(conn, diseaseScales);
            insertDiseasePositions(conn, diseasePositions);
            insertTODiseasePositionMappings(conn, toDpMappings);
            conn.commit();
        }
        return tempFile;
    }

    private void exportInspectionData(Connection conn, List<Long> buildingIds) throws Exception {
        List<Building> buildings = buildingMapper.selectBuildingsByIds(buildingIds);
        List<Long> rootObjectIds = buildings.stream().map(Building::getRootObjectId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());

        List<BiObject> allObjects = new ArrayList<>();
        for (Long rootId : rootObjectIds) {
            allObjects.addAll(biObjectMapper.selectBiObjectsByIds(Collections.singletonList(rootId)));
            List<BiObject> children = biObjectMapper.selectChildrenById(rootId);
            if (children != null)
                allObjects.addAll(children);
        }

        List<Long> oIds = allObjects.stream().map(BiObject::getId).distinct().collect(Collectors.toList());
        List<Component> components = oIds.isEmpty() ? Collections.emptyList()
                : componentMapper.selectComponentsByBiObjectIds(oIds);
        List<Disease> diseases = diseaseMapper.selectDiseasesByBuildingIds(buildingIds);
        List<Long> dIds = diseases.stream().map(Disease::getId).collect(Collectors.toList());
        List<DiseaseDetail> details = dIds.isEmpty() ? Collections.emptyList()
                : diseaseDetailMapper.selectDiseaseDetailsByDiseaseIds(dIds);

        List<Attachment> attachments = new ArrayList<>();
        if (!dIds.isEmpty())
            attachments.addAll(attachmentService.getAttachmentBySubjectIds(dIds));
        
        // 导出构件与桥跨关联关系 (2026新标)
        if (!oIds.isEmpty()) {
            // 查询所有以导出对象作为“桥跨”的关联记录
            BiObjectComponent query = new BiObjectComponent();
            List<BiObjectComponent> rels = new ArrayList<>();
            for (Long oId : oIds) {
                query.setBiObjectId(oId);
                rels.addAll(biObjectComponentMapper.selectBiObjectComponentList(query));
            }
            if (!rels.isEmpty()) {
                insertBiObjectComponents(conn, rels);
            }
        }

        if (!buildingIds.isEmpty()) {
            attachments.addAll(attachmentService.getAttachmentBySubjectIds(buildingIds).stream()
                    .filter(a -> Integer.valueOf(6).equals(a.getType())).collect(Collectors.toList()));
        }

        // 搜集所有关联的文件 ID (原图和缩略图)
        List<Long> mIds = attachments.stream()
                .flatMap(a -> java.util.stream.Stream.of(a.getMinioId(), a.getThumbMinioId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<FileMap> fMaps = mIds.isEmpty() ? Collections.emptyList() : fileMapMapper.selectFileMapByIds(mIds);

        insertBiObjects(conn, allObjects);
        insertComponents(conn, components);
        insertDiseases(conn, diseases);
        insertDiseaseDetails(conn, details);
        insertAttachments(conn, attachments);
        insertFileMaps(conn, fMaps);
    }

    // ======================== 辅助方法 ========================

    private Connection connect(File file) throws SQLException {
        try {
            // 某些运行方式下 SPI 自动注册不会生效，这里显式加载避免 No suitable driver
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC 驱动未加载，请检查 org.xerial:sqlite-jdbc 依赖是否在运行时类路径中", e);
        }

        String jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
        conn.setAutoCommit(false);
        return conn;
    }

    private boolean isDebounced(String key) {
        long now = System.currentTimeMillis();
        Long last = lastTriggerTime.get(key);
        if (last != null && now - last < DEBOUNCE_MS)
            return true;
        lastTriggerTime.put(key, now);
        return false;
    }

    private void cleanupTempFile(File file) {
        if (file != null && file.exists())
            file.delete();
    }

    private String uploadToMinio(File file, String oldName) throws Exception {
        String objectName = UUID.randomUUID().toString().replace("-", "") + ".db";
        try (FileInputStream fis = new FileInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName.substring(0, 2) + "/" + objectName)
                    .stream(fis, file.length(), -1)
                    .contentType("application/x-sqlite3")
                    .build());
        }
        FileMap fm = new FileMap();
        fm.setOldName(oldName);
        fm.setNewName(objectName);
        fm.setCreateTime(new Date());
        fm.setCreateBy("system");
        fileMapMapper.insertFileMap(fm);
        return fm.getId().toString();
    }

    private void updateProjectSqliteRef(Long projectId, String fileMapId) {
        Project p = new Project();
        p.setId(projectId);
        p.setSqliteMinioId(Long.valueOf(fileMapId));
        projectMapper.updateProject(p);
    }

    private void updateUserSqliteRef(Long userId, String fileMapId, long size) {
        UserSqlite us = userSqliteMapper.selectUserSqliteByUserId(userId);
        boolean exists = (us != null);
        if (!exists)
            us = new UserSqlite();
        us.setUserId(userId);
        us.setMinioId(Long.valueOf(fileMapId));
        us.setPackageTime(new Date());
        us.setUpdateTime(new Date());
        us.setPackageSize(size / 1024 + " KB");
        if (exists)
            userSqliteMapper.updateUserSqlite(us);
        else
            userSqliteMapper.insertUserSqlite(us);
    }

    // ======================== DDL/DML ========================

    private void createAllTables(Connection conn) throws SQLException {
        createTables(conn, "bi_project", "bi_building", "bi_task", "bi_object", "bi_component", "bi_disease",
                "bi_disease_detail", "bi_attachment", "bi_file_map", "bi_object_component");
    }

    private void createTables(Connection conn, String... tableNames) throws SQLException {
        Set<String> set = new HashSet<>(Arrays.asList(tableNames));
        try (Statement s = conn.createStatement()) {
            if (set.contains("bi_project"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_project (id INTEGER PRIMARY KEY, name TEXT, year INTEGER, status TEXT, code TEXT, start_date TEXT, end_date TEXT)");
            if (set.contains("bi_task"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_task (id INTEGER PRIMARY KEY, building_id INTEGER, project_id INTEGER, status TEXT, evaluation_result INTEGER, type INTEGER, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)");
            if (set.contains("bi_building"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_building (id INTEGER PRIMARY KEY, name TEXT, is_leaf TEXT, status TEXT, del_flag TEXT, longitude REAL, latitude REAL, altitude REAL, address TEXT, area TEXT, line TEXT, admin_dept TEXT, weight REAL, video_feed TEXT, root_object_id INTEGER, root_property_id INTEGER, remark TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, offline_uuid TEXT, root_object_uuid TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0)");
            if (set.contains("bi_object"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_object (id INTEGER PRIMARY KEY, parent_id INTEGER, name TEXT, ancestors TEXT, status TEXT, del_flag TEXT, longitude REAL, latitude REAL, altitude REAL, position TEXT, area TEXT, admin_dept TEXT, weight REAL, standard_weight REAL, video_feed TEXT, props TEXT, template_object_id INTEGER, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT, offline_uuid TEXT, parent_uuid TEXT, building_uuid TEXT, is_offline_data INTEGER DEFAULT 0, span_index INTEGER, span_length REAL, offline_deleted INTEGER DEFAULT 0)");
            if (set.contains("bi_component"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_component (id INTEGER PRIMARY KEY, bi_object_id INTEGER, name TEXT, code TEXT, status TEXT, del_flag TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT, offline_uuid TEXT, object_uuid TEXT, is_offline_data INTEGER DEFAULT 0, edi INTEGER, efi INTEGER, eai INTEGER, offline_deleted INTEGER DEFAULT 0)");
            if (set.contains("bi_disease"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_disease (id INTEGER PRIMARY KEY, position TEXT, position_number TEXT, type TEXT, disease_type_id INTEGER, description TEXT, level TEXT, quantity TEXT, units TEXT, nature TEXT, participate_assess TEXT, deduct_points INTEGER, img_no_exp TEXT, project_id INTEGER, bi_object_id INTEGER, bi_object_name TEXT, building_id INTEGER, component_id INTEGER, commit_type TEXT, local_id TEXT, remark TEXT, cause TEXT, repair_recommendation TEXT, crack_type TEXT, development_trend TEXT, detection_method TEXT, attachment_count INTEGER, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, task_id INTEGER, offline_uuid TEXT, building_uuid TEXT, object_uuid TEXT, component_uuid TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0)");
            if (set.contains("bi_file_map"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_file_map (id INTEGER PRIMARY KEY, old_name TEXT, new_name TEXT, create_time TEXT, update_time TEXT, create_by TEXT, file_type TEXT)");
            if (set.contains("bi_attachment"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_attachment (id INTEGER PRIMARY KEY, name TEXT, subject_id INTEGER, type INTEGER, del_flag TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, weight REAL, minio_id INTEGER, thumb_minio_id INTEGER, offline_uuid TEXT, offline_subject_uuid TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0)");
            if (set.contains("bi_disease_detail"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_disease_detail (id INTEGER PRIMARY KEY, disease_id INTEGER, reference1_location TEXT, reference1_location_start REAL, reference1_location_end REAL, reference2_location TEXT, reference2_location_start REAL, reference2_location_end REAL, length1 REAL, length2 REAL, length3 REAL, width REAL, height_depth REAL, crack_width REAL, area_length REAL, area_width REAL, area_identifier INTEGER, deformation REAL, angle INTEGER, numerator_ratio INTEGER, denominator_ratio INTEGER, length_range_start REAL, length_range_end REAL, width_range_start REAL, width_range_end REAL, height_depth_range_start REAL, height_depth_range_end REAL, crack_width_range_start REAL, crack_width_range_end REAL, area_range_start REAL, area_range_end REAL, deformation_range_start REAL, deformation_range_end REAL, angle_range_start REAL, angle_range_end REAL, other TEXT, offline_uuid TEXT, disease_uuid TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0)");

            // 基础模板表
            if (set.contains("bi_template_object"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_template_object (id INTEGER PRIMARY KEY, parent_id INTEGER, name TEXT, ancestors TEXT, status TEXT, del_flag TEXT, weight REAL, props TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)");
            if (set.contains("bi_template_object_disease_type"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_template_object_disease_type (template_object_id INTEGER, disease_type_id INTEGER, PRIMARY KEY(template_object_id, disease_type_id))");
            if (set.contains("bi_disease_type"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_disease_type (id INTEGER PRIMARY KEY, code TEXT, name TEXT, max_scale INTEGER, min_scale INTEGER, status TEXT, threshold INTEGER, group_name TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)");
            if (set.contains("bi_disease_scale"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_disease_scale (id INTEGER PRIMARY KEY, type_code TEXT, scale INTEGER, qualitative_description TEXT, quantitative_description TEXT, status TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)");
            if (set.contains("bi_disease_position"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_disease_position (id INTEGER PRIMARY KEY, name TEXT, code TEXT, props TEXT, ref1 TEXT, ref2 TEXT, sort_order INTEGER, status TEXT, del_flag TEXT, remark TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT)");
            if (set.contains("bi_template_object_disease_position"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_template_object_disease_position (template_object_id INTEGER, disease_position_id INTEGER, PRIMARY KEY(template_object_id, disease_position_id))");
            if (set.contains("bi_object_component"))
                s.execute(
                        "CREATE TABLE IF NOT EXISTS bi_object_component (id INTEGER PRIMARY KEY, component_id INTEGER, bi_object_id INTEGER, component_uuid TEXT, object_uuid TEXT, weight REAL, offline_uuid TEXT, is_offline_data INTEGER DEFAULT 0, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, offline_deleted INTEGER DEFAULT 0)");
        }
    }

    private void insertProjects(Connection conn, List<Project> projects) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_project (id, name, year, status, code, start_date, end_date) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Project p : projects) {
                ps.setLong(1, p.getId());
                ps.setString(2, p.getName());
                setIntOrNull(ps, 3, p.getYear());
                ps.setString(4, p.getStatus());
                ps.setString(5, p.getCode());
                ps.setString(6, dateToStr(p.getStartDate()));
                ps.setString(7, dateToStr(p.getEndDate()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertTasks(Connection conn, List<Task> tasks) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_task (id, building_id, project_id, status, evaluation_result, type, create_by, create_time, update_by, update_time, remark) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Task t : tasks) {
                ps.setLong(1, t.getId());
                setLongOrNull(ps, 2, t.getBuildingId());
                setLongOrNull(ps, 3, t.getProjectId());
                ps.setString(4, t.getStatus());
                setIntOrNull(ps, 5, t.getEvaluationResult());
                setIntOrNull(ps, 6, t.getType());
                ps.setString(7, t.getCreateBy());
                ps.setString(8, dateToStr(t.getCreateTime()));
                ps.setString(9, t.getUpdateBy());
                ps.setString(10, dateToStr(t.getUpdateTime()));
                ps.setString(11, t.getRemark());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertBuildings(Connection conn, List<Building> buildings) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_building (id, name, is_leaf, status, del_flag, longitude, latitude, altitude, address, area, line, admin_dept, weight, video_feed, root_object_id, root_property_id, remark, create_by, create_time, update_by, update_time, offline_uuid, root_object_uuid, is_offline_data, offline_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Building b : buildings) {
                ps.setLong(1, b.getId());
                ps.setString(2, b.getName());
                ps.setString(3, b.getIsLeaf());
                ps.setString(4, b.getStatus());
                ps.setString(5, b.getDelFlag());
                setDecimalOrNull(ps, 6, b.getLongitude());
                setDecimalOrNull(ps, 7, b.getLatitude());
                setDecimalOrNull(ps, 8, b.getAltitude());
                ps.setString(9, b.getAddress());
                ps.setString(10, b.getArea());
                ps.setString(11, b.getLine());
                ps.setString(12, b.getAdminDept());
                setDecimalOrNull(ps, 13, b.getWeight());
                ps.setString(14, b.getVideoFeed());
                setLongOrNull(ps, 15, b.getRootObjectId());
                setLongOrNull(ps, 16, b.getRootPropertyId());
                ps.setString(17, b.getRemark());
                ps.setString(18, b.getCreateBy());
                ps.setString(19, dateToStr(b.getCreateTime()));
                ps.setString(20, b.getUpdateBy());
                ps.setString(21, dateToStr(b.getUpdateTime()));
                ps.setString(22, b.getOfflineUuid());
                ps.setString(23, b.getRootObjectUuid());
                ps.setInt(24, 0); // 云端下发的数据，状态标记为已完成同步 (0)
                ps.setInt(25, 0); // 默认未被删除
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertBiObjects(Connection conn, List<BiObject> objects) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_object (id, parent_id, name, ancestors, status, del_flag, longitude, latitude, altitude, position, area, admin_dept, weight, standard_weight, video_feed, props, template_object_id, create_by, create_time, update_by, update_time, remark, offline_uuid, parent_uuid, building_uuid, is_offline_data, span_index, span_length, offline_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (BiObject o : objects) {
                ps.setLong(1, o.getId());
                setLongOrNull(ps, 2, o.getParentId());
                ps.setString(3, o.getName());
                ps.setString(4, o.getAncestors());
                ps.setString(5, o.getStatus());
                ps.setString(6, o.getDelFlag());
                setDecimalOrNull(ps, 7, o.getLongitude());
                setDecimalOrNull(ps, 8, o.getLatitude());
                setDecimalOrNull(ps, 9, o.getAltitude());
                ps.setString(10, o.getPosition());
                ps.setString(11, o.getArea());
                ps.setString(12, o.getAdminDept());
                setDecimalOrNull(ps, 13, o.getWeight());
                setDecimalOrNull(ps, 14, o.getStandardWeight());
                ps.setString(15, o.getVideoFeed());
                ps.setString(16, o.getProps());
                setLongOrNull(ps, 17, o.getTemplateObjectId());
                ps.setString(18, o.getCreateBy());
                ps.setString(19, dateToStr(o.getCreateTime()));
                ps.setString(20, o.getUpdateBy());
                ps.setString(21, dateToStr(o.getUpdateTime()));
                ps.setString(22, o.getRemark());
                ps.setString(23, o.getOfflineUuid());
                ps.setString(24, o.getParentUuid());
                ps.setString(25, o.getBuildingUuid());
                ps.setInt(26, 0); 
                setIntOrNull(ps, 27, o.getSpanIndex());
                setDecimalOrNull(ps, 28, o.getSpanLength());
                ps.setInt(29, 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertBiObjectComponents(Connection conn, List<BiObjectComponent> rels) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_object_component (id, component_id, bi_object_id, component_uuid, object_uuid, weight, offline_uuid, is_offline_data, create_by, create_time, update_by, update_time, offline_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (BiObjectComponent r : rels) {
                ps.setLong(1, r.getId());
                setLongOrNull(ps, 2, r.getComponentId());
                setLongOrNull(ps, 3, r.getBiObjectId());
                ps.setString(4, r.getComponentUuid());
                ps.setString(5, r.getObjectUuid());
                setDecimalOrNull(ps, 6, r.getWeight());
                ps.setString(7, r.getOfflineUuid());
                ps.setInt(8, 0); 
                ps.setString(9, r.getCreateBy());
                ps.setString(10, dateToStr(r.getCreateTime()));
                ps.setString(11, r.getUpdateBy());
                ps.setString(12, dateToStr(r.getUpdateTime()));
                ps.setInt(13, 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertComponents(Connection conn, List<Component> components) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_component (id, bi_object_id, name, code, status, del_flag, create_by, create_time, update_by, update_time, remark, offline_uuid, object_uuid, is_offline_data, edi, efi, eai, offline_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Component c : components) {
                ps.setLong(1, c.getId());
                setLongOrNull(ps, 2, c.getBiObjectId());
                ps.setString(3, c.getName());
                ps.setString(4, c.getCode());
                ps.setString(5, c.getStatus());
                ps.setString(6, c.getDelFlag());
                ps.setString(7, c.getCreateBy());
                ps.setString(8, dateToStr(c.getCreateTime()));
                ps.setString(9, c.getUpdateBy());
                ps.setString(10, dateToStr(c.getUpdateTime()));
                ps.setString(11, c.getRemark());
                ps.setString(12, c.getOfflineUuid());
                ps.setString(13, c.getObjectUuid());
                ps.setInt(14, 0); // 云端下发的数据，状态标记为已完成同步 (0)
                setIntOrNull(ps, 15, c.getEdi());
                setIntOrNull(ps, 16, c.getEfi());
                setIntOrNull(ps, 17, c.getEai());
                ps.setInt(18, 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertDiseases(Connection conn, List<Disease> diseases) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_disease (id, position, position_number, type, disease_type_id, description, level, quantity, units, nature, participate_assess, deduct_points, img_no_exp, project_id, bi_object_id, bi_object_name, building_id, component_id, commit_type, local_id, remark, cause, repair_recommendation, crack_type, development_trend, detection_method, attachment_count, create_by, create_time, update_by, update_time, task_id, offline_uuid, building_uuid, object_uuid, component_uuid, is_offline_data, offline_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Disease d : diseases) {
                ps.setLong(1, d.getId());
                ps.setString(2, d.getPosition());
                ps.setString(3, d.getPositionNumber() == null ? null : String.valueOf(d.getPositionNumber()));
                ps.setString(4, d.getType());
                setLongOrNull(ps, 5, d.getDiseaseTypeId());
                ps.setString(6, d.getDescription());
                ps.setString(7, String.valueOf(d.getLevel()));
                ps.setString(8, String.valueOf(d.getQuantity()));
                ps.setString(9, d.getUnits());
                ps.setString(10, d.getNature());
                ps.setString(11, d.getParticipateAssess());
                setIntOrNull(ps, 12, d.getDeductPoints());
                ps.setString(13, d.getImgNoExp());
                setLongOrNull(ps, 14, d.getProjectId());
                setLongOrNull(ps, 15, d.getBiObjectId());
                ps.setString(16, d.getBiObjectName());
                setLongOrNull(ps, 17, d.getBuildingId());
                setLongOrNull(ps, 18, d.getComponentId());
                ps.setString(19, d.getCommitType() == null ? null : String.valueOf(d.getCommitType()));
                ps.setString(20, d.getLocalId() == null ? null : String.valueOf(d.getLocalId()));
                ps.setString(21, d.getRemark());
                ps.setString(22, d.getCause());
                ps.setString(23, d.getRepairRecommendation());
                ps.setString(24, d.getCrackType());
                ps.setString(25, d.getDevelopmentTrend());
                ps.setString(26, d.getDetectionMethod());
                setIntOrNull(ps, 27, d.getAttachmentCount());
                ps.setString(28, d.getCreateBy());
                ps.setString(29, dateToStr(d.getCreateTime()));
                ps.setString(30, d.getUpdateBy());
                ps.setString(31, dateToStr(d.getUpdateTime()));
                setLongOrNull(ps, 32, d.getTaskId());
                ps.setString(33, d.getOfflineUuid());
                ps.setString(34, d.getBuildingUuid());
                ps.setString(35, d.getObjectUuid());
                ps.setString(36, d.getComponentUuid());
                ps.setInt(37, 0); // 云端下发的数据，状态标记为已完成同步 (0)
                ps.setInt(38, 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertDiseaseDetails(Connection conn, List<DiseaseDetail> details) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_disease_detail (id, disease_id, reference1_location, reference1_location_start, reference1_location_end, reference2_location, reference2_location_start, reference2_location_end, length1, length2, length3, width, height_depth, crack_width, area_length, area_width, area_identifier, deformation, angle, numerator_ratio, denominator_ratio, length_range_start, length_range_end, width_range_start, width_range_end, height_depth_range_start, height_depth_range_end, crack_width_range_start, crack_width_range_end, area_range_start, area_range_end, deformation_range_start, deformation_range_end, angle_range_start, angle_range_end, other, offline_uuid, disease_uuid, is_offline_data, offline_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (DiseaseDetail dd : details) {
                ps.setLong(1, dd.getId());
                setLongOrNull(ps, 2, dd.getDiseaseId());
                ps.setString(3, dd.getReference1Location());
                setDecimalOrNull(ps, 4, dd.getReference1LocationStart());
                setDecimalOrNull(ps, 5, dd.getReference1LocationEnd());
                ps.setString(6, dd.getReference2Location());
                setDecimalOrNull(ps, 7, dd.getReference2LocationStart());
                setDecimalOrNull(ps, 8, dd.getReference2LocationEnd());
                setDecimalOrNull(ps, 9, dd.getLength1());
                setDecimalOrNull(ps, 10, dd.getLength2());
                setDecimalOrNull(ps, 11, dd.getLength3());
                setDecimalOrNull(ps, 12, dd.getWidth());
                setDecimalOrNull(ps, 13, dd.getHeightDepth());
                setDecimalOrNull(ps, 14, dd.getCrackWidth());
                setDecimalOrNull(ps, 15, dd.getAreaLength());
                setDecimalOrNull(ps, 16, dd.getAreaWidth());
                setIntOrNull(ps, 17, dd.getAreaIdentifier());
                setDecimalOrNull(ps, 18, dd.getDeformation());
                setIntOrNull(ps, 19, dd.getAngle());
                setIntOrNull(ps, 20, dd.getNumeratorRatio());
                setIntOrNull(ps, 21, dd.getDenominatorRatio());
                setDecimalOrNull(ps, 22, dd.getLengthRangeStart());
                setDecimalOrNull(ps, 23, dd.getLengthRangeEnd());
                setDecimalOrNull(ps, 24, dd.getWidthRangeStart());
                setDecimalOrNull(ps, 25, dd.getWidthRangeEnd());
                setDecimalOrNull(ps, 26, dd.getHeightDepthRangeStart());
                setDecimalOrNull(ps, 27, dd.getHeightDepthRangeEnd());
                setDecimalOrNull(ps, 28, dd.getCrackWidthRangeStart());
                setDecimalOrNull(ps, 29, dd.getCrackWidthRangeEnd());
                setDecimalOrNull(ps, 30, dd.getAreaRangeStart());
                setDecimalOrNull(ps, 31, dd.getAreaRangeEnd());
                setDecimalOrNull(ps, 32, dd.getDeformationRangeStart());
                setDecimalOrNull(ps, 33, dd.getDeformationRangeEnd());
                setDecimalOrNull(ps, 34, dd.getAngleRangeStart());
                setDecimalOrNull(ps, 35, dd.getAngleRangeEnd());
                ps.setString(36, dd.getOther());
                ps.setString(37, dd.getOfflineUuid());
                ps.setString(38, dd.getDiseaseUuid());
                ps.setInt(39, 0); // 已同步标志
                ps.setInt(40, 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertAttachments(Connection conn, List<Attachment> attachments) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_attachment (id, name, subject_id, type, minio_id, thumb_minio_id, offline_uuid, offline_subject_uuid, is_offline_data, offline_deleted) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Attachment a : attachments) {
                ps.setLong(1, a.getId());
                ps.setString(2, a.getName());
                setLongOrNull(ps, 3, a.getSubjectId());
                setIntOrNull(ps, 4, a.getType());
                setLongOrNull(ps, 5, a.getMinioId());
                setLongOrNull(ps, 6, a.getThumbMinioId());
                ps.setString(7, a.getOfflineUuid());
                ps.setString(8, a.getOfflineSubjectUuid());
                ps.setInt(9, 0); // 已同步标志
                ps.setInt(10, 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertFileMaps(Connection conn, List<FileMap> fileMaps) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_file_map (id, old_name, new_name, create_time, update_time, create_by, file_type) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (FileMap f : fileMaps) {
                ps.setInt(1, f.getId());
                ps.setString(2, f.getOldName());
                ps.setString(3, f.getNewName());
                ps.setString(4, dateToStr(f.getCreateTime()));
                ps.setString(5, dateToStr(f.getUpdateTime()));
                ps.setString(6, f.getCreateBy());
                ps.setString(7, f.getFileType());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertTemplateObjects(Connection conn, List<BiTemplateObject> objects) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_template_object (id, parent_id, name, ancestors, status, del_flag, weight, props, create_by, create_time, update_by, update_time, remark) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (BiTemplateObject o : objects) {
                ps.setLong(1, o.getId());
                setLongOrNull(ps, 2, o.getParentId());
                ps.setString(3, o.getName());
                ps.setString(4, o.getAncestors());
                ps.setString(5, o.getStatus());
                ps.setString(6, o.getDelFlag());
                setDecimalOrNull(ps, 7, o.getWeight());
                ps.setString(8, o.getProps());
                ps.setString(9, o.getCreateBy());
                ps.setString(10, dateToStr(o.getCreateTime()));
                ps.setString(11, tToUpdateBy(o));
                ps.setString(12, dateToStr(o.getUpdateTime()));
                ps.setString(13, o.getRemark());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertTODiseaseTypeMappings(Connection conn, List<Map<String, Object>> mappings) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_template_object_disease_type (template_object_id, disease_type_id) VALUES (?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, Object> m : mappings) {
                ps.setObject(1, m.get("template_object_id"));
                ps.setObject(2, m.get("disease_type_id"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertDiseaseTypes(Connection conn, List<DiseaseType> types) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_disease_type (id, code, name, max_scale, min_scale, status, threshold, group_name, create_by, create_time, update_by, update_time, remark) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (DiseaseType t : types) {
                ps.setLong(1, t.getId());
                ps.setString(2, t.getCode());
                ps.setString(3, t.getName());
                setIntOrNull(ps, 4, t.getMaxScale());
                setIntOrNull(ps, 5, t.getMinScale());
                ps.setString(6, t.getStatus());
                setIntOrNull(ps, 7, t.getThreshold());
                ps.setString(8, t.getGroupName());
                ps.setString(9, t.getCreateBy());
                ps.setString(10, dateToStr(t.getCreateTime()));
                ps.setString(11, t.getUpdateBy());
                ps.setString(12, dateToStr(t.getUpdateTime()));
                ps.setString(13, t.getRemark());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertDiseaseScales(Connection conn, List<DiseaseScale> scales) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_disease_scale (id, type_code, scale, qualitative_description, quantitative_description, status, create_by, create_time, update_by, update_time, remark) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (DiseaseScale s : scales) {
                ps.setLong(1, s.getId());
                ps.setString(2, s.getTypeCode());
                setIntOrNull(ps, 3, s.getScale());
                ps.setString(4, s.getQualitativeDescription());
                ps.setString(5, s.getQuantitativeDescription());
                ps.setString(6, s.getStatus());
                ps.setString(7, s.getCreateBy());
                ps.setString(8, dateToStr(s.getCreateTime()));
                ps.setString(9, s.getUpdateBy());
                ps.setString(10, dateToStr(s.getUpdateTime()));
                ps.setString(11, s.getRemark());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertDiseasePositions(Connection conn, List<DiseasePosition> positions) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_disease_position (id, name, code, props, ref1, ref2, sort_order, status, del_flag, remark, create_by, create_time, update_by, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (DiseasePosition dp : positions) {
                ps.setLong(1, dp.getId());
                ps.setString(2, dp.getName());
                ps.setString(3, dp.getCode());
                ps.setString(4, dp.getProps());
                ps.setString(5, dp.getRef1());
                ps.setString(6, dp.getRef2());
                setIntOrNull(ps, 7, dp.getSortOrder());
                ps.setString(8, dp.getStatus());
                ps.setString(9, dp.getDelFlag());
                ps.setString(10, dp.getRemark());
                ps.setString(11, dp.getCreateBy());
                ps.setString(12, dateToStr(dp.getCreateTime()));
                ps.setString(13, dp.getUpdateBy());
                ps.setString(14, dateToStr(dp.getUpdateTime()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertTODiseasePositionMappings(Connection conn, List<Map<String, Object>> mappings) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_template_object_disease_position (template_object_id, disease_position_id) VALUES (?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, Object> m : mappings) {
                ps.setObject(1, m.get("template_object_id"));
                ps.setObject(2, m.get("disease_position_id"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String tToUpdateBy(BiTemplateObject o) {
        try {
            return o.getUpdateBy();
        } catch (Error | Exception e) {
            return null;
        }
    }

    private void setLongOrNull(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null)
            ps.setLong(index, value);
        else
            ps.setNull(index, Types.INTEGER);
    }

    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null)
            ps.setInt(index, value);
        else
            ps.setNull(index, Types.INTEGER);
    }

    private void setDecimalOrNull(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value != null)
            ps.setDouble(index, value.doubleValue());
        else
            ps.setNull(index, Types.REAL);
    }

    private String dateToStr(Date date) {
        if (date == null)
            return null;
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
}
