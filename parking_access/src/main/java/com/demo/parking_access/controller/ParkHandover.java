package com.demo.parking_access.controller;

import com.demo.parking_access.handEntity.*;
import com.demo.parking_access.mapper.ParkHandoverMapper;
import com.demo.parking_access.util.Md5Util;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.demo.parking_access.service.*;
import com.demo.parking_access.entity.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Controller
@RequestMapping(value = "service")
public class ParkHandover {

    @Resource
    private ParkHandoverService parkHandoverService;

    @Resource
    private ParkHandoverMapper parkHandoverMapper;


    /**
     * 用户登录
     *
     * @param worker name parkid password
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "workerLogin.ss")
    @ResponseBody
    public Object workerLogin(Worker worker) throws Exception {
        Boolean suc = false;
        int role;
        String uuid;
        Map<String, Object> map = new HashMap<>();

        worker.setUserName(worker.getName());
        System.err.println("---执行登录---name:" + worker.getUserName() + "password:" + worker.getPassword());

        Md5Util md5 = new Md5Util();
        if (parkHandoverMapper.selectUserLogin(worker) != null) {
            Worker ww = parkHandoverMapper.selectUserLogin(worker);
            //密码加密后进行对比
            if (md5.MD5Encode(worker.getPassword()).equals(ww.getPassWord())) {
                suc = true;
                uuid = ww.getUuid();
                role = ww.getRole();
                map.put("role", role);
                map.put("uuid", uuid);
            }
        }

        map.put("suc", suc);
        return map;
    }

    /**
     * 根据停车场编码查询在场车辆数目
     *
     * @param parkid
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "getParkInfo.ss")
    @ResponseBody
    public Object getParkInfo(String parkid) throws Exception {
        System.err.println("---执行查找剩余车位---");
        int availableCapcity;
        String name = "";
        Boolean suc = false;


        CarLogPresent car = parkHandoverMapper.countCarPresent(parkid);  //查找当前在场车次数量
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> dataMap = new HashMap<>();
        if (car.getCompany().getName() != null) {
            suc = true;
            name = car.getCompany().getName();
            dataMap.put("name", name);
            availableCapcity = car.getId(); //代表在场车次数量
            dataMap.put("availableCapcity", car.getCompany().getCapcity() - availableCapcity);  //停车场剩余车辆
            dataMap.put("capcity", car.getCompany().getCapcity());
            map.put("data", dataMap);
        }

        map.put("suc", suc);
        return map;
    }

    /**
     * 根据时间查询在场车辆详细信息
     *
     * @param car
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "queryParkingCars.ss")
    @ResponseBody
    public Object queryParkingCars(CarLogPresent car) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        car.setStartTime(String.valueOf(sdf.parse(car.getStartTime()).getTime() / 1000));
        car.setEndTime(String.valueOf(sdf.parse(car.getEndTime()).getTime() / 1000));
        System.err.println(car.getStartTime() + "--" + car.getEndTime() + "--" + car.getParkid() + "--" + car.getPageNo() + "--" + car.getPageSize());
        Boolean suc = true;
        int count = 0;
        List<CarLogPresent> data = new ArrayList<>();
        /* int newpageNo = car.getPageNo() * car.getPageSize() - car.getPageSize() ;*/
        data = parkHandoverMapper.selectCarLogPresentPage(car);  //查询数据

        Map<String, Object> map = new HashMap<>();
        if (data != null) {
            count = parkHandoverMapper.countCarPresentDate(car); //获取在场车辆数目
            map.put("count", count);
            map.put("data", data);
        }

        map.put("suc", suc);

