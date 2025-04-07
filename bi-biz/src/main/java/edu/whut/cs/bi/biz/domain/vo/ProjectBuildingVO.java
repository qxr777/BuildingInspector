package edu.whut.cs.bi.biz.domain.vo;


import edu.whut.cs.bi.biz.domain.Building;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectBuildingVO extends Building {

    /** 是否项目选中 */
    private Boolean isSelected;
}
