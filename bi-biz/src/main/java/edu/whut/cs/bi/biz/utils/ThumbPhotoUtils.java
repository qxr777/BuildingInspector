package edu.whut.cs.bi.biz.utils;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ThumbPhotoUtils {
    /**
     * 将 MultipartFile 类型的图片缩放为指定尺寸，并返回新的 MultipartFile 对象。
     * 所有操作均在内存中完成，不产生临时文件。
     *
     * @param originalFile 原始的 MultipartFile 图片
     * @param quality  缩略图质量
     * @return 缩放后的 MultipartFile 图片
     * @throws IOException 如果处理过程中发生 I/O 错误
     */
    public static MultipartFile createThumbnail(MultipartFile originalFile, int width, int height, float quality) throws IOException {
        // 1. 检查输入文件是否为空
        if (originalFile.isEmpty()) {
            throw new IllegalArgumentException("原始文件不能为空");
        }

        // 2. 从原始文件获取输入流
        try (InputStream inputStream = originalFile.getInputStream()) {
            // 3. 创建一个字节数组输出流，用于接收 Thumbnails 处理后的图片数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // 4. 使用 Thumbnails 进行缩放，并将结果写入到 ByteArrayOutputStream
            Thumbnails.of(inputStream)
                    .size(width, height)
                    .crop(Positions.CENTER)
                    .outputQuality(quality)
                    .outputFormat(getFileExtension(originalFile.getOriginalFilename())) // 根据原始文件名推断输出格式
                    .toOutputStream(outputStream); // 将缩放后的图片写入输出流

            // 5. 从 ByteArrayOutputStream 中获取字节数组
            byte[] thumbnailBytes = outputStream.toByteArray();

            // 6. 使用 MockMultipartFile 构建一个新的 MultipartFile 对象
            // 参数分别为：文件名、原始文件名、ContentType、字节数组
            return new MockMultipartFile(
                    originalFile.getOriginalFilename() + "_thumbnail",
                    originalFile.getOriginalFilename(),
                    originalFile.getContentType(),
                    thumbnailBytes
            );
        }
    }

    // 指定压缩尺寸和质量，输入输出均为File
    public static File createThumbnail(File originalFile, int width, int height, float quality) throws IOException {
        // 1. 检查输入文件是否有效
        if (!originalFile.exists() || originalFile.isDirectory()) {
            throw new IllegalArgumentException("原始文件不存在或不是有效文件");
        }

        // 2. 构建缩略图输出文件（在原文件目录下，添加_thumbnail后缀）
        String originalName = originalFile.getName();
        String outputName = originalName.substring(0, originalName.lastIndexOf("."))
                + "_thumbnail"
                + originalName.substring(originalName.lastIndexOf("."));
        File outputFile = new File(originalFile.getParent(), outputName);

        // 3. 使用Thumbnails处理图片：缩放+裁剪+压缩，直接写入目标文件
        Thumbnails.of(originalFile)
                .size(width, height)          // 指定目标尺寸
                .crop(Positions.CENTER)       // 居中裁剪（保持比例，避免拉伸）
                .outputQuality(quality)       // 压缩质量（0.0-1.0）
                .outputFormat(getFileExtension(originalName)) // 保持原格式
                .toFile(outputFile);          // 直接写入输出文件

        return outputFile;
    }

    /**
     * 从文件名中获取文件扩展名（不包含点）
     *
     * @param fileName 文件名
     * @return 文件扩展名，如 "jpg", "png"
     */
    private static String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "jpg"; // 默认格式
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
