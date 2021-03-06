package com.mindata.ecserver.main.service;

import com.mindata.ecserver.global.async.AsyncTask;
import com.mindata.ecserver.global.bean.ResultGenerator;
import com.mindata.ecserver.global.geo.GeoCoordinateService;
import com.mindata.ecserver.global.util.CommonUtil;
import com.mindata.ecserver.main.manager.CompanyCoordinateManager;
import com.mindata.ecserver.main.manager.ContactManager;
import com.mindata.ecserver.main.manager.EsCompanyCoordinateManager;
import com.mindata.ecserver.main.model.primary.EcContactEntity;
import com.mindata.ecserver.main.model.secondary.PtCompanyCoordinate;
import com.xiaoleilu.hutool.date.DateUtil;
import com.xiaoleilu.hutool.util.CollectionUtil;
import com.xiaoleilu.hutool.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.mindata.ecserver.global.GeoConstant.PAGE_SIZE;
import static com.mindata.ecserver.global.GeoConstant.PER_THREAD_DEAL_COUNT;


/**
 * @author hanliqiang wrote on 2017/11/24
 */
@Service
public class CompanyCoordinateService {
    @Resource
    private GeoCoordinateService geoCoordinateService;
    @Resource
    private ContactManager contactManager;
    @Resource
    private CompanyCoordinateManager companyCoordinateManager;
    @Resource
    private EsCompanyCoordinateManager esCompanyCoordinateManager;
    @Resource
    private AsyncTask asyncTask;
    /**
     * 并发计数器
     */
    private CountDownLatch countDownLatch;

    private Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 补充所有的公司经纬度信息
     *
     * @throws IOException
     *         异常
     */
    public void completeAllCompanyCoordinate(Boolean force) throws IOException {
        Long beginId = contactManager.findFirstOne().getId();
        Long endId = contactManager.findLastOne().getId();
        partInsertIdBetween(beginId, endId, force);
    }

    /**
     * 导入CompanyCoordinate中尚没有的contact数据，增量导入
     * @param force
     * force
     */
    public void completeRemainCompanyCoordinate(Boolean force) throws IOException {
        //当前数据库里contactId最大的
        Long beginId = companyCoordinateManager.findLastContactIdByContactId();
        //昨天12点前，contactId最大的
        Date beginOfDay = DateUtil.beginOfDay(CommonUtil.getNow());
        Page<EcContactEntity> page = contactManager.findByIdGreaterThanAndCreateTimeLessThan(beginId, beginOfDay, new
                PageRequest(0, 1, Sort.Direction.DESC, "id"));
        if (page.getContent().size() == 0) {
            return;
        }
        Long endId = page.getContent().get(0).getId();
        partInsertIdBetween(beginId, endId, force);
    }

