package edu.whut.cs.bi.biz.service.impl;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.FileUploadRegistry;
import edu.whut.cs.bi.biz.domain.vo.BatchUploadResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadItemResultVo;
import edu.whut.cs.bi.biz.domain.vo.UploadStatusVo;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.mapper.FileUploadRegistryMapper;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.ISyncAttachmentService;
import edu.whut.cs.bi.biz.utils.ThumbPhotoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通道A 附件幂等预上传服务实现。
 *
 * <p>流程：uploadUuid 校验 → 登记表幂等快路径（命中零流量）→ 落临时文件 →
 * 原图 storeFromFile（MinIO+bi_file_map，DB 失败自删对象）→ 同步生成缩略图（失败不阻断）
 * → 写登记表（唯一键兜底并发，负方补偿删除自己写入的对象/行后返回胜方结果）。</p>
 */
@Service
@Slf4j
public class SyncAttachmentServiceImpl implements ISyncAttachmentService {

    /** 去横线后的 32 位十六进制 UUID */
    private static final Pattern UPLOAD_UUID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    @Autowired
    private FileUploadRegistryMapper registryMapper;

    @Autowired
    private FileMapMapper fileMapMapper;

    @Autowired
    private IFileMapService fileMapService;

    @Value("${bi.sync.attachment.max-file-bytes:20971520}")
    private long maxFileBytes;

    @Value("${bi.sync.attachment.thumbnail-enabled:true}")
    private boolean thumbnailEnabled;

    @Override
    public UploadItemResultVo preUpload(String rawUploadUuid, MultipartFile file) {
        String uploadUuid = normalizeUploadUuid(rawUploadUuid);
        if (uploadUuid == null) {
            return UploadItemResultVo.failed(normalizeForDisplay(rawUploadUuid), "uploadUuid 非法（需为32位UUID）");
        }
        if (file == null || file.isEmpty()) {
            return UploadItemResultVo.failed(uploadUuid, "文件为空");
        }
        if (file.getSize() > maxFileBytes) {
            return UploadItemResultVo.failed(uploadUuid, "超出单文件大小限制");
        }

        // ① 幂等快路径：响应丢失/超时重试不再重复上传
        FileUploadRegistry hit = registryMapper.selectByUploadUuid(uploadUuid);
        if (hit != null) {
            if (fileMapMapper.selectFileMapById(hit.getFileMapId()) == null) {
                // 登记存在但原图记录缺失（被人工清理等），不自动重传，避免 App 无感知地更换 minioId
                log.error("预上传登记状态不一致：uploadUuid={} 指向的 bi_file_map.id={} 不存在",
                        uploadUuid, hit.getFileMapId());
                return UploadItemResultVo.failed(uploadUuid, "服务端登记状态不一致，请联系管理员");
            }
            return UploadItemResultVo.success(uploadUuid, hit.getFileMapId(), hit.getThumbFileMapId(), true);
        }

        String originalFilename = sanitizeOriginalName(file.getOriginalFilename());
        String extension = FilenameUtils.getExtension(originalFilename);
        File tmpFile = null;
        File thumbFile = null;
        try {
            // ② 落临时文件（App 已在端侧压缩，原图原样存储，服务端不二次重编码）
            tmpFile = File.createTempFile("syncatt_", extension.isEmpty() ? "" : "." + extension);
            Files.copy(file.getInputStream(), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // ③ 原图落 MinIO + bi_file_map（putObject 成功但 insert 失败时 storeFromFile 自删对象）
            FileMap orig = fileMapService.storeFromFile(tmpFile, originalFilename, safeLoginName());
            Long origId = orig.getId().longValue();

            // ④ 缩略图：复用 1024×768 居中裁剪 q0.5 标准；任何失败只告警，不阻断原图成功
            Long thumbId = null;
            if (thumbnailEnabled && fileMapService.isImageFile(originalFilename)) {
                try {
                    thumbFile = ThumbPhotoUtils.createThumbnail(tmpFile, 1024, 768, 0.5f);
                    FileMap thumb = fileMapService.storeFromFile(thumbFile, originalFilename, safeLoginName());
                    thumbId = thumb.getId().longValue();
                } catch (Exception thumbEx) {
                    log.warn("生成/存储缩略图失败，thumbMinioId=null，不影响原图: uploadUuid={}", uploadUuid, thumbEx);
                }
            }

            // ⑤ 写登记表，唯一键兜底并发双发
            FileUploadRegistry registry = new FileUploadRegistry();
            registry.setUploadUuid(uploadUuid);
            registry.setFileMapId(origId);
            registry.setThumbFileMapId(thumbId);
            registry.setOriginalName(truncate(originalFilename, 255));
            registry.setFileSize(file.getSize());
            registry.setContentType(file.getContentType());
            registry.setUserId(safeUserId());
            registry.setCreateBy(safeLoginName());
            registry.setCreateTime(new Date());
            try {
                registryMapper.insertFileUploadRegistry(registry);
            } catch (DuplicateKeyException dke) {
                // 并发请求赢家已登记：补偿删除自己刚写入的 bi_file_map 行与 MinIO 对象，返回赢家结果
                compensateDeleteQuietly(origId);
                compensateDeleteQuietly(thumbId);
                FileUploadRegistry winner = registryMapper.selectByUploadUuid(uploadUuid);
                if (winner != null && fileMapMapper.selectFileMapById(winner.getFileMapId()) != null) {
                    return UploadItemResultVo.success(uploadUuid, winner.getFileMapId(),
                            winner.getThumbFileMapId(), true);
                }
                log.error("预上传唯一键冲突但无法读取胜方记录: uploadUuid={}", uploadUuid, dke);
                return UploadItemResultVo.failed(uploadUuid, "并发上传冲突，请重试");
            }

            return UploadItemResultVo.success(uploadUuid, origId, thumbId, false);
        } catch (Exception e) {
            log.error("附件幂等预上传失败: uploadUuid={}", uploadUuid, e);
            return UploadItemResultVo.failed(uploadUuid, e.getMessage() == null ? "上传失败" : e.getMessage());
        } finally {
            deleteQuietly(tmpFile);
            deleteQuietly(thumbFile);
        }
    }

    @Override
    public BatchUploadResultVo preUploadBatch(String[] uploadUuids, MultipartFile[] files) {
        BatchUploadResultVo result = new BatchUploadResultVo();
        if (uploadUuids == null || files == null || uploadUuids.length != files.length) {
            return result;
        }
        // 批内去重：同一 uploadUuid 只处理第一项，其余确定性失败（App 组包 bug，避免悄悄覆盖）
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < files.length; i++) {
            String rawUuid = uploadUuids[i];
            String normalized = normalizeUploadUuid(rawUuid);
            if (normalized == null) {
                result.addItem(UploadItemResultVo.failed(normalizeForDisplay(rawUuid), "uploadUuid 非法（需为32位UUID）"));
                continue;
            }
            if (!seen.add(normalized)) {
                result.addItem(UploadItemResultVo.failed(normalized, "批内 uploadUuid 重复"));
                continue;
            }
            // 批内顺序执行：缩略图解码为 CPU 密集型，并发放大由 App 侧批间并发（建议 ≤3）提供
            result.addItem(preUpload(normalized, files[i]));
        }
        return result;
    }

