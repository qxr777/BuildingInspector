package edu.whut.cs.bi.api.vo;

import edu.whut.cs.bi.biz.domain.Project;
import lombok.Data;

import java.util.List;

/**
 * @author QiXin
 * @date 2025/4/27
 */
@Data
public class ProjectsOfUserVo {
    private Long userId;
    private String projectStatus;
    private List<Project> projects;
}
