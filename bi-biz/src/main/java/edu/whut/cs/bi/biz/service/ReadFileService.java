package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.vo.BatchBridgeCardImportResult;
import edu.whut.cs.bi.biz.domain.vo.BatchCbmsDiseaseImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ReadFileService {

    /**
     * 批量导入CBMS病害信息
     *
     * @param file
     */
    void readCBMSDiseaseExcel(MultipartFile file, Long taskId);

    /**
     * 批量导入病害信息
     *
     * @param file
     */
    void readDiseaseExcel(MultipartFile file, Long taskId);

    /**
     * 上传病害图片
     *
     * @param photos
     */
    List<String> uploadPictures(List<MultipartFile> photos, Long taskId);

    /**
     * 批量导入桥梁信息
     *
     * @param file
     */
    int ReadBuildingFile(MultipartFile file, Long projectId);

    /**
     * 批量修复已存在桥幅的构件树
     *
     * @param file 桥梁Excel文件
     * @return 修复数量
     */
    int resumeBuildingFile(MultipartFile file);

    /**
     * 批量导入桥梁基本状况卡Word压缩包
     *
     * @param file      包含Word文件的zip压缩包
     * @param projectId 可选项目ID，用于限定桥梁匹配范围
     * @return 导入结果
     */
    BatchBridgeCardImportResult batchImportBridgeCards(MultipartFile file, Long projectId);

    /**
     * 批量导入CBMS病害Excel压缩包
     *
     * @param file        包含CBMS病害Excel的zip压缩包
     * @param projectId   可选项目ID，用于限定任务匹配范围
     * @param projectName 可选项目名称，用于模糊限定任务匹配范围
     * @return 导入结果
     */
    BatchCbmsDiseaseImportResult batchImportCBMSDiseases(MultipartFile file, Long projectId, String projectName);

    /**
     * 匹配生成缩略图
     *
     * @param attachmentList
     */
    List<CompletableFuture<Void>> addThumbPhoto(List<Attachment> attachmentList);
}
