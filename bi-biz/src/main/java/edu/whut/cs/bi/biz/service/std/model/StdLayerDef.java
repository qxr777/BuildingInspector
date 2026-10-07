package edu.whut.cs.bi.biz.service.std.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 可复用结构层定义（shared-layers.yml 中的一层）。
 */
@Data
public class StdLayerDef {

    /** 结构层逻辑键：deck / sub / ancillary */
    private String key;

    /** 结构层名称 */
    private String name;

    /** 层内部件 */
    private List<StdPartDef> parts = new ArrayList<>();
}
