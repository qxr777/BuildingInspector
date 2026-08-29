package edu.whut.cs.bi.biz.service.impl;

import edu.whut.cs.bi.biz.domain.LineReportData;
import edu.whut.cs.bi.biz.domain.ReportData;
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
 * <p>桥梁归属用现有 key 字段做逻辑分区，不改动 bi_report_data 表结构，
 * 例如 __task_101__chapter-4-1-1-designPoints。落库时编码，读取时解码。</p>
 *
 * @author wanzheng
 */
@Slf4j
@Service
public class LineMultiBridgeReportDataServiceImpl implements ILineMultiBridgeReportDataService {
    private static final String TASK_KEY_PREFIX = "__task_";
    private static final String TASK_KEY_SEPARATOR = "__";
    private static final Pattern SCOPED_TASK_KEY_PATTERN =
            Pattern.compile("^" + TASK_KEY_PREFIX + "(\\d+)" + TASK_KEY_SEPARATOR + "(.*)$");

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
                .filter(data -> Objects.equals(taskId, data.getTaskId()))
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
            existingDataMap.put(scopedKey(existingData.getTaskId(), existingData.getKey()), existingData);
        }

        List<ReportData> toInsertList = new ArrayList<>();
        List<ReportData> toUpdateList = new ArrayList<>();
        List<Long> minioIdsToDelete = new ArrayList<>();

        for (LineReportData newData : dataList) {
            if (newData.getTaskId() == null && !isLineKey(newData.getKey())) {
                log.warn("跳过未绑定桥梁的填报项: reportId={}, key={}", reportId, newData.getKey());
                continue;
            }
            newData.setReportId(reportId);
            LineReportData existingData = existingDataMap.get(scopedKey(newData.getTaskId(), newData.getKey()));

            if (existingData != null) {
                newData.setId(existingData.getId());
                collectRemovedMinioIds(existingData, newData, minioIdsToDelete);
                toUpdateList.add(newData);
            } else {
                toInsertList.add(newData);
            }

            // 落库前把桥梁归属编码进 key，读取时再解码。
            newData.setKey(encodeKey(newData.getTaskId(), newData.getKey()));
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

    /**
     * 图片类型数据被移除的 MinIO 文件需要一并清理。
     */
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

    private boolean isLineKey(String key) {
        return key != null && (key.startsWith("line-") || key.startsWith("line."));
    }

    private String scopedKey(Long taskId, String key) {
        return (taskId == null ? "line" : "task:" + taskId) + '\u0000' + (key == null ? "" : key);
    }

    private String encodeKey(Long taskId, String key) {
        if (taskId == null || key == null || key.startsWith(TASK_KEY_PREFIX)) {
            return key;
        }
        return TASK_KEY_PREFIX + taskId + TASK_KEY_SEPARATOR + key;
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
