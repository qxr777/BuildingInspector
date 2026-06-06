package edu.whut.cs.bi.biz.domain.vo;

import edu.whut.cs.bi.biz.domain.Disease;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * JGLP05017 桥梁结构桥梁技术状况检测记录表 视图数据
 */
@Data
public class Jglp05017Vo {

    /** 工程名称（项目名称） */
    private String projectName;

    /** 工程部位/用途（桥梁名称） */
    private String buildingName;

    /** 桥梁ID */
    private Long buildingId;

    /** 记录编号（任务ID） */
    private Long taskId;

    /** 检测日期 */
    private Date checkDate;

    /** 病害记录列表 */
    private List<Disease> diseases;
}
