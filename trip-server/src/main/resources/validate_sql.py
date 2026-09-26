#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
data.sql 导入前结构自检（对照 docs/dev/建表对照清单.md 与 08 业务规则）
用法：python validate_sql.py   （与 data.sql 同目录，自动定位）

检查项：
  1. 每条 INSERT 的列数与每行值个数一致
  2. 主键 / 业务唯一键不重复（route_like、route_favorite 幂等键等）
  3. 引用完整性：外键目标存在（route→destination、route_item→attraction 等）
  4. BR-RTE-01：route.days 与 route_day 行数一致，day_index 从 1 连续
  5. BR-RTE-02：route_item sort_no 每天从 1 连续且无重复；单一 uk(day, sort)
  6. 预约：booking_no 唯一；状态 ∈ 0-3；travel_date ≥ 今天
  7. 评论：sentiment ∈ positive/neutral/negative/unknown；score 1-5
  8. 统计：stat_daily 30 天 × 14 键无重复；route_total 恒为 20
  9. 规模抽查：80 景点 / 20 路线 / 79 行程天 / 276 行程项 / 82 预约 /
     143 评论 / 637 行为 / 420 统计 / 10 知识库 / 5 轮播 / 2 洞察 / 6 规划
"""
import os
import re
import sys
from collections import defaultdict
from datetime import date

DATA_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sql", "data.sql")
TODAY = date(2026, 9, 19)

errors = []
warnings = []


def error(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


# ---------------------------------------------------------------------------
# 迷你 SQL 解析（只支持 data.sql 的 INSERT 形态，够用即可）
# ---------------------------------------------------------------------------

def split_top_level(s, delim=","):
    """按顶层逗号切分，单引号字符串（含 '' 转义）视为整体"""
    parts, buf, in_q = [], [], False
    i = 0
    while i < len(s):
        c = s[i]
        if in_q:
            if c == "'":
                if i + 1 < len(s) and s[i + 1] == "'":
                    buf.append("''")
                    i += 2
                    continue
                in_q = False
            buf.append(c)
        elif c == "'":
            in_q = True
            buf.append(c)
        elif c == delim:
            parts.append("".join(buf).strip())
            buf = []
        else:
            buf.append(c)
        i += 1
    parts.append("".join(buf).strip())
    return parts


def strip_quotes(v):
    v = v.strip()
    if v.startswith("'"):
        # 还原 '' → '
        return v[1:-1].replace("''", "'")
    return v


def as_int(v):
    v = strip_quotes(v)
    try:
        return int(v)
    except ValueError:
        return None


def parse_insert_statements(sql):
    """返回 [(table, columns, rows)]，rows 为 [ [value,...], ... ]"""
    statements = []
    for m in re.finditer(r"INSERT\s+INTO\s+(\w+)\s*\(([^)]*)\)\s*VALUES\s*(.*?);", sql, re.S):
        table, colstr, valstr = m.group(1), m.group(2), m.group(3)
        columns = [c.strip() for c in split_top_level(colstr)]
        # 提取顶层圆括号行组
        rows, buf, depth, in_q = [], [], 0, False
        i = 0
        while i < len(valstr):
            c = valstr[i]
            if in_q:
                if c == "'":
                    if i + 1 < len(valstr) and valstr[i + 1] == "'":
                        buf.append("''")
                        i += 2
                        continue
                    in_q = False
                buf.append(c)
            elif c == "'":
                in_q = True
                buf.append(c)
            elif c == "(":
                depth += 1
                if depth == 1:
                    buf = []
                else:
                    buf.append(c)
            elif c == ")":
                depth -= 1
                if depth == 0:
                    rows.append([strip_quotes(x) for x in split_top_level("".join(buf))])
                    buf = []
                else:
                    buf.append(c)
            else:
                buf.append(c)
            i += 1
        statements.append((table, columns, rows))
    return statements


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def main():
    # Windows GBK 控制台兼容：强制 UTF-8 输出
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    with open(DATA_FILE, encoding="utf-8") as f:
        sql = f.read()

    stats = parse_insert_statements(sql)
    tables = {}
    for table, columns, rows in stats:
        if table in tables:
            error(f"表 {table} 出现多次 INSERT")
            continue
        tables[table] = (columns, rows)
        # 1. 列数一致性
        for r in rows:
            if len(r) != len(columns):
                error(f"{table}: 行值个数 {len(r)} != 列数 {len(columns)}: {r[:5]}...")

    def rows_of(name):
        t = tables.get(name)
        return t[1] if t else []

    def col_idx(name, col):
        t = tables.get(name)
        if not t:
            return None
        return t[0].index(col) if col in t[0] else None

    # ---- sys_user ----
    su = rows_of("sys_user")
    ids = [as_int(r[col_idx("sys_user", "id")]) for r in su]
    if len(ids) != len(set(ids)):
        error("sys_user.id 存在重复")
    usernames = [r[col_idx("sys_user", "username")] for r in su]
    if len(usernames) != len(set(usernames)):
        error("sys_user.username 存在重复")
    if len(su) != 21:
        error(f"sys_user 应为 21 条，实际 {len(su)}")
    roles = {r[col_idx("sys_user", "username")]: r[col_idx("sys_user", "role")] for r in su}
    if roles.get("admin") != "ADMIN":
        error("admin 用户 role 应为 ADMIN")

    # ---- user_preference ----
    up = rows_of("user_preference")
    pids = [as_int(r[col_idx("user_preference", "user_id")]) for r in up]
    if len(pids) != len(set(pids)):
        error("user_preference.uk(user_id) 冲突")
    if len(up) != 20:
        error(f"user_preference 应为 20 条，实际 {len(up)}")

    # ---- user_behavior ----
    ub = rows_of("user_behavior")
    if len(ub) != 637:
        error(f"user_behavior 应为 637 条，实际 {len(ub)}")
    for r in ub:
        bt = r[col_idx("user_behavior", "behavior_type")]
        if bt not in ("VIEW", "LIKE", "FAVORITE", "BOOK", "SEARCH"):
            error(f"user_behavior 非法行为类型 {bt}")

    # ---- destination ----
    dest = rows_of("destination")
    dest_ids = set(as_int(r[col_idx("destination", "id")]) for r in dest)
    if len(dest_ids) != 10:
        error(f"destination 应为 10 条，实际 {len(dest)}")
    dest_route_count = {as_int(r[col_idx("destination", "id")]): as_int(r[col_idx("destination", "route_count")]) for r in dest}

    # ---- attraction ----
    attr = rows_of("attraction")
    attr_ids = set(as_int(r[col_idx("attraction", "id")]) for r in attr)
    if len(attr_ids) != 80:
        error(f"attraction 应为 80 条，实际 {len(attr)}")
    attr_dest = defaultdict(int)
    for r in attr:
        did = as_int(r[col_idx("attraction", "destination_id")])
        if did not in dest_ids:
            error(f"attraction 引用不存在的目的地 {did}")
        attr_dest[did] += 1
    for did, n in attr_dest.items():
        if n != 8:
            error(f"目的地 {did} 景点数 {n}，应为 8")

    # ---- route / route_day / route_item（BR-RTE-01/02）----
    routes = rows_of("route")
    route_ids = set(as_int(r[col_idx("route", "id")]) for r in routes)
    if len(route_ids) != 20:
        error(f"route 应为 20 条，实际 {len(routes)}")
    route_days_map = {}
    for r in routes:
        rid = as_int(r[col_idx("route", "id")])
        did = as_int(r[col_idx("route", "destination_id")])
        days = as_int(r[col_idx("route", "days")])
        status = as_int(r[col_idx("route", "status")])
        if did not in dest_ids:
            error(f"route {rid} 引用不存在的目的地 {did}")
        if status not in (0, 1):
            error(f"route {rid} status {status} 非法，只能是 0下架/1上架")
        if dest_route_count.get(did) != None and dest_route_count[did] != sum(1 for x in routes if as_int(x[col_idx("route", "destination_id")]) == did):
            error(f"destination {did} route_count 冗余字段与在架路线数不一致")
        route_days_map[rid] = days

    rdays = rows_of("route_day")
    rd_by_route = defaultdict(list)
    rday_ids = set()
    for r in rdays:
        rid = as_int(r[col_idx("route_day", "route_id")])
        di = as_int(r[col_idx("route_day", "day_index")])
        rdid = as_int(r[col_idx("route_day", "id")])
        if rid not in route_ids:
            error(f"route_day 引用不存在的路线 {rid}")
        if rdid in rday_ids:
            error(f"route_day.id {rdid} 重复")
        rday_ids.add(rdid)
        if di in [d for d, _ in rd_by_route[rid]]:
            error(f"route_day uk(route_id,day_index) 冲突: {rid}-{di}")
        rd_by_route[rid].append((di, rdid))
    if len(rd_by_route) != 20 or sum(len(v) for v in rd_by_route.values()) != 79:
        error(f"route_day 总量应为 79，实际 {sum(len(v) for v in rd_by_route.values())}")
    day_id_of = {}
    for rid, days in route_days_map.items():
        got = rd_by_route.get(rid, [])
        idxs = sorted(i for i, _ in got)
        if idxs != list(range(1, days + 1)):
            error(f"route {rid}: days={days}，route_day 实际 day_index={idxs}（BR-RTE-01 违反）")
        for di, rdid in got:
            day_id_of[(rid, di)] = rdid

    ritems = rows_of("route_item")
    ritem_ids = set()
    sort_by_day = defaultdict(list)
    for r in ritems:
            riid = as_int(r[col_idx("route_item", "id")])
            rdid = as_int(r[col_idx("route_item", "route_day_id")])
            sort = as_int(r[col_idx("route_item", "sort_no")])
            aid = as_int(r[col_idx("route_item", "attraction_id")])
            if riid in ritem_ids:
                error(f"route_item.id {riid} 重复")
            ritem_ids.add(riid)
            if rdid not in rday_ids:
                error(f"route_item 引用不存在的行程天 {rdid}")
            if aid != 0 and aid not in attr_ids:
                error(f"route_item {riid} 引用不存在的景点 {aid}")
            if sort in [s for s, _ in sort_by_day[rdid]]:
                error(f"route_item uk(route_day_id,sort_no) 冲突: day {rdid} sort {sort}（BR-RTE-02 违反）")
            sort_by_day[rdid].append((sort, riid))
    if len(ritems) != 276:
        error(f"route_item 应为 276 条，实际 {len(ritems)}")
    for rdid, lst in sort_by_day.items():
        sorts = sorted(s for s, _ in lst)
        if sorts != list(range(1, len(lst) + 1)):
            error(f"route_item: day {rdid} sort_no 不连续 {sorts}（BR-RTE-02 违反）")

    # ---- 互动 ----
    def check_pair_table(tname):
        rows = rows_of(tname)
        pairs = [(as_int(r[col_idx(tname, "user_id")]), as_int(r[col_idx(tname, "route_id")])) for r in rows]
        if len(pairs) != len(set(pairs)):
            error(f"{tname} uk(user_id, route_id) 冲突")
        for u, rid in pairs:
            if rid not in route_ids:
                error(f"{tname} 引用不存在的路线 {rid}")

    check_pair_table("route_like")
    if len(rows_of("route_like")) != 180:
        error(f"route_like 应为 180 条，实际 {len(rows_of('route_like'))}")
    check_pair_table("route_favorite")
    if len(rows_of("route_favorite")) != 131:
        error(f"route_favorite 应为 131 条，实际 {len(rows_of('route_favorite'))}")

    bk = rows_of("route_booking")
    bk_no = [r[col_idx("route_booking", "booking_no")] for r in bk]
    if len(bk_no) != len(set(bk_no)):
        error("route_booking.booking_no 重复")
    if len(bk) != 82:
        error(f"route_booking 应为 82 条，实际 {len(bk)}")
    for r in bk:
        rid = as_int(r[col_idx("route_booking", "route_id")])
        st = as_int(r[col_idx("route_booking", "status")])
        td = r[col_idx("route_booking", "travel_date")]
        if rid not in route_ids:
            error(f"route_booking 引用不存在的路线 {rid}")
        if st not in (0, 1, 2, 3):
            error(f"route_booking status {st} 非法，应为 0-3")
        y, m, d = map(int, td.split("-"))
        if date(y, m, d) < TODAY:
            error(f"route_booking travel_date {td} 早于当天 {TODAY}")

    rc = rows_of("route_comment")
    if len(rc) != 143:
        error(f"route_comment 应为 143 条，实际 {len(rc)}")
    for r in rc:
        senti = r[col_idx("route_comment", "sentiment")]
        score = as_int(r[col_idx("route_comment", "score")])
        rid = as_int(r[col_idx("route_comment", "route_id")])
        if senti not in ("positive", "neutral", "negative", "unknown"):
            error(f"route_comment sentiment {senti} 非法")
        if score is None or not (1 <= score <= 5):
            error(f"route_comment score {score} 超出 1-5")
        if rid not in route_ids:
            error(f"route_comment 引用不存在的路线 {rid}")

    # ---- 规划（BR-PLN：days 与 user_plan_day 一致）----
    plans = rows_of("user_plan")
    if len(plans) != 6:
        error(f"user_plan 应为 6 条，实际 {len(plans)}")
    plan_days_map = {}
    for r in plans:
        pid = as_int(r[col_idx("user_plan", "id")])
        st = as_int(r[col_idx("user_plan", "status")])
        days = as_int(r[col_idx("user_plan", "days")])
        src = as_int(r[col_idx("user_plan", "source_route_id")])
        if st not in (0, 1):
            error(f"user_plan {pid} status {st} 非法")
        if src not in route_ids:
            error(f"user_plan {pid} source_route_id {src} 不存在")
        plan_days_map[pid] = days
    pdays = rows_of("user_plan_day")
    pd_by_plan = defaultdict(list)
    pd_ids = set()
    for r in pdays:
        pid = as_int(r[col_idx("user_plan_day", "user_plan_id")])
        di = as_int(r[col_idx("user_plan_day", "day_index")])
        pdi = as_int(r[col_idx("user_plan_day", "id")])
        if pid not in plan_days_map:
            error(f"user_plan_day 引用不存在的规划 {pid}")
        if pdi in pd_ids:
            error(f"user_plan_day.id {pdi} 重复")
        pd_ids.add(pdi)
        pd_by_plan[pid].append(di)
    for pid, days in plan_days_map.items():
        idxs = sorted(pd_by_plan.get(pid, []))
        if idxs != list(range(1, days + 1)):
            error(f"user_plan {pid}: days={days} 与 user_plan_day {idxs} 不一致")
    ditem = rows_of("user_plan_item")
    sort_by_pd = defaultdict(list)
    for r in ditem:
        pdi = as_int(r[col_idx("user_plan_item", "plan_day_id")])
        sort = as_int(r[col_idx("user_plan_item", "sort_no")])
        aid = as_int(r[col_idx("user_plan_item", "attraction_id")])
        if pdi not in pd_ids:
            error(f"user_plan_item 引用不存在的规划天 {pdi}")
        if aid != 0 and aid not in attr_ids:
            error(f"user_plan_item 引用不存在的景点 {aid}")
        sort_by_pd[pdi].append(sort)
    for pdi, lst in sort_by_pd.items():
        if sorted(lst) != list(range(1, len(lst) + 1)):
            error(f"user_plan_item: plan_day {pdi} sort_no 不连续")

    # ---- 统计 ----
    sd = rows_of("stat_daily")
    if len(sd) != 420:
        error(f"stat_daily 应为 420 条，实际 {len(sd)}")
    date_keys = defaultdict(set)
    for r in sd:
        d = r[col_idx("stat_daily", "stat_date")]
        k = r[col_idx("stat_daily", "metric_key")]
        if k in date_keys[d]:
            error(f"stat_daily uk(stat_date,metric_key) 冲突: {d}-{k}")
        date_keys[d].add(k)
    if len(date_keys) != 30:
        error(f"stat_daily 应覆盖 30 天，实际 {len(date_keys)} 天")
    for d, keys in date_keys.items():
        if len(keys) != 14:
            error(f"stat_daily {d} 只有 {len(keys)} 个指标键，应为 14")
    metric_idx = col_idx("stat_daily", "metric_key")
    val_idx = col_idx("stat_daily", "metric_value")
    for r in sd:
        if r[metric_idx] == "route_total" and as_int(r[val_idx]) != 20:
            error(f"stat_daily {r[col_idx('stat_daily','stat_date')]} route_total 应为 20")

    # ---- 知识库 / 轮播 / 洞察 ----
    if len(rows_of("knowledge_doc")) != 10:
        error(f"knowledge_doc 应为 10 条，实际 {len(rows_of('knowledge_doc'))}")
    if len(rows_of("banner")) != 5:
        error(f"banner 应为 5 条，实际 {len(rows_of('banner'))}")
    ai = rows_of("ai_insight")
    if len(ai) != 2:
        error(f"ai_insight 应为 2 条，实际 {len(ai)}")
    adates = [r[col_idx("ai_insight", "insight_date")] for r in ai]
    if len(adates) != len(set(adates)):
        error("ai_insight.uk(insight_date) 冲突")

    # ---- 输出 ----
    print(f"检查文件：{DATA_FILE}")
    print(f"解析到 {len(tables)} 张表：{', '.join(sorted(tables))}")
    print(f"数据行数合计：{sum(len(v[1]) for v in tables.values())}")
    print("-" * 60)
    if errors:
        print(f"❌ 发现 {len(errors)} 个错误：")
        for e in errors:
            print("  - " + e)
        sys.exit(1)
    if warnings:
        print(f"⚠️  {len(warnings)} 个警告：")
        for wmsg in warnings:
            print("  - " + wmsg)
    print("✅ 结构自检通过：唯一键、引用完整性、BR 规则全部符合")


if __name__ == "__main__":
    main()