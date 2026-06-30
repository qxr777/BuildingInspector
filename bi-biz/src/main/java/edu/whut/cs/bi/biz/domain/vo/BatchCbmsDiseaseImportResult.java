package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchCbmsDiseaseImportResult {
    private int totalCount;
    private List<Success> successes = new ArrayList<>();
    private List<Failure> failures = new ArrayList<>();
    private List<Skipped> skipped = new ArrayList<>();

    public void addSuccess(String fileName, String bridgeName, Long taskId, Long buildingId, String buildingName, Long projectId, String projectName) {
        this.totalCount++;
        Success success = new Success();
        success.setFileName(fileName);
        success.setBridgeName(bridgeName);
        success.setTaskId(taskId);
        success.setBuildingId(buildingId);
        success.setBuildingName(buildingName);
        success.setProjectId(projectId);
        success.setProjectName(projectName);
        this.successes.add(success);
    }

    public void addFailure(String fileName, String bridgeName, String reason) {
        this.totalCount++;
        Failure failure = new Failure();
        failure.setFileName(fileName);
        failure.setBridgeName(bridgeName);
        failure.setReason(reason);
        this.failures.add(failure);
    }

    public void addSkipped(String fileName, String bridgeName, Long taskId, Long buildingId, String buildingName, String reason) {
        this.totalCount++;
        Skipped item = new Skipped();
        item.setFileName(fileName);
        item.setBridgeName(bridgeName);
        item.setTaskId(taskId);
        item.setBuildingId(buildingId);
        item.setBuildingName(buildingName);
        item.setReason(reason);
        this.skipped.add(item);
    }

    public int getSuccessCount() {
        return successes == null ? 0 : successes.size();
    }

    public int getFailureCount() {
        return failures == null ? 0 : failures.size();
    }

    public int getSkippedCount() {
        return skipped == null ? 0 : skipped.size();
    }

    @Data
    public static class Success {
        private String fileName;
        private String bridgeName;
        private Long taskId;
        private Long buildingId;
        private String buildingName;
        private Long projectId;
        private String projectName;
    }

    @Data
    public static class Failure {
        private String fileName;
        private String bridgeName;
        private String reason;
    }

    @Data
    public static class Skipped {
        private String fileName;
        private String bridgeName;
        private Long taskId;
        private Long buildingId;
        private String buildingName;
        private String reason;
    }
}
