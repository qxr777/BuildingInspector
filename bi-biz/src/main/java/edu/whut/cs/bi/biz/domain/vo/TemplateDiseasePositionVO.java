package edu.whut.cs.bi.biz.domain.vo;

import edu.whut.cs.bi.biz.domain.DiseasePosition;

/**
 * 模板对象病害位置视图对象
 *
 * @author XinChao
 */
public class TemplateDiseasePositionVO extends DiseasePosition {
    /** 是否已选 */
    private Boolean isSelected;

    public Boolean getIsSelected() {
        return isSelected;
    }

    public void setIsSelected(Boolean isSelected) {
        this.isSelected = isSelected;
    }
}
