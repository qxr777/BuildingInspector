package edu.whut.cs.bm.common.manager;



import edu.whut.cs.bm.common.base.ResultVo;
import edu.whut.cs.bm.common.dto.DeviceDto;
import edu.whut.cs.bm.common.dto.MessageDto;

import java.util.List;

/**
 * @author qixin on 2021/8/6.
 * @version 1.0
 */
public interface IHubManager {

    DeviceDto register(String productName);

    List<MessageDto> findMessagesByMeasurementAndPeroid(String measurement, Long peroid);

    ResultVo command(String productName, String deviceName, String command);

}
