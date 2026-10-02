package com.trip.module.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trip.common.exception.BizException;
import com.trip.module.user.entity.SysUser;
import com.trip.module.user.mapper.SysUserMapper;
import com.trip.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserSettingsService {
    private static final Set<String> PROFILE = Set.of("nickname", "phone", "email", "city", "avatar");
    private static final Set<String> PREFERENCE = Set.of("preferenceTags", "avoidTags", "budgetMin", "budgetMax", "preferredDays", "companions", "pace");
    private static final Set<String> TAGS = Set.copyOf(com.trip.common.TravelTags.ALL);
    private final SysUserMapper users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public record Preference(List<String> preferenceTags, List<String> avoidTags, BigDecimal budgetMin,
                             BigDecimal budgetMax, Integer preferredDays, String companions, String pace) {}

    public UserVO profile(Long id) { return UserVO.from(user(id)); }

    @Transactional
    public UserVO updateProfile(Long id, Map<String,Object> body) {
        fields(body, PROFILE, false);
        // Locking avoids lost updates when two tabs submit different profile fields.
        jdbc.queryForObject("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE", Long.class, id);
        SysUser current = user(id);
        String nickname = body.containsKey("nickname") ? string(body, "nickname", 50, false) : current.getNickname();
        String city = body.containsKey("city") ? string(body, "city", 50, true) : current.getCity();
        String avatar = body.containsKey("avatar") ? string(body, "avatar", 255, true) : current.getAvatar();
        String phone = body.containsKey("phone") ? string(body, "phone", 20, true) : current.getPhone();
        String email = body.containsKey("email") ? string(body, "email", 100, true) : current.getEmail();
        if (!Objects.equals(blankToNull(email), blankToNull(current.getEmail())))
            throw bad("请通过邮箱验证功能更换邮箱");
        if (phone != null && !phone.isEmpty() && !phone.matches("1[3-9][0-9]{9}")) throw bad("手机号格式不正确");
        if (email != null && !email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw bad("邮箱格式不正确");
        if (avatar != null && !safeAvatar(avatar)) throw bad("头像必须为本站上传路径或 http/https 图片地址");
        try {
            // Mapper writes invalidate the transaction's MyBatis local read cache.
            // Explicit sets also preserve the ability to clear nullable profile fields.
            users.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, id)
                    .set(SysUser::getNickname, nickname).set(SysUser::getCity, city)
                    .set(SysUser::getAvatar, avatar).set(SysUser::getPhone, blankToNull(phone))
                    .set(SysUser::getEmail, blankToNull(email)));
        } catch (DuplicateKeyException e) { throw new BizException(409, "手机号或邮箱已被使用"); }
        return profile(id);
    }

    @Transactional
    public void password(Long id, Map<String,Object> body) {
        fields(body, Set.of("oldPassword", "newPassword"), true);
        String oldPassword = rawString(body, "oldPassword", 100);
        String newPassword = rawString(body, "newPassword", 20);
        if (newPassword.length() < 8 || newPassword.chars().noneMatch(Character::isLetter)
                || newPassword.chars().noneMatch(Character::isDigit)) throw bad("新密码须为 8-20 位并同时包含字母与数字");
        SysUser current = user(id);
        if (!passwords.matches(oldPassword, current.getPassword())) throw bad("原密码不正确");
        if (oldPassword.equals(newPassword)) throw bad("新密码不能与原密码相同");
        int changed = jdbc.update("UPDATE sys_user SET password=? WHERE id=? AND password=? AND status=1 AND deleted=0",
                passwords.encode(newPassword), id, current.getPassword());
        if (changed != 1) throw new BizException(409, "账号已发生变化，请重新登录后重试");
    }

    public Preference preference(Long id) {
        var rows = jdbc.query("SELECT preference_tags,avoid_tags,budget_min,budget_max,preferred_days,companions,pace FROM user_preference WHERE user_id=?",
                (rs,n) -> new Preference(split(rs.getString(1)), split(rs.getString(2)), rs.getBigDecimal(3), rs.getBigDecimal(4),
                        (Integer)rs.getObject(5), rs.getString(6), rs.getString(7)), id);
        return rows.isEmpty() ? new Preference(List.of(),List.of(),BigDecimal.ZERO,BigDecimal.ZERO,null,"","") : rows.get(0);
    }

    public Preference updatePreference(Long id, Map<String,Object> body) {
        fields(body, PREFERENCE, true);
        List<String> preferred = tags(body.get("preferenceTags"), 8), avoid = tags(body.get("avoidTags"), 5);
        if (!Collections.disjoint(preferred, avoid)) throw bad("偏好标签与避雷标签不能重叠");
        BigDecimal min = decimal(body.get("budgetMin")), max = decimal(body.get("budgetMax"));
        if (!(min.signum() == 0 && max.signum() == 0) && min.compareTo(max) >= 0) throw bad("预算下限必须小于上限，或均为 0 表示未设置");
        Object value = body.get("preferredDays");
        Integer days = null;
        if (value != null) {
            if (!(value instanceof Integer) || (Integer)value < 1 || (Integer)value > 30) throw bad("常用天数必须为 1-30 的整数或 null");
            days = (Integer)value;
        }
        String companions = string(body,"companions",20,true), pace = string(body,"pace",20,true);
        if (!Set.of("", "single", "couple", "family", "group").contains(companions)) throw bad("同行人类型不正确");
        if (!Set.of("", "relaxed", "normal", "intense").contains(pace)) throw bad("旅行节奏不正确");
        jdbc.update("INSERT INTO user_preference(user_id,preference_tags,avoid_tags,budget_min,budget_max,preferred_days,companions,pace) VALUES(?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE preference_tags=VALUES(preference_tags),avoid_tags=VALUES(avoid_tags),budget_min=VALUES(budget_min),budget_max=VALUES(budget_max),preferred_days=VALUES(preferred_days),companions=VALUES(companions),pace=VALUES(pace)",
                id, String.join(",", preferred), String.join(",", avoid), min, max, days, companions, pace);
        return preference(id);
    }

    public Map<String,Long> stats(Long id) {
        Map<String,Long> counts = new LinkedHashMap<>();
        counts.put("favoriteCount", count("route_favorite", "", id));
        counts.put("bookingCount", count("route_booking", "", id));
        counts.put("commentCount", count("route_comment", " AND deleted=0", id));
        counts.put("planCount", count("user_plan", " AND deleted=0", id));
        counts.put("chatCount", count("llm_chat_session", " AND deleted=0", id));
        return counts;
    }
    private Long count(String table, String suffix, Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id=?" + suffix, Long.class, id);
    }
    private SysUser user(Long id) {
        SysUser user = users.selectById(id);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BizException(401, "请重新登录");
        return user;
    }
    private static void fields(Map<String,Object> body, Set<String> allowed, boolean required) {
        if (body.isEmpty() || !allowed.containsAll(body.keySet()) || (required && !body.keySet().equals(allowed))) throw bad("请求字段缺失或包含不支持的字段");
    }
    private static String rawString(Map<String,Object> body, String key, int max) {
        if (!(body.get(key) instanceof String text) || text.isBlank() || text.length() > max) throw bad(key + " 格式或长度不正确");
        return text;
    }
    private static String string(Map<String,Object> body, String key, int max, boolean empty) {
        if (!(body.get(key) instanceof String text) || text.length() > max || (!empty && text.isBlank())) throw bad(key + " 格式或长度不正确");
        return text.trim();
    }
    static boolean safeAvatar(String value) {
        if (value.isEmpty()) return true;
        // Match the names generated and served by FileController (hyphenless UUID).
        if (value.matches("/api/files/[a-f0-9]{32}\\.(png|jpg)")) return true;
        try {
            URI uri = URI.create(value);
            return Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception e) { return false; }
    }
    private static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : List.of(value.split(",")); }
    private static List<String> tags(Object value, int max) {
        if (!(value instanceof List<?> list) || list.size() > max) throw bad("标签必须为数组且数量不能超限");
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String tag) || !TAGS.contains(tag) || result.contains(tag)) throw bad("标签不支持或重复");
            result.add(tag);
        }
        return result;
    }
    private static BigDecimal decimal(Object value) {
        if (!(value instanceof Number)) throw bad("预算必须为数字");
        BigDecimal number;
        try { number = new BigDecimal(value.toString()); } catch (Exception e) { throw bad("预算格式不正确"); }
        if (number.signum() < 0 || number.compareTo(new BigDecimal("99999999.99")) > 0 || number.stripTrailingZeros().scale() > 2) throw bad("预算范围为 0-99999999.99，最多两位小数");
        return number;
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static BizException bad(String message) { return new BizException(400, message); }
}
