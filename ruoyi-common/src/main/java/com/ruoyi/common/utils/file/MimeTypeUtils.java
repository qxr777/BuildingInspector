package com.ruoyi.common.utils.file;

/**
 * 媒体类型工具类
 * 
 * @author ruoyi
 */
public class MimeTypeUtils
{
    public static final String IMAGE_PNG = "image/png";

    public static final String IMAGE_JPG = "image/jpg";

    public static final String IMAGE_JPEG = "image/jpeg";

    public static final String IMAGE_BMP = "image/bmp";

    public static final String IMAGE_GIF = "image/gif";
    
    public static final String APPLICATION_ZIP = "application/zip";
    
    public static final String APPLICATION_RAR = "application/x-rar-compressed";
    
    public static final String APPLICATION_PDF = "application/pdf";
    
    public static final String APPLICATION_WORD = "application/msword";
    
    public static final String APPLICATION_EXCEL = "application/vnd.ms-excel";
    
    public static final String APPLICATION_PPT = "application/vnd.ms-powerpoint";

    public static final String[] IMAGE_EXTENSION = { "bmp", "gif", "jpg", "jpeg", "png" };

    public static final String[] FLASH_EXTENSION = { "swf", "flv" };

    public static final String[] MEDIA_EXTENSION = { "swf", "flv", "mp3", "wav", "wma", "wmv", "mid", "avi", "mpg",
            "asf", "rm", "rmvb" };

    public static final String[] VIDEO_EXTENSION = { "mp4", "avi", "rmvb" };

    public static final String[] DEFAULT_ALLOWED_EXTENSION = {
            // 图片
            "bmp", "gif", "jpg", "jpeg", "png",
            // word excel powerpoint
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "html", "htm", "txt",
            // 压缩文件
            "rar", "zip", "gz", "bz2",
            // 视频格式
            "mp4", "avi", "rmvb",
            // pdf
            "pdf" };

    public static String getExtension(String prefix)
    {
        switch (prefix)
        {
            case IMAGE_PNG:
                return "png";
            case IMAGE_JPG:
                return "jpg";
            case IMAGE_JPEG:
                return "jpeg";
            case IMAGE_BMP:
                return "bmp";
            case IMAGE_GIF:
                return "gif";
            default:
                return "";
        }
    }
    
    /**
     * 根据文件扩展名获取对应的MIME类型
     * 
     * @param extension 文件扩展名
     * @return MIME类型
     */
    public static String getContentType(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "application/octet-stream";
        }
        
        extension = extension.toLowerCase();
        
        // 图片类型
        if ("bmp".equals(extension)) {
            return IMAGE_BMP;
        } else if ("gif".equals(extension)) {
            return IMAGE_GIF;
        } else if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return IMAGE_JPEG;
        } else if ("png".equals(extension)) {
            return IMAGE_PNG;
        } 
        // 文档类型
        else if ("pdf".equals(extension)) {
            return APPLICATION_PDF;
        } else if ("doc".equals(extension) || "docx".equals(extension)) {
            return APPLICATION_WORD;
        } else if ("xls".equals(extension) || "xlsx".equals(extension)) {
            return APPLICATION_EXCEL;
        } else if ("ppt".equals(extension) || "pptx".equals(extension)) {
            return APPLICATION_PPT;
        }
        // 压缩文件类型
        else if ("zip".equals(extension)) {
            return APPLICATION_ZIP;
        } else if ("rar".equals(extension)) {
            return APPLICATION_RAR;
        }
        // 其他文本文件
        else if ("txt".equals(extension) || "html".equals(extension) || "htm".equals(extension)) {
            return "text/" + extension;
        }
        // 默认类型
        else {
            return "application/octet-stream";
        }
    }
}