package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.BiObject;

import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/3/20 22:21
 * @Description:
 **/
public interface IBiObjectService
{
    /**
     * 查询桥梁构件
     *
     * @param id 桥梁构件主键
     * @return 桥梁构件
     */
    public BiObject selectBiObjectById(Long id);

    /**
     * 查询桥梁构件列表
     *
     * @param biObject 桥梁构件
     * @return 桥梁构件集合
     */
    public List<BiObject> selectBiObjectList(BiObject biObject);

    /**
     * 查询桥梁列表（顶级节点）
     *
     * @return 桥梁列表
     */
    public List<BiObject> selectBridges();

    /**
     * 查询桥梁下的所有构件
     *
     * @param bridgeId 桥梁ID
     * @return 构件列表
     */
    public List<BiObject> selectComponentsByBridgeId(Long bridgeId);

    /**
     * 新增桥梁构件
     *
     * @param biObject 桥梁构件
     * @return 结果
     */
    public int insertBiObject(BiObject biObject);

    /**
     * 修改桥梁构件
     *
     * @param biObject 桥梁构件
     * @return 结果
     */
    public int updateBiObject(BiObject biObject);

    /**
     * 批量删除桥梁构件
     *
     * @param ids 需要删除的桥梁构件主键集合
     * @return 结果
     */
    public int deleteBiObjectByIds(String ids);

    /**
     * 删除桥梁构件信息
     *
     * @param id 桥梁构件主键
     * @return 结果
     */
    public int deleteBiObjectById(Long id);

    /**
     * 导入桥梁构件数据
     *
     * @param jsonData JSON格式数据
     * @param updateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    public String importData(String jsonData, boolean updateSupport, String operName) throws Exception;

    /**
     * 导出桥梁构件数据
     *
     * @param id 桥梁构件ID
     * @return JSON格式数据
     */
    public String exportData(Long id) throws Exception;

    /**
     * 导入Excel数据
     *
     * @param biObjectList Excel数据列表
     * @param updateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    public String importExcelData(List<BiObject> biObjectList, boolean updateSupport, String operName);

    /**
     * 构建树结构
     */
    List<BiObject> buildTree(List<BiObject> list);
}