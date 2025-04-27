package edu.whut.cs.bi.api.vo;

import edu.whut.cs.bi.biz.domain.Task;
import lombok.Data;

import java.util.List;

/**
 * @author QiXin
 * @date 2025/4/27
 */
@Data
public class TasksOfProjectVo {
    private Long projectId;
    private List<Task> tasks;
}
