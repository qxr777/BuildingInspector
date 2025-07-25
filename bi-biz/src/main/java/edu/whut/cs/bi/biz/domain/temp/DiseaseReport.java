package edu.whut.cs.bi.biz.domain.temp;

import lombok.Data;

import java.util.List;

@Data
public class DiseaseReport {
    private String projectName;

    private int reportYear;

    private List<Building> building;

    @Data
    public static class Building {
        private String buildingName;
        private String bridgeType;
        private String zipCode;
        private String lineCode;
        private List<Disease> disease;
        private String lineName;
        private List<String> frontImage;
        private List<String> sideImage;
        private String buildingCard;
    }

    @Data
    public static class Disease {
        private String biObject2;
        private String biObject3;
        private String biObject4;
        private String position;
        private String diseaseType;
        private String description;
        private String level;
        private String quantity;
        private String developmentTrend;
        private String image;
    }
}