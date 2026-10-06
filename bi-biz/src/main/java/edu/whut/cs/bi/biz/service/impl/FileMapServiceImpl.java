package edu.whut.cs.bi.biz.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.PageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.mapper.AttachmentMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.config.MinioConfig;
import com.ruoyi.common.core.text.Convert;
import org.apache.commons.io.FilenameUtils;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;

import static com.ruoyi.common.utils.PageUtils.startPage;
import static edu.whut.cs.bi.biz.utils.ThumbPhotoUtils.createThumbnail;

/**
 * 文件管理Service业务层处理
 * 
 * @author zzzz
 * @date 2025-03-29
 */
@Service
@Slf4j
public class FileMapServiceImpl implements IFileMapService {
    @Autowired
    private FileMapMapper fileMapMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Resource
    private AttachmentService attachmentService;

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private BuildingMapper buildingMapper;

    @Resource
    private IFileMapService fileMapService;

    /**
     * 查询文件管理
     * 
     * @param id 文件管理主键
     * @return 文件管理
     */
    @Override
    public FileMap selectFileMapById(Long id) {
        return fileMapMapper.selectFileMapById(id);
    }

    /**
     * 查询文件管理列表
     * 
     * @param fileMap 文件管理
     * @return 文件管理
     */
    @Override
    public List<FileMap> selectFileMapList(FileMap fileMap) {
        return fileMapMapper.selectFileMapList(fileMap);
    }

    /**
     * 新增文件管理
     * 
     * @param fileMap 文件管理
     * @return 结果
     */
    @Override
    public int insertFileMap(FileMap fileMap) {
        fileMap.setCreateTime(DateUtils.getNowDate());
        return fileMapMapper.insertFileMap(fileMap);
    }

    /**
     * 修改文件管理
     * 
     * @param fileMap 文件管理
     * @return 结果
     */
    @Override
    public int updateFileMap(FileMap fileMap) {
        fileMap.setUpdateTime(DateUtils.getNowDate());
        return fileMapMapper.updateFileMap(fileMap);
    }

