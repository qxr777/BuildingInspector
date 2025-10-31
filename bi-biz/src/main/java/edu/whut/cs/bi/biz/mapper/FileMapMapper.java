package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.FileMap;
import org.apache.ibatis.annotations.Param;

/**
 * 文件管理Mapper接口
 * 
 * @author zzzz
 * @date 2025-03-29
 */
public interface FileMapMapper {
    /**
     * 查询文件管理
     * 
     * @param id 文件管理主键
     * @return 文件管理
     */
    public FileMap selectFileMapById(Long id);

    /**Sa
     * 查询文件管理列表
     * 
     * @param fileMap 文件管理
     * @return 文件管理集合
     */
    public List<FileMap> selectFileMapList(FileMap fileMap);

    /**
     * 新增文件管理
     * 
     * @param fileMap 文件管理
     * @return 结果
     */
    public int insertFileMap(FileMap fileMap);

    /**
     * 修改文件管理
     * 
     * @param fileMap 文件管理
     * @return 结果
     */
    public int updateFileMap(FileMap fileMap);

    /**
     * 删除文件管理
     * 
     * @param id 文件管理主键
     * @return 结果
     */
    public int deleteFileMapById(Long id);

    /**
     * 批量删除文件管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteFileMapByIds(@Param("list") List<String> ids);

    /**
     * 批量查询文件管理
     *
     * @param ids 文件管理主键列表
     * @return 文件管理集合
     */
    public List<FileMap> selectFileMapByIds(@Param("list") List<Long> ids);
}
