package edu.whut.cs.bi.biz.domain.temp;

/**
 * @Author:wanzheng
 * @Date:2025/9/12 13:11
 * @Description: 构件病害类型组合（支持多任务）
 **/
public class ComponentDiseaseType {
    private Long taskId;
    private Long componentId;
    private Long diseaseTypeId;

    public ComponentDiseaseType(Long taskId, Long componentId, Long diseaseTypeId) {
        this.taskId = taskId;
        this.componentId = componentId;
        this.diseaseTypeId = diseaseTypeId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getComponentId() {
        return componentId;
    }

    public Long getDiseaseTypeId() {
        return diseaseTypeId;
    }
}
