package edu.whut.cs.bm.iot.mapper;

import java.util.List;
import edu.whut.cs.bm.iot.domain.Channel;

/**
 * 数据通道管理Mapper接口
 * 
 * @author qixin
 * @date 2021-08-04
 */
public interface ChannelMapper 
{
    /**
     * 查询数据通道管理
     * 
     * @param name 数据通道管理ID
     * @return 数据通道管理
     */
    public Channel selectChannelById(String name);

    /**
     * 查询数据通道管理列表
     * 
     * @param channel 数据通道管理
     * @return 数据通道管理集合
     */
    public List<Channel> selectChannelList(Channel channel);

    /**
     * 新增数据通道管理
     * 
     * @param channel 数据通道管理
     * @return 结果
     */
    public int insertChannel(Channel channel);

    /**
     * 修改数据通道管理
     * 
     * @param channel 数据通道管理
     * @return 结果
     */
    public int updateChannel(Channel channel);

    /**
     * 删除数据通道管理
     * 
     * @param name 数据通道管理ID
     * @return 结果
     */
    public int deleteChannelById(String name);

    /**
     * 批量删除数据通道管理
     * 
     * @param names 需要删除的数据ID
     * @return 结果
     */
    public int deleteChannelByIds(String[] names);
}
