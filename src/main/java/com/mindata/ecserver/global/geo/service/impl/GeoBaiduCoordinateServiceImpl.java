package com.mindata.ecserver.global.geo.service.impl;

import com.mindata.ecserver.global.geo.service.BaseGeoCoordinateService;
import com.mindata.ecserver.global.geo.service.IGeoCoordinateService;
import com.mindata.ecserver.global.http.CallManager;
import com.mindata.ecserver.global.http.RetrofitServiceBuilder;
import com.mindata.ecserver.global.http.request.MapBaiduRequestProperty;
import com.mindata.ecserver.global.http.request.base.RequestProperty;
import com.mindata.ecserver.global.http.response.BaiduLocationResultBean;
import com.mindata.ecserver.global.http.response.BaiduMultipleResponseBean;
import com.mindata.ecserver.global.http.response.BaiduMultipleResponseData;
import com.mindata.ecserver.global.http.response.BaiduResponseData;
import com.mindata.ecserver.global.http.response.base.CoordinateResultData;
import com.mindata.ecserver.global.http.service.BaiduCoordinateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.mindata.ecserver.global.GeoConstant.BAIDU_SOURCE;

/**
 * 获取百度地图api的数据
 *
 * @author hanliqiang wrote on 2017/11/27
 */
@Order(0)
@Service
public class GeoBaiduCoordinateServiceImpl extends BaseGeoCoordinateService implements IGeoCoordinateService {
    @Resource
    private RetrofitServiceBuilder retrofitServiceBuilder;
    @Resource
    private CallManager callManager;
    @Value("${geo.baidu-url}")
    private String baiduUrl;
    @Value("${geo.baidu-ak}")
    private String baiduAK;

    private Logger logger = LoggerFactory.getLogger(getClass());

    private BaiduCoordinateService baiduCoordinateService;

    private BaiduCoordinateService getBaiduCoordinateService(RequestProperty requestProperty) {
         if (baiduCoordinateService == null) {
             baiduCoordinateService = retrofitServiceBuilder.getRetrofit(requestProperty).create
                     (BaiduCoordinateService.class);
         }
         return baiduCoordinateService;
    }

    /**
     * 根据地址去百度地图获取经纬度
     *
     * @param address 地址
     * @return 结果
     * @throws IOException 异常
     */
    @Override
    public CoordinateResultData getCoordinateByAddress(String address) throws IOException {
        RequestProperty requestProperty = new MapBaiduRequestProperty(baiduUrl);
        BaiduCoordinateService baiduCoordinateService = getBaiduCoordinateService(requestProperty);
        //两个ak随机取一个
        BaiduResponseData baiduResponseData = (BaiduResponseData) callManager.execute(
                baiduCoordinateService.getCoordinateByAddress(address, "json", getAK(baiduAK)));
        logger.info("获取到百度返回的地址信息：" + baiduResponseData);

        if (baiduResponseData.getResult() == null) {
            return null;
        }
        return singleCoordinate(baiduResponseData, address);
    }

    /**
     * 根据公司名称或者地址去百度地图获取经纬度
     *
     * @param parameter 公司名字或地址
     * @param city      城市
     * @return 结果
     * @throws IOException 异常
     */
    @Override
    public List<CoordinateResultData> getCoordinateByParameter(String parameter, String city, Integer pageSize,
                                                               Integer page) throws IOException {
        RequestProperty requestProperty = new MapBaiduRequestProperty(baiduUrl);
        BaiduCoordinateService baiduCoordinateService = getBaiduCoordinateService(requestProperty);

        BaiduMultipleResponseData baiduMultipleResponseData = (BaiduMultipleResponseData) callManager.execute(
                baiduCoordinateService.getCoordinateByParameter(parameter, city, pageSize, page, true, "json",
                        getAK(baiduAK)));
        logger.info("获取到百度返回的地址信息：" + baiduMultipleResponseData);
        List<BaiduMultipleResponseBean> baiduMultipleResponseBeans = baiduMultipleResponseData.getResults();
        //302访问过多
        if (baiduMultipleResponseBeans == null) {
            return null;
        }

        List<BaiduLocationResultBean> baiduLocationResultBeans = new ArrayList<>();
        baiduMultipleResponseBeans.forEach(multipleResponseBean -> baiduLocationResultBeans.add(multipleResponseBean
                .getLocation()));
        return getMultipleBaiduCoordinate(baiduLocationResultBeans, parameter);
    }

    /**
     * 获取多个百度坐标
     *
     * @param parameter
     *         parameter
     * @return 结果
     */
    private List<CoordinateResultData> getMultipleBaiduCoordinate(List<BaiduLocationResultBean> baiduMultipleDatas, String parameter) {
        List<CoordinateResultData> coordinateEntities = new ArrayList<>();
        if (baiduMultipleDatas.size() == 1) {
            if (baiduMultipleDatas.get(0) == null) {
                return coordinateEntities;
            }
            CoordinateResultData resultData = parseCoordinateResultData(parameter, true);
            resultData.setSource(BAIDU_SOURCE);
            resultData.setBaiduCoordinate(baiduMultipleDatas.get(0).getCoordinate());
            coordinateEntities.add(resultData);
            return coordinateEntities;
        }
        for (BaiduLocationResultBean baiduLocationResultBean : baiduMultipleDatas) {
            if (baiduLocationResultBean == null) {
                continue;
            }
            CoordinateResultData resultData = parseCoordinateResultData(parameter, false);
            resultData.setSource(BAIDU_SOURCE);
            resultData.setBaiduCoordinate(baiduLocationResultBean.getCoordinate());
            coordinateEntities.add(resultData);
        }
        return coordinateEntities;
    }
}
