package edu.whut.cs.bm.biz.data.converter;

import edu.whut.cs.bm.common.util.PropertyUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author qixin on 2021/10/30.
 * @version 1.0
 */
public class ConverterFactory {
    public static BaseConverter produce(String converterName) throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException {
        String converterClassName = PropertyUtils.getInstance("data-converter.properties").getProperty(converterName);

        Class clz = Class.forName(converterClassName);

        Constructor<?> constructor = clz.getConstructor();
        return (BaseConverter)constructor.newInstance();

    }
}
