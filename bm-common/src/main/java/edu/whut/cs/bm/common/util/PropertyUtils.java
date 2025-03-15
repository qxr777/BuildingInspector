package edu.whut.cs.bm.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author qixin on 2021/7/14.
 * @version 1.0
 */
@Slf4j
public class PropertyUtils {
    private static Properties properties;
    //    private File file = null;
//    private static long lastModified = 0L;
    private static String DEFAULT_VALUE = null;

    private static String PROPERTIE_FILE_NAME = null;

    /**
     * 构造函数
     * @param propertyFileName
     */
    private PropertyUtils(String propertyFileName) {
        try {
            PROPERTIE_FILE_NAME = propertyFileName;
            Resource resource = new ClassPathResource(PROPERTIE_FILE_NAME);
//            file = resource.getFile();
//            lastModified = file.lastModified();
//            if (lastModified == 0) {
//                log.error(PROPERTIE_FILE_NAME + " file does not exist!");
//            }
            properties = new Properties();
            properties.load(resource.getInputStream());
        } catch (IOException e) {
            log.error(e.toString());
            log.error("can not read config file " + PROPERTIE_FILE_NAME);
        }
        log.debug(PROPERTIE_FILE_NAME + " loaded.");
    }

    /**
     * 获取实例
     * @param propertyFileName
     * @return
     */
    public static PropertyUtils getInstance(String propertyFileName) {
        return new PropertyUtils(propertyFileName);
    }

    /**
     * 获取配置文件中的Property
     * @param key
     * @return
     */
    public final String getProperty(String key) {
        return getProperty(key, DEFAULT_VALUE);
    }

    public final String getProperty(String key, String defaultValue) {
//        long newTime = file.lastModified();
//        if (newTime == 0) {
//            return defaultValue;
//        } else if (newTime > lastModified) {
        try {
            properties.clear();
            Resource resource = new ClassPathResource(PROPERTIE_FILE_NAME);
//                properties.load(new FileInputStream(resource.getFile()));
            properties.load(resource.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
//        }
//        lastModified = newTime;
        return properties.getProperty(key) == null ? defaultValue : properties
                .getProperty(key);
    }
}
