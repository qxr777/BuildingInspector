package edu.whut.cs.bm.biz.service.impl;

import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bm.biz.domain.BmObject;
import edu.whut.cs.bm.biz.domain.Evaluation;
import edu.whut.cs.bm.biz.domain.ObjectIndex;
import edu.whut.cs.bm.biz.mapper.BmObjectMapper;
import edu.whut.cs.bm.biz.mapper.ObjectIndexMapper;
import edu.whut.cs.bm.biz.service.IBmObjectService;
import edu.whut.cs.bm.common.constant.BizConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 监测对象Service业务层处理
 *
 * @author qixin
 * @date 2021-08-10
 */
@Service
public class BmObjectServiceImpl implements IBmObjectService
{
    @Autowired
    private BmObjectMapper bmObjectMapper;

    @Autowired
    private ObjectIndexMapper objectIndexMapper;

    /**
     * 查询监测对象
     *
     * @param id 监测对象ID
     * @return 监测对象
     */
    @Override
    public BmObject selectBmObjectById(Long id)
    {
        return bmObjectMapper.selectBmObjectById(id);
    }

    /**
     * 查询监测对象列表
     *
     * @param bmObject 监测对象
     * @return 监测对象
     */
    @Override
    public List<BmObject> selectBmObjectList(BmObject bmObject)
    {
        return bmObjectMapper.selectBmObjectList(bmObject);
    }

    /**
     * 新增监测对象
     *
     * @param bmObject 监测对象
     * @return 结果
     */
    @Override
    public int insertBmObject(BmObject bmObject)
    {
        bmObject.setCreateTime(DateUtils.getNowDate());

        BmObject info = bmObjectMapper.selectBmObjectById(bmObject.getParentId());
        // 如果父节点不为"正常"状态,则不允许新增子节点
        if (!UserConstants.DEPT_NORMAL.equals(info.getStatus()))
        {
            throw new ServiceException("对象停用，不允许新增");
        }
        bmObject.setAncestors(info.getAncestors() + "," + bmObject.getParentId());

        return bmObjectMapper.insertBmObject(bmObject);
    }

    /**
     * 修改监测对象
     *
     * @param bmObject 监测对象
     * @return 结果
     */
    @Override
    public int updateBmObject(BmObject bmObject)
    {
        BmObject newParentObject = bmObjectMapper.selectBmObjectById(bmObject.getParentId());
        BmObject oldObject = bmObjectMapper.selectBmObjectById(bmObject.getId());
        if (StringUtils.isNotNull(newParentObject) && StringUtils.isNotNull(oldObject))
        {
            String newAncestors = newParentObject.getAncestors() + "," + newParentObject.getId();
            String oldAncestors = oldObject.getAncestors();
            bmObject.setAncestors(newAncestors);
            updateObjectChildren(bmObject.getId(), newAncestors, oldAncestors);
        }
        bmObject.setUpdateTime(DateUtils.getNowDate());
        return bmObjectMapper.updateBmObject(bmObject);
    }

    /**
     * 修改子元素关系
     *
     * @param objectId 被修改的对象ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    public void updateObjectChildren(Long objectId, String newAncestors, String oldAncestors)
    {
        List<BmObject> children = bmObjectMapper.selectChildrenObjectById(objectId);
        for (BmObject child : children)
        {
            child.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
        }
        if (children.size() > 0)
        {
            bmObjectMapper.updateObjectChildren(children);
        }
    }

    /**
     * 删除监测对象对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteBmObjectByIds(String ids)
    {
        return bmObjectMapper.deleteBmObjectByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除监测对象信息
     *
     * @param id 监测对象ID
     * @return 结果
     */
    @Override
    public int deleteBmObjectById(Long id)
    {
        return bmObjectMapper.deleteBmObjectById(id);
    }

