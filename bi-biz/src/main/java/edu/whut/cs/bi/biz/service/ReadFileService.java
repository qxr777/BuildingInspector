package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Attachment;
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
    void ReadBuildingFile(MultipartFile file, Long projectId);

    /**
     * 匹配生成缩略图
     *
     * @param attachmentList
     */
    List<CompletableFuture<Void>> addThumbPhoto(List<Attachment> attachmentList);
}