    /**
     * 批量删除文件管理
     * 
     * @param ids 需要删除的文件管理主键
     * @return 结果
     */
    @Override
    public int deleteFileMapByIds(String ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String[] fileIds = Convert.toStrArray(ids);
        String[] filteredFileIds = Arrays.stream(fileIds)
                .filter(Objects::nonNull) // 先过滤 null 引用，避免空指针
                .filter(str -> !"null".equalsIgnoreCase(str)) // 忽略大小写过滤 "null" 字符串
                .filter(str -> !str.trim().isEmpty())
                .toArray(String[]::new);
        for (String idStr : filteredFileIds) {
            if (idStr == null || idStr.isEmpty() || "null".equals(idStr)) {
                continue;
            }
            Long id = Long.valueOf(idStr);
            FileMap fileMap = fileMapMapper.selectFileMapById(id);
            if (fileMap != null) {
                try {
                    // 从MinIO删除文件
                    String objectName = fileMap.getNewName();
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName.substring(0,2)+ "/" + objectName)
                            .build());
                } catch (Exception e) {
                    throw new RuntimeException("删除文件失败", e);
                }
            }
        }
        if(filteredFileIds.length == 0){
            return 0;
        }
        return fileMapMapper.deleteFileMapByIds(List.of(filteredFileIds));
    }

    /**
     * 删除文件管理信息
     * 
     * @param id 文件管理主键
     * @return 结果
     */
    @Override
    public int deleteFileMapById(Long id) {
        FileMap fileMap = fileMapMapper.selectFileMapById(id);
        if (fileMap != null) {
            try {
                // 从MinIO删除文件
                String objectName = fileMap.getNewName();
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectName.substring(0,2)+ "/" + objectName)
                        .build());
            } catch (Exception e) {
                throw new RuntimeException("删除文件失败{}", e);
            }
        }
        return fileMapMapper.deleteFileMapById(id);
    }

    @Override
    public FileMap handleFileUpload(MultipartFile file) {
        InputStream fileInputStream = null;
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = FilenameUtils.getExtension(originalFilename);

            // 使用UUID作为新文件名
            String uuid = UUID.randomUUID().toString().replace("-", "");
            // 构建新的文件名，包含路径
            String objectName =  uuid + "." + extension;

            // 获取输入流并确保它会被关闭
            fileInputStream = file.getInputStream();

            // 上传文件到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName.substring(0,2)+"/"+objectName)
                    .stream(fileInputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // 保存文件信息
            FileMap fileMap = new FileMap();
            fileMap.setOldName(originalFilename);
            fileMap.setNewName(objectName);
            fileMap.setCreateTime(DateUtils.getNowDate());
            fileMap.setCreateBy(ShiroUtils.getLoginName());
            fileMapMapper.insertFileMap(fileMap);

            return fileMap;
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败", e);
        } finally {
            // 确保输入流关闭
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (Exception e) {
                    // 记录关闭流失败但不抛出异常
                }
            }
        }
    }

    /**
     * 直接从文件上传到MinIO，避免重复读取到内存
     */
    @Override
    public FileMap handleFileUploadFromFile(File file, String originalFilename, String loginName) {
        FileInputStream fileInputStream = null;
        try {
            String extension = FilenameUtils.getExtension(originalFilename);

            // 使用UUID作为新文件名
            String uuid = UUID.randomUUID().toString().replace("-", "");
            String objectName = uuid + "." + extension;

            // 直接从文件创建输入流
            fileInputStream = new FileInputStream(file);

            // 上传文件到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName.substring(0, 2) + "/" + objectName)
                    .stream(fileInputStream, file.length(), -1)
                    .contentType(MimeTypeUtils.getContentType(extension))
                    .build());

            // 保存文件信息
            FileMap fileMap = new FileMap();
            fileMap.setOldName(originalFilename);
            fileMap.setNewName(objectName);
            fileMap.setCreateTime(DateUtils.getNowDate());
            fileMap.setCreateBy(loginName);
            fileMapMapper.insertFileMap(fileMap);

            return fileMap;
        } catch (Exception e) {
            log.error("上传到MinIO失败: {}", originalFilename, e);
            throw new RuntimeException("文件上传失败", e);
        } finally {
            // 确保输入流关闭
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (Exception e) {
                    log.error("输入流关闭失败");
                }
            }
        }
    }

    @Override
    public List<FileMap> handleBatchFileUpload(MultipartFile[] files) {
        List<FileMap> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(handleFileUpload(file));
        }
        return results;
    }

    @Override
    public byte[] handleFileDownload(Long id) {
        try {
            FileMap fileMap = fileMapMapper.selectFileMapById(id);
            if (fileMap == null) {
                throw new RuntimeException("文件不存在");
            }

            // 使用try-with-resources确保流被关闭
            try (
                    InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(toMinioObjectKey(fileMap.getNewName()))
                            .build());
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                // 将InputStream转换为byte数组
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = stream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                return buffer.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    @Override
    public byte[] handleBatchFileDownload(Long[] ids) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Long id : ids) {
                FileMap fileMap = fileMapMapper.selectFileMapById(id);
                if (fileMap == null) {
                    continue;
                }

                // 从MinIO获取文件并确保流被关闭
                try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(fileMap.getNewName().substring(0,2)+"/"+ fileMap.getNewName())
                        .build())) {

                    // 添加到ZIP
                    ZipEntry entry = new ZipEntry(fileMap.getOldName());
                    zos.putNextEntry(entry);

                    // 复制数据
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = stream.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("批量下载文件失败", e);
        }
    }

    @Override
    public FileMap copyFile(Long id) {
        InputStream sourceStream = null;
        try {
            // 查询源文件信息
            FileMap sourceFile = fileMapMapper.selectFileMapById(id);
            if (sourceFile == null) {
                throw new RuntimeException("源文件不存在");
            }

            // 从MinIO获取源文件
            sourceStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(sourceFile.getNewName().substring(0,2)+"/"+sourceFile.getNewName())
                    .build());

            // 准备新文件信息
            String originalFilename = sourceFile.getOldName();
            String extension = FilenameUtils.getExtension(originalFilename);

            // 修改文件名，添加"副本"后缀
            String filenameWithoutExt = FilenameUtils.removeExtension(originalFilename);
            String newOriginalFilename = filenameWithoutExt + " - 副本." + extension;

            // 使用新的UUID作为复制文件的文件名
            String uuid = UUID.randomUUID().toString().replace("-", "");

            // 构建新的文件名，包含路径
            String newObjectName = uuid + "." + extension;

            // 上传复制的文件到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(newObjectName.substring(0,2)+"/"+uuid+"."+extension)
                    .stream(sourceStream, -1, 10485760)
                    .contentType("application/octet-stream")
                    .build());

            // 获取当前登录用户
            String loginName = ShiroUtils.getLoginName();
            if (loginName == null || loginName.isEmpty()) {
                loginName = "system";
            }

            // 创建新的文件记录
            FileMap newFile = new FileMap();
            newFile.setOldName(newOriginalFilename); // 使用添加了"副本"的文件名
            newFile.setNewName(newObjectName);
            newFile.setCreateTime(DateUtils.getNowDate());
            newFile.setCreateBy(loginName);
            fileMapMapper.insertFileMap(newFile);

            return newFile;
        } catch (Exception e) {
            throw new RuntimeException("复制文件失败", e);
        } finally {
            // 确保输入流关闭
            if (sourceStream != null) {
                try {
                    sourceStream.close();
                } catch (Exception e) {
                    // 记录关闭流失败但不抛出异常
                }
            }
        }
    }

    @Override
    public byte[] handleFileDownloadByNewName(String newName) {
        try (
                InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(newName.substring(0,2)+"/" + newName)
                        .build());
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            // 将InputStream转换为byte数组
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        } catch (Exception e) {
            log.error("文件下载失败", e);
            return null;
        }
    }

    public void streamFileDownloadByNewName(String newName, ServletOutputStream outputStream) {
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucketName())
                .object(newName.substring(0,2)+"/" + newName)
                .build())) {

            // 流式复制，使用缓冲区
            byte[] buffer = new byte[8192]; // 8KB缓冲区
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush(); // 及时刷新输出流
            }

        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    @Override
    public FileMap selectFileMapByNewName(String newName) {
        // 创建查询条件
        FileMap fileMap = new FileMap();
        fileMap.setNewName(newName);
        List<FileMap> list = fileMapMapper.selectFileMapList(fileMap);

        // 返回第一个匹配的结果
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    @Override
    public List<FileMap> selectBiObjectPhotoList(Long biObjectId) {
        List<BiObject> biObjects = biObjectMapper.selectBiObjectAndChildren(biObjectId);
        List<Long> biObjectIds = biObjects.stream().map(biObject -> biObject.getId()).toList();

        List<Map<String, Object>> imageMap = getImage(biObjectIds, "biObject");

        return imageMap.stream()
                .filter(image -> image.get("type").equals(8))
                .map(image -> (FileMap) image.get("fileMap"))
                .toList();
    }

    @Override
    public List<Map<String, Object>> getImage(List<Long> ids, String name) {
        List<Attachment> attachments = attachmentService.getAttachmentBySubjectIds(ids).stream().filter(e->e.getName().startsWith(name)).toList();

        // 转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (Attachment attachment : attachments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", attachment.getId());
            map.put("fileName", attachment.getName().split("_")[1]);
            FileMap fileMap = selectFileMapById(attachment.getMinioId());
            if(fileMap == null) continue;
            String s = fileMap.getNewName();
            String url = minioConfig.getUrl()+ "/"+minioConfig.getBucketName()+"/"+s.substring(0,2)+"/"+s;
            fileMap.setAttachmentRemark(attachment.getRemark());
            fileMap.setUrl(url);
            fileMap.setSubjectId(attachment.getSubjectId());
            map.put("fileMap", fileMap);
            // 根据文件后缀判断是否为图片
            if (!isImageFile(attachment.getName()))
                continue;
            map.put("type", attachment.getType());
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getImage(Long id, String name) {
        List<Attachment> attachments = attachmentService.getAttachmentList(id).stream().filter(e->e.getName().startsWith(name)).toList();

        // 转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (Attachment attachment : attachments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", attachment.getId());
            map.put("fileName", attachment.getName().split("_")[1]);
            FileMap fileMap = selectFileMapById(attachment.getMinioId());
            if(fileMap == null) continue;
            String s = fileMap.getNewName();
            String url = minioConfig.getUrl()+ "/"+minioConfig.getBucketName()+"/"+s.substring(0,2)+"/"+s;
            fileMap.setUrl(url);
            map.put("fileMap", fileMap);
            // 根据文件后缀判断是否为图片
            if (!isImageFile(attachment.getName()))
                continue;
            map.put("type", attachment.getType());
            result.add(map);
        }
        return result;
    }

    @Override
    public boolean isImageFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        String extension = fileName.toLowerCase();
        return extension.endsWith(".jpg") ||
                extension.endsWith(".jpeg") ||
                extension.endsWith(".png") ||
                extension.endsWith(".gif") ||
                extension.endsWith(".webp");
    }

    @Override
    public void handleBiObjectAttachment(MultipartFile[] files, Long biObjectId, int type, List<String> informations) {
        if(files == null){
            return;
        }

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String information = (informations != null && i < informations.size()) ? informations.get(i) : null;
            
            FileMap fileMap = handleFileUpload(file);
            Attachment attachment = new Attachment();

            try {
                // 缩略图
                MultipartFile thumbnailFile = null;
                thumbnailFile = createThumbnail(file, 1024,768, 0.5f);
                FileMap thumbnailFileMap = fileMapService.handleFileUpload(thumbnailFile);
                attachment.setThumbMinioId(Long.valueOf(thumbnailFileMap.getId()));
            } catch (Exception ex) {
                log.error("转化缩略图异常！" + ex.toString());
//                throw new RuntimeException("转化缩略图异常！");
            }

            attachment.setMinioId(Long.valueOf(fileMap.getId()));
            attachment.setName("biObject_"+fileMap.getOldName());
            attachment.setSubjectId(biObjectId);
            attachment.setType(type);
            if (information != null) {
                attachment.setRemark(information);
            }
            attachmentService.insertAttachment(attachment);
        }
    }

    /**
     * 批量查询文件管理
     *
     * @param ids 文件管理主键列表
     * @return 文件管理集合
     */
    @Override
    public List<FileMap> selectFileMapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return fileMapMapper.selectFileMapByIds(ids);
    }

    /** MinIO 对象 key：与上传时 objectName.substring(0,2)+"/"+objectName 保持一致 */
    private static String toMinioObjectKey(String newName) {
        if (newName == null || newName.length() < 2) {
            return newName;
        }
        return newName.substring(0, 2) + "/" + newName;
    }

    /**
     * 处理病害照片 ZIP 包（兼容入口，实际支持四类照片混合）。
     *
     * <p>App 端将采集照片打包为 ZIP 直传 OSS，后端凭 objectName 拉取后调用本方法解压处理。
     * 包内为纯图片文件，文件名约定 {@code {category}_{entityOfflineUuid}_{attachmentOfflineUuid}.{ext}}：
     * <ul>
     *   <li>category：disease（病害图片）/ component（构件现状照）/ front（正立面照）/ side（侧立面照）</li>
     *   <li>entityOfflineUuid：归属实体（病害/构件/建筑）的离线 UUID</li>
     *   <li>attachmentOfflineUuid：该图片自身的离线 UUID，用于幂等去重</li>
     * </ul></p>
     *
     * <p>每张图片：按 category 路由反查服务端实体 id → 原图存 MinIO → 生成缩略图存 MinIO → 写 bi_attachment
     * （type/name 前缀按类别）。已按 attachmentOfflineUuid 去重，重复上传不产生脏数据；
     * 反查不到实体的图片记入失败清单，不中断整体处理。</p>
     *
     * @param zipFile 从 OSS 流式适配得到的 ZIP（仅支持 getInputStream()）
     * @return Map：successCount 成功张数；skippedCount 去重跳过张数；failures 失败清单 [{fileName, reason}]
     */
    @Override
    public Map<String, Object> handleDiseasePhotosZip(MultipartFile zipFile) {
        int successCount = 0;
        int skippedCount = 0;
        List<Map<String, String>> failures = new ArrayList<>();
        String loginName = resolveLoginName();

        try (ZipInputStream zipIn = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String rawName = entry.getName().replace('\\', '/');
                String fileName = rawName.contains("/")
                        ? rawName.substring(rawName.lastIndexOf('/') + 1)
                        : rawName;

                if (!isImageFile(fileName)) {
                    failures.add(Map.of("fileName", fileName, "reason", "非图片文件，跳过"));
                    continue;
                }

                PhotoMeta meta = parsePhotoFileName(fileName);
                if (meta == null) {
                    failures.add(Map.of("fileName", fileName, "reason", "文件名无法解析，应为 category_实体UUID_图片UUID.ext"));
                    continue;
                }

                // 按类别反查归属实体 id
                Long subjectId = resolveSubjectId(meta.category, meta.entityUuid);
                if (subjectId == null) {
                    failures.add(Map.of("fileName", fileName, "reason", "未找到离线UUID对应的" + meta.categoryLabel()));
                    continue;
                }

                // 幂等去重：同一张图（attachmentOfflineUuid）已处理过则跳过，避免重复写 bi_attachment
                Attachment existing = attachmentMapper.selectByOfflineUuid(meta.attachmentUuid);
                if (existing != null) {
                    skippedCount++;
                    continue;
                }

                File tmpFile = null;
                File thumbFile = null;
                try {
                    tmpFile = File.createTempFile("photo_", extensionOf(fileName));
                    Files.copy(zipIn, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    FileMap fileMap = handleFileUploadFromFile(tmpFile, fileName, loginName);
                    if (fileMap == null || fileMap.getId() == null) {
                        failures.add(Map.of("fileName", fileName, "reason", "原图上传失败"));
                        continue;
                    }

                    Attachment attachment = new Attachment();
                    attachment.setMinioId(Long.valueOf(fileMap.getId()));
                    attachment.setName(meta.namePrefix() + fileName);
                    attachment.setSubjectId(subjectId);
                    attachment.setType(meta.type());
                    attachment.setOfflineUuid(meta.attachmentUuid);

                    try {
                        thumbFile = createThumbnail(tmpFile, 1024, 768, 0.5f);
                        FileMap thumbFileMap = handleFileUploadFromFile(thumbFile, fileName, loginName);
                        if (thumbFileMap != null && thumbFileMap.getId() != null) {
                            attachment.setThumbMinioId(Long.valueOf(thumbFileMap.getId()));
                        }
                    } catch (Exception ex) {
                        log.warn("生成照片缩略图失败，沿用原图: {} -> {}", fileName, ex.toString());
                    }

                    attachment.setIsOfflineData(1);
                    attachment.setCreateBy(loginName);
                    attachment.setCreateTime(DateUtils.getNowDate());
                    attachmentService.insertAttachment(attachment);
                    successCount++;
                } catch (Exception e) {
                    log.error("处理照片失败: {}", fileName, e);
                    failures.add(Map.of("fileName", fileName, "reason", "处理异常: " + e.getMessage()));
                } finally {
                    deleteQuietly(tmpFile);
                    deleteQuietly(thumbFile);
                }
            }
        } catch (Exception e) {
            log.error("解压照片 ZIP 失败", e);
            throw new RuntimeException("解压照片 ZIP 失败: " + e.getMessage(), e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("skippedCount", skippedCount);
        result.put("failures", failures);
        return result;
    }

    /** 按照片类别反查归属实体的服务端 id。 */
    private Long resolveSubjectId(String category, String entityUuid) {
        switch (category) {
            case "disease": {
                Disease disease = diseaseMapper.selectByOfflineUuid(entityUuid);
                return disease != null ? disease.getId() : null;
            }
            case "component": {
                BiObject biObject = biObjectMapper.selectByOfflineUuid(entityUuid);
                return biObject != null ? biObject.getId() : null;
            }
            case "front":
            case "side": {
                Building building = buildingMapper.selectByOfflineUuid(entityUuid);
                return building != null ? building.getId() : null;
            }
            default:
                return null;
        }
    }

    /** 照片元信息：category + 归属实体离线 UUID + 图片自身离线 UUID，以及对应的 type/name 前缀。 */
    private static final class PhotoMeta {
        final String category;
        final String entityUuid;
        final String attachmentUuid;

        PhotoMeta(String category, String entityUuid, String attachmentUuid) {
            this.category = category;
            this.entityUuid = entityUuid;
            this.attachmentUuid = attachmentUuid;
        }

        int type() {
            switch (category) {
                case "disease":
                    return 1;
                case "component":
                    return 8;
                case "front":
                case "side":
                    return 6;
                default:
                    return 1;
            }
        }

        String namePrefix() {
            switch (category) {
                case "disease":
                    return "disease_";
                case "component":
                    return "biObject_";
                case "front":
                    return "newfront_";
                case "side":
                    return "newside_";
                default:
                    return "disease_";
            }
        }

        String categoryLabel() {
            switch (category) {
                case "disease":
                    return "病害";
                case "component":
                    return "构件";
                case "front":
                    return "建筑(正立面)";
                case "side":
                    return "建筑(侧立面)";
                default:
                    return "实体";
            }
        }
    }

    /** 从文件名解析照片元信息：{category}_{entityOfflineUuid}_{attachmentOfflineUuid}.{ext}。 */
    private PhotoMeta parsePhotoFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String[] parts = base.split("_");
        if (parts.length < 3) {
            return null;
        }
        String category = parts[0];
        String entityUuid = normalizeUuid(parts[1]);
        String attachmentUuid = normalizeUuid(parts[2]);
        if (!isValidCategory(category) || entityUuid == null || attachmentUuid == null) {
            return null;
        }
        return new PhotoMeta(category, entityUuid, attachmentUuid);
    }

    private boolean isValidCategory(String category) {
        return "disease".equals(category) || "component".equals(category)
                || "front".equals(category) || "side".equals(category);
    }

    private String normalizeUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String normalized = uuid.replace("-", "");
        return (normalized.length() == 32 && normalized.matches("[0-9a-fA-F]{32}")) ? normalized : null;
    }

    private String extensionOf(String fileName) {
        String ext = FilenameUtils.getExtension(fileName);
        return (ext == null || ext.isEmpty()) ? ".jpg" : "." + ext;
    }

    private String resolveLoginName() {
        String loginName;
        try {
            loginName = ShiroUtils.getLoginName();
        } catch (Exception ignored) {
            loginName = null;
        }
        return (loginName == null || loginName.isEmpty()) ? "system" : loginName;
    }

    private void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception ignored) {
        }
    }
}
