package edu.whut.cs.bi.biz.utils;

import com.ruoyi.common.utils.bean.BeanUtils;

import java.util.ArrayList;
import java.util.List;

public class Convert2VO {
    public static <T, S> List<T> copyList(List<S> list, Class<T> tClass) {
        List<T> ans = new ArrayList<T>();
        for (S s : list) {
            T t = null;
            try {
                t = tClass.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
            BeanUtils.copyProperties(s, t);
            ans.add(t);
        }
        return ans;
    }

    public static <T, S> T copyOne(S s, Class<T> tClass) {
        T t = null;
        try {
            t = tClass.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        BeanUtils.copyProperties(s, t);
        return t;
    }
}
