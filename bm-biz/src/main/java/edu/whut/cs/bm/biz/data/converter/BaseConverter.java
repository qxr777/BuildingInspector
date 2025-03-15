package edu.whut.cs.bm.biz.data.converter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author qixin on 2021/10/30.
 * @version 1.0
 */
public abstract class BaseConverter {
    protected String[] argArray;
    protected Map paramMap = new HashMap<String, String>();

    private String args;
    private String params;

    public abstract Double convert();

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
        this.argArray = args.split(";;");
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
        String[] paramArray = params.split(";;");
        for (String param : paramArray) {
            String[] paramKeyValue = param.split(":=");
            paramMap.put(paramKeyValue[0], paramKeyValue[1]);
        }
    }
}
