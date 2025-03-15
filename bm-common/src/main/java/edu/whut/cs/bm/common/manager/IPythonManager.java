package edu.whut.cs.bm.common.manager;

import edu.whut.cs.bm.common.base.ResultVo;

import java.util.List;

/**
 * @author qixin on 2022/1/18.
 * @version 1.0
 */
public interface IPythonManager {

    ResultVo predictEvaluationScore(double[] historyEvaluations, int nextN);

}
