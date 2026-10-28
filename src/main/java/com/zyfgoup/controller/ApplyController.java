package com.zyfgoup.controller;


import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.zyfgoup.common.dto.Conditions;
import com.zyfgoup.common.dto.Record;
import com.zyfgoup.common.lang.Result;
import com.zyfgoup.entity.Apply;
import com.zyfgoup.entity.ConferenceRecord;
import com.zyfgoup.entity.ConferenceRoom;
import com.zyfgoup.service.ApplyService;
import com.zyfgoup.service.ConferenceRecordService;
import com.zyfgoup.service.ConferenceRoomService;
import com.zyfgoup.shiro.AccountProfile;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


/**
 * <p>
 *  申请会议室时所需要的api
 * </p>
 *
 * @author zyfgoup
 * @since 2020-06-18
 */
@RestController
@RequestMapping("/apply")
public class ApplyController {


    @Autowired
    ApplyService applyService;

    @Autowired
    ConferenceRecordService conferenceRecordService;

    @Autowired
    ConferenceRoomService conferenceRoomService;


    /**
     * {
          {
            depId
            roomId
           }
           {
            statrTime
            endTime
            theme
            digest
            personCount

           }
       }
     *
     *
     * 添加一个会议室申请申请
     * @return
     */
    @PostMapping("/add")
    @RequiresRoles(value = "user")
    public Result add(@RequestBody Record record){

        Apply apply = record.getApply();
        ConferenceRecord conferenceRecord = record.getConferenceRecord();

        if(apply == null || apply.getRoomId() == null){
            return Result.fail("申请信息不完整，请重新选择会议室");
        }
        //e_id 在表里是 NOT NULL 且没有默认值  前端没有传这个字段
        //这里补成当前登录的账号id（员工id或部门id）  不处理会报 Field 'e_id' doesn't have a default value
        if(StrUtil.isBlank(apply.getEId())){
            apply.setEId(StrUtil.blankToDefault(currentUserId(),""));
        }
        //前端虽然已经校验过时间  但接口自己也要判断一次
        //否则绕过页面直接调用接口可以写入已经过去的时间或者与已有会议冲突的申请
        String error = checkTime(apply.getRoomId(),conferenceRecord);
        if(error != null){
            return Result.fail(error);
        }

        applyService.save(apply);

        //要设置外键
        conferenceRecord.setApplyId(apply.getApplyId());
        conferenceRecordService.save(conferenceRecord);

        return Result.succ("");

    }

    /**
     * 管理员紧急申请
     * @param record
     * @return
     */
    @PostMapping("/addbyadmin")
    @RequiresRoles(value = "admin")
    public Result addByAdmin(@RequestBody Record record){

        System.out.println(record);

        Apply apply = record.getApply();
        ConferenceRecord conferenceRecord = record.getConferenceRecord();

        if(apply == null || apply.getRoomId() == null){
            return Result.fail("申请信息不完整，请重新选择会议室");
        }
        //管理员是代部门申请  没有具体的员工  与表里历史数据保持一致存空串
        //（e_id 在表里是 NOT NULL 且没有默认值 不赋值会插入失败）
        if(StrUtil.isBlank(apply.getEId())){
            apply.setEId("");
        }
        String error = checkTime(apply.getRoomId(),conferenceRecord);
        if(error != null){
            return Result.fail(error);
        }

        applyService.save(apply);

        System.out.println(apply);

        //直接通过  时间冲突已经在checkTime里判断过了
        applyService.update(new UpdateWrapper<Apply>().eq("apply_id",apply.getApplyId())
        .set("audit_state",1));

        //要设置外键
        conferenceRecord.setApplyId(apply.getApplyId());
        conferenceRecordService.save(conferenceRecord);

        return Result.succ("");

    }


    /**
     * 取当前登录账号的id  可能是员工id、部门id或者管理员id（认证时放在 AccountProfile 里）
     */
    private String currentUserId(){
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if(principal instanceof AccountProfile){
            return ((AccountProfile) principal).getId();
        }
        return null;
    }


    /**
     * 保存申请前的时间合法性校验
     * 规则与页面保持一致：结束时间晚于开始时间、会议最短10分钟、开始时间不能早于当前时间、
     * 会议室使用时间为每天的10点到23点、所选时间段不能与该会议室已审核通过的会议冲突
     *
     * @return 校验不通过时的提示语  校验通过返回 null
     */
    private String checkTime(Integer roomId, ConferenceRecord conferenceRecord){

        if(conferenceRecord == null){
            return "申请信息不完整，请重新填写会议信息";
        }
        LocalDateTime startTime = conferenceRecord.getStartTime();
        LocalDateTime endTime = conferenceRecord.getEndTime();

        if(startTime == null || endTime == null){
            return "请选择会议的开始时间和结束时间";
        }
        if(!endTime.isAfter(startTime)){
            return "结束时间必须晚于开始时间";
        }
        if(Duration.between(startTime,endTime).toMinutes() < 10){
            return "会议时间最短为10分钟";
        }
        //留10分钟容错  避免用户选的是"此刻" 提交上来时已经过去几秒被判为非法
        if(startTime.isBefore(LocalDateTime.now().minusMinutes(10))){
            return "开始时间不能早于当前时间";
        }
        if(startTime.getHour() < 10 || endTime.getHour() >= 23){
            return "会议室的使用时间为每天的10:00-23:00";
        }
        if(applyService.searchTimeConflict(roomId,startTime,endTime,null).size() > 0){
            return "该时间段已被占用，请选择其他时间或者会议室";
        }
        return null;
    }


    /**
     * 查出来如果是List不为空 则证明有冲突
     * @param roomId
     * @param startTime
     * @param endTime
     * @return
     */
    @GetMapping("/searchtimeconflict/{roomId}/{startTime}/{endTime}")
    @RequiresRoles(value = {"admin","user"},logical = Logical.OR)
    public Result searchtimeconflict(@PathVariable("roomId") Integer roomId,
                                     @PathVariable("startTime")  @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                     @PathVariable("endTime")  @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") LocalDateTime endTime){


        List<Integer> listApplyId = applyService.searchTimeConflict(roomId,startTime,endTime,null);

        if(listApplyId.size()>0){
            return Result.succ("0");
        }
        return Result.succ("1");
    }


    /**
     * 根据id查一个会议室  供页面申请会议室时 点击申请 就是到后台拿到这个会议室的所有数据
     */
    @GetMapping("/getconfroom/{id}")
    @RequiresRoles(value = {"admin","user"},logical = Logical.OR)
    public Result getById(@PathVariable("id") Integer id){
        ConferenceRoom byId = conferenceRoomService.getById(id);
        return Result.succ(byId);
    }


}
