package edu.whut.cs.bi.biz.service.v2;

import com.ruoyi.common.exception.ServiceException;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import org.springframework.stereotype.Component;

/**
 * 新标评定任务上下文解析：任务校验、评定单元桥型解析。
 * 供 V2EvaluationServiceImpl 与 App 同步处理器共用。
 */
@Component
public class V2TaskContextResolver {

    public static final String STD_VERSION = "5230-2026";

    private final TaskMapper taskMapper;
    private final BuildingMapper buildingMapper;
    private final BiObjectMapper biObjectMapper;

    public V2TaskContextResolver(TaskMapper taskMapper,
                                 BuildingMapper buildingMapper,
                                 BiObjectMapper biObjectMapper) {
        this.taskMapper = taskMapper;
        this.buildingMapper = buildingMapper;
        this.biObjectMapper = biObjectMapper;
    }

    /**
     * 校验任务存在且为 JTG/T 5230—2026 任务。
     */
    public Task require5230Task(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        Task task = taskMapper.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在: " + taskId);
        }
        if (!STD_VERSION.equals(task.getStdVersion())) {
            throw new ServiceException("任务(id=" + taskId + ")非 JTG/T 5230—2026 任务，禁止使用新标评定接口");
        }
        return task;
    }

    /**
     * 任务关联建筑物 → 新标评定单元根节点 ID。
     */
    public Long unitRootId(Task task) {
        if (task.getBuildingId() == null) {
            throw new ServiceException("任务(id=" + task.getId() + ")未关联建筑物");
        }
        Building building = buildingMapper.selectBuildingById(task.getBuildingId());
        if (building == null || building.getNewRootObjectId() == null) {
            throw new ServiceException("任务(id=" + task.getId()
                    + ")关联建筑物缺少新标评定单元根节点(new_root_object_id)");
        }
        return building.getNewRootObjectId();
    }

    /**
     * 任务 → 评定单元桥型代码。
     */
    public String bridgeTypeOf(Long taskId) {
        Task task = require5230Task(taskId);
        BiObject unit = biObjectMapper.selectBiObjectById(unitRootId(task));
        if (unit == null || unit.getBridgeType() == null) {
            throw new ServiceException("评定单元缺少 bridge_type");
        }
        return unit.getBridgeType();
    }
}
