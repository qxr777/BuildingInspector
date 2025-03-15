package edu.whut.cs.bm.biz.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bm.common.constant.BizConstants;

import java.math.BigDecimal;

/**
 * 监测对象健康评估结果
 * @author qixin on 2021/10/9.
 * @version 1.0
 */
public class Evaluation extends BaseEntity {
    private Long id;
    @Excel(name = "监测对象id")
    private Long objectId;
    private BmObject object;
    @Excel(name = "监测对象预测评估分")
    private Double score;
    private Integer level;
    private Integer childrenObjectCount;
    @Excel(name = "监测指标个数")
    private Integer objectIndexCount;
    @Excel(name = "异常监测指标个数")
    private Integer objectIndexExceptionCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public BmObject getObject() {
        return object;
    }

    public void setObject(BmObject object) {
        this.object = object;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
        this.level = this.convertScore(score);
    }

    public Integer getLevel() {
        return level;
    }

    public String getLevelCh() {
        return level != null ? BizConstants.EVALUATE_LEVEL_CH_ARRAY[level] : null;
    }

    public Integer getChildrenObjectCount() {
        return childrenObjectCount;
    }

    public void setChildrenObjectCount(Integer objectCount) {
        this.childrenObjectCount = objectCount;
    }

    public Integer getObjectIndexCount() {
        return objectIndexCount;
    }

    public void setObjectIndexCount(Integer objectIndexCount) {
        this.objectIndexCount = objectIndexCount;
    }

    public Integer getObjectIndexExceptionCount() {
        return objectIndexExceptionCount;
    }

    public void setObjectIndexExceptionCount(Integer objectIndexExceptionCount) {
        this.objectIndexExceptionCount = objectIndexExceptionCount;
    }

    public String getCreateDateStr() {
        return this.getCreateTime() != null ? DateUtils.parseDateToStr("yyyy年MM月dd日", this.getCreateTime()) : null;
    }

    /**
     * 健康评估分 转换为 健康等级
     *
     * @param score
     * @return level
     */
    private int convertScore(double score) {
        for (int i = 0; i < BizConstants.EVALUATE_LEVEL_SCORE_ARRAY.length; i++) {
            if (score > BizConstants.EVALUATE_LEVEL_SCORE_ARRAY[i]) {
                return i;
            }
        }
        return BizConstants.EVALUATE_LEVEL_SCORE_ARRAY.length;
    }
}
