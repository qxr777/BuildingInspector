package edu.whut.cs.bi.biz.service;

import java.util.List;
import edu.whut.cs.bi.biz.domain.FileMap;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件管理Service接口
 * 
 * @author zzzz
 * @date 2025-03-29
 */
public interface IFileMapService {
    /**
     * 查询文件管理
     * 
     * @param id 文件管理主键
     * @return 文件管理
     */
    public FileMap selectFileMapById(Long id);

    /**
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
     * 批量删除文件管理
     * 
     * @param ids 需要删除的文件管理主键集合
     * @return 结果
     */
    public int deleteFileMapByIds(String ids);

    /**
     * 删除文件管理信息
     * 
     * @param id 文件管理主键
     * @return 结果
     */
    public int deleteFileMapById(Long id);

    /**
     * 上传单个文件
     * 
     * @param file 文件
     * @return 文件信息
     */
    public FileMap handleFileUpload(MultipartFile file);

    /**
     * 批量上传文件
     * 
     * @param files 文件数组
     * @return 文件信息列表
     */
    public List<FileMap> handleBatchFileUpload(MultipartFile[] files);

    /**
     * 下载文件
     * 
     * @param id 文件ID
     * @return 文件字节数组
     */
    public byte[] handleFileDownload(Long id);

    /**
     * 批量下载文件
     * 
     * @param ids 文件ID数组
     * @return 打包后的文件字节数组
     */
    public byte[] handleBatchFileDownload(Long[] ids);

    /**
     * 复制文件
     *
     * @param id 源文件ID
     * @return 复制后的文件信息
     */
    public FileMap copyFile(Long id);

    /**
     * 通过newName下载文件
     * 
     * @param newName 文件的newName
     * @return 文件字节数组
     */
    public byte[] handleFileDownloadByNewName(String newName);

    /**
     * 根据文件newName查询文件信息
     * 
     * @param newName 文件新名称
     * @return 文件信息
     */
    public FileMap selectFileMapByNewName(String newName);

    /**
     * 根据biObjectId查询图片列表
     *
     * @param biObjectId biObjectId
     * @return 图片列表
     */
    List<FileMap> selectBiObjectPhotoList(Long biObjectId);
}
