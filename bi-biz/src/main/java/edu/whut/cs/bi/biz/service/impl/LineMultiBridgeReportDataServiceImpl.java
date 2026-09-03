package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.LineBridgeGroup;
import edu.whut.cs.bi.biz.domain.LineReportData;
import edu.whut.cs.bi.biz.domain.ReportData;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.ReportDataMapper;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.ILineMultiBridgeReportDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 定期检查多桥报告数据Service实现。
 *
 * <p>桥梁归属用现有 key 字段做逻辑分区，不改动 bi_report_data 表结构。
 * 子桥：__task_101__designPoints；大桥：__group_g1__overallOverview；线路：line-project-overview。</p>
 */
@Slf4j
@Service
public class LineMultiBridgeReportDataServiceImpl implements ILineMultiBridgeReportDataService {
    private static final String TASK_KEY_PREFIX = "__task_";
    private static final String GROUP_KEY_PREFIX = "__group_";
    private static final String TASK_KEY_SEPARATOR = "__";
    private static final Pattern SCOPED_TASK_KEY_PATTERN =
            Pattern.compile("^" + TASK_KEY_PREFIX + "(\\d+)" + TASK_KEY_SEPARATOR + "(.*)$");
    private static final Pattern SCOPED_GROUP_KEY_PATTERN =
            Pattern.compile("^" + GROUP_KEY_PREFIX + "(.+?)" + TASK_KEY_SEPARATOR + "(.*)$");

    @Autowired
    private ReportDataMapper reportDataMapper;

    @Autowired
    private IFileMapService fileMapService;

    @Override
    public List<LineReportData> selectByReportId(Long reportId) {
        List<ReportData> rows = reportDataMapper.selectReportDataByReportId(reportId);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        return rows.stream().map(this::decode).collect(Collectors.toList());
    }

