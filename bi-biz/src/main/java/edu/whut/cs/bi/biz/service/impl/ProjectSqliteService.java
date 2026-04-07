package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.config.MinioConfig;
import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.domain.vo.ProjectSqliteVo;
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
 * 项目级 SQLite 数据库文件生成服务
 * <p>
 * 负责从 MySQL 提取项目关联的 7 张核心表数据，写入独立的 SQLite 文件，
 * 并上传到 MinIO 供移动端下载。
 *
 * @author Antigravity
 */
@Service
@Slf4j
public class ProjectSqliteService {

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

    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioConfig minioConfig;

    /**
     * 防抖控制：记录每个 projectId 最后一次触发时间戳，
     * 如果 5 秒内有多次触发，仅执行最后一次。
     */
    private final ConcurrentHashMap<Long, Long> lastTriggerTime = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 5000;

    /**
     * 异步生成 SQLite 数据库文件（对外暴露的入口方法）
     *
     * @param projectId 项目 ID
     */
    @Async("sqliteTaskExecutor")
    public void generateSqliteAsync(Long projectId) {
        if (projectId == null) {
            return;
        }

        // 防抖：记录触发时间，延迟后检查是否为最新触发
        long triggerTime = System.currentTimeMillis();
        lastTriggerTime.put(projectId, triggerTime);

        try {
            Thread.sleep(DEBOUNCE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // 如果在等待期间又有新触发，则放弃本次执行
        Long latestTrigger = lastTriggerTime.get(projectId);
        if (latestTrigger != null && latestTrigger > triggerTime) {
            log.info("[SQLite] 项目 {} 在防抖期间有新触发，跳过本次生成", projectId);
            return;
        }

        log.info("[SQLite] 开始为项目 {} 生成 SQLite 数据库文件", projectId);
        File sqliteFile = null;
        try {
            sqliteFile = doGenerateSqlite(projectId);
            if (sqliteFile != null && sqliteFile.exists()) {
                String minioObjectName = uploadToMinio(sqliteFile, projectId);
                updateProjectSqliteRef(projectId, minioObjectName);
                log.info("[SQLite] 项目 {} 的 SQLite 文件生成并上传成功: {}", projectId, minioObjectName);
            }
        } catch (Exception e) {
            log.error("[SQLite] 项目 {} 的 SQLite 文件生成失败", projectId, e);
        } finally {
            // 清理临时文件
            if (sqliteFile != null && sqliteFile.exists()) {
                sqliteFile.delete();
            }
        }
    }

    /**
     * 获取项目的 SQLite 文件下载 URL
     *
     * @param projectId 项目 ID
     * @return MinIO 下载 URL，如果不存在返回 null
     */
    public ProjectSqliteVo getSqliteDownloadUrl(Long projectId) {
        Project project = projectMapper.selectProjectById(projectId);
        if (project == null || project.getSqliteMinioId() == null) {
            return null;
        }
        FileMap fileMap = fileMapMapper.selectFileMapById(project.getSqliteMinioId());
        if (fileMap == null) {
            return null;
        }
        String objectName = fileMap.getNewName();
        String downloadUrl = minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/"
                + objectName.substring(0, 2) + "/" + objectName;

        ProjectSqliteVo vo = new ProjectSqliteVo();
        vo.setUrl(downloadUrl);
        vo.setTimestamp(fileMap.getCreateTime());
        return vo;
    }

    // ======================== 私有方法 ========================

    /**
     * 核心生成逻辑：收集数据 → 建表 → 写入 → 返回临时文件
     */
    private File doGenerateSqlite(Long projectId) throws Exception {
        // 1. 收集数据
        List<Task> tasks = taskMapper.selectFullTaskListByProjectId(projectId);
        if (tasks == null || tasks.isEmpty()) {
            log.warn("[SQLite] 项目 {} 没有关联任务，跳过生成", projectId);
            return null;
        }

        List<Long> buildingIds = tasks.stream()
                .map(Task::getBuildingId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (buildingIds.isEmpty()) {
            log.warn("[SQLite] 项目 {} 的任务没有关联建筑，跳过生成", projectId);
            return null;
        }

        // 查询建筑
        List<Building> buildings = buildingMapper.selectBuildingsByIds(buildingIds);

        // 查询 BiObject（通过桥梁根节点的 ancestors 批量查找所有子孙节点）
        List<Long> rootObjectIds = buildings.stream()
                .map(Building::getRootObjectId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<BiObject> allBiObjects = new ArrayList<>();
        if (!rootObjectIds.isEmpty()) {
            // 先把根节点本身加进去
            allBiObjects.addAll(biObjectMapper.selectBiObjectsByIds(rootObjectIds));
            // 再批量查询所有以这些根节点为祖先的节点
            for (Long rootId : rootObjectIds) {
                List<BiObject> children = biObjectMapper.selectChildrenById(rootId);
                if (children != null) {
                    allBiObjects.addAll(children);
                }
            }
        }

        // 收集所有 biObjectId 用于查询 Component
        List<Long> allBiObjectIds = allBiObjects.stream()
                .map(BiObject::getId)
                .distinct()
                .collect(Collectors.toList());
        List<Component> components = allBiObjectIds.isEmpty()
                ? Collections.emptyList()
                : componentMapper.selectComponentsByBiObjectIds(allBiObjectIds);

        // 通过 buildingId 获取 Disease
        List<Disease> diseases = diseaseMapper.selectDiseasesByBuildingIds(buildingIds);

        // 获取 DiseaseDetail
        List<Long> diseaseIds = diseases.stream()
                .map(Disease::getId)
                .collect(Collectors.toList());
        List<DiseaseDetail> diseaseDetails = diseaseIds.isEmpty()
                ? Collections.emptyList()
                : diseaseDetailMapper.selectDiseaseDetailsByDiseaseIds(diseaseIds);

        // 获取 Attachment（通过 disease subjectId）
        List<Attachment> diseaseAttachments = diseaseIds.isEmpty()
                ? Collections.emptyList()
                : attachmentService.getAttachmentBySubjectIds(diseaseIds);

        // 获取桥梁附件（通过 buildingIds，且 type = 6）
        List<Attachment> buildingAttachments = buildingIds.isEmpty()
                ? Collections.emptyList()
                : attachmentService.getAttachmentBySubjectIds(buildingIds).stream()
                    .filter(a -> Integer.valueOf(6).equals(a.getType()))
                    .collect(Collectors.toList());

        // 合并附件
        List<Attachment> allAttachments = new ArrayList<>();
        allAttachments.addAll(diseaseAttachments);
        allAttachments.addAll(buildingAttachments);

        // 获取 FileMap（通过 Attachment 的 minioId）
        List<Long> minioIds = allAttachments.stream()
                .map(Attachment::getMinioId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<FileMap> fileMaps = minioIds.isEmpty()
                ? Collections.emptyList()
                : fileMapMapper.selectFileMapByIds(minioIds);

        // 2. 创建 SQLite 文件并写入数据
        File tempFile = File.createTempFile("project_" + projectId + "_", ".db");
        String jdbcUrl = "jdbc:sqlite:" + tempFile.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            // 性能优化：必须在 setAutoCommit(false) 之前设置模式
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }

            conn.setAutoCommit(false);

            createTables(conn);
            insertTasks(conn, tasks);
            insertBuildings(conn, buildings);
            insertBiObjects(conn, allBiObjects);
            insertComponents(conn, components);
            insertDiseases(conn, diseases);
            insertDiseaseDetails(conn, diseaseDetails);
            insertAttachments(conn, allAttachments);
            insertFileMaps(conn, fileMaps);

            conn.commit();
        }

        log.info("[SQLite] 项目 {} 数据写入完成: tasks={}, buildings={}, objects={}, " +
                        "components={}, diseases={}, details={}, attachments={}, fileMaps={}",
                projectId, tasks.size(), buildings.size(), allBiObjects.size(),
                components.size(), diseases.size(), diseaseDetails.size(), allAttachments.size(), fileMaps.size());

        return tempFile;
    }

    /**
     * 上传 SQLite 文件到 MinIO
     */
    private String uploadToMinio(File file, Long projectId) throws Exception {
        String objectName = UUID.randomUUID().toString().replace("-", "") + ".db";
        try (FileInputStream fis = new FileInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName.substring(0, 2) + "/" + objectName)
                    .stream(fis, file.length(), -1)
                    .contentType("application/x-sqlite3")
                    .build());
        }

        // 保存 FileMap 记录
        FileMap fileMap = new FileMap();
        fileMap.setOldName("project_" + projectId + ".db");
        fileMap.setNewName(objectName);
        fileMap.setCreateTime(new Date());
        fileMap.setCreateBy("system");
        fileMapMapper.insertFileMap(fileMap);

        return fileMap.getId().toString();
    }

    /**
     * 更新 Project 的 sqliteMinioId 关联
     */
    private void updateProjectSqliteRef(Long projectId, String fileMapId) {
        Project project = new Project();
        project.setId(projectId);
        project.setSqliteMinioId(Long.valueOf(fileMapId));
        projectMapper.updateProject(project);
    }

    // ======================== DDL: 建表语句 ========================

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // bi_task
            stmt.execute("CREATE TABLE IF NOT EXISTS bi_task (" +
                    "id INTEGER PRIMARY KEY, " +
                    "building_id INTEGER, " +
                    "project_id INTEGER, " +
                    "status TEXT, " +
                    "evaluation_result INTEGER, " +
                    "type INTEGER, " +
                    "create_by TEXT, " +
                    "create_time TEXT, " +
                    "update_by TEXT, " +
                    "update_time TEXT, " +
                    "remark TEXT)");

            // bi_building
            stmt.execute("CREATE TABLE IF NOT EXISTS bi_building (" +
                    "id INTEGER PRIMARY KEY, " +
                    "name TEXT, " +
                    "is_leaf TEXT, " +
                    "status TEXT, " +
                    "del_flag TEXT, " +
                    "longitude REAL, " +
                    "latitude REAL, " +
                    "altitude REAL, " +
                    "address TEXT, " +
                    "area TEXT, " +
                    "line TEXT, " +
                    "admin_dept TEXT, " +
                    "weight REAL, " +
                    "video_feed TEXT, " +
                    "root_object_id INTEGER, " +
                    "root_property_id INTEGER, " +
                    "remark TEXT, " +
                    "create_by TEXT, " +
                    "create_time TEXT, " +
                    "update_by TEXT, " +
                    "update_time TEXT)");

            // bi_object
            stmt.execute("CREATE TABLE IF NOT EXISTS bi_object (" +
                    "id INTEGER PRIMARY KEY, " +
                    "parent_id INTEGER, " +
                    "name TEXT, " +
                    "ancestors TEXT, " +
                    "status TEXT, " +
                    "del_flag TEXT, " +
                    "longitude REAL, " +
                    "latitude REAL, " +
                    "altitude REAL, " +
                    "position TEXT, " +
                    "area TEXT, " +
                    "admin_dept TEXT, " +
                    "weight REAL, " +
                    "standard_weight REAL, " +
                    "video_feed TEXT, " +
                    "props TEXT, " +
                    "template_object_id INTEGER, " +
                    "create_by TEXT, " +
                    "create_time TEXT, " +
                    "update_by TEXT, " +
                    "update_time TEXT, " +
                    "remark TEXT)");

            // bi_component
            stmt.execute("CREATE TABLE IF NOT EXISTS bi_component (" +
                    "id INTEGER PRIMARY KEY, " +
                    "bi_object_id INTEGER, " +
                    "name TEXT, " +
                    "code TEXT, " +
                    "status TEXT, " +
                    "del_flag TEXT, " +
                    "create_by TEXT, " +
                    "create_time TEXT, " +
                    "update_by TEXT, " +
                    "update_time TEXT, " +
                    "remark TEXT)");

            // bi_disease
            stmt.execute("CREATE TABLE IF NOT EXISTS bi_disease (" +
                    "id INTEGER PRIMARY KEY, " +
                    "position TEXT, " +
                    "position_number TEXT, " +
                    "type TEXT, " +
                    "disease_type_id INTEGER, " +
                    "description TEXT, " +
                    "level TEXT, " +
                    "quantity TEXT, " +
                    "units TEXT, " +
                    "nature TEXT, " +
                    "participate_assess TEXT, " +
                    "deduct_points INTEGER, " +
                    "img_no_exp TEXT, " +
                    "project_id INTEGER, " +
                    "bi_object_id INTEGER, " +
                    "bi_object_name TEXT, " +
                    "building_id INTEGER, " +
                    "component_id INTEGER, " +
                    "commit_type TEXT, " +
                    "local_id TEXT, " +
                    "remark TEXT, " +
                    "cause TEXT, " +
                    "repair_recommendation TEXT, " +
                    "crack_type TEXT, " +
                    "development_trend TEXT, " +
                    "detection_method TEXT, " +
                    "attachment_count INTEGER, " +
                    "create_by TEXT, " +
                    "create_time TEXT, " +
                    "update_by TEXT, " +
                    "update_time TEXT, " +
                    "task_id INTEGER)");

            // bi_disease_detail
            stmt.execute("CREATE TABLE IF NOT EXISTS bi_disease_detail (" +
                    "id INTEGER PRIMARY KEY, " +
                    "disease_id INTEGER, " +
                    "reference1_location TEXT, " +
                    "reference1_location_start REAL, " +
                    "reference1_location_end REAL, " +
                    "reference2_location TEXT, " +
                    "reference2_location_start REAL, " +
                    "reference2_location_end REAL, " +
                    "length1 REAL, " +
                    "length2 REAL, " +
                    "length3 REAL, " +
                    "width REAL, " +
                    "height_depth REAL, " +
                    "crack_width REAL, " +
                    "area_length REAL, " +
                    "area_width REAL, " +
                    "area_identifier INTEGER, " +
                    "deformation REAL, " +
                    "angle INTEGER, " +
                    "numerator_ratio INTEGER, " +
                    "denominator_ratio INTEGER, " +
                    "length_range_start REAL, " +
                    "length_range_end REAL, " +
                    "width_range_start REAL, " +
                    "width_range_end REAL, " +
                    "height_depth_range_start REAL, " +
                    "height_depth_range_end REAL, " +
                    "crack_width_range_start REAL, " +
                    "crack_width_range_end REAL, " +
                    "area_range_start REAL, " +
                    "area_range_end REAL, " +
                    "deformation_range_start REAL, " +
                    "deformation_range_end REAL, " +
                    "angle_range_start REAL, " +
                    "angle_range_end REAL, " +
                    "other TEXT)");

            // bi_attachment
            stmt.execute("CREATE TABLE IF NOT EXISTS bi_attachment (" +
                    "id INTEGER PRIMARY KEY, " +
                    "name TEXT, " +
                    "subject_id INTEGER, " +
                    "type INTEGER, " +
                    "minio_id INTEGER, " +
                    "thumb_minio_id INTEGER)");

            // bi_file_map
            stmt.execute("CREATE TABLE IF NOT EXISTS bi_file_map (" +
                    "id INTEGER PRIMARY KEY, " +
                    "old_name TEXT, " +
                    "new_name TEXT, " +
                    "create_time TEXT, " +
                    "update_time TEXT, " +
                    "create_by TEXT, " +
                    "file_type TEXT, " +
                    "url TEXT, " +
                    "attachment_remark TEXT, " +
                    "subject_id INTEGER)");
        }
    }

    // ======================== DML: 数据写入 ========================

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
        String sql = "INSERT OR REPLACE INTO bi_building (id, name, is_leaf, status, del_flag, longitude, latitude, altitude, address, area, line, admin_dept, weight, video_feed, root_object_id, root_property_id, remark, create_by, create_time, update_by, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertBiObjects(Connection conn, List<BiObject> objects) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_object (id, parent_id, name, ancestors, status, del_flag, longitude, latitude, altitude, position, area, admin_dept, weight, standard_weight, video_feed, props, template_object_id, create_by, create_time, update_by, update_time, remark) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertComponents(Connection conn, List<Component> components) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_component (id, bi_object_id, name, code, status, del_flag, create_by, create_time, update_by, update_time, remark) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
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
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertDiseases(Connection conn, List<Disease> diseases) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_disease (id, position, position_number, type, disease_type_id, description, level, quantity, units, nature, participate_assess, deduct_points, img_no_exp, project_id, bi_object_id, bi_object_name, building_id, component_id, commit_type, local_id, remark, cause, repair_recommendation, crack_type, development_trend, detection_method, attachment_count, create_by, create_time, update_by, update_time, task_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertDiseaseDetails(Connection conn, List<DiseaseDetail> details) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_disease_detail (id, disease_id, reference1_location, reference1_location_start, reference1_location_end, reference2_location, reference2_location_start, reference2_location_end, length1, length2, length3, width, height_depth, crack_width, area_length, area_width, area_identifier, deformation, angle, numerator_ratio, denominator_ratio, length_range_start, length_range_end, width_range_start, width_range_end, height_depth_range_start, height_depth_range_end, crack_width_range_start, crack_width_range_end, area_range_start, area_range_end, deformation_range_start, deformation_range_end, angle_range_start, angle_range_end, other) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertAttachments(Connection conn, List<Attachment> attachments) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_attachment (id, name, subject_id, type, minio_id, thumb_minio_id) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Attachment a : attachments) {
                ps.setLong(1, a.getId());
                ps.setString(2, a.getName());
                setLongOrNull(ps, 3, a.getSubjectId());
                setIntOrNull(ps, 4, a.getType());
                setLongOrNull(ps, 5, a.getMinioId());
                setLongOrNull(ps, 6, a.getThumbMinioId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertFileMaps(Connection conn, List<FileMap> fileMaps) throws SQLException {
        String sql = "INSERT OR REPLACE INTO bi_file_map (id, old_name, new_name, create_time, update_time, create_by, file_type, url, attachment_remark, subject_id) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (FileMap f : fileMaps) {
                ps.setInt(1, f.getId());
                ps.setString(2, f.getOldName());
                ps.setString(3, f.getNewName());
                ps.setString(4, dateToStr(f.getCreateTime()));
                ps.setString(5, dateToStr(f.getUpdateTime()));
                ps.setString(6, f.getCreateBy());
                ps.setString(7, f.getFileType());
                ps.setString(8, f.getUrl());
                ps.setString(9, f.getAttachmentRemark());
                setLongOrNull(ps, 10, f.getSubjectId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ======================== 工具方法 ========================

    private void setLongOrNull(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private void setDecimalOrNull(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value != null) {
            ps.setDouble(index, value.doubleValue());
        } else {
            ps.setNull(index, Types.REAL);
        }
    }

    private String dateToStr(Date date) {
        if (date == null) {
            return null;
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
}
