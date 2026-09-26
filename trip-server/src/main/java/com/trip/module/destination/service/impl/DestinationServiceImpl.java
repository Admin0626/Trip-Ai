package com.trip.module.destination.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import com.trip.common.result.ResultCode;
import com.trip.module.destination.entity.Attraction;
import com.trip.module.destination.entity.Destination;
import com.trip.module.destination.mapper.AttractionMapper;
import com.trip.module.destination.mapper.DestinationMapper;
import com.trip.module.destination.service.DestinationService;
import com.trip.module.destination.vo.AttractionVO;
import com.trip.module.destination.vo.DestinationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationServiceImpl implements DestinationService {

    private final DestinationMapper destinationMapper;
    private final AttractionMapper attractionMapper;

    @Override
    public PageResult<DestinationVO> page(long current, long size, String keyword, String province, String tag, Integer status) {
        LambdaQueryWrapper<Destination> w = new LambdaQueryWrapper<>();
        w.eq(Destination::getStatus, status != null ? status : 1);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Destination::getName, keyword)
                    .or().like(Destination::getCity, keyword)
                    .or().like(Destination::getProvince, keyword));
        }
        if (StringUtils.hasText(province)) {
            w.eq(Destination::getProvince, province);
        }
        if (StringUtils.hasText(tag)) {
            w.like(Destination::getTags, tag);
        }
        w.orderByDesc(Destination::getHeat);

        Page<Destination> p = destinationMapper.selectPage(new Page<>(current, size), w);
        PageResult<DestinationVO> pr = new PageResult<>();
        pr.setRecords(toVOList(p.getRecords()));
        pr.setTotal(p.getTotal());
        pr.setCurrent(p.getCurrent());
        pr.setSize(p.getSize());
        pr.setPages(p.getPages());
        return pr;
    }

    @Override
    public List<DestinationVO> list() {
        List<Destination> list = destinationMapper.selectList(
                new LambdaQueryWrapper<Destination>().eq(Destination::getStatus, 1)
                        .orderByDesc(Destination::getHeat));
        return toVOList(list);
    }

    @Override
    public DestinationVO detail(Long id) {
        Destination d = destinationMapper.selectById(id);
        if (d == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return DestinationVO.from(d);
    }

    @Override
    public List<DestinationVO> hot(int limit) {
        List<Destination> list = destinationMapper.selectList(
                new LambdaQueryWrapper<Destination>()
                        .eq(Destination::getStatus, 1)
                        .orderByDesc(Destination::getHeat)
                        .last("LIMIT " + Math.max(1, limit)));
        return toVOList(list);
    }

    @Override
    public List<String> provinces() {
        List<Object> objs = destinationMapper.selectObjs(
                new QueryWrapper<Destination>().select("DISTINCT province")
                        .eq("status", 1));
        return objs.stream()
                .filter(o -> o != null && StringUtils.hasText(o.toString()))
                .map(o -> (String) o)
                .sorted()
                .toList();
    }

    @Override
    public List<AttractionVO> attractions(Long destinationId) {
        List<Attraction> list = attractionMapper.selectList(
                new LambdaQueryWrapper<Attraction>()
                        .eq(Attraction::getDestinationId, destinationId)
                        .eq(Attraction::getStatus, 1)
                        .orderByAsc(Attraction::getId));
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(AttractionVO::from).toList();
    }

    private List<DestinationVO> toVOList(List<Destination> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(DestinationVO::from).toList();
    }
}