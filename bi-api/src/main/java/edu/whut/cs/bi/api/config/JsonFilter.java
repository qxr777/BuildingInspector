package edu.whut.cs.bi.api.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 配置json序列化，去除空值字段
 */
@Component
public class JsonFilter implements Filter {

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 根据请求路径或其他条件决定配置
        if (httpRequest.getRequestURI().startsWith("/api")) {
            handlerAdapter.getMessageConverters().stream()
                    .filter(c -> c instanceof MappingJackson2HttpMessageConverter)
                    .forEach(c -> {
                        MappingJackson2HttpMessageConverter converter = (MappingJackson2HttpMessageConverter) c;
                        ObjectMapper mapper = converter.getObjectMapper();
                        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
                    });
        }

        chain.doFilter(request, response);
    }
}