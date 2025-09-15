package edu.whut.cs.bi.biz.domain.temp;

/**
 * @Author:wanzheng
 * @Date:2025/9/12 13:11
 * @Description:
 **/
public class ComponentDiseaseType {
    private Long componentId;
    private Long diseaseTypeId;

    public ComponentDiseaseType(Long componentId, Long diseaseTypeId) {
        this.componentId = componentId;
        this.diseaseTypeId = diseaseTypeId;
    }

    public Long getComponentId() {
        return componentId;
    }

    public Long getDiseaseTypeId() {
        return diseaseTypeId;
    }
}
