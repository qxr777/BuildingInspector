package edu.whut.cs.bm.biz.data.converter;

import lombok.SneakyThrows;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author qixin on 2023/4/16.
 * @version 1.0
 */
public class RunTimeConverter extends BaseConverter{
    @SneakyThrows
    @Override
    public Double convert() {
        String baseDateTimeStr = (String) this.paramMap.get("BaseDateTime");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date baseDate = sdf.parse(baseDateTimeStr);
        double hours = (System.currentTimeMillis() - baseDate.getTime()) / (1000 * 60 * 60);
        return hours;
    }
}
