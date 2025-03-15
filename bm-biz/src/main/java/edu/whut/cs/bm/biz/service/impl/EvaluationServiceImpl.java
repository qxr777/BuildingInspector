package edu.whut.cs.bm.biz.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import com.ruoyi.common.utils.DateUtils;
import edu.whut.cs.bm.common.base.ResultVo;
import edu.whut.cs.bm.common.constant.BizConstants;
import edu.whut.cs.bm.common.manager.IPythonManager;
import edu.whut.cs.bm.common.util.BeanConvertUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bm.biz.mapper.EvaluationMapper;
import edu.whut.cs.bm.biz.domain.Evaluation;
import edu.whut.cs.bm.biz.service.IEvaluationService;
import com.ruoyi.common.core.text.Convert;

/**
 * 对象健康评估Service业务层处理
 *
 * @author qixin
 * @date 2021-10-31
 */
@Service
public class EvaluationServiceImpl implements IEvaluationService
{
    @Autowired
    private EvaluationMapper evaluationMapper;

    @Autowired
    private IPythonManager pythonManager;

    /**
     * 查询对象健康评估
     *
     * @param id 对象健康评估ID
     * @return 对象健康评估
     */
    @Override
    public Evaluation selectEvaluationById(Long id)
    {
        return evaluationMapper.selectEvaluationById(id);
    }

    /**
     * 查询对象健康评估列表
     *
     * @param evaluation 对象健康评估
     * @return 对象健康评估
     */
    @Override
    public List<Evaluation> selectEvaluationList(Evaluation evaluation)
    {
        return evaluationMapper.selectEvaluationList(evaluation);
    }

    /**
     * 新增对象健康评估
     *
     * @param evaluation 对象健康评估
     * @return 结果
     */
    @Override
    public int insertEvaluation(Evaluation evaluation)
    {
        evaluation.setCreateTime(DateUtils.getNowDate());
        return evaluationMapper.insertEvaluation(evaluation);
    }

    /**
     * 修改对象健康评估
     *
     * @param evaluation 对象健康评估
     * @return 结果
     */
    @Override
    public int updateEvaluation(Evaluation evaluation)
    {
        evaluation.setUpdateTime(DateUtils.getNowDate());
        return evaluationMapper.updateEvaluation(evaluation);
    }

    /**
     * 删除对象健康评估对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteEvaluationByIds(String ids)
    {
        return evaluationMapper.deleteEvaluationByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除对象健康评估信息
     *
     * @param id 对象健康评估ID
     * @return 结果
     */
    @Override
    public int deleteEvaluationById(Long id)
    {
        return evaluationMapper.deleteEvaluationById(id);
    }

    /**
     * 随机游走模型
     * @param dataArray
     * @return
     */
    private double randomWalk(double[] dataArray) {
        Double predictedScore = 0.0;
        if (dataArray[0] != 0) {
            Random random = new Random();
            predictedScore = dataArray[0] + (random.nextInt(1000) - 500) / 100.0;
            predictedScore = Math.round(predictedScore * 100) / 100.0;
            if (predictedScore > 100) {
                predictedScore = 100.0;
            }
            if (predictedScore < 0) {
                predictedScore = 0.0;
            }
        }
        return predictedScore;
    }

    private List<Evaluation> randomWalkPredict(List<Evaluation> evaluations, int nextN) {
        List<Evaluation> resultEvaluations = new ArrayList<>();
        for (int i = 0; i < nextN; i++) {
            double[] dataArray = new double[evaluations.size()];
            for (int j = 0; j < evaluations.size(); j++) {
                dataArray[j] = evaluations.get(j).getScore();
            }
            Evaluation latestEvaluation = evaluations.get(0);
            Date nextDate =DateUtils.addDays(latestEvaluation.getCreateTime(), 1);
            Double predictedScore = randomWalk(dataArray);   // 随机游走模型预测
            Evaluation predictedEvaluation = BeanConvertUtils.convertTo(latestEvaluation, Evaluation::new);
            predictedEvaluation.setScore(predictedScore);
            predictedEvaluation.setCreateTime(nextDate);
            resultEvaluations.add(predictedEvaluation);
            evaluations.add(0, predictedEvaluation);
        }
        return resultEvaluations;
    }

    private List<Evaluation> pythonPredict(List<Evaluation> evaluations, int nextN) {
        List<Evaluation> resultEvaluations = new ArrayList<>();

        double[] dataArray = new double[evaluations.size()];
        for (int j = 0; j < evaluations.size(); j++) {
            dataArray[j] = evaluations.get(j).getScore();
        }

        ResultVo resultVo = pythonManager.predictEvaluationScore(dataArray, nextN);
        String predictScoresString = resultVo.getData().toString();
        String[] predictScoreArray = predictScoresString.substring(1, predictScoresString.length() - 1).split(", ");

        Evaluation latestEvaluation = evaluations.get(0);

        for (int i = 0; i < nextN; i++) {
            Date nextDate =DateUtils.addDays(latestEvaluation.getCreateTime(), 1);
            Double predictedScore = Double.parseDouble(predictScoreArray[i]);
            Evaluation predictedEvaluation = BeanConvertUtils.convertTo(latestEvaluation, Evaluation::new);
            predictedEvaluation.setScore(predictedScore);
            predictedEvaluation.setCreateTime(nextDate);
            resultEvaluations.add(predictedEvaluation);
            evaluations.add(0, predictedEvaluation);
        }
        return resultEvaluations;
    }

    @Override
    public List<Evaluation> predict(Long objectId, int nextN) {
        List<Evaluation> resultEvaluations = new ArrayList<>();
        Evaluation queryEvaluation = new Evaluation();
        queryEvaluation.setObjectId(objectId);
        List<Evaluation> evaluations = evaluationMapper.selectEvaluationList(queryEvaluation);
        if (evaluations.size() > BizConstants.HISTORY_EVALUATION_DATA_SIZE) {
            evaluations = evaluations.subList(0, BizConstants.HISTORY_EVALUATION_DATA_SIZE);
//            resultEvaluations = randomWalkPredict(evaluations, nextN);  //随机游走模型预测
            resultEvaluations = pythonPredict(evaluations, nextN);  //python数据分析接口预测

        }
        return resultEvaluations;
    }
}
