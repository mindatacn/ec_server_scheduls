package com.mindata.ecserver.global.geo.service;

import com.mindata.ecserver.global.http.response.base.ResponseValue;

import java.io.IOException;
import java.util.List;

/**
 * @author hanliqiang wrote on 2017/11/27
 */

public interface IGeoCoordinateService<T extends ResponseValue> {

    /**
     * 根据地址获取经纬度
     *
     * @param address
     *         地址
     * @return 结果
     * @throws IOException
     *         异常
     */
    T getCoordinateByAddress(String address) throws IOException;

    /**
     * 根据公司名称或者地址获取经纬度
     *
     * @param parameter
     *         公司名字或地址
     * @param city
     *         城市
     * @param pageSize
     *         pageSize
     * @param page
     *         page
     * @return 结果
     * @throws IOException
     *         异常
     */
    List<T> getCoordinateByParameter(String parameter, String city,
                                     Integer pageSize,
                                     Integer page) throws IOException;

}
