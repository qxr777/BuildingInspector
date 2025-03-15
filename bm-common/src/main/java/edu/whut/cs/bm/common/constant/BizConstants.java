package edu.whut.cs.bm.common.constant;

/**
 * @author qixin on 2021/8/10.
 * @version 1.0
 */
public class BizConstants {
    public final static long HUB_MESSAGE_TASK_PULL_PERIOD = 1 * 60;  // 单位：秒

    public final static String ORDINAL_DATA_TYPE_SEPARATOR = "/";

    public final static int INDEX_TYPE_DATA_TYPE_NUMERIC = 1;
    public final static int INDEX_TYPE_DATA_TYPE_NOMINAL = 2;
    public final static int INDEX_TYPE_DATA_TYPE_BINARY = 3;
    public final static int INDEX_TYPE_DATA_TYPE_ORDINAL = 4;

    public final static int CREATE_TYPE_SYSTEM = 0;
    public final static int CREATE_TYPE_MANUAL = 1;


    public final static String STATUS_ACTIVE = "0";
    public final static String STATUS_SUSPENDED = "1";

    public final static String ALERT_STATUS_WARNING = "0";
    public final static String ALERT_STATUS_PROCESSED = "1";

    public final static String ALERT_RULE_TYPE_THRESHOLD = "0";
    public final static String ALERT_RULE_TYPE_RELATIVE = "1";
    public final static String ALERT_RULE_TYPE_DEADMAN = "2";

    public final static int ALERT_RULE_OPERATOR_TYPE_GREATER_THAN = 1;
    public final static int ALERT_RULE_OPERATOR_TYPE_EQUAL_TO_OR_GREATER_THAN = 2;
    public final static int ALERT_RULE_OPERATOR_TYPE_EQUAL_TO_OR_LESS_THAN = 3;
    public final static int ALERT_RULE_OPERATOR_TYPE_LESS_THAN = 4;
    public final static int ALERT_RULE_OPERATOR_TYPE_EQUAL_TO = 5;
    public final static int ALERT_RULE_OPERATOR_TYPE_NOT_EQUAL_TO = 6;
    public final static int ALERT_RULE_OPERATOR_TYPE_INSIDE_RANGE = 7;
    public final static int ALERT_RULE_OPERATOR_TYPE_OUTSIDE_RANGE = 8;
    public final static String[] ALERT_RULE_OPERATOR_TYPE_CH_ARRAY = {"", "大于", "大于等于", "小于等于", "小于", "等于", "不等于", "在范围内", "在范围外"};

    public final static int ALERT_RULE_RELATIVE_CHANGE_TYPE_QUANTITY = 1;
    public final static int ALERT_RULE_RELATIVE_CHANGE_TYPE_RATE = 2;
    public final static String[] ALERT_RULE_RELATIVE_CHANGE_TYPE_CH_ARRAY = {"", "变化量", "变化率%"};

    public final static int ALERT_RULE_CORRELATION_DATA_SCORE_POSITIVE = 1;
    public final static int ALERT_RULE_CORRELATION_DATA_SCORE_NEGATIVE = 2;

    public final static double[] ALERT_LEVEL_SCORE_ARRAY = {100.0, 0.0, 60.0, 80.0};

    public final static double[] ALERT_LEVEL_SCORE_RANGE = {0.0, 60.0, 80.0, 90.0, 100.0};
    public final static int ALERT_LEVEL_RED = 1;
    public final static int ALERT_LEVEL_YELLOW = 2;
    public final static int ALERT_LEVEL_BLUE = 3;
    public final static String[] ALERT_LEVEL_NAME_ARRAY = {"", "红色预警", "黄色预警", "蓝色预警"};

    public final static int[] EVALUATE_LEVEL_SCORE_ARRAY = {90, 80, 60};
    public final static String[] EVALUATE_LEVEL_CH_ARRAY = {"优","良","中", "差"};

    public static final int HISTORY_EVALUATION_DATA_SIZE = 100;    // 预测健康评估值的时序数据大小
    public static final String PREDICT_MODEL = "cnn";

    public static final long VIDEO_DETECT_ALERT_RULE_ID = 25L;
    public static final long ANOMALY_DETECT_ALERT_RULE_ID = 26L;

}
