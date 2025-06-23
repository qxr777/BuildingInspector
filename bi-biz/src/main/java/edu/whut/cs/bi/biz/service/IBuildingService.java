package edu.whut.cs.bi.biz.service;

import java.io.IOException;
import java.util.List;

import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.vo.ProjectBuildingVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 建筑Service接口
 *
 * @author wanzheng
 * @date 2025-03-27
 */
public interface IBuildingService {
    /**
     * 查询建筑
     *
     * @param id 建筑主键
     * @return 建筑
     */
    public Building selectBuildingById(Long id);

    /**
     * 查询建筑列表
     *
     * @param building 建筑
     * @return 建筑集合
     */
    public List<Building> selectBuildingList(Building building);

    /**
     * 新增建筑
     *
     * @param building 建筑
     * @return 结果
     */
    public int insertBuilding(Building building);

    /**
     * 修改建筑
     *
     * @param building 建筑
     * @return 结果
     */
    public int updateBuilding(Building building);

    /**
     * 批量删除建筑
     *
     * @param ids 需要删除的建筑主键集合
     * @return 结果
     */
    public int deleteBuildingByIds(String ids);

    /**
     * 删除建筑信息
     *
     * @param id 建筑主键
     * @return 结果
     */
    public int deleteBuildingById(Long id);

    /**
     * 导入JSON文件
     *
     * @param file JSON文件
     * @return 结果
     */
    public int importJson(MultipartFile file) throws IOException;

    /**
     * 查询建筑VO列表
     *
     * @param building  建筑
     * @param projectId 项目ID
     * @return 结果
     */
    List<ProjectBuildingVO> selectBuildingVOList(ProjectBuildingVO building, Long projectId);


    /**
     * 获取指定组合桥下的所有子桥ID（包括直接子桥和间接子桥）
     *
     * @param buildingId 组合桥ID
     * @return 所有子桥ID列表
     */
    public List<Long> selectChildBuildingIds(Long buildingId);

    /**
     * 查询建筑及其父桥关系信息
     *
     * @param id 建筑主键
     * @return 带有父桥信息的建筑
     */
    public Building selectBuildingWithParentInfo(Long id);

    /**
     * excel导入建筑信息
     *
     * @param file
     */
    public void readBuildingExcel(MultipartFile file, Long projectId);
}
