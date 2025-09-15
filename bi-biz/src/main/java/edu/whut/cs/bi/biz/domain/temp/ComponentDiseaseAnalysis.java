package edu.whut.cs.bi.biz.domain.temp;

/**
 * 构件病害分析内部类
 */
public class ComponentDiseaseAnalysis {
    private Long componentId;
    private String componentName;
    private String bridgeName;
    private Long diseaseTypeId;
    private String diseaseTypeName;
    private String structureType;

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public String getBridgeName() { return bridgeName; }
    public void setBridgeName(String bridgeName) { this.bridgeName = bridgeName; }

    public Long getDiseaseTypeId() { return diseaseTypeId; }
    public void setDiseaseTypeId(Long diseaseTypeId) { this.diseaseTypeId = diseaseTypeId; }

    public String getDiseaseTypeName() { return diseaseTypeName; }
    public void setDiseaseTypeName(String diseaseTypeName) { this.diseaseTypeName = diseaseTypeName; }

    public String getStructureType() { return structureType; }
    public void setStructureType(String structureType) { this.structureType = structureType; }
}