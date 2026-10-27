package edu.whut.cs.bi.biz.service.std.evaluation;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bi.biz.domain.AssessAbsentMark;
import edu.whut.cs.bi.biz.domain.AssessManualInput;
import edu.whut.cs.bi.biz.domain.AssessSingleControlMark;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Element;
import edu.whut.cs.bi.biz.mapper.AssessAbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.AssessManualInputMapper;
import edu.whut.cs.bi.biz.mapper.AssessSingleControlMarkMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.ElementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 5230 人工判定直连录入服务（/biz/assess5230，区别于 sync/upload 离线通道）。
 *
 * <p>三表均为"每任务唯一锚点"语义：构件判定按 (taskId, elementUuid)、单项控制按
 * (taskId, indicatorNo) 自然键 upsert；应设未设标记无数据库自然唯一约束（unit/span
 * 可空，MySQL NULL 不参与唯一比较），在服务层按 (taskId, unitId, spanId, partKey)
 * 去重，id 非空时按 id 更新。offline_uuid 由服务端生成，is_offline_data=0。</p>
 */
@Service
public class Assess5230InputService {

    @Autowired
    private AssessManualInputMapper manualInputMapper;
    @Autowired
    private AssessAbsentMarkMapper absentMarkMapper;
    @Autowired
    private AssessSingleControlMarkMapper singleControlMarkMapper;
    @Autowired
    private ElementMapper elementMapper;
    @Autowired
    private BiObjectMapper biObjectMapper;
    @Autowired
    private AssessInputRowValidator rowValidator;

    /** 构件级人工判定 upsert（EDDI/ESDI覆盖/EFI/EAI/3.3.3 安全影响）。 */
    public AssessManualInput upsertManualInput(AssessManualInput row, String loginName) {
        List<String> errors = rowValidator.validateManualInput(row);
        if (!errors.isEmpty()) {
            throw new ServiceException(String.join("; ", errors));
        }
        Element element = elementMapper.selectByOfflineUuid(row.getElementUuid());
        if (element == null) {
            throw new ServiceException("elementUuid 反查 bi_element 失败，构件可能未同步: " + row.getElementUuid());
        }
        row.setElementId(element.getId());

        AssessManualInput existing = manualInputMapper.selectByTaskAndElement(
                row.getTaskId(), row.getElementUuid());
        if (existing != null) {
            row.setId(existing.getId());
            row.setOfflineUuid(existing.getOfflineUuid());
            row.setIsOfflineData(existing.getIsOfflineData() == null ? 0 : existing.getIsOfflineData());
            row.setUpdateBy(loginName);
            row.setUpdateTime(DateUtils.getNowDate());
            manualInputMapper.updateAssessManualInput(row);
        } else {
            row.setOfflineUuid(newOfflineUuid());
            row.setIsOfflineData(0);
            row.setOfflineDeleted(0);
            row.setCreateBy(loginName);
            row.setCreateTime(DateUtils.getNowDate());
            manualInputMapper.insertAssessManualInput(row);
        }
        return row;
    }

