package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个桥型的模板定义（templates/Bxx.yml）。
 */
@Data
public class StdBridgeTemplate {

    /** 桥型代码，如 B01 */
    private String code;

    /** 四结构层影响系数 γ：键为 deck/superstructure/substructure/ancillary */
    private Map<String, BigDecimal> gamma = new LinkedHashMap<>();

    /** 上部结构层名称（默认“上部结构”） */
    private String superstructureName;

    /** 上部结构部件 */
    private List<StdPartDef> superstructureParts = new ArrayList<>();

    /** 构件划分等特别规则说明 */
    private List<String> rules = new ArrayList<>();
}
