package edu.whut.cs.bi.biz.domain.vo;

import edu.whut.cs.bi.biz.domain.*;
import edu.whut.cs.bi.biz.utils.Convert2VO;
import edu.whut.cs.bi.biz.utils.ReportGenerateTools;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class Disease2ReportSummaryAiVO {

    /**
     * 病害id
     */
    private Long id;

    /**
     * 病害位置
     */
    private String position;

    /**
     * 病害描述
     */
    private String description;

    /**
     * 病害数量
     */
    private int quantity;

    /**
     * 病害类型
     */
    private String type;

    // 裂缝特征 (纵向、横向、斜向、L型、U型）
    private String crackType;

    /**
     * 构件名称 （支持自定义）
     */
    private String biObjectName;

    /**
     * 转换方法
     * 转换 一个 或 一个list
     *
     */
    public static Disease2ReportSummaryAiVO convert(Disease disease) {
        Disease2ReportSummaryAiVO result = Convert2VO.copyOne(disease, Disease2ReportSummaryAiVO.class);
        String postion = disease.getPosition();
        postion = postion.substring(postion.lastIndexOf('#') + 1);
        result.setPosition(postion);
        // 数据库中的type带有编号，模型不需要这个信息，并且需要对裂缝病害单独处理包装。
        result.setType(ReportGenerateTools.reportDiseaseTypeNameIfCrack(disease));
        return result;
    }

    public static List<Disease2ReportSummaryAiVO> convert(List<Disease> diseases) {
        List<Disease2ReportSummaryAiVO> result = new ArrayList<>();
        // 数据库中的type带有编号，模型不需要这个信息，并且需要对裂缝病害单独处理包装。
        diseases.stream().forEach(disease -> {
            Disease2ReportSummaryAiVO tempVo = convert(disease);
            result.add(tempVo);
        });
        return result;
    }
}
