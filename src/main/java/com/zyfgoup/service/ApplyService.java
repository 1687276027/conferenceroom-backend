package com.zyfgoup.service;

import com.zyfgoup.entity.Apply;
import com.baomidou.mybatisplus.extension.service.IService;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zyfgoup
 * @since 2020-06-18
 */
public interface ApplyService extends IService<Apply> {

    /**
     * 查询同一会议室在指定时间段内是否已经存在冲突的申请
     *
     * @param excludeApplyId 需要排除的申请id 审核通过时排除自己 不需要时传null
     */
    List<Integer> searchTimeConflict(Integer roomId, LocalDateTime startTime, LocalDateTime endTime, Integer excludeApplyId);
}
