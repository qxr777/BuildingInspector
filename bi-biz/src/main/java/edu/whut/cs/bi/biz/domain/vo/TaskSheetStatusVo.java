package edu.whut.cs.bi.biz.domain.vo;

import lombok.Data;

/**
 * 任务表格状态（字典项 + 是否已提交）
 */
@Data
public class TaskSheetStatusVo {

    /** 表格类型 dict_value，如 carbon_depth */
    private String type;

    /** 表格名称 dict_label */
    private String sheetName;

    /** 表号（来自字典 remark，未配置时为空） */
    private String sheetNo;

    /** 是否已提交 */
    private boolean submitted;

    /** bi_task_sheets 主键，未提交时为 null */
    private Long taskSheetId;

    /** MinIO 文件映射 ID（bi_file_map.id），未提交时为 null */
    private Long sheetMinioId;
}
