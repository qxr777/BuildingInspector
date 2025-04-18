package edu.whut.cs.bi.biz.controller;

import com.ruoyi.common.utils.ShiroUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/biz/chat")
public class ChatUtils {

    @GetMapping("/getUserId")
    public Long getUserId(){
        return ShiroUtils.getUserId();
    }
}
