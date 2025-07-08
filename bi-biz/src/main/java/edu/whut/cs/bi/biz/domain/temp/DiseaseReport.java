package edu.whut.cs.bi.biz.domain.temp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DiseaseReport {
    private String projectName;

    private List<Building> building;

    @Data
    public static class Building {
        private String buildingName;
        private List<Disease> disease;
    }

    @Data
    public static class Disease {
        private String buildingName;
        private String biObject2;
        private String biObject3;
        private String biObject4;
        private String position;
        private String diseaseType;
        private String description;
        private Integer level;
        private Integer quantity;
        private String developmentTrend;
        private String image;
    }
}