package com.trip.module.destination.service;

import com.trip.common.result.PageResult;
import com.trip.module.destination.vo.AttractionVO;
import com.trip.module.destination.vo.DestinationVO;

import java.util.List;

public interface DestinationService {

    PageResult<DestinationVO> page(long current, long size, String keyword, String province, String tag, Integer status);

    List<DestinationVO> list();

    DestinationVO detail(Long id);

    List<DestinationVO> hot(int limit);

    List<String> provinces();

    List<AttractionVO> attractions(Long destinationId);
}