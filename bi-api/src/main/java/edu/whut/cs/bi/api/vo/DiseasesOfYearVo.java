package edu.whut.cs.bi.api.vo;

import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Disease;
import lombok.Data;

import java.util.List;

/**
 * @author QiXin
 * @date 2025/4/27
 * 根据桥幅id查询桥梁历史病害
 */
@Data
public class DiseasesOfYearVo {
    private Integer year;
    private Long buildingId;
    private List<Disease> diseases;
}
