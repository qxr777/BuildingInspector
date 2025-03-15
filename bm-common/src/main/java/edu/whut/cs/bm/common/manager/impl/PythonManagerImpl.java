package edu.whut.cs.bm.common.manager.impl;

import edu.whut.cs.bm.common.base.ResultVo;
import edu.whut.cs.bm.common.constant.BizConstants;
import edu.whut.cs.bm.common.constant.PythonConstants;
import edu.whut.cs.bm.common.manager.IPythonManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * @author qixin on 2022/1/18.
 * @version 1.0
 * 调用基于Pythond的数据分析接口
 */
@Service
public class PythonManagerImpl implements IPythonManager {

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public ResultVo predictEvaluationScore(double[] historyScores, int nextN) {
        String url = PythonConstants.PYTHON_API_URL_PREFIX + "/test_1.0?data1={historyScores}&m={model}";
        url = url.replace("{model}", BizConstants.PREDICT_MODEL);
        StringBuffer scoreBuffer = new StringBuffer();
        scoreBuffer.append("[");
        for (int i = 0; i < historyScores.length; i++) {
            scoreBuffer.append(historyScores[i]).append(",");
        }
        String scoreArrayString = scoreBuffer.toString();
        scoreArrayString = scoreArrayString.substring(0, scoreArrayString.length() - 1);
        scoreArrayString += "]";

        url = url.replace("{historyScores}", scoreArrayString);
        ResultVo resultVo = restTemplate.getForObject( url, ResultVo.class);
        return resultVo;
    }
}
