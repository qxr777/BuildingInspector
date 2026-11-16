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
     * 导入无人机病害 Excel。按 App 同款规则可新建构件实例，不改对象树构件数量；
     * 定量列全部列出，入库时只保留病害类型 selectColumn 命中的列。
     */
    void readDroneDiseaseExcel(MultipartFile file, Long taskId);

    /**
     * 无人机病害与照片一次性导入：先落库 Excel 病害，再按本批照片编号从 ZIP 挂图（与 App 同批匹配，不查库内重名）。
     *
     * @param excelFile 病害 Excel
     * @param photoZip  照片 ZIP，可为 null/空（仅导入病害）
     * @return 照片侧提示（ZIP 未引用、编号缺图等），空表示照片全部匹配
     */
    List<String> importDroneDiseaseWithPhotos(MultipartFile excelFile, MultipartFile photoZip, Long taskId);

    /**
     * 上传病害图片
     *
     * @param photos
     */
    List<String> uploadPictures(List<MultipartFile> photos, Long taskId);

    /**
     * 从 ZIP 导入病害照片。压缩包内照片文件名用照片编号命名，如 6759.jpg，对应 Excel 照片编号。
     *
     * @return 未能匹配到病害的文件名列表
     */
    List<String> uploadPicturesFromZip(MultipartFile zipFile, Long taskId);

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