    /** 应设未设部件标记保存：id 非空按 id 更新；否则按 (task,unit,span,partKey) 去重 upsert。 */
    public AssessAbsentMark saveAbsentMark(AssessAbsentMark row, String loginName) {
        List<String> errors = rowValidator.validateAbsent(row);
        if (!errors.isEmpty()) {
            throw new ServiceException(String.join("; ", errors));
        }
        if (row.getUnitUuid() != null && !row.getUnitUuid().isEmpty()) {
            BiObject unit = biObjectMapper.selectByOfflineUuid(row.getUnitUuid());
            if (unit == null) {
                throw new ServiceException("unitUuid 反查 bi_object 失败: " + row.getUnitUuid());
            }
            row.setUnitId(unit.getId());
        }
        if (row.getSpanUuid() != null && !row.getSpanUuid().isEmpty()) {
            BiObject span = biObjectMapper.selectByOfflineUuid(row.getSpanUuid());
            if (span == null) {
                throw new ServiceException("spanUuid 反查 bi_object 失败: " + row.getSpanUuid());
            }
            row.setSpanId(span.getId());
        }

        AssessAbsentMark existing = null;
        if (row.getId() != null) {
            for (AssessAbsentMark mark : absentMarkMapper.selectByTaskId(row.getTaskId())) {
                if (row.getId().equals(mark.getId())) {
                    existing = mark;
                    break;
                }
            }
            if (existing == null) {
                throw new ServiceException("应设未设标记不存在或不属于该任务: id=" + row.getId());
            }
        } else {
            for (AssessAbsentMark mark : absentMarkMapper.selectByTaskId(row.getTaskId())) {
                if (Objects.equals(mark.getPartKey(), row.getPartKey())
                        && Objects.equals(mark.getUnitId(), row.getUnitId())
                        && Objects.equals(mark.getSpanId(), row.getSpanId())) {
                    existing = mark;
                    break;
                }
            }
        }

        if (existing != null) {
            row.setId(existing.getId());
            row.setOfflineUuid(existing.getOfflineUuid());
            row.setIsOfflineData(existing.getIsOfflineData() == null ? 0 : existing.getIsOfflineData());
            row.setUpdateBy(loginName);
            row.setUpdateTime(DateUtils.getNowDate());
            absentMarkMapper.updateAssessAbsentMark(row);
        } else {
            row.setOfflineUuid(newOfflineUuid());
            row.setIsOfflineData(0);
            row.setOfflineDeleted(0);
            row.setCreateBy(loginName);
            row.setCreateTime(DateUtils.getNowDate());
            absentMarkMapper.insertAssessAbsentMark(row);
        }
        return row;
    }

    /** 单项控制命中标记 upsert（3.4.5，16 项）。证据 span/element UUID 反查 best-effort。 */
    public AssessSingleControlMark upsertSingleControl(AssessSingleControlMark row, String loginName) {
        List<String> errors = rowValidator.validateSingleControl(row);
        if (!errors.isEmpty()) {
            throw new ServiceException(String.join("; ", errors));
        }
        if (row.getSpanUuid() != null && !row.getSpanUuid().isEmpty()) {
            BiObject span = biObjectMapper.selectByOfflineUuid(row.getSpanUuid());
            if (span != null) {
                row.setSpanId(span.getId());
            }
        }
        if (row.getElementUuid() != null && !row.getElementUuid().isEmpty()) {
            Element element = elementMapper.selectByOfflineUuid(row.getElementUuid());
            if (element != null) {
                row.setElementId(element.getId());
            }
        }

        AssessSingleControlMark existing = singleControlMarkMapper.selectByTaskAndIndicator(
                row.getTaskId(), row.getIndicatorNo());
        if (existing != null) {
            row.setId(existing.getId());
            row.setOfflineUuid(existing.getOfflineUuid());
            row.setIsOfflineData(existing.getIsOfflineData() == null ? 0 : existing.getIsOfflineData());
            row.setUpdateBy(loginName);
            row.setUpdateTime(DateUtils.getNowDate());
            singleControlMarkMapper.updateAssessSingleControlMark(row);
        } else {
            row.setOfflineUuid(newOfflineUuid());
            row.setIsOfflineData(0);
            row.setOfflineDeleted(0);
            row.setCreateBy(loginName);
            row.setCreateTime(DateUtils.getNowDate());
            singleControlMarkMapper.insertAssessSingleControlMark(row);
        }
        return row;
    }

    public int deleteManualInput(Long id) {
        return manualInputMapper.deleteAssessManualInputById(id);
    }

    public int deleteAbsentMark(Long id) {
        return absentMarkMapper.deleteAssessAbsentMarkById(id);
    }

    public int deleteSingleControlMark(Long id) {
        return singleControlMarkMapper.deleteAssessSingleControlMarkById(id);
    }

    private String newOfflineUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
