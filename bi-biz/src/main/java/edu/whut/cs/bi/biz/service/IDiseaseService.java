package edu.whut.cs.bi.biz.service;


import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.dto.CauseQuery;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 病害Service接口
 *
 * @author chenwenqi
 * @date 2025-04-10
 */
public interface IDiseaseService {
    /**
     * 查询病害
     *
     * @param id 病害ID
     * @return 病害
     */
    Disease selectDiseaseById(Long id);


    /**
     * 查询病害列表
     *
     * @param disease 病害
     * @return 病害集合
     */
    List<Disease> selectDiseaseList(Disease disease);

    /**
     * 查询 当前 task 的所有病害，并且不分页。
     *
     * @return
     */
    List<Disease> selectDiseaseListForTask(Disease disease);

    /**
     * 批量查询纯Excel导出需要的病害、构件和图片URL。
     * 按各任务的项目+桥梁查询，与病害页单桥导出一致，不依赖可能错乱的 task_id。
     *
     * @param tasks 已选任务（需含 id、projectId、buildingId）
     * @return 病害集合
     */
    List<Disease> selectDiseaseListForExcel(List<Task> tasks);

    /**
     * 查询病害列表
     *
     * @param disease 病害
     * @return 病害集合
     */
    List<Disease> selectDiseaseListForApi(Disease disease);

    /**
     * 查询病害列表
     *
     * @param disease 病害
     * @return 病害集合
     */
    List<Disease> selectDiseaseListForZip(Disease disease);

    /**
     * 新增病害
     *
     * @param disease 病害
     * @return 结果
     */
    public Integer insertDisease(Disease disease);

    /**
     * 修改病害
     *
     * @param disease 病害
     * @return 结果
     */
    public int updateDisease(Disease disease);

    /**
     * 修改病害
     *
     * @param disease 病害
     * @return 结果
     */
    public int newUpdateDisease(Disease disease);

    /**
     * 更新病害发展趋势
     *
     * @param id 病害ID
     * @param developmentTrend 发展趋势
     * @param updateBy 更新人
     * @return 结果
     */
    int updateDevelopmentTrend(Long id, String developmentTrend, String updateBy);

    /**
     * 给当前项目、当前桥梁中未挂构件的病害补关联。
     * 缺损位置含 # 时，构件名称用缺损位置，编码用 # 前一段。
     * 缺损位置不含 # 时，从病害描述开头取编码，名称拼成「编码#绑定构件」。
     *
     * @return 已关联条数、跳过条数
     */
    int[] linkMissingComponentsByPosition(Long projectId, Long buildingId, String updateBy);

    /**
     * 批量删除病害
     *
     * @param ids 需要删除的病害主键集合
     * @return 结果
     */
    public int deleteDiseaseByIds(String ids);

    /**
     * 删除病害信息
     *
     * @param id 病害主键
     * @return 结果
     */
    public int deleteDiseaseById(Long id);

    /**
     * 计算扣分
     *
     * @param maxScale
     * @param scale
     * @return
     */
    public int computeDeductPoints(int maxScale, int scale);

    /**
     * 处理病害附件
     *
     * @param files
     * @param id
     * @param type
     */
    public void handleDiseaseAttachment(MultipartFile[] files, Long id, int type);

    /**
     * 获取病害原因分析
     *
     * @param causeQuery
     * @return
     */
    public String getCauseAnalysis(CauseQuery causeQuery);

    /**
     * 获取病害原因分析
     *
     * @param ids
     * @return
     */
    public int deleteDiseaseByDiseaseIds(String ids);

    /**
     * Delete all diseases of every task under the project identified by name, year and code.
     */
    int deleteDiseasesByProjectIdentity(String projectName, Integer year, String code);


    /**
     * 批量导入病害信息
     *
     * @param file
     */
    void readDiseaseExcel(MultipartFile file, Long projectId);

    /**
     * 批量保存病害信息
     *
     * @param diseases 病害列表
     * @return 处理结果
     */
    int batchSaveDiseases(List<Disease> diseases);

    /**
     * 批量导入病害信息
     *
     * @param file
     */
    Boolean readDiseaseZip(MultipartFile file);

}
