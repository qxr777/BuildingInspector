package edu.whut.cs.bi.biz.domain;

import lombok.Data;

/**
 * 任务表格记录 bi_task_sheets
 */
@Data
public class TaskSheet {

    private Long id;

    /** 检测任务ID */
    private Long taskId;

    /** 桥梁ID */
    private Long buildingId;

    /** 表格JSON文件在MinIO中的文件ID（对应 bi_file_map.id） */
    private Long sheetMinioId;

    /** 表格类型（对应 sys_dict_data.dict_value，如 carbon_depth） */
    private String type;
}
