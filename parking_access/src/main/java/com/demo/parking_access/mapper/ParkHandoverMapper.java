package com.demo.parking_access.mapper;

import com.demo.parking_access.handEntity.*;
import com.demo.parking_access.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ParkHandoverMapper {

    //根据账号名称查询账号信息
    public Worker selectUserLogin(Worker worker);





    //查询在场车次数量与停车场名称
    public CarLogPresent countCarPresent(String parkid);

    //根据时间查询在场车辆全部信息
    public List<CarLogPresent> selectCarLogPresentPage(CarLogPresent car);

    //按时间查询在场车辆数量
    public int countCarPresentDate(CarLogPresent car);

    //车辆进场时添加数据
    public int addCarLogOver(HandCar carLog);

    //添加车辆在场记录
    public int addCarLogPresent(HandCar carLog);

    //车辆出场缴费记录 临时
    public int addPayLog(PayLog payLog);

    //获取停车场全部闸口信息
    public List<HandGate> selectHandGate(HandGate gate);



    //根据进出记录uuid删除在场车辆信息
    public int deleteCarLogPresent(String logUuid);

    //增添收款记录
    public int addHandPayLog(HandCarLog carLog);

    //查询收费记录
    public List<HandPayLog> queryTransactions(HandPayLog payLog);

    //储蓄卡支付费用出场后更新卡上余额
    public int updateCar5(Carowner car);

    //根据方案uuid获取方案数据
    public Feesolution selectFeesolution(String uuid);

    //收费设置
    public List<Feeplan> selectFeeplan(String uuid);

    //阶梯
    public List<LadderPrice> selectLadder(String uuid);

    //更新记录
    public int updateCarLog(HandCarLog carLog);

    //需要人工收费时在出场接口中更新进出记录的出场图片
    public int updateCarLogOutPic(HandCarLog carLog);

    //获取一天中入场的车辆数目
    public int getInCount(@Param(value = "parkid") String parkid, @Param(value = "enterTime")int enterTime, @Param(value = "exitTime")int exitTime);

    //获取一天中出场的车辆数目
    public int getOutCount(@Param(value = "parkid") String parkid,@Param(value = "enterTime")int enterTime, @Param(value = "exitTime")int exitTime);



    // --- //

    //根据闸口uuid查询信息
    public Gate getGateUuid(@Param(value = "gateUuid")String gateUuid);

    //获取单个车主信息
    public Carowner getCarownerUuid(@Param(value = "license")String license,@Param(value = "parkUuid")String parkUuid);

    //根据停车场编码和车牌获取在场车辆详细记录
    public CarLogPresent getCarLogPresent(HandCar handCar);

    //根据进出记录uuid获取信息
    public CarLog  selectCaownerLogUuid(String logUuid);

    //查询选中停车场是否存在重复车牌
    public Carowner selectLicense(@Param(value = "license") String license,@Param(value = "parkuuid") String parkuuid);

    //根据停车场编码和车牌获取在场车辆详细记录
    public CarLogPresent getCarLog(CarLog carLog);

    public Company getFacilityIdEnt(String idEntity);


}