    /**
     * 修补id范围内的数据
     *
     * @param beginId
     *         开始id
     * @param endId
     *         结束id
     * @throws IOException
     *         异常
     */
    public void partInsertIdBetween(Long beginId, Long endId, Boolean force) throws IOException {
        if (force == null) {
            force = false;
        }
        if (endId == null) {
            endId = contactManager.findLastOne().getId();
        }
        if (beginId == null) {
            beginId = contactManager.findFirstOne().getId();
        }

        Long count = contactManager.countIdBetween(beginId, endId);
        Long size = count / PER_THREAD_DEAL_COUNT + 1;

        //判断多少个线程
        countDownLatch = new CountDownLatch(size.intValue());

        logger.info("开始插入id范围" + beginId + "到" + endId + "之间的所有经纬度数据");
        logger.info("共有" + size + "个线程");

        //一个线程处理5千条
        for (int i = 0; i < size; i++) {
            Long tempBeginId = beginId + PER_THREAD_DEAL_COUNT * i;
            Long tempEndId = tempBeginId + PER_THREAD_DEAL_COUNT - 1;
            if (tempEndId > endId) {
                tempEndId = endId;
            }
            Long finalTempEndId = tempEndId;
            Boolean finalForce = force;
            asyncTask.doTask(s -> dealPartInsert(tempBeginId, finalTempEndId, finalForce));
        }

        try {
            //开启线程等待
            countDownLatch.await();
            //开始往ES插值
            logger.info("---------------------------开始往ES插值----------------------");
            esCompanyCoordinateManager.bulkIndexCompany(beginId, endId, count, force);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 多线程异步执行
     *
     * @param beginId
     *         起始id
     * @param endId
     *         结束id
     * @param force
     *         是否强制更新
     */
    private void dealPartInsert(Long beginId, Long endId, Boolean force) {
        logger.info("线程id为" + Thread.currentThread().getId() + "开始处理DB插入的事");
        List<EcContactEntity> contactEntities;
        for (int i = 0; i < (endId - beginId) / PAGE_SIZE + 1; i++) {
            Pageable pageable = new PageRequest(i, PAGE_SIZE, Sort.Direction.ASC, "id");
            contactEntities = contactManager.findByIdBetween(beginId, endId, pageable)
                    .getContent();
            if (contactEntities.size() == 0) {
                continue;
            }
            try {
                //插入DB
                companyCoordinateManager.saveByContacts(contactEntities, force);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //计数器减一
        countDownLatch.countDown();
        logger.info("计数器减一");
    }


    /**
     * 修补一段时间内的数据
     *
     * @param begin
     *         开始时间
     * @param end
     *         结束时间
     * @throws IOException
     *         异常
     */
    public void partInsertDateBetween(String begin, String end, Boolean force) throws IOException {
        Date beginTime = DateUtil.beginOfDay(DateUtil.parseDate(begin));
        Date endTime = DateUtil.endOfDay(DateUtil.parseDate(end));

        logger.info("开始获取开始日期为" + begin + "，结束日期为" + end + "的经纬度");

        Sort sort = new Sort(Sort.Direction.ASC, "id");
        Pageable pageable = new PageRequest(0, 1, sort);
        //找到第一个
        Page<EcContactEntity> page = contactManager.findByDateBetween(beginTime, endTime, pageable);
        if (page.getContent().size() == 0) {
            logger.info("该时间范围内没有数据");
            return;
        }

        Long beginId = page.getContent().get(0).getId();
        sort = new Sort(Sort.Direction.DESC, "id");
        pageable = new PageRequest(0, 1, sort);
        //找到最后一个
        page = contactManager.findByDateBetween(beginTime, endTime, pageable);
        Long endId = page.getContent().get(0).getId();
        partInsertIdBetween(beginId, endId, force);
    }

    /**
     * 定时修改不太靠谱或者没有坐标的数据
     *
     * @throws IOException
     *         异常
     */
    public void timingUpdateCoordinate() throws IOException {
        Sort sort = new Sort(Sort.Direction.ASC, "id");
        Pageable pageable = new PageRequest(0, 1, sort);
        Page<PtCompanyCoordinate> page = companyCoordinateManager.findByStatusOrAccuracy(pageable);
        List<EcContactEntity> contactEntities = new ArrayList<>();
        for (int i = 0; i < page.getTotalElements() / PAGE_SIZE + 1; i++) {
            pageable = new PageRequest(i, PAGE_SIZE, sort);
            List<PtCompanyCoordinate> coordinateEntities = companyCoordinateManager.findByStatusOrAccuracy(pageable)
                    .getContent();
            for (PtCompanyCoordinate ptCompanyCoordinate : coordinateEntities) {
                EcContactEntity ecContactEntity = contactManager.findOne(ptCompanyCoordinate.getContactId());
                contactEntities.add(ecContactEntity);
            }
            List<PtCompanyCoordinate> entityList = companyCoordinateManager.saveByContacts(contactEntities, null);
            esCompanyCoordinateManager.bulkIndexCompany(entityList, null);
        }
    }

    /**
     * 根据地址查经纬度
     *
     * @param address
     *         地址
     * @return 结果
     * @throws IOException
     *         异常
     */
    public Object getOutLocation(String address, String city) throws IOException {
        if (StrUtil.isEmpty(address) || StrUtil.isEmpty(city)) {
            return ResultGenerator.genFailResult("城市和地址不能为空");
        }
        List<String> coordinateEntities = geoCoordinateService.getOutLocationByParameter(address, city);
        if (CollectionUtil.isNotEmpty(coordinateEntities)) {
            return ResultGenerator.genSuccessResult(coordinateEntities);
        }
        return ResultGenerator.genFailResult("没有查到该地址的经纬度");
    }
}
