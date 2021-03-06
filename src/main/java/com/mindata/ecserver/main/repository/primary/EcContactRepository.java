package com.mindata.ecserver.main.repository.primary;

import com.mindata.ecserver.main.model.primary.EcContactEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * @author wuweifeng wrote on 2017/10/25.
 */
public interface EcContactRepository extends JpaRepository<EcContactEntity, Integer>,
        JpaSpecificationExecutor<EcContactEntity> {

    /**
     * 统计两个id间的数量
     * @param beginId
     * begin
     * @param endId
     * end
     * @return
     * 数量
     */
    Long countByIdBetween(Long beginId, Long endId);

    /**
     * 根据id集合查询线索集合
     *
     * @param ids id集合
     * @return 线索集合
     */
    List<EcContactEntity> findByIdIn(List<Integer> ids);

    /**
     * 查询省等于某个
     *
     * @param province 省
     * @return 分页结果
     */
    Page<EcContactEntity> findByProvince(String province, Pageable pageable);

    /**
     * 查询创建时间比目标时间晚的，用于增量插入ES
     *
     * @param date     目标时间
     * @param pageable 分页
     * @return 结果
     */
    Page<EcContactEntity> findByCreateTimeAfter(Date date, Pageable pageable);

    /**
     * 查询创建时间比目标时间晚的，用于增量插入ES
     *
     * @param begin    开始时间
     * @param end      结束时间
     * @param pageable 分页
     * @return 结果
     */
    Page<EcContactEntity> findByCreateTimeBetween(Date begin, Date end, Pageable pageable);

    /**
     * 查询id大于某个id，且时间小于某个时间的
     *
     * @param id       id
     * @param end      结束时间
     * @param pageable 分页
     * @return 结果
     */
    Page<EcContactEntity> findByIdGreaterThanAndCreateTimeLessThan(Long id, Date end, Pageable pageable);

    /**
     * 查询id大于某个值的
     *
     * @param id       id
     * @param pageable 分页
     * @return 结果
     */
    Page<EcContactEntity> findByIdGreaterThan(Long id, Pageable pageable);

    /**
     * 查询id大于某个值的
     *
     * @param beginId  beginId
     * @param endId    endId
     * @param pageable 分页
     * @return 结果
     */
    Page<EcContactEntity> findByIdBetween(Long beginId, Long endId, Pageable pageable);

    /**
     * 根据行业名称修改行业code
     *
     * @param compId       公司Id
     * @param vocationCode 行业code值
     * @return 成功的个数
     */
    @Query(value = "update EcContactEntity p set p.vocation=?1 where p.compId=?2 ")
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    Integer updateCodeByVocationName(Integer vocationCode, Long compId);

    /**
     * 根据状态查询
     *
     * @param state
     * 是否被推送了
     * @return
     * 结果
     */
    Page<EcContactEntity> findByState(Integer state, Pageable pageable);


    EcContactEntity findById(Long id);

    @Query(value = "update EcContactEntity p set p.state = 3 where p.id in (?1)")
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    Integer updateState(String ids);

    /**
     *
     * @param mobile
     * @param phone
     * @return
     */
    Integer countByMobileAndPhone(String mobile,String phone);

//    Integer countByName(String name);
//
//    Integer countByCompany(String company);

}
