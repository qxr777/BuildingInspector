package edu.whut.cs.bi.biz.domain.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 项目用户角色枚举
 */
@Getter
public enum ProjectUserRoleEnum {

    INSPECTOR("检测人员", "inspector"),
    AUTHOR("报告编写人员", "author"),
    REVIEWER("报告审核人员", "reviewer"),
    APPROVER("报告批准人员", "approver");

    private final String text;
    private final String value;

    ProjectUserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据value获取枚举
     * @param value 枚举值
     * @return 对应的枚举，未找到返回null
     */
    public static ProjectUserRoleEnum getEnumByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (ProjectUserRoleEnum role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 检查value是否有效
     * @param value 待校验的值
     * @return 是否有效
     */
    public static boolean isValid(String value) {
        return getEnumByValue(value) != null;
    }

    /**
     * 获取所有枚举的文本列表
     * @return 文本列表
     */
    public static List<String> getAllTexts() {
        return Arrays.stream(values())
                .map(ProjectUserRoleEnum::getText)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有枚举的值列表
     * @return 值列表
     */
    public static List<String> getAllValues() {
        return Arrays.stream(values())
                .map(ProjectUserRoleEnum::getValue)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有枚举项
     * @return 枚举列表
     */
    public static List<ProjectUserRoleEnum> getAllEnums() {
        return Arrays.asList(values());
    }
}