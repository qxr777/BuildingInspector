package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.vo.Jglp05017Vo;
import edu.whut.cs.bi.biz.domain.vo.TaskSheetStatusVo;

import java.util.List;

/**
 * 任务表格数据 Service 接口
 */
public interface ITaskSheetService {

    /** 数据字典类型：桥梁检测表格 */
    String SHEET_DICT_TYPE = "bi_inspection_sheet";

    /** JGLP05017 桥梁结构桥梁技术状况检测记录表（dict_value） */
    String SHEET_TYPE_TECHNICAL_CONDITION = "technical_condition";

    /**
     * 列出某任务下全部表格类型及提交状态（以字典为准，合并 bi_task_sheets）
     */
    List<TaskSheetStatusVo> listSheetStatusByTaskId(Long taskId);

    /**
     * 读取已提交表格的 JSON 文本（格式化后返回）
     */
    String getSheetJsonContent(Long taskId, String type);

    /**
     * 保存或覆盖一条表格记录。
     * <p>
     * 若 bi_task_sheets 中已存在相同 taskId + type 的记录，则先删除旧 MinIO 文件，
     * 再上传新内容并更新记录；否则直接上传并新增记录。
     * </p>
     * <p>
     * 任意步骤失败均抛出异常，由调用方的事务边界负责回滚数据库操作。
     * </p>
     *
     * @param taskId     检测任务ID
     * @param buildingId 桥梁ID
     * @param type       表格类型（即 JSON 中的 sheetId，对应 sys_dict_data.dict_value）
     * @param jsonBytes  表格 JSON 内容的字节数组
     * @param fileName   上传到 MinIO 时使用的文件名（如 carbon_depth.json）
     */
    void saveOrUpdateSheet(Long taskId, Long buildingId, String type,
                           byte[] jsonBytes, String fileName);

    /**
     * 下载已提交表格的原始 JSON 文件字节流（不含 JGLP05017 Word 生成）
     */
    byte[] downloadSheetBytes(Long taskId, String type);

    /**
     * 实时生成 JGLP05017 Word 文件字节流（纯生成，不存库；预览用，不含页眉页码）
     */
    byte[] generateJglp05017WordBytes(Long taskId);

    /**
     * 实时生成 JGLP05017 Word 文件，保存到 MinIO，返回文件字节流（下载用，含页眉页码）
     */
    byte[] generateAndSaveJglp05017Word(Long taskId);

    /**
     * 查询 JGLP05017 桥梁结构桥梁技术状况检测记录表所需数据
     */
    Jglp05017Vo getJglp05017Data(Long taskId);

    /**
     * 判断任务病害数据是否已由 App 端提交（bi_task.type == 1）
     */
    boolean hasDiseaseDataByTaskId(Long taskId);
}
