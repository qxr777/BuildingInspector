package edu.whut.cs.bi.api.vo;

import edu.whut.cs.bi.biz.domain.Building;
import lombok.Data;

import java.util.List;

/**
 * @author QiXin
 * @date 2025/4/27
 */
@Data
public class BuildingsOfTaskVo {
    private Long taskId;
    private List<Building> buildings;
}
