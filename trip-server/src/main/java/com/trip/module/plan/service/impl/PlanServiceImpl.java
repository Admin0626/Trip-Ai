package com.trip.module.plan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import com.trip.common.result.ResultCode;
import com.trip.module.plan.dto.PlanItemDTO;
import com.trip.module.plan.dto.PlanSaveDTO;
import com.trip.module.plan.entity.UserPlan;
import com.trip.module.plan.entity.UserPlanDay;
import com.trip.module.plan.entity.UserPlanItem;
import com.trip.module.plan.mapper.UserPlanDayMapper;
import com.trip.module.plan.mapper.UserPlanItemMapper;
import com.trip.module.plan.mapper.UserPlanMapper;
import com.trip.module.plan.service.PlanService;
import com.trip.module.plan.vo.PlanDayVO;
import com.trip.module.plan.vo.PlanItemVO;
import com.trip.module.plan.vo.PlanVO;
import com.trip.module.route.entity.Route;
import com.trip.module.route.entity.RouteDay;
import com.trip.module.route.entity.RouteItem;
import com.trip.module.route.mapper.RouteDayMapper;
import com.trip.module.route.mapper.RouteItemMapper;
import com.trip.module.route.mapper.RouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规划服务实现（PLN-01~08）。
 * 核心决策：保存用"事务内先删后插"整体替换（BR-PLN-03 排序以服务端数组顺序为准）；
 * from-route 把 route 转成 dayList 走同一构建路径，不写第二套。
 * 注意：@Transactional 只在 public 入口方法上，私有方法依赖调用方的代理事务。
 */
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private static final int PLAN_LIMIT = 50;

    private final com.trip.module.user.mapper.SysUserMapper sysUserMapper;
    private final UserPlanMapper userPlanMapper;
    private final UserPlanDayMapper userPlanDayMapper;
    private final UserPlanItemMapper userPlanItemMapper;
    private final RouteMapper routeMapper;
    private final RouteDayMapper routeDayMapper;
    private final RouteItemMapper routeItemMapper;

    @Override
    @Transactional
    public Long create(Long userId, PlanSaveDTO dto) {
        validateBeforeSave(dto);
        checkPlanLimit(userId);
        UserPlan plan = newPlan(userId, dto, null);
        userPlanMapper.insert(plan);
        replaceDayList(plan.getId(), dto.getDayList());
        return plan.getId();
    }

    @Override
    @Transactional
    public void update(Long userId, Long planId, PlanSaveDTO dto) {
        userPlanMapper.selectOne(new LambdaQueryWrapper<UserPlan>().eq(UserPlan::getId, planId)
                .eq(UserPlan::getUserId, userId).last("FOR UPDATE"));
        UserPlan plan = requireOwnedPlan(userId, planId);
        validateBeforeSave(dto);
        // 主表字段更新
        plan.setTitle(dto.getTitle());
        plan.setDestinationIds(joinIds(dto.getDestinationIds()));
        plan.setStartDate(dto.getStartDate());
        plan.setDays(dto.getDays());
        plan.setBudget(dto.getBudget());
        plan.setPeopleNum(dto.getPeopleNum());
        plan.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        plan.setUpdateTime(LocalDateTime.now());
        userPlanMapper.updateById(plan);
        replaceDayList(plan.getId(), dto.getDayList());
    }

    @Override
    public PageResult<PlanVO> page(Long userId, long current, long size, Integer status) {
        LambdaQueryWrapper<UserPlan> w = new LambdaQueryWrapper<>();
        w.eq(UserPlan::getUserId, userId);
        if (status != null) {
            w.eq(UserPlan::getStatus, status);
        }
        w.orderByDesc(UserPlan::getUpdateTime);
        Page<UserPlan> p = userPlanMapper.selectPage(new Page<>(current, size), w);
        List<UserPlan> plans = p.getRecords();
        PageResult<PlanVO> pr = new PageResult<>();
        pr.setRecords(CollectionUtils.isEmpty(plans) ? Collections.emptyList() : toVOList(plans));
        pr.setTotal(p.getTotal());
        pr.setCurrent(p.getCurrent());
        pr.setSize(p.getSize());
        pr.setPages(p.getPages());
        return pr;
    }

    @Override
    public PlanVO detail(Long userId, Long planId) {
        UserPlan plan = requireOwnedPlan(userId, planId);
        PlanVO vo = toVO(plan);
        vo.setDayList(buildDayVOs(plan.getId()));
        return vo;
    }

    @Override
    @Transactional
    public void delete(Long userId, Long planId) {
        requireOwnedPlan(userId, planId);
        userPlanMapper.deleteById(planId);
    }

    @Override
    @Transactional
    public Long copy(Long userId, Long planId) {
        UserPlan source = requireOwnedPlan(userId, planId);
        checkPlanLimit(userId);
        List<UserPlanDay> days = userPlanDayMapper.selectList(
                new LambdaQueryWrapper<UserPlanDay>()
                        .eq(UserPlanDay::getUserPlanId, planId)
                        .orderByAsc(UserPlanDay::getDayIndex));
        // 复制主表：标题加"（副本）"，还原为草稿
        UserPlan copy = newPlan(userId, toSaveDTO(source), source.getSourceRouteId());
        copy.setTitle(source.getTitle().substring(0, Math.min(196, source.getTitle().length())) + "（副本）");
        copy.setStatus(0);
        userPlanMapper.insert(copy);
        replaceDayList(copy.getId(), daysToDTO(days, planId));
        return copy.getId();
    }

    @Override
    public String export(Long userId, Long planId) {
        UserPlan plan = requireOwnedPlan(userId, planId);
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(plan.getTitle()).append("】\n");
        sb.append("出发日期：").append(plan.getStartDate())
                .append(" · ").append(plan.getDays()).append(" 天")
                .append(" · 预算 ¥").append(plan.getBudget())
                .append(" · ").append(plan.getPeopleNum()).append(" 人\n\n");
        List<PlanDayVO> days = buildDayVOs(planId);
        for (PlanDayVO day : days) {
            sb.append("第 ").append(day.getDayIndex()).append(" 天");
            if (day.getTitle() != null && !day.getTitle().isEmpty()) {
                sb.append(" · ").append(day.getTitle());
            }
            sb.append("\n");
            for (PlanItemVO item : day.getItems()) {
                sb.append("  ").append(item.getTimePoint() == null ? "  -- " : item.getTimePoint())
                        .append("  ").append(item.getTitle());
                if (item.getActivity() != null && !item.getActivity().isEmpty()) {
                    sb.append("（").append(item.getActivity()).append("）");
                }
                if (item.getCost() != null) {
                    sb.append("  ¥").append(item.getCost());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public Long fromRoute(Long userId, Long routeId) {
        Route route = routeMapper.selectById(routeId);
        if (route == null || (route.getStatus() != null && route.getStatus() == 0)) {
            throw new BizException(ResultCode.ROUTE_NOT_FOUND);
        }
        checkPlanLimit(userId);
        // 读取路线行程 → 转 dayList → 走同一构建路径
        List<RouteDay> rDays = routeDayMapper.selectList(
                new LambdaQueryWrapper<RouteDay>()
                        .eq(RouteDay::getRouteId, routeId)
                        .orderByAsc(RouteDay::getDayIndex));
        List<Long> dayIds = rDays.stream().map(RouteDay::getId).toList();
        List<RouteItem> rItems = dayIds.isEmpty() ? Collections.emptyList() : routeItemMapper.selectList(
                new LambdaQueryWrapper<RouteItem>()
                        .in(RouteItem::getRouteDayId, dayIds)
                        .orderByAsc(RouteItem::getSortNo));
        Map<Long, List<RouteItem>> itemMap = rItems.stream()
                .collect(Collectors.groupingBy(RouteItem::getRouteDayId));

        UserPlan plan = new UserPlan();
        plan.setUserId(userId);
        plan.setTitle("基于《" + route.getTitle() + "》的规划");
        plan.setDestinationIds(String.valueOf(route.getDestinationId()));
        plan.setStartDate(LocalDate.now());
        plan.setDays(route.getDays());
        plan.setBudget(route.getPrice());
        plan.setPeopleNum(1);
        plan.setStatus(0);
        plan.setSourceRouteId(routeId);
        LocalDateTime now = LocalDateTime.now();
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        userPlanMapper.insert(plan);

        // 逐天复制：day_index/title → user_plan_day；item 全字段 → user_plan_item
        for (int i = 0; i < rDays.size(); i++) {
            RouteDay rd = rDays.get(i);
            UserPlanDay day = new UserPlanDay();
            day.setUserPlanId(plan.getId());
            day.setDayIndex(i + 1);
            day.setTitle(rd.getTitle());
            day.setSummary(rd.getSummary());
            day.setCreateTime(now);
            day.setUpdateTime(now);
            userPlanDayMapper.insert(day);
            List<RouteItem> items = itemMap.getOrDefault(rd.getId(), Collections.emptyList());
            for (int j = 0; j < items.size(); j++) {
                userPlanItemMapper.insert(fromRouteItem(day.getId(), j + 1, items.get(j), now));
            }
        }
        return plan.getId();
    }

    @Override
    public String aiOptimize(Long userId, Long planId) {
        requireOwnedPlan(userId, planId);
        throw new BizException(3001, "AI 优化能力将于第 3 批开放，敬请期待");
    }

    // ---------------- 私有构建（由 public @Transactional 入口持有事务） ----------------

    /** BR-PLN-01/02 校验（天数/每天条目/日期/预算 在 DTO 校验基础上兜底） */
    private void validateBeforeSave(PlanSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "规划内容不能为空");
        }
        if (dto.getStartDate() != null && dto.getStartDate().isBefore(LocalDate.now())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "出发日期须为今天及以后");
        }
        // 正式保存必须有行程；草稿允许空白（前端边填边存）
        boolean formal = dto.getStatus() != null && dto.getStatus() == 1;
        if (formal && CollectionUtils.isEmpty(dto.getDayList())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "正式保存需至少一天的行程安排");
        }
        if (!CollectionUtils.isEmpty(dto.getDayList()) && dto.getDayList().size() != dto.getDays()) {
            throw new BizException(400, "天数须与行程天列表一致");
        }
        if (formal && !CollectionUtils.isEmpty(dto.getDayList())) {
            for (PlanSaveDTO.DayDTO day : dto.getDayList()) {
                if (CollectionUtils.isEmpty(day.getItems())) {
                    throw new BizException(ResultCode.BAD_REQUEST.getCode(), "每天至少 1 条行程项（BR-PLN-01）");
                }
            }
        }
    }

    /** BR-PLN-04：每人最多 50 条有效规划 */
    private void checkPlanLimit(Long userId) {
        sysUserMapper.selectOne(new LambdaQueryWrapper<com.trip.module.user.entity.SysUser>()
                .eq(com.trip.module.user.entity.SysUser::getId, userId).last("FOR UPDATE"));
        int count = userPlanMapper.selectList(new LambdaQueryWrapper<UserPlan>()
                .eq(UserPlan::getUserId, userId).last("FOR UPDATE")).size();
        if (count >= PLAN_LIMIT) throw new BizException(ResultCode.PLAN_LIMIT);
    }

    /** 归属校验：查不到或他人数据 → 403 */
    private UserPlan requireOwnedPlan(Long userId, Long planId) {
        UserPlan plan = userPlanMapper.selectOne(
                new LambdaQueryWrapper<UserPlan>()
                        .eq(UserPlan::getId, planId)
                        .eq(UserPlan::getUserId, userId));
        if (plan == null) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return plan;
    }

    private UserPlan newPlan(Long userId, PlanSaveDTO dto, Long sourceRouteId) {
        LocalDateTime now = LocalDateTime.now();
        UserPlan plan = new UserPlan();
        plan.setUserId(userId);
        plan.setTitle(dto.getTitle());
        plan.setDestinationIds(joinIds(dto.getDestinationIds()));
        plan.setStartDate(dto.getStartDate());
        plan.setDays(dto.getDays());
        plan.setBudget(dto.getBudget());
        plan.setPeopleNum(dto.getPeopleNum());
        plan.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        plan.setSourceRouteId(sourceRouteId == null ? 0L : sourceRouteId);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        return plan;
    }

    /** 事务内先删后插：删旧 day/item → 按数组顺序重排重建（BR-PLN-03） */
    private void replaceDayList(Long planId, List<PlanSaveDTO.DayDTO> dayList) {
        // 1. 删旧（先 item 后 day）
        List<UserPlanDay> oldDays = userPlanDayMapper.selectList(
                new LambdaQueryWrapper<UserPlanDay>().eq(UserPlanDay::getUserPlanId, planId));
        if (!CollectionUtils.isEmpty(oldDays)) {
            List<Long> oldDayIds = oldDays.stream().map(UserPlanDay::getId).toList();
            userPlanItemMapper.delete(new LambdaQueryWrapper<UserPlanItem>()
                    .in(UserPlanItem::getPlanDayId, oldDayIds));
            userPlanDayMapper.delete(new LambdaQueryWrapper<UserPlanDay>()
                    .eq(UserPlanDay::getUserPlanId, planId));
        }
        if (CollectionUtils.isEmpty(dayList)) {
            return;
        }
        // 2. 重建：dayIndex 按数组下标重排，items sortNo 按顺序 1..n
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < dayList.size(); i++) {
            PlanSaveDTO.DayDTO dayDto = dayList.get(i);
            UserPlanDay day = new UserPlanDay();
            day.setUserPlanId(planId);
            day.setDayIndex(i + 1);
            day.setTitle(dayDto.getTitle());
            day.setSummary(dayDto.getSummary());
            day.setCreateTime(now);
            day.setUpdateTime(now);
            userPlanDayMapper.insert(day);
            List<PlanItemDTO> items = dayDto.getItems();
            if (CollectionUtils.isEmpty(items)) {
                continue;
            }
            for (int j = 0; j < items.size(); j++) {
                userPlanItemMapper.insert(fromItemDTO(day.getId(), j + 1, items.get(j), now));
            }
        }
    }

    private UserPlanItem fromItemDTO(Long dayId, int sortNo, PlanItemDTO dto, LocalDateTime now) {
        UserPlanItem item = new UserPlanItem();
        item.setPlanDayId(dayId);
        item.setSortNo(sortNo);
        item.setTimePoint(dto.getTimePoint());
        item.setAttractionId(dto.getAttractionId());
        item.setTitle(dto.getTitle());
        item.setActivity(dto.getActivity());
        item.setTransport(dto.getTransport());
        item.setHotel(dto.getHotel());
        item.setMeal(dto.getMeal());
        item.setDurationMin(dto.getDurationMin());
        item.setCost(dto.getCost());
        item.setTips(dto.getTips());
        item.setCreateTime(now);
        item.setUpdateTime(now);
        return item;
    }

    private UserPlanItem fromRouteItem(Long dayId, int sortNo, RouteItem r, LocalDateTime now) {
        UserPlanItem item = new UserPlanItem();
        item.setPlanDayId(dayId);
        item.setSortNo(sortNo);
        item.setTimePoint(r.getTimePoint());
        item.setAttractionId(r.getAttractionId());
        item.setTitle(r.getTitle());
        item.setActivity(r.getActivity());
        item.setTransport(r.getTransport());
        item.setHotel(r.getHotel());
        item.setMeal(r.getMeal());
        item.setDurationMin(r.getDurationMin());
        item.setCost(r.getCost());
        item.setTips(r.getTips());
        item.setCreateTime(now);
        item.setUpdateTime(now);
        return item;
    }

    /** 详情用：读 day + item 组装 */
    private List<PlanDayVO> buildDayVOs(Long planId) {
        List<UserPlanDay> days = userPlanDayMapper.selectList(
                new LambdaQueryWrapper<UserPlanDay>()
                        .eq(UserPlanDay::getUserPlanId, planId)
                        .orderByAsc(UserPlanDay::getDayIndex));
        if (CollectionUtils.isEmpty(days)) {
            return Collections.emptyList();
        }
        List<Long> dayIds = days.stream().map(UserPlanDay::getId).toList();
        List<UserPlanItem> items = userPlanItemMapper.selectList(
                new LambdaQueryWrapper<UserPlanItem>()
                        .in(UserPlanItem::getPlanDayId, dayIds)
                        .orderByAsc(UserPlanItem::getSortNo));
        Map<Long, List<UserPlanItem>> itemMap = items.stream()
                .collect(Collectors.groupingBy(UserPlanItem::getPlanDayId));
        return days.stream()
                .map(d -> {
                    PlanDayVO vo = new PlanDayVO();
                    vo.setId(d.getId());
                    vo.setDayIndex(d.getDayIndex());
                    vo.setTitle(d.getTitle());
                    vo.setSummary(d.getSummary());
                    vo.setItems(itemMap.getOrDefault(d.getId(), Collections.emptyList()).stream()
                            .map(this::toItemVO).toList());
                    return vo;
                })
                .toList();
    }

    private PlanItemVO toItemVO(UserPlanItem item) {
        PlanItemVO vo = new PlanItemVO();
        vo.setId(item.getId());
        vo.setSortNo(item.getSortNo());
        vo.setTimePoint(item.getTimePoint());
        vo.setAttractionId(item.getAttractionId());
        vo.setTitle(item.getTitle());
        vo.setActivity(item.getActivity());
        vo.setTransport(item.getTransport());
        vo.setHotel(item.getHotel());
        vo.setMeal(item.getMeal());
        vo.setDurationMin(item.getDurationMin());
        vo.setCost(item.getCost());
        vo.setTips(item.getTips());
        return vo;
    }

    /** 列表用：批量查 dayCount（哪天没填项前端能提示） */
    private List<PlanVO> toVOList(List<UserPlan> plans) {
        List<Long> planIds = plans.stream().map(UserPlan::getId).toList();
        List<UserPlanDay> allDays = userPlanDayMapper.selectList(
                new LambdaQueryWrapper<UserPlanDay>().in(UserPlanDay::getUserPlanId, planIds));
        Map<Long, Long> dayCountMap = allDays.stream()
                .collect(Collectors.groupingBy(UserPlanDay::getUserPlanId, Collectors.counting()));
        return plans.stream()
                .map(p -> {
                    PlanVO vo = toVO(p);
                    vo.setDayCount(dayCountMap.getOrDefault(p.getId(), 0L).intValue());
                    return vo;
                })
                .toList();
    }

    private PlanVO toVO(UserPlan plan) {
        PlanVO vo = new PlanVO();
        vo.setId(plan.getId());
        vo.setTitle(plan.getTitle());
        vo.setDestinationIds(splitIds(plan.getDestinationIds()));
        vo.setStartDate(plan.getStartDate());
        vo.setDays(plan.getDays());
        vo.setBudget(plan.getBudget());
        vo.setPeopleNum(plan.getPeopleNum());
        vo.setStatus(plan.getStatus());
        vo.setSourceRouteId(plan.getSourceRouteId());
        vo.setCreateTime(plan.getCreateTime());
        vo.setUpdateTime(plan.getUpdateTime());
        return vo;
    }

    private String joinIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return "";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> splitIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    /** copy 复用：主表 → 保存 DTO（dayList 由 daysToDTO 提供） */
    private PlanSaveDTO toSaveDTO(UserPlan plan) {
        PlanSaveDTO dto = new PlanSaveDTO();
        dto.setTitle(plan.getTitle());
        dto.setDestinationIds(splitIds(plan.getDestinationIds()));
        dto.setStartDate(plan.getStartDate());
        dto.setDays(plan.getDays());
        dto.setBudget(plan.getBudget());
        dto.setPeopleNum(plan.getPeopleNum());
        dto.setStatus(plan.getStatus());
        return dto;
    }

    /** copy 复用：深拷贝 day + item 为 DTO 数组 */
    private List<PlanSaveDTO.DayDTO> daysToDTO(List<UserPlanDay> days, Long planId) {
        if (CollectionUtils.isEmpty(days)) {
            return Collections.emptyList();
        }
        List<Long> dayIds = days.stream().map(UserPlanDay::getId).toList();
        List<UserPlanItem> items = userPlanItemMapper.selectList(
                new LambdaQueryWrapper<UserPlanItem>()
                        .in(UserPlanItem::getPlanDayId, dayIds)
                        .orderByAsc(UserPlanItem::getSortNo));
        Map<Long, List<UserPlanItem>> itemMap = items.stream()
                .collect(Collectors.groupingBy(UserPlanItem::getPlanDayId));
        return days.stream()
                .map(d -> {
                    PlanSaveDTO.DayDTO dayDto = new PlanSaveDTO.DayDTO();
                    dayDto.setDayIndex(d.getDayIndex());
                    dayDto.setTitle(d.getTitle());
                    dayDto.setSummary(d.getSummary());
                    dayDto.setItems(itemMap.getOrDefault(d.getId(), Collections.emptyList()).stream()
                            .map(i -> {
                                PlanItemDTO itemDto = new PlanItemDTO();
                                itemDto.setSortNo(i.getSortNo());
                                itemDto.setTimePoint(i.getTimePoint());
                                itemDto.setAttractionId(i.getAttractionId());
                                itemDto.setTitle(i.getTitle());
                                itemDto.setActivity(i.getActivity());
                                itemDto.setTransport(i.getTransport());
                                itemDto.setHotel(i.getHotel());
                                itemDto.setMeal(i.getMeal());
                                itemDto.setDurationMin(i.getDurationMin());
                                itemDto.setCost(i.getCost());
                                itemDto.setTips(i.getTips());
                                return itemDto;
                            })
                            .toList());
                    return dayDto;
                })
                .toList();
    }
}