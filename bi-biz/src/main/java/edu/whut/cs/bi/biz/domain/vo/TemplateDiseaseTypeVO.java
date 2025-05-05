package edu.whut.cs.bi.biz.domain.vo;

import edu.whut.cs.bi.biz.domain.DiseaseType;

/**
 * 模板对象病害类型视图对象
 *
 * @author wanzheng
 */
public class TemplateDiseaseTypeVO extends DiseaseType {
    /** 是否已选 */
    private Boolean isSelected;

    public Boolean getIsSelected() {
        return isSelected;
    }

    public void setIsSelected(Boolean isSelected) {
        this.isSelected = isSelected;
    }
}