package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 控制器契约测试：不启容器，反射断言路径、HTTP 方法、权限与日志注解。
 */
class V2EvaluationControllerContractTest {

    private static final String BASE = "/biz/v2evaluation";

    @Test
    void classIsControllerMappedUnderV2Base() {
        Class<V2EvaluationController> type = V2EvaluationController.class;
        assertNotNull(type.getAnnotation(Controller.class));
        RequestMapping mapping = type.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{BASE}, mapping.value());
    }

    @Test
    void treeEndpointContract() throws NoSuchMethodException {
        Method method = V2EvaluationController.class.getMethod("tree", Long.class);
        assertGet(method, "/task/{taskId}/tree");
        assertPermission(method, "biz:v2evaluation:view");
    }

    @Test
    void getInputsEndpointContract() throws NoSuchMethodException {
        Method method = V2EvaluationController.class.getMethod("getInputs", Long.class);
        assertGet(method, "/task/{taskId}/inputs");
        assertPermission(method, "biz:v2evaluation:view");
    }

    @Test
    void calculateEndpointContract() throws NoSuchMethodException {
        Method method = V2EvaluationController.class.getMethod("calculate", Long.class);
        assertPost(method, "/task/{taskId}/calculate");
        assertPermission(method, "biz:v2evaluation:calculate");
        Log log = method.getAnnotation(Log.class);
        assertNotNull(log);
        assertEquals(BusinessType.OTHER, log.businessType());
    }

    @Test
    void resultEndpointContract() throws NoSuchMethodException {
        Method method = V2EvaluationController.class.getMethod("result", Long.class);
        assertGet(method, "/task/{taskId}/result");
        assertPermission(method, "biz:v2evaluation:view");
    }

    @Test
    void singleIndicatorsEndpointContract() throws NoSuchMethodException {
        Method method = V2EvaluationController.class.getMethod("singleIndicators");
        assertGet(method, "/singleIndicators");
        assertPermission(method, "biz:v2evaluation:view");
    }

    private void assertGet(Method method, String path) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        assertNotNull(get, "缺少 @GetMapping: " + method.getName());
        assertArrayEquals(new String[]{path}, get.value());
        assertNotNull(method.getAnnotation(ResponseBody.class));
    }

    private void assertPost(Method method, String path) {
        PostMapping post = method.getAnnotation(PostMapping.class);
        assertNotNull(post, "缺少 @PostMapping: " + method.getName());
        assertArrayEquals(new String[]{path}, post.value());
        assertNotNull(method.getAnnotation(ResponseBody.class));
    }

    private void assertPermission(Method method, String permission) {
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertNotNull(annotation, "缺少 @RequiresPermissions: " + method.getName());
        assertArrayEquals(new String[]{permission}, annotation.value());
    }
}