    /**
     * 查询监测对象树列表
     *
     * @return 所有监测对象信息
     */
    @Override
    public List<Ztree> selectBmObjectTree()
    {
        List<BmObject> bmObjectList = bmObjectMapper.selectBmObjectList(new BmObject());
        List<Ztree> ztrees = new ArrayList<Ztree>();
        for (BmObject bmObject : bmObjectList)
        {
            Ztree ztree = new Ztree();
            ztree.setId(bmObject.getId());
            ztree.setpId(bmObject.getParentId());
            ztree.setName(bmObject.getName());
            ztree.setTitle(bmObject.getName());
            ztrees.add(ztree);
        }
        return ztrees;
    }

    /**
     * 新增对象指标信息
     *
     * @param objectId 对象ID
     * @param indexIds 指标组
     */
    public void insertObjectIndex(Long objectId, Long[] indexIds)
    {
        if (StringUtils.isNotNull(indexIds))
        {
            // 新增对象与指标管理
            List<ObjectIndex> list = new ArrayList<ObjectIndex>();
            for (Long indexId : indexIds)
            {
                ObjectIndex oi = new ObjectIndex();
                oi.setObjectId(objectId);
                oi.setIndexId(indexId);
                list.add(oi);
            }
            if (list.size() > 0)
            {
                objectIndexMapper.batchObjectIndex(list);
            }
        }
    }

    @Override
    @Transactional
    public void insertObjectAssign(Long objectId, Long[] indexIds)
    {
        objectIndexMapper.deleteObjectIndexByObjectId(objectId);
        insertObjectIndex(objectId, indexIds);
    }

    /**
     * 评估监测对象的健康状况
     *
     * @param objectId
     * @return 评估分
     */
    @Override
    public Evaluation evaluate(Long objectId) {
        Evaluation evaluation = new Evaluation();
        double score = 0.0;
        int objectIndexExceptionCount = 0;
        BmObject object = this.selectBmObjectById(objectId);
        ObjectIndex queryObjectIndex = new ObjectIndex();
        queryObjectIndex.setObjectId(objectId);
        List<ObjectIndex> objectIndexList = objectIndexMapper.selectObjectIndexList(queryObjectIndex);
        double sumOfWeight = 0;
        for (ObjectIndex objectIndex : objectIndexList) {
            sumOfWeight += objectIndex.getWeight().doubleValue();
            if (objectIndex.getIndexData() != null && objectIndex.getIndexData().getIsAlert() != null && objectIndex.getIndexData().getIsAlert() > 0) {
                objectIndexExceptionCount++;
            }
        }
        for (ObjectIndex objectIndex : objectIndexList) {
            int alert_level = (objectIndex.getIndexData() != null && objectIndex.getIndexData().getIsAlert() != null) ? objectIndex.getIndexData().getIsAlert() : 0;
            Double singleScoreObject = objectIndex.getIndexData() != null && objectIndex.getIndexData().getScore() != null ? objectIndex.getIndexData().getScore() : null;
            double singleScore = singleScoreObject != null ? singleScoreObject.doubleValue() : BizConstants.ALERT_LEVEL_SCORE_ARRAY[alert_level];
            double weight = objectIndex.getWeight().doubleValue();
            score += singleScore * weight / sumOfWeight;
        }
        score = Math.round(score * 100) / 100.0;
        int objectIndexCount = objectIndexList.size();
        List<BmObject> childreObjects = bmObjectMapper.selectChildrenObjectById(objectId);
        int childrenObjectCount = childreObjects.size();

        evaluation.setObjectId(object.getId());
        evaluation.setObject(object);
        evaluation.setChildrenObjectCount(childrenObjectCount);
        evaluation.setObjectIndexCount(objectIndexCount);
        evaluation.setObjectIndexExceptionCount(objectIndexExceptionCount);
        evaluation.setScore(score);
        return evaluation;
    }

    /**
     * 得到祖级对象名层次
     *
     * @param objectId
     * @return
     */
    @Override
    public String getAncestorNames(Long objectId) {
        String result = "";
        BmObject bmObject = this.selectBmObjectById(objectId);
        String ancestors = bmObject.getAncestors();
        String[] ancestorIdArray = ancestors.split(",");
        for (String ancestorId : ancestorIdArray) {
            if (ancestorId.length() > 0) {
                result += this.selectBmObjectById(Long.parseLong(ancestorId)).getName();
                result += "-";
            }
        }
        return result;
    }

}
