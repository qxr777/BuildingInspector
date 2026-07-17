package edu.whut.cs.bi.biz.service;

import com.alibaba.fastjson.JSONObject;
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
     * 判断任务下指定类型的表格是否已提交。
     */
    boolean hasSubmittedSheetByTaskIdAndType(Long taskId, String type);

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
     * 实时生成 JGLP05017 Word 文件字节流（下载用，含页眉页码；不存 MinIO、不写 bi_task_sheets）
     */
    byte[] generateJglp05017WordDownloadBytes(Long taskId);

    /**
     * 查询 JGLP05017 桥梁结构桥梁技术状况检测记录表所需数据
     */
    Jglp05017Vo getJglp05017Data(Long taskId);

    /** App 上传 JSON 表格类型：碳化深度检测记录表 */
    String SHEET_TYPE_CARBON_DEPTH = "carbon_depth";

    /**
     * 判断表格类型是否支持 JSON → Word 预览/下载
     */
    boolean supportsJsonSheetWord(String type);

    /**
     * 返回所有支持 JSON → Word 的表格 type 列表
     */
    List<String> listJsonSheetWordTypes();

    /**
     * 从 MinIO JSON 实时生成 Word 字节流（预览用，页码在正文）
     */
    byte[] generateJsonSheetWordBytes(Long taskId, String type);

    /**
     * 从 MinIO JSON 实时生成 Word 字节流（下载用，页码在页眉）
     */
    byte[] generateJsonSheetWordDownloadBytes(Long taskId, String type);

    /**
     * JSON 表格 Word 下载默认文件名（不含扩展名）
     */
    String resolveJsonSheetDownloadBaseName(String type);

    /**
     * 判断任务是否存在可生成技术状况表的病害数据。
     */
    boolean hasDiseaseDataByTaskId(Long taskId);

    /**
     * 读取表格 JSON 供网页编辑；未提交时返回 pages 为空的结构。
     */
    String getSheetJsonForEdit(Long taskId, String type);

    /**
     * 删除任务下某类型的已提交表格（清除 MinIO 文件与 bi_task_sheets 记录）。
     */
    void deleteSheetByTaskIdAndType(Long taskId, String type);

    /**
     * 保存网页端提交的表格 JSON；pages 为空时清除已提交记录。
     */
    void saveSheetFromWeb(Long taskId, Long buildingId, String type, byte[] jsonBytes);

    /**
     * 判断表格类型是否支持网页端 JSON 编辑（不含 technical_condition）。
     */
    boolean supportsWebJsonEdit(String type);

    /**
     * 判断表格类型是否支持网页端编辑（JSON 表格 + 技术状况表）。
     */
    boolean supportsWebEdit(String type);

    /**
     * 读取技术状况表编辑数据（由 bi_disease 组装为 pages 结构）。
     */
    String getTechnicalConditionJsonForEdit(Long taskId);

    /**
     * 保存网页端提交的技术状况表表头/备注，并用当前 bi_disease 生成只读病害快照。
     */
    boolean saveTechnicalConditionFromWeb(Long taskId, byte[] jsonBytes);

    /**
     * 技术状况表网页编辑默认表头。
     */
    JSONObject buildTechnicalConditionDefaultHeader(Long taskId);

    /**
     * 网页编辑新建页时使用的默认表头（预填工程/桥梁名称）。
     */
    JSONObject buildDefaultSheetHeader(Long taskId);

    /**
     * 读取该表格类型模板中已固定的表头字段（如检测依据、判定依据），供网页编辑只读展示。
     */
    JSONObject getSheetTemplateFixedFields(String type);
}
