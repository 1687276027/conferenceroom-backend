package com.zyfgoup.mapper;


import com.zyfgoup.entity.Apply;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author zyfgoup
 * @since 2020-06-18
 */
@Repository
public interface ApplyMapper extends BaseMapper<Apply> {

    /**
     * 自定义的查询语句  查询申请是否时间冲突
     *
     * @param roomId         会议室id
     * @param startTime      会议开始时间
     * @param endTime        会议结束时间
     * @param excludeApplyId 需要排除的申请id（审核通过时要排除自己这条申请，否则会跟自己判为冲突），不需要排除时传null
     */
    List<Integer> searchTimeConflict(@Param("roomId") Integer roomId,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("excludeApplyId") Integer excludeApplyId);

}