        return map;
    }

    /**
     * 车辆进场   carLog
     *
     * @param
     * @return map
     * @throws Exception
     */
    @RequestMapping(value = "carEnter.ss")
    @ResponseBody
    public Object carEnter(HandCar carLog) throws Exception {

        carLog.setLicense(carLog.getLicense().substring(1, carLog.getLicense().length() - 1));

        System.err.println("picUrl" + carLog.getPicUrl() + "license:" + carLog.getLicense() + "gateuuid:" + carLog.getGateUuid() + "time:" + carLog.getTime() + "parkid:" + carLog.getParkid()
                + "picUrl:" + carLog.getPicUrl());

        Gson gson = new Gson();
        Map<String, Object> LicenMap = new HashMap<String, Object>();
        LicenMap = gson.fromJson(carLog.getLicense(), LicenMap.getClass());
        String license = (String) LicenMap.get("license");
        carLog.setLicense(license);
        String color = (String) LicenMap.get("color");

        //记录uuid
        String logUuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        carLog.setUuid(logUuid);

        //状态
        Boolean suc;

        //查询此进入闸口层级
        int gate_level = parkHandoverMapper.getGateUuid(carLog.getGateUuid()).getLevel();
        //车辆进入状态
        int status = 0;
        //车辆类型
        int carType = 1;
        //储值卡用户余额
        Double card_amount = 0.0;
        //月租车结束时间
        Date endDatex;
        //结束时间
        Long endDate;

        //返回的结果集
        Map<String, Object> map = new HashMap<>();

        //查询车辆信息
        Carowner carowner;
        if (parkHandoverMapper.getCarownerUuid(carLog.getLicense(), carLog.getParkid()) != null) {
            carowner = parkHandoverMapper.getCarownerUuid(carLog.getLicense(), carLog.getParkid());
            if (carowner.getType() == 3 || carowner.getType() == 2) {
                endDatex = carowner.getEndDate(); //月租车则获取结束时间
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //date转换时间字符串
                long endTime = formatter.parse(formatter.format(endDatex)).getTime() / 1000;
                System.err.println(endTime);
                if ((int) endTime < carLog.getTime()) {   //车辆月租小于进场时间时 说明已经月租过期
                    status = 1;
                }
                map.put("endDate", endDatex);
                carType = carowner.getType();
            } else if (carowner.getType() == 4) {
                card_amount = carowner.getAmount().doubleValue(); //储值卡车辆则获取卡上余额
                map.put("card_amount", card_amount);
                carType = carowner.getType();
            } else if (carowner.getType() == 5) {
                carType = carowner.getType();
            } else if (carowner.getType() == 6) {  //黑名单
                map.put("suc", true);
                map.put("status", CarLog.STATUS_BLACKLIST);
                return map;
            }
        } else {
            carType = 1;
        }

        if (parkHandoverMapper.getCarLogPresent(carLog) != null) {  //如果在场车辆存在此车则不允许重复入场
            map.put("suc", true);
            map.put("status", 4);
            map.put("carType", carType);
            return map;
        }

        //找出最高组织编码
        carLog.setOrgUuid(parkOrgUuid(carLog.getParkid()));

        carLog.setCarType(carType);

        //添加车辆记录
        if (parkHandoverService.addCarLogOver(carLog) == 1) {
            suc = true;
        } else {
            suc = false;
        }


        parkHandoverMapper.addCarLogPresent(carLog); //添加在场车辆记录

        map.put("suc", suc);
        map.put("logUuid", logUuid);
        map.put("gate_level", gate_level);
        map.put("status", status);
        map.put("carType", carType);

        return map;
    }

    /**
     * 车辆出场
     *
     * @param carLog
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "carOut.ss")
    @ResponseBody
    public Object carOut(CarLog carLog) throws Exception {
        int gate_level = 0;
        int carType = 0;
        int carModal = 0;
        Boolean already_paid;
        int status;
        float amount;
        float original_amount;


        //记录uuid
        String logUuid = carLog.getLogUuid();

        Map<String, Object> map = new HashMap<>();
        Carowner car = null; //车辆信息
        CarLog log = parkHandoverMapper.selectCaownerLogUuid(logUuid); //进出记录信息

        if (log.getPayTime() != 0) {  //如果进出记录中已存在付款时间 则判断逗留时间是否超过方案规定停留时间如停留则再次收费 未停留则放行
            map.put("status", CarLog.STATUS_PAYDONE); //已缴费
            map.put("already_paid", true); //已支付
            map.put("payMethod", 1);//付费方式

        } else {
            if (parkHandoverMapper.selectLicense(log.getLicense(), log.getParkUuid()) == null) {  //在车辆信息找不到则是临时车
                CarLogPresent carLogPresent = parkHandoverMapper.getCarLog(log);  //找出场车辆信息
                int timeLong = carLog.getTime() - carLogPresent.getEnterTime();
                Gate gate = parkHandoverMapper.getGateUuid(carLog.getGateUuid());  //获取闸口信息

                String smallCarSolutionUuid = gate.getSmallCarSolutionUuid(); //获取小型车方案uuid
                Feesolution feesolution = parkHandoverMapper.selectFeesolution(smallCarSolutionUuid); //获取小型车方案信息
                feesolution.setFeeplanList(parkHandoverMapper.selectFeeplan(smallCarSolutionUuid));
                feesolution.setLadderPriceList(parkHandoverMapper.selectLadder(smallCarSolutionUuid));

                amount = billing(feesolution, carLogPresent); //original_amount
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
                HttpSession session = request.getSession();
                amount += (float) session.getAttribute("minutesAmount");
                System.err.println("需支付金额:" + amount);

                gate_level = gate.getLevel();
                carModal = carLogPresent.getCarModal();
                map.put("suc", true);
                map.put("gate_level", gate_level);
                map.put("carModal", carModal);
                map.put("carType", 1);
                map.put("amount", amount);
                map.put("original_amount", amount);
                map.put("discount", carLogPresent.getDiscount());
                map.put("timeLong", timeLong);

                //更新进出记录
                HandCarLog handCar = new HandCarLog();

                if (amount != 0) {
                    handCar.setLogUuid(carLog.getLogUuid());
                    handCar.setPicUrl(carLog.getPicUrl()); //当需要支付金额时在此先追加进出境记录的出场图片路径
                    map.put("status", CarLog.STATUS_NORMAL); //待缴费
                    map.put("already_paid", false); //未支付
                    parkHandoverMapper.updateCarLogOutPic(handCar);

                } else {

                    //当金额为0时则在本地端不经过人工收费,所以在此更新进出记录与收费记录并且删除此在场车辆信息
                    handCar.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
                    handCar.setLogUuid(carLog.getLogUuid());
                    handCar.setLicense(carLogPresent.getLicense());
                    handCar.setAmount(0.0);
                    handCar.setPayMethod(9); //免费
                    handCar.setTime(carLog.getTime()); //付款时间则是出场时间
                    handCar.setParkid(carLog.getParkid());
                    handCar.setGateUuid(carLog.getGateUuid());
                    handCar.setPicUrl(carLog.getPicUrl());
                    //找出最高组织编码
                    Company company = new Company();
                    company.setCoding(handCar.getParkid()); //将闸道的上层编码放入companyd对象中使用
                    Boolean b = true;
                    do {
                        company = parkHandoverMapper.getFacilityIdEnt(company.getCoding());
                        if (company.getCoding().equals("0")) {
                            b = false;
                            handCar.setOrgUuid(handCar.getParkid()); //防止出现一级公司添加人员情景则不入循环值 当coding
                        } else if (company.getParentId().equals("0")) {
                            b = false;
                            handCar.setOrgUuid(company.getCoding());
                        }
                    } while (b);//while判断成功则继续查询
                    handCar.setWorkerUuid(CarLog.PAY_METHOD_DXY); //用户uuid

                    // 更新车辆进出记录
                    int y = parkHandoverMapper.updateCarLog(handCar);

                    //增添付款记录
                    int x = parkHandoverMapper.addHandPayLog(handCar);

                    //删除在场车辆信息
                    int i = parkHandoverMapper.deleteCarLogPresent(carLog.getLogUuid());


                    map.put("status", CarLog.STATUS_PAYDONE); //已缴费
                    map.put("already_paid", true); //已支付
                    map.put("payMethod", 1);//付费方式
                }

            } else if (parkHandoverMapper.selectLicense(log.getLicense(), log.getParkUuid()).getType() == 3) {

                car = parkHandoverMapper.selectLicense(log.getLicense(), log.getParkUuid());  //车辆信息

                //月租需要数据
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd"); //date转换时间字符串
                long endDate = formatter.parse(formatter.format(car.getEndDate())).getTime() / 1000;
                System.err.println("月租到期时间 long年月日：" + endDate);
                int endTime = (int) endDate;
                System.err.println("到期时间 int：" + endTime);

                int enterTime = log.getEnterTime();//月租车  数据库存储的格式就为十位数时间戳
                System.err.println("车辆进场时间" + enterTime);


                CarLogPresent carLogPresent = parkHandoverMapper.getCarLog(log);  //找出场车辆信息

                Gate gate = parkHandoverMapper.getGateUuid(carLog.getGateUuid());  //获取闸口信息
                gate_level = gate.getLevel();  //闸口级别
                carModal = carLogPresent.getCarModal();
                int timeLong = carLog.getTime() - carLogPresent.getEnterTime(); //停留时间


                if (endTime > carLog.getTime()) {  //如果未过期则免费进出

                    map.put("suc", true);
                    map.put("gate_level", gate_level);
                    map.put("carType", 3);
                    map.put("original_amount", 0);
                    map.put("amount", 0);
                    map.put("payMethod", 4); //月租支付
                    map.put("timeLong", timeLong);
                    System.err.println(formatter.format(car.getEndDate()));
                    map.put("endDate", formatter.format(car.getEndDate())); //yyyy-MM-dd
                    map.put("already_paid", true);
                    map.put("status", 0);

                    // 删除在场车辆信息
                    int i = parkHandoverMapper.deleteCarLogPresent(carLog.getLogUuid());

                    // 更新车辆进出记录
                    HandCarLog handCar = new HandCarLog();
                    handCar.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
                    handCar.setLogUuid(carLog.getLogUuid());
                    handCar.setLicense(car.getLicense());  //出场不发送车牌
                    handCar.setAmount(0.0);
                    handCar.setPayMethod(4); //月租支付
                    handCar.setTime(carLog.getTime()); //付款时间则是出场时间
                    handCar.setParkid(carLog.getParkid());
                    handCar.setGateUuid(carLog.getGateUuid());
                    handCar.setOrgUuid(parkOrgUuid(carLog.getParkid()));
                    handCar.setWorkerUuid(CarLog.PAY_METHOD_DXY); //用户uuid   因为是自由进出所有没有收费员.
                    handCar.setPicUrl(carLog.getPicUrl());

                    // 更新车辆进出记录
                    int y = parkHandoverMapper.updateCarLog(handCar);

                    // 增添付款记录
                    int x = parkHandoverMapper.addHandPayLog(handCar);

                } else { // 若已过期则判断进场时是否过期
                    if (enterTime > endTime) {  //进场时间超过月租到期时间  按进场时间至出场时间收费

                        String smallCarSolutionUuid = gate.getSmallCarSolutionUuid(); //获取小型车方案uuid
                        Feesolution feesolution = parkHandoverMapper.selectFeesolution(smallCarSolutionUuid); //获取小型车方案信息
                        feesolution.setFeeplanList(parkHandoverMapper.selectFeeplan(smallCarSolutionUuid));
                        feesolution.setLadderPriceList(parkHandoverMapper.selectLadder(smallCarSolutionUuid));

                        amount = billing(feesolution, carLogPresent); //original_amount

                        map.put("suc", true);
                        map.put("gate_level", gate_level);
                        map.put("carType", 3);
                        map.put("amount", amount);
                        map.put("original_amount", amount);
                        map.put("timeLong", timeLong);
                        map.put("carModal", carModal);
                        map.put("endDate", formatter.format(car.getEndDate())); //yyyy-MM-dd


                        // 更新车辆进出记录
                        HandCarLog handCar = new HandCarLog();

                        if (amount != 0) {
                            handCar.setPicUrl(carLog.getPicUrl()); //当需要支付金额时在此先追加进出境记录的出场图片路径
                            map.put("status", CarLog.STATUS_NORMAL); //待缴费
                            map.put("already_paid", false); //未支付
                            handCar.setLogUuid(carLog.getLogUuid());
                            parkHandoverMapper.updateCarLogOutPic(handCar);

                        } else {  //月租已过期但是停留时间产生的费用为0
                            map.put("status", CarLog.STATUS_PAYDONE); //已缴费
                            map.put("already_paid", true); //已支付
                            map.put("payMethod", 1);//付费方式

                            // 删除在场车辆信息
                            int i = parkHandoverMapper.deleteCarLogPresent(carLog.getLogUuid());

                            handCar.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
                            handCar.setLogUuid(carLog.getLogUuid());
                            handCar.setLicense(car.getLicense());
                            handCar.setAmount(0.0);  //月租过期但未产生费用
                            handCar.setPayMethod(4); //月租支付
                            handCar.setTime(carLog.getTime()); //付款时间则是出场时间
                            handCar.setParkid(carLog.getParkid());
                            handCar.setGateUuid(carLog.getGateUuid());
                            handCar.setOrgUuid(parkOrgUuid(carLog.getParkid()));
                            handCar.setWorkerUuid(CarLog.PAY_METHOD_DXY); //用户uuid   因为是自由进出所有没有收费员
                            handCar.setPicUrl(carLog.getPicUrl());

                            // 更新车辆进出记录
                            int y = parkHandoverMapper.updateCarLog(handCar);

                            // 增添付款记录
                            int x = parkHandoverMapper.addHandPayLog(handCar);
                        }

                    } else {  //进场时未过期,出场时过期  按月租到期时间至出场时间收费

                        carLogPresent.setEnterTime(endTime); //到期时间至出场时间计费

                        String smallCarSolutionUuid = gate.getSmallCarSolutionUuid(); //获取小型车方案uuid
                        Feesolution feesolution = parkHandoverMapper.selectFeesolution(smallCarSolutionUuid); //获取小型车方案信息
                        feesolution.setFeeplanList(parkHandoverMapper.selectFeeplan(smallCarSolutionUuid));
                        feesolution.setLadderPriceList(parkHandoverMapper.selectLadder(smallCarSolutionUuid));

                        amount = billing(feesolution, carLogPresent); //original_amount

                        map.put("suc", true);
                        map.put("gate_level", gate_level);
                        map.put("carType", 3);
                        map.put("amount", amount);
                        map.put("original_amount", amount);
                        map.put("timeLong", timeLong);
                        map.put("carModal", carModal);
                        map.put("endDate", formatter.format(car.getEndDate())); //yyyy-MM-dd

                        // 更新车辆进出记录
                        HandCarLog handCar = new HandCarLog();

                        if (amount != 0) {
                            map.put("status", CarLog.STATUS_NORMAL); //待缴费
                            map.put("already_paid", false); //未支付
                            handCar.setPicUrl(carLog.getPicUrl()); //当需要支付金额时在此先追加进出境记录的出场图片路径
                            handCar.setLogUuid(carLog.getLogUuid());
                            parkHandoverMapper.updateCarLogOutPic(handCar);

                        } else {  //月租已过期但是停留时间产生的费用为0
                            map.put("status", CarLog.STATUS_PAYDONE); //已缴费
                            map.put("already_paid", true); //已支付
                            map.put("payMethod", 1);//付费方式

                            // 删除在场车辆信息
                            int i = parkHandoverMapper.deleteCarLogPresent(carLog.getLogUuid());

                            int b;
                            handCar.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
                            handCar.setLogUuid(carLog.getLogUuid());
                            handCar.setLicense(car.getLicense());
                            handCar.setAmount(0.0);  //月租过期但未产生费用
                            handCar.setPayMethod(4); //月租支付
                            handCar.setTime(carLog.getTime()); //付款时间则是出场时间
                            handCar.setParkid(carLog.getParkid());
                            handCar.setGateUuid(carLog.getGateUuid());
                            handCar.setOrgUuid(parkOrgUuid(carLog.getParkid()));
                            handCar.setWorkerUuid(CarLog.PAY_METHOD_DXY); //用户uuid   因为是自由进出所有没有收费员
                            handCar.setPicUrl(carLog.getPicUrl());

                            // 更新车辆进出记录
                            int y = parkHandoverMapper.updateCarLog(handCar);

                            // 增添付款记录
                            int x = parkHandoverMapper.addHandPayLog(handCar);
                        }
                    }
                }

            } else if (parkHandoverMapper.selectLicense(log.getLicense(), log.getParkUuid()).getType() == 4) {  //储蓄卡

                car = parkHandoverMapper.selectLicense(log.getLicense(), log.getParkUuid());  //车辆信息

                CarLogPresent carLogPresent = parkHandoverMapper.getCarLog(log);  //找出场车辆信息
                int timeLong = carLog.getTime() - carLogPresent.getEnterTime();
                Gate gate = parkHandoverMapper.getGateUuid(carLog.getGateUuid());  //获取闸口信息

                String smallCarSolutionUuid = car.getFeeSolutionUuid(); //获取储蓄卡自带的方案
                Feesolution feesolution = parkHandoverMapper.selectFeesolution(smallCarSolutionUuid);
                feesolution.setFeeplanList(parkHandoverMapper.selectFeeplan(smallCarSolutionUuid));
                feesolution.setLadderPriceList(parkHandoverMapper.selectLadder(smallCarSolutionUuid));

                amount = billing(feesolution, carLogPresent); //original_amount

                gate_level = gate.getLevel();
                carModal = carLogPresent.getCarModal();
                // 更新车辆进出记录
                HandCarLog handCar = new HandCarLog();


                if (amount != 0) {
                    if (car.getAmount().floatValue() > amount) {  //储值卡余额能够支付本次停车费用  现场支付
                        map.put("suc", true);
                        map.put("already_paid", true);
                        map.put("status", 0);
                        map.put("payMethod", 5);
                        map.put("card_amount", car.getAmount().subtract(new BigDecimal(amount)));

                        handCar.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
                        handCar.setLogUuid(carLog.getLogUuid());
                        handCar.setLicense(car.getLicense()); //出场发送数据不包含车牌
                        handCar.setAmount(new BigDecimal(amount).doubleValue());  //本次支付费用
                        handCar.setPayMethod(5); //储蓄卡支付
                        handCar.setTime(carLog.getTime()); //付款时间则是出场时间
                        handCar.setParkid(carLog.getParkid());
                        handCar.setGateUuid(carLog.getGateUuid());
                        handCar.setOrgUuid(parkOrgUuid(carLog.getParkid()));
                        handCar.setWorkerUuid(CarLog.PAY_METHOD_DXY); //用户uuid   因为是自由进出所有没有收费员
                        handCar.setPicUrl(carLog.getPicUrl());

                        // 删除在场车辆信息
                        int i = parkHandoverMapper.deleteCarLogPresent(carLog.getLogUuid());

                        // 更新车辆进出记录
                        parkHandoverMapper.updateCarLog(handCar);

                        // 增添付款记录
                        int x = parkHandoverMapper.addHandPayLog(handCar);

                        car.setAmount(car.getAmount().subtract(new BigDecimal(amount)));  //付款后卡上余额
                        //更新车辆储蓄卡余额
                        parkHandoverMapper.updateCar5(car);

                        map.put("carType", 4);


                    } else {  //不能支付

                        float newAmout = amount - car.getAmount().floatValue(); //剩余待支付费用
                        map.put("suc", true);
                        map.put("already_paid", false);
                        map.put("status", 0);
                        map.put("card_amount", car.getAmount());
                        map.put("carType", 4);
                        map.put("amount", newAmout);
                        map.put("original_amount", newAmout);
                        map.put("carModal", 0);

                        handCar.setLogUuid(carLog.getLogUuid());
                        handCar.setPicUrl(carLog.getPicUrl()); //当需要支付金额时在此先追加进出境记录的出场图片路径
                        parkHandoverMapper.updateCarLogOutPic(handCar);

                    }
                } else {  //未产生费用
                    map.put("suc", true);
                    map.put("already_paid", true);
                    map.put("status", 0);
                    map.put("payMethod", 5);
                    map.put("card_amount", amount);

                    handCar.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
                    handCar.setLogUuid(carLog.getLogUuid());
                    handCar.setLicense(car.getLicense()); //出场发送数据不包含车牌
                    handCar.setAmount(0.0);  //本次支付费用
                    handCar.setPayMethod(5); //储蓄卡支付
                    handCar.setTime(carLog.getTime()); //付款时间则是出场时间
                    handCar.setParkid(carLog.getParkid());
                    handCar.setGateUuid(carLog.getGateUuid());
                    handCar.setOrgUuid(parkOrgUuid(carLog.getParkid()));
                    handCar.setWorkerUuid(CarLog.PAY_METHOD_DXY); //用户uuid   因为是自由进出所有没有收费员
                    handCar.setPicUrl(carLog.getPicUrl());

                    // 删除在场车辆信息
                    int i = parkHandoverMapper.deleteCarLogPresent(carLog.getLogUuid());

                    // 更新车辆进出记录
                    parkHandoverMapper.updateCarLog(handCar);

                    // 增添付款记录
                    int x = parkHandoverMapper.addHandPayLog(handCar);

                    map.put("carType", 4);
                }

                map.put("gate_level", gate_level);
                map.put("timeLong", timeLong);

            } else if (parkHandoverMapper.selectLicense(log.getLicense(), log.getParkUuid()).getType() == 5) {  //免费车辆

                car = parkHandoverMapper.selectLicense(log.getLicense(), log.getParkUuid());  //车辆信息

                CarLogPresent carLogPresent = parkHandoverMapper.getCarLog(log);  //找出场车辆信息
                int timeLong = carLog.getTime() - carLogPresent.getEnterTime();
                Gate gate = parkHandoverMapper.getGateUuid(carLog.getGateUuid());  //获取闸口信息
                gate_level = gate.getLevel();
                carModal = carLogPresent.getCarModal();

                map.put("suc", true);
                map.put("amount", 0);
                map.put("original_amount", 0);
                map.put("timeLong", timeLong);
                map.put("carType", 1);
                map.put("gate_level", gate_level);
                map.put("carModal", carModal);
                map.put("payMethod", 1);
                map.put("already_paid", true);
                map.put("status", 10);

                // 更新车辆进出记录
                HandCarLog handCar = new HandCarLog();


                handCar.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
                handCar.setLogUuid(carLog.getLogUuid());
                handCar.setLicense(car.getLicense()); //出场发送数据不包含车牌
                handCar.setAmount(0.0);  //本次支付费用
                handCar.setPayMethod(9); //付款方式
                handCar.setTime(carLog.getTime()); //付款时间则是出场时间
                handCar.setParkid(carLog.getParkid());
                handCar.setGateUuid(carLog.getGateUuid());
                handCar.setOrgUuid(parkOrgUuid(carLog.getParkid()));
                handCar.setWorkerUuid(CarLog.PAY_METHOD_DXY); //用户uuid   因为是自由进出所有没有收费员
                handCar.setPicUrl(carLog.getPicUrl());


                // 更新车辆进出记录
                parkHandoverMapper.updateCarLog(handCar);

                // 增添付款记录
                int x = parkHandoverMapper.addHandPayLog(handCar);

                // 删除在场车辆信息
                int i = parkHandoverMapper.deleteCarLogPresent(carLog.getLogUuid());

                return map;


            } else if (parkHandoverMapper.selectLicense(log.getLicense(), log.getParkUuid()).getType() == 6) {   //黑名单
                map.put("suc", true);
                map.put("status", CarLog.STATUS_BLACKLIST);
                return map;
            }

        }

        return map;

    }


    /**
     * 收费接口
     *
     * @param carLog
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "cashPay.ss")
    @ResponseBody
    public Object cashPay(HandCarLog carLog) throws Exception {

        Boolean suc = false;

        //车辆出场时删除在场车辆 记录出场记录 记录付款记录
        Map<String, Object> map = new HashMap<>();

        //删除在场车辆信息 更新车辆进出记录
        int i = parkHandoverMapper.deleteCarLogPresent(carLog.getLogUuid());


        int carType = parkHandoverMapper.selectCaownerLogUuid(carLog.getLogUuid()).getCarType();
     /*   if (carType == 4) {  //储蓄卡
            Carowner carowner = additionMapper.selectLicense(carLog.getLicense(), carLog.getParkid());  //车辆信息
            carLog.setAmount(carowner.getAmount().doubleValue() + carLog.getAmount());  //支付金额 = 卡上余额加上人工收费金额
            carowner.setAmount(new BigDecimal(0));  //付款后卡上余额
            //更新车辆储蓄卡余额
            parkHandoverMapper.updateCar5(carowner);
        }*/

        //更新进出记录
        int y = parkHandoverMapper.updateCarLog(carLog);

        carLog.setUuid(UUID.randomUUID().toString().replace("-", "").toUpperCase());
        carLog.setOrgUuid(parkOrgUuid(carLog.getParkid()));

        carLog.setPayMethod(1);
        //增添付款记录
        int x = parkHandoverMapper.addHandPayLog(carLog);

        System.err.println("人工收费记录:" + i + "--" + y + "--" + x);
        if (i == 1 && y == 1 && x == 1) {
            suc = true;
        }

        map.put("suc", suc);

        return map;
    }

    /**
     * 查询停车场所有闸口信息
     *
     * @param gate parkid,last_update_time
     * @return
     */
    @RequestMapping(value = "synGates.ss")
    @ResponseBody
    public Object synGates(HandGate gate) {

        int count = 0;
        Boolean suc = true;

        Map<String, Object> map = new HashMap<>();

        if (parkHandoverMapper.selectHandGate(gate) != null) {
            List<HandGate> gateList = parkHandoverMapper.selectHandGate(gate); //获取全部闸口信息
            for (HandGate g : gateList) {
                if (g.getDelete() == 0) {
                    g.setDeleted("true");
                } else {
                    g.setDeleted("false");
                }
            }
            count = gateList.size();
            map.put("data", gateList);
        } else {
            suc = false;
        }

        map.put("suc", suc);
        map.put("count", count);

        return map;
    }


    /**
     * 查询收费报表信息
     *
     * @param payLog
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "queryTransactions.ss")
    @ResponseBody
    public Object queryTransactions(HandPayLog payLog) throws Exception {
        Map<String, Object> map = new HashMap<>();
        Double totleAmount = 0.0; //收费总金额
        Boolean suc = false;
        int count = 0; //收费条数
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        payLog.setEnterTime(Integer.parseInt(String.valueOf(sdf.parse(payLog.getStartTime()).getTime() / 1000)));  //date 转int时间戳
        System.err.println("转换后时间戳:" + payLog.getEnterTime());
        payLog.setExitTime(Integer.parseInt(String.valueOf(sdf.parse(payLog.getEndTime()).getTime() / 1000)));


        if (parkHandoverMapper.queryTransactions(payLog) != null) {
            suc = true;
            List<HandPayLog> payLogList = parkHandoverMapper.queryTransactions(payLog);
            count = payLogList.size();

            for (HandPayLog p : payLogList) {
                totleAmount += p.getAmount();  //总金额
                p.setPaidAmount(p.getCarLog().getPaidAmount().intValue());
                p.setEnterTime(p.getCarLog().getEnterTime());
                p.setExitTime(p.getCarLog().getExitTime());
            }
            map.put("data", payLogList);
        }

        map.put("suc", suc);
        map.put("totleAmount", totleAmount);
        map.put("count", count);
        System.err.println("map:" + map.toString());

        return map;
    }


    /**
     * 获取车辆类型
     *
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "getCarType.ss")
    @ResponseBody
    public Object getCarType(HandCarowner handCarowner) throws Exception {
        Boolean suc = true;
        int count = 1;
        Map<String, Object> map = new HashMap<>();
        List<Map> data = new ArrayList<>();
        Map<String, Object> datas = new HashMap<>();
        //查找出当车辆信息
        if (parkHandoverMapper.selectLicense(handCarowner.getLicense(), handCarowner.getParkid()) != null) {
            suc = true;
            Carowner carowner = parkHandoverMapper.selectLicense(handCarowner.getLicense(), handCarowner.getParkid());
            datas.put("license", carowner.getLicense()); //车牌
            datas.put("carType", carowner.getType()); //车辆类型
            int type = carowner.getType();
            switch (type) {
                case 1:
                    break;
                case 2:
                    break;
                case 3:
                    datas.put("endDate", carowner.getEndDate().getTime() / 1000); //月租到期时间
                    break;
                case 4:
                    datas.put("card_amount", carowner.getAmount().doubleValue()); //储蓄卡余额
                    break;
                case 5:
                    break;
                case 6:
                    break;
            }
            count = 1;
        } else {
            datas.put("carType", 1);
        }
        data.add(datas);
        map.put("suc", suc);
        map.put("count", count);
        map.put("data", data);

        return map;
    }


    /**
     * 计算闸口的三种收费方式返回
     *
     * @param handCar{license,gateUuidd,parkid}
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "queryGateFee.ss")
    @ResponseBody
    public Object queryGateFee(HandCar handCar) throws Exception {


        Map<String, Object> map = new HashMap<>();
        System.err.println(handCar.getGateUuid());


        Gate gate = parkHandoverMapper.getGateUuid(handCar.getGateUuid());
        String smallCarSolutionUuid = gate.getSmallCarSolutionUuid(); //获取小型车方案uuid
        String middleCarSolutionUuid = gate.getMiddleCarSolutionUuid(); //获取中型车方案uuid
        String largeCarSolutionUuid = gate.getLargeCarSolutionUuid(); //获取大型车方案uuid

        Feesolution feesolution = parkHandoverMapper.selectFeesolution(smallCarSolutionUuid); //获取小型车方案信息
        feesolution.setFeeplanList(parkHandoverMapper.selectFeeplan(smallCarSolutionUuid));
        feesolution.setLadderPriceList(parkHandoverMapper.selectLadder(smallCarSolutionUuid));

        Feesolution feesolution2 = parkHandoverMapper.selectFeesolution(middleCarSolutionUuid); //获取中型车方案信息
        feesolution2.setFeeplanList(parkHandoverMapper.selectFeeplan(middleCarSolutionUuid));
        feesolution2.setLadderPriceList(parkHandoverMapper.selectLadder(middleCarSolutionUuid));

        Feesolution feesolution3 = parkHandoverMapper.selectFeesolution(largeCarSolutionUuid); //获取大型车方案信息
        feesolution3.setFeeplanList(parkHandoverMapper.selectFeeplan(largeCarSolutionUuid));
        feesolution3.setLadderPriceList(parkHandoverMapper.selectLadder(largeCarSolutionUuid));


        //找出车辆的进场记录核算金额
        CarLogPresent carlog = parkHandoverMapper.getCarLogPresent(handCar);


        float small_amount = billing(feesolution, carlog);
        small_amount = ratherAmount(small_amount, feesolution); //小

        float middle_amount = billing(feesolution2, carlog);
        middle_amount = ratherAmount(middle_amount, feesolution2); //中

        float large_amount = billing(feesolution3, carlog);
        large_amount = ratherAmount(large_amount, feesolution3); //大

        map.put("small_amount", small_amount);
        map.put("middle_amount", middle_amount);
        map.put("large_amount", large_amount);
        map.put("queryTime", new Date());
        map.put("suc", true);

        return map;

    }

    /**
     * 核算费用
     *
     * @param small_amount
     * @param feesolution
     * @return
     */
    public float ratherAmount(float small_amount, Feesolution feesolution) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession();
        float dayAmount = (float) session.getAttribute("dayAmount");  //超过整天的金钱与多余分钟的钱总和
        float minutesAmount = (float) session.getAttribute("minutesAmount");  //不超过一小时的计费总和
        small_amount += minutesAmount;//总金额
        if (small_amount - dayAmount > feesolution.getDailyLimitAmount().floatValue()) {  //当日费用超出时 按日最高相加
            System.err.println("日封顶:" + feesolution.getDailyLimitAmount().floatValue());
            small_amount = dayAmount + feesolution.getDailyLimitAmount().floatValue();
        }

        if (small_amount > feesolution.getMaxAmount().floatValue()) {  //超过收费封顶时按封顶价格计费
            small_amount = feesolution.getMaxAmount().floatValue();
        }

        return small_amount;
    }

    /**
     * 根据方案和进场时间计算价格
     *
     * @param feesolution
     * @param carlog
     * @return
     */
    public float billing(Feesolution feesolution, CarLogPresent carlog) {

        int dayStart = feesolution.getDayTimeStart();  //日间开始时间
        int dayEnd = feesolution.getDayTimeEnd();  //日间结束时间
        //获取当前时间戳 计算停车时间 按小时计算；进场小时单位对比开始时间 来选择收费规则
        int exitTime = (int) (new Date().getTime() / 1000);
        double xx = (double) (exitTime - carlog.getEnterTime()) / 3600; //当前时间戳减去车辆进场时间  得到车辆停车时间戳 除以一小时毫秒数得到停车小时 秒级
        System.err.println("总停车时间：" + xx);
       /* String s = xx + "";
        int length = s.length() - s.indexOf(".") + 1;  //判断小时是否多出 如有则+1小时*/


        Float CLOUD_KEY_SMALL_AMOUNT = 0f;  //float金额
        int freeTimeUnit = 0; //免费时长 分钟
        switch (feesolution.getFreeTimeUnitType()) {
            case 0:
                freeTimeUnit = feesolution.getFreeTimeUnit() * 60; //小时换算分钟
                break;
            case 1:
                freeTimeUnit = feesolution.getFreeTimeUnit(); //分钟
                break;
        }

        int nums = 0;
        int xxInt = 0;
        if (xx * 60 > freeTimeUnit) {  //当停车时间大于免费时间并且为小数则加1小时
            nums = (int) xx;
            xxInt = (int) xx;
        } else {
            System.err.println("未超过免费时间");
            return 0f;  //小于时间则不收取费用
        }


        System.err.println("余小时：" + nums);


        //停车时间超过一天时余出天数
        int day = 0;
        if (nums >= 24) {
            day = nums / 24; //天数
            nums = nums % 24; //余下小时
        }
        float amount = day * feesolution.getDailyLimitAmount().floatValue(); //天数计费 不超过一天则为0
        System.err.println("天数金额:" + amount);
        float minutesAmount = minutesAmount(xx, xxInt, feesolution); //获取多余分钟的计费 不存在多余分钟则为0
        System.err.println("多余分钟按日间或夜间金额:" + minutesAmount);
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession();
        session.setAttribute("dayAmount", amount);
        session.setAttribute("minutesAmount", minutesAmount);


        //是否开启日夜间模式
        if (feesolution.getWholeDay() == 0) {
            System.err.println("进场时间：" + carlog.getEnterTime());
            System.err.println("出场时间：" + exitTime);
            System.err.println("余下小时：" + nums);
            System.err.println("日开始：" + dayStart);
            System.err.println("日结束：" + dayEnd);
            System.err.println("免费时间：" + freeTimeUnit);
            //获取进场时间的时段
            long enterTime = (long) carlog.getEnterTime();

            //获取小时段  yyyy-MM-dd HH:mm:ss
            SimpleDateFormat sdf = new SimpleDateFormat("HH");
            int HH = Integer.parseInt(sdf.format(enterTime * 1000));  //毫秒级转换小时 由于数据库存储为10位所以需要乘以1000
            System.err.println("进场小时单位：" + HH);

            //如果进场小时段在日间区间按日间计费   开始时间  停车时间：nums 进场小时时间: HH  日间开始：dayStart  结束：dayEnd
            if (HH >= dayStart && HH < dayEnd) {
                //细分日夜区间计费
                if (nums > (dayEnd - HH)) {
                    int nums1 = dayEnd - HH;
                    CLOUD_KEY_SMALL_AMOUNT = sun(feesolution, nums1, amount); //首先计算进场至日间结束费用
                    if (nums - (dayEnd - HH) > dayStart + 24 - dayEnd) {  //剩余时长是否超出夜间区间
                        //计算整个夜间费用
                        CLOUD_KEY_SMALL_AMOUNT += moon(feesolution, (dayStart + 24 - dayEnd), amount);

                        //计算超出夜间费用按日间收费
                        CLOUD_KEY_SMALL_AMOUNT += sun(feesolution, (nums - nums1) - (dayStart + 24 - dayEnd), amount);
                    } else {  //根据剩余小时按夜间方案计费
                        CLOUD_KEY_SMALL_AMOUNT += moon(feesolution, nums - nums1, amount);
                    }
                    return CLOUD_KEY_SMALL_AMOUNT;
                } else { //计费情况类别：时间不超出日间区间
                    return sun(feesolution, nums, amount);
                }
            } else {  //进场时间不在日间区间则先按夜晚计费
                int interval = 0;  //夜晚区间
                if (HH < dayStart) {  //进场时间在时间左还是右
                    interval = dayStart - HH;
                } else {
                    interval = HH - dayStart;
                }
                //细分日夜区间计费
                if (nums > interval) {
                    //先计算进场夜间计费
                    CLOUD_KEY_SMALL_AMOUNT = moon(feesolution, interval, amount); //首先计算进场至夜间间结束费用
                    if (nums - interval > dayEnd - dayStart) { //剩余时长是否超出日间区间
                        //超出则先计算整个日间费用
                        CLOUD_KEY_SMALL_AMOUNT += sun(feesolution, dayEnd - dayStart, amount);
                        //超出部分再由日间计费
                        CLOUD_KEY_SMALL_AMOUNT += moon(feesolution, (nums - interval) - (dayEnd - dayStart), amount);

                    } else {//不超出则根据剩余小时按日间方案计费
                        CLOUD_KEY_SMALL_AMOUNT += sun(feesolution, nums - interval, amount);
                    }
                    return CLOUD_KEY_SMALL_AMOUNT;
                } else { //计费情况类别：时间不超出夜间区间
                    return moon(feesolution, nums, amount);
                }
            }
        } else {  //未开启日夜收费 直接日间计费方式收费
            return sun(feesolution, nums, amount);
        }
    }


    /**
     * 计算余下分钟费用
     *
     * @param xx
     * @param xxInt
     * @param feesolution
     * @return
     */
    public float minutesAmount(double xx, int xxInt, Feesolution feesolution) {
        double minutes = (xx - xxInt) * 60; //余下不足一小时的时间  /分钟
        System.err.println("计算分钟:" + minutes);
        double feeMinutes = 0.0;
        SimpleDateFormat sdf = new SimpleDateFormat("HH");
        int HH = Integer.parseInt(sdf.format(new Date().getTime()));  //毫秒级转换小时 由于数据库存储为10位所以需要乘以1000
        System.err.println("出场小时单位：" + HH);
        if (minutes != 0) { //如果存在多余分钟
            Feeplan feeplan = new Feeplan();
            if (feesolution.getWholeDay() == 1 || (HH >= feesolution.getDayTimeStart() && HH < feesolution.getDayTimeEnd())) { //0为开启日夜,如果未开启日夜分时 或 开启后出场小时也在日间范围
                //余下分钟按日间收费标准计费
                for (Feeplan f : feesolution.getFeeplanList()) {
                    if (f.getNighttime() == 0) { //日间方案
                        feeplan = f;
                    }
                }
                //小时转分钟
                switch (feeplan.getUnitType()) {
                    case 1:  //分钟
                        feeMinutes = feeplan.getTimeUnit();
                        break;
                    case 2: //小时
                        feeMinutes = feeplan.getTimeUnit() * 60;
                        break;
                    case 3://次

                        break;
                }

                if (feeMinutes > minutes) { //不超过设定计费时间标准 返回指定费用
                    return feeplan.getAmount().floatValue();
                } else {
                    int x = (int) (minutes / feeMinutes); //余下分钟多出方案一次收费时间几倍
                    double y = minutes % feeMinutes; //余数
                    if (y != 0) {
                        x = x + 1;
                    }
                    return feeplan.getAmount().multiply(new BigDecimal(x)).floatValue();
                }

            } else {
                //余下分钟按日间收费标准计费
                //余下分钟按日间收费标准计费
                for (Feeplan f : feesolution.getFeeplanList()) {
                    if (f.getNighttime() == 1) { //夜间方案
                        feeplan = f;
                    }
                }
                switch (feeplan.getUnitType()) {
                    case 1:  //分钟
                        feeMinutes = feeplan.getTimeUnit();
                        break;
                    case 2: //小时
                        feeMinutes = feeplan.getTimeUnit() * 60;
                        break;
                    case 3://次

                        break;
                }

                if (feeMinutes > minutes) { //不超过不同计费时间标准
                    return feeplan.getAmount().floatValue();
                } else {
                    int x = (int) (minutes / feeMinutes); //余下分钟多出方案一次收费时间几倍
                    double y = minutes % feeMinutes; //余数
                    if (y != 0) {
                        x = x + 1;
                    }
                    return feeplan.getAmount().multiply(new BigDecimal(x)).floatValue();
                }

            }
        } else {//不存在多余分钟
            return 0;
        }
    }

    /**
     * 日间方案计费
     *
     * @param feesolution
     * @return CLOUD_KEY_SMALL_AMOUNT
     */
    public float sun(Feesolution feesolution, int nums, float amount) {
        Feeplan feeplan = null;
        for (Feeplan f : feesolution.getFeeplanList()) {
            if (f.getNighttime() == 0) { //日间方案
                feeplan = f;
            }
        }
        if (feeplan.getUnitType() == 0) { //按次收费直接返回费用
            return feeplan.getAmount().floatValue();
        }

        feeplan = feeSwitch(feeplan); //转换时间金额

        List<LadderPrice> ladderPriceList = new ArrayList<>(); //阶梯集合
        //此方案是否有阶梯计费
        if (feeplan.getLadderPricing() == 1) {
            for (LadderPrice l : feesolution.getLadderPriceList()) {
                if (l.getPlanUuid().equals(feeplan.getUuid())) { //查出日间收费对应的阶梯集合
                    ladderPriceList.add(l);
                }
            }
            amount = sunLadder(ladderPriceList, feeplan, nums, amount);  //阶梯计费

        } else {  //未开启阶梯计费
            amount = feeplan.getAmount().multiply(new BigDecimal(nums)).floatValue() + amount; //金额结算   转换成float
        }

        return amount;

    }


    /**
     * 夜间方案计费
     *
     * @param feesolution
     * @param nums
     * @return
     */
    public float moon(Feesolution feesolution, int nums, float amount) {
        Feeplan feeplan = null;
        for (Feeplan f : feesolution.getFeeplanList()) {
            if (f.getNighttime() == 1) { //夜间方案
                feeplan = f;
            }
        }

        if (feeplan.getUnitType() == 0) { //按次收费直接返回费用
            amount = feeplan.getAmount().floatValue();
            return amount;
        }

        feeplan = feeSwitch(feeplan);

        List<LadderPrice> ladderPriceList = new ArrayList<>(); //夜间阶梯集合
        //此方案是否有阶梯计费
        if (feeplan.getLadderPricing() == 1) {
            for (LadderPrice l : feesolution.getLadderPriceList()) {
                if (l.getPlanUuid().equals(feeplan.getUuid())) { //查出夜间间收费对应的阶梯集合
                    ladderPriceList.add(l);
                }
            }
            int i; //注释
            amount = sunLadder(ladderPriceList, feeplan, nums, amount);  //阶梯计费
        } else {
            amount = feeplan.getAmount().multiply(new BigDecimal(nums)).floatValue() + amount; //小方案结算   转换成float
        }

        return amount;

    }


    /**
     * 收费基础设置转换
     *
     * @param feeplan
     * @return
     */
    public Feeplan feeSwitch(Feeplan feeplan) {
        switch (feeplan.getUnitType()) {
            case 0:
                break;
            case 1:  //收费分钟
                feeplan.setAmount(feeplan.getAmount().multiply(new BigDecimal(60 / feeplan.getTimeUnit())));  //转换小时收费多少钱  60/分钟时间*金额
                break;
            case 2:  //小时
                break;
            case 3:  //天
                break;
        }
        return feeplan;
    }

    /**
     * 收费阶梯转换
     *
     * @param ladder
     * @return
     */
    public LadderPrice laddSwitch(LadderPrice ladder) {
        switch (ladder.getLadderAfterTimeUnitType()) { //修改超出时间类型单位
            case 1: //收费分钟
                ladder.setLadderAfterTimeUnit(ladder.getLadderAfterTimeUnit() / 60);  //超出规则换算成小时制 用于比较
                break;
            case 2: //小时
                break;
        }

        switch (ladder.getLadderUnitType()) {
            case 1:
                ladder.setLadderAmount(ladder.getLadderAmount().multiply(new BigDecimal(60 / ladder.getLadderTimeUnit())));  //如果超出后计费类型为分钟 则将金钱换算成小时制
                break;
            case 2:
                break;
        }

        return ladder;
    }


    /**
     * 阶梯计费
     *
     * @param ladderPriceList 阶梯集合
     * @param feeplan         收费设置
     * @param nums            停车时间
     * @param amount          金额
     * @return
     */
    public float sunLadder(List<LadderPrice> ladderPriceList, Feeplan feeplan, int nums, float amount) {
        for (int i = 0; i < ladderPriceList.size(); i++) {
            LadderPrice ladder = ladderPriceList.get(i);
            ladder = laddSwitch(ladderPriceList.get(i));  //时间单位转换
            if (i + 1 < ladderPriceList.size()) { //下一个阶梯是否存在
                LadderPrice ladder2 = laddSwitch(ladderPriceList.get(i + 1));  //阶梯2时间单位转换
                if (amount == 0) { //如果日封顶为0  则未超过一天
                    if (nums > ladder.getLadderAfterTimeUnit() && nums <= ladder2.getLadderAfterTimeUnit()) { //如果剩余时间大于阶梯制度时间且小于第二个阶梯时间  则用上第二阶梯
                        amount += feeplan.getAmount().multiply(new BigDecimal(ladder.getLadderAfterTimeUnit())).floatValue(); //未超过首个阶梯时间则先按正常收费计算
                        amount += ladder.getLadderAmount().multiply(new BigDecimal(nums - ladder.getLadderAfterTimeUnit())).floatValue(); //超过时间按阶梯计费
                        nums = 0;
                    } else if (nums > ladder.getLadderAfterTimeUnit() && nums > ladder2.getLadderAfterTimeUnit()) {  //同时使用两阶梯
                        amount += feeplan.getAmount().multiply(new BigDecimal(ladder.getLadderAfterTimeUnit())).floatValue(); //未超过首个阶梯时间则先按正常收费计算
                        amount += ladder.getLadderAmount().multiply(new BigDecimal(ladder2.getLadderAfterTimeUnit() - ladder.getLadderAfterTimeUnit())).floatValue(); //超过时间先由第一阶梯计费  第二阶梯下一次for循环再计费
                        nums = nums - ladder2.getLadderAfterTimeUnit(); //剩余停车小时
                    } else {//剩余时间小于第一个阶梯时间
                        amount += feeplan.getAmount().multiply(new BigDecimal(nums)).floatValue(); //将剩余时间按普通计费计算费用
                        nums = 0;
                    }
                } else { //超过一天则直接按照阶梯收费
                    if (nums > ladder.getLadderAfterTimeUnit() && nums <= ladder2.getLadderAfterTimeUnit()) { //如果剩余时间大于阶梯制度时间且小于第二个阶梯时间  则用上第二阶梯
                        amount += ladder.getLadderAmount().multiply(new BigDecimal(nums)).floatValue(); //超过时间按阶梯计费
                        nums = 0;
                    } else if (nums > ladder.getLadderAfterTimeUnit() && nums > ladder2.getLadderAfterTimeUnit()) {  //同时使用两阶梯
                        amount += ladder.getLadderAmount().multiply(new BigDecimal(nums - ladder2.getLadderAfterTimeUnit())).floatValue(); //超过时间先由第一阶梯计费  第二阶梯下一次for循环再计费
                        nums = nums - ladder2.getLadderAfterTimeUnit(); //剩余停车小时
                    } else {//剩余时间小于第一个阶梯时间
                        amount += feeplan.getAmount().multiply(new BigDecimal(nums)).floatValue(); //将剩余时间按普通计费计算费用
                        nums = 0;
                    }
                }

            } else { //不存在下个阶梯
                if (amount == 0) { //如果日封顶为0  则未超过一天
                    if (nums > ladder.getLadderAfterTimeUnit()) {  //如果剩余时间大于阶梯制度时间
                        amount += feeplan.getAmount().multiply(new BigDecimal(ladder.getLadderAfterTimeUnit())).floatValue(); //未超过阶梯时间则先按正常收费计算
                        amount += ladder.getLadderAmount().multiply(new BigDecimal(nums - ladder.getLadderAfterTimeUnit())).floatValue(); //超过时间按阶梯计费
                    } else { //剩余时间小于阶梯时间
                        amount += feeplan.getAmount().multiply(new BigDecimal(nums)).floatValue(); //将剩余时间按普通计费计算费用
                    }
                    nums = 0;
                } else { //超过一天则直接按照阶梯收费
                    if (nums > ladder.getLadderAfterTimeUnit()) {
                        amount += ladder.getLadderAmount().multiply(new BigDecimal(nums)).floatValue(); //超过时间按阶梯计费
                    } else { //剩余时间小于阶梯时间
                        amount += feeplan.getAmount().multiply(new BigDecimal(nums)).floatValue(); //将剩余时间按普通计费计算费用
                    }
                    nums = 0;
                }
            }

        }
        return amount;
    }


    /**
     * 上报当天进出场车次数量
     *
     * @param parkid
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "parkState.ss")
    @ResponseBody
    public Object parkState(String parkid) throws Exception {
        Date date = new Date();
        // 获得某天最大时间
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
        LocalDateTime startDay = localDateTime.with(LocalTime.MIN);
        LocalDateTime endDay = localDateTime.with(LocalTime.MAX);
        Date startDay1 = Date.from(startDay.atZone(ZoneId.systemDefault()).toInstant());
        Date endDay1 = Date.from(endDay.atZone(ZoneId.systemDefault()).toInstant());

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //date转换时间字符串
        String startDate = formatter.format(startDay1);
        String endDate = formatter.format(endDay1);

        //时间字符串转long时间戳 换算时不要强转int 会超出长度产生负数
        long enterTimex = formatter.parse(startDate).getTime() / 1000;
        long exitTimex = formatter.parse(endDate).getTime() / 1000;

        int enterTime = (int) enterTimex;
        int exitTime = (int) exitTimex;


        int inCount = parkHandoverMapper.getInCount(parkid, enterTime, exitTime);
        int outCount = parkHandoverMapper.getOutCount(parkid, enterTime, exitTime);

        Map<String, Object> map = new HashMap<>();
        map.put("inCount", inCount);
        map.put("outCount", outCount);

        return map;

    }

    /**
     * 查询停车场最高组织编码
     *
     * @param parkid
     * @return
     */
    public String parkOrgUuid(String parkid) {
        //找出最高组织编码
        Company company = new Company();
        company.setCoding(parkid); //将闸道的上层编码放入companyd对象中使用
        Boolean b = true;
        do {
            company = parkHandoverMapper.getFacilityIdEnt(company.getCoding());
            if (company.getCoding().equals("0")) {
                b = false;
                parkid = parkid;//防止出现一级公司添加人员情景则不入循环值 当coding
            } else if (company.getParentId().equals("0")) {
                b = false;
                parkid = company.getCoding();
            }
        } while (b);//while判断成功则继续查询


        return parkid;
    }

}
