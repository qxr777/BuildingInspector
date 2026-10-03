package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchBridgeCardImportResult {
    private int totalCount;
    private int successCount;
    private List<Failure> failures = new ArrayList<>();
    private List<Skipped> skipped = new ArrayList<>();

    public void addSuccess() {
        this.totalCount++;
        this.successCount++;
    }

    public void addFailure(String fileName, String bridgeName, String reason) {
        this.totalCount++;
        Failure failure = new Failure();
        failure.setFileName(fileName);
        failure.setBridgeName(bridgeName);
        failure.setReason(reason);
        this.failures.add(failure);
    }

    public void addSkipped(String fileName, String bridgeName, Long buildingId, String reason) {
        this.totalCount++;
        Skipped item = new Skipped();
        item.setFileName(fileName);
        item.setBridgeName(bridgeName);
        item.setBuildingId(buildingId);
        item.setReason(reason);
        this.skipped.add(item);
    }

    public int getFailureCount() {
        return failures == null ? 0 : failures.size();
    }

    public int getSkippedCount() {
        return skipped == null ? 0 : skipped.size();
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
        private Long buildingId;
        private String reason;
    }
}