    @Override
    public List<LineReportData> selectByTask(Long reportId, Long taskId) {
        return selectByReportId(reportId).stream()
                .filter(data -> data.getGroupId() == null && Objects.equals(taskId, data.getTaskId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<LineReportData> selectByGroup(Long reportId, String groupId) {
        return selectByReportId(reportId).stream()
                .filter(data -> groupId != null && groupId.equals(data.getGroupId()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveBatch(Long reportId, List<LineReportData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return 0;
        }

        Map<String, LineReportData> existingDataMap = new HashMap<>();
        for (LineReportData existingData : selectByReportId(reportId)) {
            existingDataMap.put(scopedKey(existingData), existingData);
        }

        List<ReportData> toInsertList = new ArrayList<>();
        List<ReportData> toUpdateList = new ArrayList<>();
        List<Long> minioIdsToDelete = new ArrayList<>();

        for (LineReportData newData : dataList) {
            if (newData.getTaskId() == null && isBlank(newData.getGroupId()) && !isLineKey(newData.getKey())) {
                log.warn("跳过未绑定桥梁或大桥的填报项: reportId={}, key={}", reportId, newData.getKey());
                continue;
            }
            newData.setReportId(reportId);
            LineReportData existingData = existingDataMap.get(scopedKey(newData));

            if (existingData != null) {
                newData.setId(existingData.getId());
                collectRemovedMinioIds(existingData, newData, minioIdsToDelete);
                toUpdateList.add(newData);
            } else {
                toInsertList.add(newData);
            }

            newData.setKey(encodeKey(newData));
        }

        int result = 0;
        if (!toInsertList.isEmpty()) {
            result += reportDataMapper.batchInsertReportData(toInsertList);
        }
        for (ReportData data : toUpdateList) {
            result += reportDataMapper.updateReportData(data);
        }
        for (Long fileId : minioIdsToDelete) {
            try {
                fileMapService.deleteFileMapById(fileId);
            } catch (Exception e) {
                log.warn("删除多桥报告旧MinIO文件失败: {}", fileId, e);
            }
        }

        log.info("保存多桥报告数据完成 - reportId: {}, 影响行数: {}", reportId, result);
        return result;
    }

    @Override
    public List<LineBridgeGroup> selectBridgeGroups(Long reportId) {
        return selectByReportId(reportId).stream()
                .filter(data -> data.getTaskId() == null && isBlank(data.getGroupId())
                        && LineBridgeGroup.DATA_KEY.equals(data.getKey()))
                .findFirst()
                .map(data -> LineBridgeGroup.parseList(data.getValue()))
                .orElseGet(ArrayList::new);
    }

    @Override
    public List<LineBridgeGroup> resolveEffectiveGroups(Long reportId, List<Task> tasks) {
        List<LineBridgeGroup> result = new ArrayList<>();
        if (tasks == null || tasks.isEmpty()) {
            return result;
        }
        Map<Long, Task> taskById = tasks.stream()
                .filter(task -> task != null && task.getId() != null)
                .collect(Collectors.toMap(Task::getId, task -> task, (left, right) -> left));
        Set<Long> assigned = new HashSet<>();
        for (LineBridgeGroup saved : selectBridgeGroups(reportId)) {
            if (saved == null) {
                continue;
            }
            List<Long> taskIds = saved.getTaskIds() == null ? new ArrayList<>() : saved.getTaskIds().stream()
                    .filter(taskById::containsKey)
                    .distinct()
                    .collect(Collectors.toList());
            if (taskIds.isEmpty() && isBlank(saved.getName())) {
                continue;
            }
            saved.setTaskIds(taskIds);
            if (isBlank(saved.getName()) && !taskIds.isEmpty()) {
                saved.setName(displayTaskName(taskById.get(taskIds.get(0))));
            }
            if (isBlank(saved.getId()) && !taskIds.isEmpty()) {
                saved.setId(LineBridgeGroup.soloId(taskIds.get(0)));
            }
            assigned.addAll(taskIds);
            result.add(saved);
        }
        for (Task task : tasks) {
            if (task == null || task.getId() == null || assigned.contains(task.getId())) {
                continue;
            }
            LineBridgeGroup solo = new LineBridgeGroup();
            solo.setId(LineBridgeGroup.soloId(task.getId()));
            solo.setName(displayTaskName(task));
            solo.setTaskIds(List.of(task.getId()));
            result.add(solo);
        }
        return result;
    }

    private void collectRemovedMinioIds(LineReportData existingData, LineReportData newData, List<Long> minioIdsToDelete) {
        if (existingData.getType() == null || existingData.getType() != 1
                || existingData.getValue() == null || existingData.getValue().isEmpty()) {
            return;
        }
        Set<String> oldMinioIds = new HashSet<>(Arrays.asList(existingData.getValue().split(",")));
        Set<String> newMinioIds = new HashSet<>();
        if (newData.getValue() != null && !newData.getValue().isEmpty()) {
            newMinioIds.addAll(Arrays.asList(newData.getValue().split(",")));
        }
        for (String oldId : oldMinioIds) {
            if (oldId.isEmpty() || newMinioIds.contains(oldId)) {
                continue;
            }
            try {
                minioIdsToDelete.add(Long.parseLong(oldId));
            } catch (NumberFormatException e) {
                log.warn("忽略无效的MinIO ID: {}", oldId);
            }
        }
    }

    private String displayTaskName(Task task) {
        if (task != null && task.getBuilding() != null && task.getBuilding().getName() != null
                && !task.getBuilding().getName().trim().isEmpty()) {
            return task.getBuilding().getName().trim();
        }
        return task == null ? "桥梁" : "任务 " + task.getId();
    }

    private boolean isLineKey(String key) {
        return key != null && (key.startsWith("line-") || key.startsWith("line."));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String scopedKey(LineReportData data) {
        String key = data.getKey() == null ? "" : data.getKey();
        if (!isBlank(data.getGroupId())) {
            return "group:" + data.getGroupId() + '\u0000' + key;
        }
        if (data.getTaskId() != null) {
            return "task:" + data.getTaskId() + '\u0000' + key;
        }
        return "line" + '\u0000' + key;
    }

    private String encodeKey(LineReportData data) {
        String key = data.getKey();
        if (key == null) {
            return null;
        }
        if (!isBlank(data.getGroupId()) && !key.startsWith(GROUP_KEY_PREFIX) && !key.startsWith(TASK_KEY_PREFIX)) {
            return GROUP_KEY_PREFIX + data.getGroupId() + TASK_KEY_SEPARATOR + key;
        }
        if (data.getTaskId() != null && !key.startsWith(TASK_KEY_PREFIX) && !key.startsWith(GROUP_KEY_PREFIX)) {
            return TASK_KEY_PREFIX + data.getTaskId() + TASK_KEY_SEPARATOR + key;
        }
        return key;
    }

    private LineReportData decode(ReportData source) {
        LineReportData data = new LineReportData();
        data.setId(source.getId());
        data.setReportId(source.getReportId());
        data.setKey(source.getKey());
        data.setValue(source.getValue());
        data.setType(source.getType());
        data.setFlag(source.getFlag());
        data.setRemark(source.getRemark());

        if (source.getKey() == null) {
            return data;
        }
        Matcher groupMatcher = SCOPED_GROUP_KEY_PATTERN.matcher(source.getKey());
        if (groupMatcher.matches()) {
            data.setGroupId(groupMatcher.group(1));
            data.setKey(groupMatcher.group(2));
            return data;
        }
        Matcher matcher = SCOPED_TASK_KEY_PATTERN.matcher(source.getKey());
        if (matcher.matches()) {
            try {
                data.setTaskId(Long.valueOf(matcher.group(1)));
                data.setKey(matcher.group(2));
            } catch (NumberFormatException ignored) {
                // 保留原始 key，避免个别异常数据影响整份报告读取
            }
        }
        return data;
    }
}
