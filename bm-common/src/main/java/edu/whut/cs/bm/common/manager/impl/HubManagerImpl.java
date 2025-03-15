package edu.whut.cs.bm.common.manager.impl;

import com.alibaba.fastjson.JSON;
import edu.whut.cs.bm.common.base.ResultVo;
import edu.whut.cs.bm.common.constant.IotConstants;
import edu.whut.cs.bm.common.dto.DeviceDto;
import edu.whut.cs.bm.common.dto.MessageDto;
import edu.whut.cs.bm.common.manager.IHubManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * @author qixin on 2021/8/6.
 * @version 1.0
 */
@Service
public class HubManagerImpl implements IHubManager {

    @Autowired
    private RestTemplate restTemplate;

    private HttpHeaders headers;

    public HubManagerImpl() {
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    }

    @Override
    public DeviceDto register(String productName) {
        String url = IotConstants.IOT_HUB_API_URL_PREFIX + "/devices/register";

//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map= new LinkedMultiValueMap<String, String>();
        map.add("productName", productName);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(map, headers);

        ResponseEntity<String> response = restTemplate.postForEntity( url, request , String.class );

        ResultVo resultVo = JSON.parseObject(response.getBody(), ResultVo.class);
        String jsonString = JSON.toJSONString(resultVo.getData());
        DeviceDto deviceDto = JSON.parseObject(jsonString, DeviceDto.class);
        return deviceDto;
    }

    @Override
    public List<MessageDto> findMessagesByMeasurementAndPeroid(String measurement, Long period) {
        String url = IotConstants.IOT_HUB_API_URL_PREFIX + "/messages?measurement={measurement}&period={period}";
//        MultiValueMap<String, String> map= new LinkedMultiValueMap<String, String>();
//        map.add("measurement", measurement);
//        map.add("period", "" + period);
//        ResultVo resultVo = restTemplate.getForObject( url, ResultVo.class, map);
        url = url.replace("{measurement}", measurement);
        url = url.replace("{period}", "" + period);
        ResultVo resultVo = restTemplate.getForObject( url, ResultVo.class);
        String jsonString = JSON.toJSONString(resultVo.getData());
        List<MessageDto> messageDtos = JSON.parseArray(jsonString, MessageDto.class);
        return messageDtos;
    }

    @Override
    public ResultVo command(String productName, String deviceName, String command) {
        String url = IotConstants.IOT_HUB_API_URL_PREFIX + "/devices/{productName}/{deviceName}/cmd";
        url = url.replace("{productName}", productName);
        url = url.replace("{deviceName}", deviceName);
        HttpEntity<String> request = new HttpEntity<>(command);
        ResponseEntity<String> response = restTemplate.postForEntity( url, request , String.class );
        ResultVo resultVo = JSON.parseObject(response.getBody(), ResultVo.class);
        return resultVo;
    }
}
