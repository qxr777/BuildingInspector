package edu.whut.cs.bm.biz.task;

import com.alibaba.fastjson.JSONObject;
import edu.whut.cs.bm.biz.domain.ObjectIndex;
import edu.whut.cs.bm.biz.service.IIndexDataService;
import edu.whut.cs.bm.biz.service.IObjectIndexService;
import edu.whut.cs.bm.common.constant.BizConstants;
import edu.whut.cs.bm.common.dto.MessageDto;
import edu.whut.cs.bm.common.manager.IHubManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author qixin on 2021/8/17.
 * @version 1.0
 */
@Component("hubMessageTask")
public class HubMessageTask {
    @Autowired
    private IHubManager hubManager;

    @Autowired
    private IObjectIndexService objectIndexService;

    @Autowired
    private IIndexDataService indexDataService;

    public void pullMeasurementData() {
        List<ObjectIndex> objectIndexList = objectIndexService.selectObjectIndexList(null);
        for (ObjectIndex objectIndex : objectIndexList) {
            if(objectIndex.getMeasurement() != null && !objectIndex.getMeasurement().equals("")) {
                if (objectIndex.getMeasurement().indexOf(";;") == -1) { // 监测指标由单个测点值计算得到
                    List<MessageDto> messages = hubManager.findMessagesByMeasurementAndPeroid(objectIndex.getMeasurement(), BizConstants.HUB_MESSAGE_TASK_PULL_PERIOD);
                    if (messages.size() != 0) {
//                        double mean = calculateMean(messages);
                        String lastValue = messages.get(messages.size() - 1).getPayload();
                        if (lastValue.isEmpty()) {
                            continue;
                        }
                        indexDataService.insertIndexData(objectIndex.getObjectId(), objectIndex.getIndexId(), lastValue, BizConstants.CREATE_TYPE_SYSTEM);
                    }
                } else {   // 监测指标由多个测点值计算得到
                    boolean insertFlag = true;
                    String jointValue = objectIndex.getMeasurement();
                    String[] measurements = objectIndex.getMeasurement().split(";;");
                    for (String measurementSeg : measurements ) {
                        List<MessageDto> messages = hubManager.findMessagesByMeasurementAndPeroid(measurementSeg, BizConstants.HUB_MESSAGE_TASK_PULL_PERIOD);
                        if (messages.size() != 0) {
//                            double mean = calculateMean(messages);
                            String lastValue = messages.get(messages.size() - 1).getPayload();
                            jointValue = jointValue.replaceAll(measurementSeg, "" + lastValue);
                        } else {
                            insertFlag = false;
                            break;  // 只要有一个测点值无法获得，此监测指标就无法计算，故退出循环
                        }
                    }
                    if (insertFlag) {
                        indexDataService.insertIndexData(objectIndex.getObjectId(), objectIndex.getIndexId(), jointValue, BizConstants.CREATE_TYPE_SYSTEM);
                    }
                }
            }
        }
    }

    private double calculateMean(List<MessageDto> messageDtos) {
        double sum = 0.0;
        for (MessageDto messageDto : messageDtos) {
//            JSONObject jsonObject = JSONObject.parseObject(messageDto.getPayload());
//            Map<String, String> map = (Map)jsonObject;// //json对象转Map
//            String valueStr = map.get("value");
            sum += Double.parseDouble(messageDto.getPayload());
        }
        double mean = messageDtos.size() != 0 ? sum / messageDtos.size() : 0.0;
        mean = Math.round(mean * 100) / 100.0;
        return mean;
    }

}