    @Override
    public List<UploadStatusVo> queryUploaded(List<String> uploadUuids) {
        List<UploadStatusVo> results = new ArrayList<>();
        if (uploadUuids == null || uploadUuids.isEmpty()) {
            return results;
        }
        // 单次 IN 查询，再按请求顺序输出（含未登记项）
        Map<String, FileUploadRegistry> registryMap = new LinkedHashMap<>();
        List<String> validUuids = new ArrayList<>();
        for (String raw : uploadUuids) {
            String normalized = normalizeUploadUuid(raw);
            if (normalized != null && !registryMap.containsKey(normalized)) {
                registryMap.put(normalized, null);
                validUuids.add(normalized);
            }
        }
        if (!validUuids.isEmpty()) {
            List<FileUploadRegistry> records = registryMapper.selectByUploadUuids(validUuids);
            for (FileUploadRegistry record : records) {
                registryMap.put(record.getUploadUuid(), record);
            }
        }
        for (String raw : uploadUuids) {
            String normalized = normalizeUploadUuid(raw);
            UploadStatusVo vo = new UploadStatusVo();
            vo.setUploadUuid(normalized != null ? normalized : normalizeForDisplay(raw));
            FileUploadRegistry record = normalized == null ? null : registryMap.get(normalized);
            if (record != null && fileMapMapper.selectFileMapById(record.getFileMapId()) != null) {
                vo.setUploaded(true);
                vo.setMinioId(record.getFileMapId());
                vo.setThumbMinioId(record.getThumbFileMapId());
            } else {
                vo.setUploaded(false);
            }
            results.add(vo);
        }
        return results;
    }

    /** 归一化：去空白、转小写、去横线；非 32 位 hex 返回 null */
    private String normalizeUploadUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase().replace("-", "");
        return UPLOAD_UUID_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private String normalizeForDisplay(String raw) {
        return raw == null ? null : raw.trim();
    }

    private String sanitizeOriginalName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "photo.jpg";
        }
        // 只取文件名部分，避免客户端传入路径
        String base = FilenameUtils.getName(name.trim());
        return base.isEmpty() ? "photo.jpg" : base;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** 补偿删除：连 MinIO 对象带 bi_file_map 行清理；任何失败只告警（幂等收尾路径不能再抛） */
    private void compensateDeleteQuietly(Long fileMapId) {
        if (fileMapId == null) {
            return;
        }
        try {
            fileMapService.deleteFileMapById(fileMapId);
        } catch (Exception e) {
            log.error("并发冲突补偿删除失败，可能残留孤儿对象: bi_file_map.id={}", fileMapId, e);
        }
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private String safeLoginName() {
        try {
            return ShiroUtils.getLoginName();
        } catch (Exception e) {
            return null;
        }
    }

    private Long safeUserId() {
        try {
            return ShiroUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
