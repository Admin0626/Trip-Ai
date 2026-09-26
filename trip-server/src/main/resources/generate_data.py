#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
演示数据生成器 → data.sql
依据：docs/README.md 第四节、docs/05-数据库设计.md §1.5、docs/10-部署手册.md 第二节
用法：python generate_data.py   （重新生成 data.sql，随机种子固定，结果可复现）
       改 GENERATED 日期后重跑即可

生成规模（README 口径）：
  21 用户（1 admin + 20 user） / 10 目的地 / 80 景点 / 20 路线 / 79 行程天 / 276 行程项
  180 点赞 / 131 收藏 / 82 预约 / 143 评论 / 6 规划（含 1 草稿）/ 637 行为埋点
  420 条统计指标（30 天 × 14 键）/ 10 篇知识库文档 / 5 条轮播图 / 2 天 AI 洞察
"""

import os
import random
from datetime import date, timedelta

random.seed(20260919)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sql", "data.sql")
TODAY = date(2026, 9, 19)
YESTERDAY = TODAY - timedelta(days=1)
# 统计窗口：近 30 个自然日（不含今天）
STAT_END = YESTERDAY
STAT_START = STAT_END - timedelta(days=29)

BCRYPT = "$2a$10$yfpXWGBpIMYigYp0/vVw4.gZ1VjQY0RS3ojT4gzPT5owHKoDYS0aK"  # 明文 123456，已实测校验通过

lines = []
def w(s=""):
    lines.append(s)

def sq(s):
    """SQL 字符串转义"""
    return "'" + str(s).replace("'", "''") + "'"

def dt(d: date, hhmmss="10:00:00"):
    return f"{d.isoformat()} {hhmmss}"

# ----------------------------------------------------------------------
# 一、基础数据定义
# ----------------------------------------------------------------------

DESTINATIONS = [
    # id, name, province, city, lng, lat, tags, best_season, avg_cost, intro
    (1, "云南", "云南省", "昆明市", 102.712251, 25.040609, "自然风光,古城,民族风情", "全年,3-5月最佳", 3000, "七彩云南，多民族文化交融的高原度假胜地，昆明是全省门户。"),
    (2, "大理", "云南省", "大理白族自治州", 100.267638, 25.606486, "自然风光,古城,慢生活", "3-5月、9-11月", 2500, "苍山洱海之间的慢生活之城，白族风情浓郁。"),
    (3, "丽江", "云南省", "丽江市", 100.229628, 26.875351, "古城,摄影,民族风情", "全年", 2800, "世界文化遗产丽江古城，玉龙雪山与纳西文化的交汇地。"),
    (4, "香格里拉", "云南省", "迪庆藏族自治州", 99.700836, 27.826853, "高原,摄影,探险", "6-10月", 3200, "心中的日月，普达措与松赞林寺的藏地秘境。"),
    (5, "三亚", "海南省", "三亚市", 109.511909, 18.252847, "海边,度假,亲子", "10月-次年3月", 4000, "热带海滨度假天堂，阳光沙滩与潜水胜地。"),
    (6, "张家界", "湖南省", "张家界市", 110.479191, 29.117096, "自然风光,摄影,探险", "4-6月、9-11月", 2200, "三千奇峰八百秀水，武陵源的峰林地貌举世无双。"),
    (7, "桂林", "广西壮族自治区", "桂林市", 110.290194, 25.273566, "自然风光,摄影,休闲", "4-11月", 2600, "桂林山水甲天下，漓江竹筏与阳朔西街。"),
    (8, "成都", "四川省", "成都市", 104.066541, 30.572269, "美食,历史文化,慢生活", "3-6月、9-11月", 2300, "天府之国，熊猫基地、宽窄巷子与麻辣火锅。"),
    (9, "西安", "陕西省", "西安市", 108.939838, 34.341568, "历史文化,美食,亲子", "3-5月、9-11月", 2400, "十三朝古都，兵马俑、城墙与回民街小吃。"),
    (10, "厦门", "福建省", "厦门市", 118.089425, 24.479833, "海边,慢生活,文艺", "3-5月、10-12月", 2700, "海上花园，鼓浪屿的文艺气息与环岛路骑行。"),
]

# 每个目的地 8 个景点（共 80 个），attraction_id 从 101 起
ATTRACTION_POOL = {
    1: ["滇池", "云南民族村", "石林风景名胜区", "翠湖公园", "大观楼", "西山龙门", "滇越铁路", "官渡古镇"],
    2: ["大理古城", "洱海", "崇圣寺三塔", "双廊古镇", "喜洲古镇", "苍山", "蝴蝶泉", "小普陀"],
    3: ["丽江古城", "玉龙雪山", "束河古镇", "蓝月谷", "拉市海", "虎跳峡", "白沙古镇", "泸沽湖"],
    4: ["松赞林寺", "普达措国家公园", "独克宗古城", "纳帕海", "梅里雪山", "巴拉格宗", "白水台", "依拉草原"],
    5: ["亚龙湾", "蜈支洲岛", "天涯海角", "南山文化旅游区", "大东海", "鹿回头", "西岛", "海棠湾免税城"],
    6: ["张家界国家森林公园", "天门山", "玻璃栈道", "黄龙洞", "大峡谷", "金鞭溪", "天子山", "宝峰湖"],
    7: ["漓江", "阳朔西街", "象鼻山", "龙脊梯田", "银子岩", "遇龙河", "兴坪古镇", "两江四湖"],
    8: ["成都大熊猫基地", "宽窄巷子", "锦里", "武侯祠", "都江堰", "青城山", "春熙路", "人民公园"],
    9: ["秦始皇兵马俑", "西安城墙", "大雁塔", "回民街", "钟鼓楼", "陕西历史博物馆", "华清宫", "大唐不夜城"],
    10:["鼓浪屿", "厦门大学", "环岛路", "南普陀寺", "曾厝垵", "沙坡尾", "白城沙滩", "中山路步行街"],
}

ROUTES = [
    # (id, dest_id, title, subtitle, days, price, difficulty, tags, highlights, notice)
    (201, 1, "云南大理丽江香格里拉 5 日纯玩", "苍山洱海 · 玉龙雪山 · 普达措", 5, 2980, 2, "自然风光,摄影,深度游", "1.洱海环湖骑行\n2.玉龙雪山冰川公园\n3.普达措国家公园", "高原地区注意防晒与保暖，行程节奏适中。"),
    (202, 1, "云南西双版纳 4 日雨林探秘", "热带雨林 · 野象谷 · 傣族风情", 4, 2680, 1, "亲子,探险,美食", "1.野象谷近距离观察亚洲象\n2.热带植物园科普", "气候湿热，注意防蚊虫。"),
    (203, 2, "大理洱海环湖 3 日慢行", "古城漫步 · 环湖骑行 · 双廊日落", 3, 1680, 1, "慢生活,自然风光,摄影", "1.古城人民路闲逛\n2.洱海西岸环湖骑行\n3.双廊看日落", "骑行注意防晒，湖边风大。"),
    (204, 2, "大理苍山洱海 6 日深度游", "苍山洗马潭 · 洱海东岸 · 喜洲古镇", 6, 3980, 3, "自然风光,历史文化,徒步", "1.苍山索道上洗马潭\n2.喜洲白族民居\n3.挖色码头日落", "苍山海拔较高，量力而行。"),
    (205, 3, "丽江古城玉龙雪山 4 日", "古城 · 雪山 · 蓝月谷", 4, 2980, 2, "古城,摄影,自然风光", "1.大研古城夜游\n2.玉龙雪山冰川大索道\n3.蓝月谷", "雪山索道需提前预订，高原反应注意预防。"),
    (206, 3, "丽江泸沽湖 3 日环湖之旅", "摩梭风情 · 环湖 · 星空", 3, 2380, 1, "民族风情,摄影,慢生活", "1.泸沽湖环湖\n2.里格半岛观景台\n3.摩梭家访", "丽江往返泸沽湖车程约 4 小时。"),
    (207, 4, "香格里拉 4 日藏地秘境", "普达措 · 松赞林寺 · 独克宗", 4, 3280, 3, "高原,摄影,历史文化", "1.普达措国家公园\n2.松赞林寺\n3.独克宗古城转动大转经筒", "高海拔注意高原反应，建议提前一周服用红景天。"),
    (208, 4, "香格里拉梅里雪山 4 日朝圣", "日照金山 · 飞来寺 · 雨崩徒步", 4, 4580, 4, "高原,徒步,摄影", "1.飞来寺观日照金山\n2.雨崩村徒步", "徒步强度较高，需良好体力。"),
    (209, 5, "三亚亚龙湾 4 日亲子度假", "亚龙湾 · 蜈支洲岛 · 免税城", 4, 3880, 1, "亲子,海边,度假", "1.亚龙湾沙滩\n2.蜈支洲岛潜水\n3.海棠湾免税购物", "潜水项目根据身体状况选择。"),
    (210, 5, "三亚 4 日浪漫海岛游", "天涯海角 · 鹿回头 · 西岛", 4, 4280, 1, "海边,情侣,度假", "1.西岛浮潜\n2.鹿回头山顶看城市夜景\n3.天涯海角打卡", "防晒霜必备，紫外线强。"),
    (211, 6, "张家界武陵源 3 日奇峰之旅", "袁家界 · 金鞭溪 · 天门山", 3, 1980, 3, "自然风光,摄影,探险", "1.袁家界看哈利路亚山\n2.金鞭溪徒步\n3.天门山玻璃栈道", "景区台阶多，穿舒适运动鞋。"),
    (212, 6, "张家界大峡谷 4 日全景游", "玻璃桥 · 黄龙洞 · 天子山", 4, 2580, 2, "探险,自然风光,亲子", "1.大峡谷玻璃桥\n2.黄龙洞探洞\n3.天子山缆车", "玻璃桥恐高者慎选。"),
    (213, 7, "桂林漓江阳朔 4 日山水游", "漓江竹筏 · 龙脊梯田 · 西街", 4, 2680, 1, "自然风光,摄影,休闲", "1.漓江精华段竹筏\n2.龙脊梯田观日出\n3.阳朔西街夜游", "雨季漓江水可能浑浊，出行前查询水位。"),
    (214, 7, "桂林阳朔 3 日轻度假", "遇龙河漂流 · 兴坪古镇", 3, 1880, 1, "慢生活,自然风光,美食", "1.遇龙河竹筏漂流\n2.兴坪 20 元人民币背景\n3.桂林米粉", "竹筏漂流注意电子设备防水。"),
    (215, 8, "成都美食文化 5 日深度游", "熊猫基地 · 宽窄巷子 · 都江堰", 5, 2980, 1, "美食,历史文化,亲子", "1.大熊猫基地看滚滚\n2.宽窄巷子小吃\n3.都江堰水利工程", "火锅需量力而行，肠胃敏感者准备常用药。"),
    (216, 8, "成都青城山 4 日休闲之旅", "青城山问道 · 都江堰 · 人民公园", 4, 2280, 1, "慢生活,历史文化,自然风光", "1.青城山前山问道\n2.人民公园盖碗茶\n3.锦里夜游", "青城山索道旺季排队时间较长。"),
    (217, 9, "西安古都 4 日历史文化之旅", "兵马俑 · 城墙 · 大雁塔", 4, 2680, 2, "历史文化,亲子,美食", "1.兵马俑一号坑\n2.城墙骑行\n3.大唐不夜城", "兵马俑建议请讲解，否则只能看个热闹。"),
    (218, 9, "西安回民街 3 日美食之旅", "回民街 · 钟鼓楼 · 华清宫", 3, 1980, 1, "美食,历史文化", "1.回民街小吃巡礼\n2.华清宫与骊山\n3.大雁塔音乐喷泉", "回民街人流量大，看管好随身物品。"),
    (219, 10, "厦门鼓浪屿 4 日文艺之旅", "鼓浪屿 · 厦门大学 · 环岛路", 4, 2780, 1, "海边,慢生活,文艺", "1.鼓浪屿万国建筑\n2.环岛路骑行\n3.沙坡尾日落", "鼓浪屿轮渡需提前购票，节假日限流。"),
    (220, 10, "厦门 4 日海岛慢生活", "曾厝垵 · 南普陀 · 白城沙滩", 4, 3180, 1, "慢生活,海边,美食", "1.曾厝垵民宿体验\n2.南普陀寺祈福\n3.中山路美食", "环岛路骑行注意防晒。"),
]

# ----------------------------------------------------------------------
# 二、生成器
# ----------------------------------------------------------------------

w(f"-- =====================================================================")
w(f"-- 演示数据（生成产物，勿手改；改参数后重跑 generate_data.py）")
w(f"-- 生成时间：{TODAY.isoformat()} ｜ 随机种子：20260919")
w(f"-- 演示账号：admin / 123456（ADMIN）、user1001~user1020 / 123456（USER）")
w(f"-- =====================================================================")
w()
w("USE trip_llm;")
w()
w("SET NAMES utf8mb4;")
w()

# ---------- 1. 用户 21（1 admin + 20 user） ----------
w("-- 1) sys_user / user_preference")
w("INSERT INTO sys_user (id, username, password, nickname, avatar, phone, email, city, role, status, last_login_time, create_time, update_time, deleted) VALUES")
admin_row = f"(1, 'admin', {sq(BCRYPT)}, '系统管理员', '', '13800000001', 'admin@trip.ai', '北京', 'ADMIN', 1, {sq(dt(YESTERDAY))}, '2026-08-01 00:00:00', '2026-08-01 00:00:00', 0)"
w(" " + admin_row + ",")
for i in range(1, 21):
    uid = 1000 + i
    city = ["杭州", "上海", "深圳", "成都", "武汉", "南京", "广州", "重庆", "西安", "长沙",
            "厦门", "青岛", "郑州", "苏州", "天津", "昆明", "合肥", "福州", "南昌", "贵阳"][i - 1]
    nick = f"旅行者{i}"
    comma = "," if i < 20 else ""
    last = dt(TODAY - timedelta(days=random.randint(0, 10)))
    reg = dt(TODAY - timedelta(days=random.randint(20, 60)))
    w(f"(100{i}, 'user100{i}', {sq(BCRYPT)}, {sq(nick)}, '', '13900{i:04d}000', NULL, {sq(city)}, 'USER', 1, {sq(last)}, {sq(reg)}, {sq(reg)}, 0){comma}")
w()

# 20 个普通用户各 1 条偏好（冷启动数据源）
w("INSERT INTO user_preference (user_id, preference_tags, avoid_tags, budget_min, budget_max, preferred_days, companions, pace, create_time, update_time) VALUES")
TAG_POOL = ["自然风光", "历史文化", "美食", "亲子", "摄影", "探险", "古城", "慢生活", "海边", "徒步", "温泉", "滑雪", "夜游", "露营", "民俗", "购物"]
for i in range(1, 21):
    uid = 1000 + i
    n = random.randint(3, 8)
    pref = ",".join(random.sample(TAG_POOL, n))
    avoid_n = random.randint(0, 2)
    avoid = ",".join(random.sample(TAG_POOL, avoid_n)) if avoid_n else ""
    lo = random.choice([1000, 2000, 2500, 3000])
    hi = lo + random.choice([1000, 2000])
    days = random.choice([3, 4, 5, 5, 6, 7])
    comp = random.choice(["single", "couple", "family", "group"])
    pace = random.choice(["relaxed", "normal", "normal", "intense"])
    comma = "," if i < 20 else ""
    w(f"({uid}, {sq(pref)}, {sq(avoid)}, {lo}, {hi}, {days}, {sq(comp)}, {sq(pace)}, '2026-08-05 00:00:00', '2026-08-05 00:00:00'){comma}")
w()

# ---------- 2. 目的地 10 ----------
w("-- 2) destination")
w("INSERT INTO destination (id, name, province, city, longitude, latitude, cover_img, intro, tags, best_season, avg_cost, heat, route_count, status, create_time, update_time, deleted) VALUES")
for i, (did, name, prov, city, lng, lat, tags, season, cost, intro) in enumerate(DESTINATIONS):
    comma = "," if i < 9 else ""
    heat = random.randint(5000, 12000)
    rc = sum(1 for r in ROUTES if r[1] == did)
    w(f"({did}, {sq(name)}, {sq(prov)}, {sq(city)}, {lng}, {lat}, {sq('https://oss.example.com/trip/dest/' + str(did) + '.jpg')}, {sq(intro)}, {sq(tags)}, {sq(season)}, {cost}, {heat}, {rc}, 1, '2026-08-01 00:00:00', '2026-08-01 00:00:00', 0){comma}")
w()

# ---------- 3. 景点 80 ----------
w("-- 3) attraction（80 个，每目的地 8 个）")
w("INSERT INTO attraction (id, destination_id, name, cover_img, intro, address, longitude, latitude, ticket_price, open_time, duration_min, tags, status, create_time, update_time, deleted) VALUES")
aid = 100
rows_attr = []
for did, names in ATTRACTION_POOL.items():
    dest = next(d for d in DESTINATIONS if d[0] == did)
    for name in names:
        aid += 1
        lng = dest[4] + random.uniform(-0.5, 0.5)
        lat = dest[5] + random.uniform(-0.4, 0.4)
        price = random.choice([0, 0, 20, 40, 60, 80, 120, 200])
        dur = random.choice([60, 90, 120, 150, 180, 240])
        tags = ",".join(random.sample(TAG_POOL, 2))
        rows_attr.append(f"({aid}, {did}, {sq(name)}, {sq('https://oss.example.com/trip/attr/' + str(aid) + '.jpg')}, {sq(name + ' 游玩攻略与贴士。')}, {sq(dest[3] + ' ' + name)}, {lng:.6f}, {lat:.6f}, {price}, '08:30-17:30', {dur}, {sq(tags)}, 1, '2026-08-02 00:00:00', '2026-08-02 00:00:00', 0)")
for r in rows_attr:
    w(" " + r + ("" if r == rows_attr[-1] else ","))
w()

# ---------- 4. 路线 20 + 行程天 79 + 行程项 276 ----------
w("-- 4) route")
w("INSERT INTO route (id, title, subtitle, cover_img, destination_id, days, price, difficulty, tags, highlights, notice, like_count, favorite_count, booking_count, comment_count, view_count, avg_score, recommend_weight, is_top, quota_per_day, status, create_by, create_time, update_time, deleted) VALUES")
for i, (rid, did, title, sub, days, price, diff, tags, hl, notice) in enumerate(ROUTES):
    comma = "," if i < 19 else ""
    is_top = 1 if rid in (201, 215) else 0
    avg = round(random.uniform(4.2, 4.9), 2)
    w(f"({rid}, {sq(title)}, {sq(sub)}, {sq('https://oss.example.com/trip/route/' + str(rid) + '.jpg')}, {did}, {days}, {price}, {diff}, {sq(tags)}, {sq(hl)}, {sq(notice)}, 0, 0, 0, 0, 0, {avg}, 0.5, {is_top}, 20, 1, 1, '2026-08-03 00:00:00', '2026-08-03 00:00:00', 0){comma}")
w()

# 行程天（共 79），目标：每路线 days 与 route_day 条数一致（BR-RTE-01）
w("-- 5) route_day")
day_id = 0
day_specs = []   # (route_day_id, route_id, day_index)
for rid, did, title, sub, days, price, diff, tags, hl, notice in ROUTES:
    for d in range(1, days + 1):
        day_id += 1
        day_specs.append((day_id, rid, d))
w("INSERT INTO route_day (id, route_id, day_index, title, summary, create_time, update_time) VALUES")
DAY_TITLES = ["抵达 · 初到目的地", "核心景点深度游", "环线游览", "沉浸体验日", "周边探索", "返程"]
DAY_SUMM = ["抵达后入住酒店，周边闲逛适应环境。", "全天游览核心景点，合理安排交通。", "环线串联多个景点，注意预留交通时间。", "深度体验当地文化与美食。", "探索周边小众目的地。", "上午自由活动，下午返程。"]
for i, (rdid, rid, d) in enumerate(day_specs):
    comma = "," if i < len(day_specs) - 1 else ""
    t = ("抵达 · " + ROUTES[rid - 201][2][:6]) if d == 1 else (DAY_TITLES[min(d - 1, 5)] + ("（第 " + str(d) + " 天）" if d > 2 else ""))
    w(f"({rdid}, {rid}, {d}, {sq(t)}, {sq(DAY_SUMM[min(d - 1, 5)])}, '2026-08-03 00:00:00', '2026-08-03 00:00:00'){comma}")
w()

# 行程项（共 276）：每行程天 3 条起，剩余 39 条随机追加到部分天（单天 ≤ 6）
w("-- 6) route_item（总 276 条）")
item_counts = [3] * len(day_specs)
extra_day_indexes = random.sample(range(len(day_specs)), 39)
for idx in extra_day_indexes:
    item_counts[idx] += 1

# 景点池按目的地映射，行程项优先取自该路线目的地景点
item_id = 0
w("INSERT INTO route_item (id, route_day_id, sort_no, time_point, attraction_id, title, activity, transport, hotel, meal, duration_min, cost, tips, create_time, update_time) VALUES")
ACTIVITIES = ["景区深度游览", "徒步观光", "乘船游览", "美食探店", "骑行体验", "古街漫步", "索道观景", "自由活动与休整", "摄影打卡", "文化体验"]
TRANSPORTS = ["步行", "景区大巴", "包车", "游船", "骑行", "索道", "公交"]
HOTELS = ["市区精品酒店", "古镇客栈", "海景度假酒店", "景区内民宿", "商务酒店", "青年旅舍"]
MEALS = ["酒店自助早餐", "当地特色晚餐", "网红餐厅午餐", "街边小吃", "团队桌餐", "海边烧烤"]
TIPS = ["注意防晒补水", "提前网上购票免排队", "穿舒适运动鞋", "携带身份证件", "傍晚光线最适合拍照", "步行路段较多", "建议错峰出行", "当地特产可作伴手礼"]
all_rows = []
for si, (rdid, rid, d) in enumerate(day_specs):
    dest_id = ROUTES[rid - 201][1]
    attr_ids = [a for a in range(100 + (dest_id - 1) * 8 + 1, 100 + dest_id * 8 + 1)]
    for k in range(item_counts[si]):
        item_id += 1
        hour = 8 + k * 2
        tpoint = f"{hour:02d}:00"
        chosen_attr = attr_ids[k % len(attr_ids)]
        attr_name = ATTRACTION_POOL[dest_id][k % len(ATTRACTION_POOL[dest_id])]
        activity = ACTIVITIES[(si + k) % len(ACTIVITIES)]
        cost = random.choice([0, 0, 20, 40, 60, 80, 100, 150, 200])
        dur = random.choice([60, 90, 120, 150, 180, 240])
        comma = "," if item_id < 276 else ""
        all_rows.append(f"({item_id}, {rdid}, {k + 1}, {sq(tpoint)}, {chosen_attr}, {sq(attr_name)}, {sq(activity)}, {sq(TRANSPORTS[(k) % len(TRANSPORTS)])}, {sq(HOTELS[(si) % len(HOTELS)])}, {sq(MEALS[(k) % len(MEALS)])}, {dur}, {cost}, {sq(TIPS[(si + k) % len(TIPS)])}, '2026-08-03 00:00:00', '2026-08-03 00:00:00')")
for r in all_rows:
    w(" " + r + ("" if r == all_rows[-1] else ","))
w()

# ---------- 5. 互动：点赞 180 / 收藏 131 / 预约 82 / 评论 143 ----------
users = list(range(1001, 1021))
route_ids = [r[0] for r in ROUTES]

def unique_pairs(n, us, rs):
    pairs = set()
    while len(pairs) < n:
        pairs.add((random.choice(us), random.choice(rs)))
    return list(pairs)

w("-- 7) route_like（180 条）")
like_pairs = unique_pairs(180, users, route_ids)
w("INSERT INTO route_like (user_id, route_id, create_time) VALUES")
for i, (u, r) in enumerate(like_pairs):
    comma = "," if i < 179 else ""
    w(f"({u}, {r}, {sq(dt(TODAY - timedelta(days=random.randint(1, 60))))}){comma}")
w()

w("-- 8) route_favorite（131 条）")
fav_pairs = unique_pairs(131, users, route_ids)
w("INSERT INTO route_favorite (user_id, route_id, create_time) VALUES")
for i, (u, r) in enumerate(fav_pairs):
    comma = "," if i < 130 else ""
    w(f"({u}, {r}, {sq(dt(TODAY - timedelta(days=random.randint(1, 60))))}){comma}")
w()

w("-- 9) route_booking（82 条）")
booking_rows = []
booking_no_seq = [0]
def gen_booking_no(d: date):
    booking_no_seq[0] += 1
    return f"BK{d.strftime('%Y%m%d')}{booking_no_seq[0]:03d}"
for i in range(82):
    u = random.choice(users)
    r = random.choice(route_ids)
    travel = TODAY + timedelta(days=random.randint(1, 30))
    people = random.randint(1, 6)
    status = random.choices([0, 1, 2, 3], weights=[15, 50, 20, 15])[0]
    audit_by = 1 if status in (1, 3) else 0
    audit_time = sq(dt(TODAY - timedelta(days=random.randint(0, 10)))) if status in (1, 3) else "NULL"
    cancel_time = sq(dt(TODAY - timedelta(days=random.randint(0, 10)))) if status == 2 else "NULL"
    contact = f"游客{random.randint(1, 50)}"
    phone = f"13{random.randint(0, 9)}{random.randint(10000000, 99999999)}"
    remark = random.choice(["", "", "希望安排靠窗房间", "带老人出行，希望节奏放缓", "需要儿童座椅", "尽量安排高楼层"])
    bno = gen_booking_no(TODAY - timedelta(days=random.randint(0, 15)))
    booking_rows.append(f"({9000 + i}, {sq(bno)}, {r}, {u}, {sq(travel.isoformat())}, {people}, {sq(contact)}, {phone}, {sq(remark)}, {status}, {audit_by}, {audit_time}, {cancel_time}, {sq(dt(TODAY - timedelta(days=random.randint(0, 20))))}, {sq(dt(TODAY))})")
w("INSERT INTO route_booking (id, booking_no, route_id, user_id, travel_date, people_num, contact_name, contact_phone, remark, status, audit_by, audit_time, cancel_time, create_time, update_time) VALUES")
for r in booking_rows:
    w(" " + r + ("" if r == booking_rows[-1] else ","))
w()

w("-- 10) route_comment（143 条，含 AI 情感字段）")
COMMENT_POS = [
    "行程安排很合理，导游专业！", "风景太美了，随手一拍都是大片。", "节奏很舒服，不赶路，推荐！",
    "酒店和餐食都很满意，性价比高。", "带孩子出行很省心，亲子项目丰富。", "一次非常棒的旅行体验！",
    "全程无购物压力，纯玩体验好。", "领队讲解细致，学到了很多当地文化。", "天气给力，路线设计很贴心。",
    "古城住了两晚，慢生活太惬意了。",
]
COMMENT_NEU = [
    "整体还行，中规中矩。", "景点不错，就是排队久了点。", "性价比一般，可以更好。",
    "天气有点热，其他都还好。", "行程较满，有点累但能接受。",
]
COMMENT_NEG = [
    "行程太赶了，每天都在赶路。", "住宿条件跟描述不符，体验不佳。", "购物点太多了，浪费时间。",
    "景点排队两小时游览十分钟。", "导游催促严重，节奏太快。",
]
sentiment_samples = [(c, "positive", round(random.uniform(0.65, 0.98), 3)) for c in COMMENT_POS] + \
                    [(c, "neutral", round(random.uniform(0.4, 0.6), 3)) for c in COMMENT_NEU] + \
                    [(c, "negative", round(random.uniform(0.02, 0.38), 3)) for c in COMMENT_NEG]
sentiment_weights = [70] * len(COMMENT_POS) + [15] * len(COMMENT_NEU) + [15] * len(COMMENT_NEG)
comment_rows = []
for i in range(143):
    u = random.choice(users)
    r = random.choice(route_ids)
    pick = random.choices(sentiment_samples, weights=sentiment_weights)[0]
    content, senti, sent_score = pick[0], pick[1], pick[2]
    score = 5 if senti == "positive" else (3 if senti == "neutral" else 2)
    keywords = {"positive": "行程合理,导游专业", "neutral": "中规中矩", "negative": "行程赶"}[senti]
    days_ago = random.randint(0, 40)
    comment_rows.append(f"({5100 + i}, {r}, {u}, 0, {score}, {sq(content)}, '', 0, {sq(senti)}, {sent_score}, 0, {sq(keywords)}, 1, {sq(dt(TODAY - timedelta(days=days_ago)))}, {sq(dt(TODAY - timedelta(days=days_ago)))}, 0)")
w("INSERT INTO route_comment (id, route_id, user_id, parent_id, score, content, images, like_count, sentiment, sentiment_score, sentiment_retry, keywords, status, create_time, update_time, deleted) VALUES")
for r in comment_rows:
    w(" " + r + ("" if r == comment_rows[-1] else ","))
w()

# ---------- 6. 规划 6（含 1 草稿） ----------
w("-- 11) user_plan / user_plan_day / user_plan_item（6 条规划，含 1 草稿）")
plan_id = 0
plan_day_id = 0
plan_item_id = 0
w("INSERT INTO user_plan (id, user_id, title, destination_ids, start_date, days, budget, people_num, status, source_route_id, create_time, update_time, deleted) VALUES")
plan_main = []
for i in range(6):
    plan_id += 1
    u = users[i]
    rid = route_ids[i]
    dest_id = ROUTES[rid - 201][1]
    days_cnt = ROUTES[rid - 201][4]
    start = TODAY + timedelta(days=random.randint(7, 40))
    budget = random.choice([2500, 3000, 3500, 4000])
    status = 0 if i == 5 else 1
    plan_main.append(f"({plan_id}, {u}, {sq('我的' + str(dest_id) + '号目的地 ' + str(days_cnt) + ' 日自由行')}, {sq(str(dest_id))}, {sq(start.isoformat())}, {days_cnt}, {budget}, {random.randint(1, 4)}, {status}, {rid}, '2026-08-20 00:00:00', '2026-08-20 00:00:00', 0)")
w(" " + ",\n ".join(plan_main))
w()

w("INSERT INTO user_plan_day (id, user_plan_id, day_index, title, summary, create_time, update_time) VALUES")
plan_day_rows = []
for pid in range(1, 7):
    rid = route_ids[pid - 1]
    days_cnt = ROUTES[rid - 201][4]
    for d in range(1, days_cnt + 1):
        plan_day_id += 1
        plan_day_rows.append(f"({plan_day_id}, {pid}, {d}, {sq('第' + str(d) + '天 ' + DAY_TITLES[min(d - 1, 5)])}, {sq(DAY_SUMM[min(d - 1, 5)])}, '2026-08-20 00:00:00', '2026-08-20 00:00:00')")
for i, r in enumerate(plan_day_rows):
    w(" " + r + ("" if i == len(plan_day_rows) - 1 else ","))
w()

w("INSERT INTO user_plan_item (id, plan_day_id, sort_no, time_point, attraction_id, title, activity, transport, hotel, meal, duration_min, cost, tips, create_time, update_time) VALUES")
plan_item_rows = []
for pdi in range(1, len(plan_day_rows) + 1):
    pid = plan_day_rows[pdi - 1].split(",")[1].strip()  # user_plan_id
    dest_id = ROUTES[int(pid) - 1][1]
    attr_ids = list(range(100 + (dest_id - 1) * 8 + 1, 100 + dest_id * 8 + 1))
    n_items = random.randint(2, 4)
    for k in range(n_items):
        plan_item_id += 1
        aid = attr_ids[k % len(attr_ids)]
        h = 9 + k * 2
        cost = random.choice([0, 20, 40, 60, 100])
        plan_item_rows.append(f"({plan_item_id}, {pdi}, {k + 1}, {sq(f'{h:02d}:00')}, {aid}, {sq(ATTRACTION_POOL[dest_id][k % 8])}, {sq(ACTIVITIES[k])}, {sq(TRANSPORTS[k % len(TRANSPORTS)])}, {sq(HOTELS[pdi % len(HOTELS)])}, {sq(MEALS[k % len(MEALS)])}, {random.choice([90, 120, 150, 180])}, {cost}, {sq(TIPS[(pdi + k) % len(TIPS)])}, '2026-08-20 00:00:00', '2026-08-20 00:00:00')")
for i, r in enumerate(plan_item_rows):
    w(" " + r + ("" if i == len(plan_item_rows) - 1 else ","))
w()

# ---------- 7. 行为埋点 637 ----------
w("-- 12) user_behavior（637 条）")
behavior_rows = []
targets = ["ROUTE", "DESTINATION"]
for i in range(637):
    u = random.choice(users)
    bt = random.choices(["VIEW", "VIEW", "VIEW", "LIKE", "FAVORITE", "BOOK", "SEARCH"], weights=[40, 25, 15, 8, 6, 3, 3])[0]
    tt = random.choice(targets)
    tid = random.randint(201, 220) if tt == "ROUTE" else random.randint(1, 10)
    kw = random.choice(["", "", "云南", "大理", "亲子", "海边"]) if bt == "SEARCH" else ""
    dur = random.randint(5, 300) if bt == "VIEW" else 0
    behavior_rows.append(f"({u}, {sq(bt)}, {sq(tt)}, {tid}, {sq(kw)}, {dur}, {sq(dt(TODAY - timedelta(days=random.randint(0, 30))))})")
w("INSERT INTO user_behavior (user_id, behavior_type, target_type, target_id, keyword, duration, create_time) VALUES")
for i, r in enumerate(behavior_rows):
    w(" " + r + ("" if i == 636 else ","))
w()

# ---------- 8. 统计指标 420（30 天 × 14 键） ----------
w("-- 13) stat_daily（420 条：30 天 × 14 指标键）")
METRIC_KEYS = [
    "today_visit", "user_total", "new_user", "route_total", "booking_count", "booking_cancel",
    "ai_chat_count", "ai_recommend_count", "comment_count", "sentiment_positive",
    "sentiment_neutral", "sentiment_negative", "avg_score", "llm_tokens",
]
stat_rows = []
user_total = 4000
sid = 0
for offset in range(30):
    d = STAT_START + timedelta(days=offset)
    day_visit = random.randint(800, 1600)
    new_user = random.randint(3, 15)
    user_total += new_user
    booking = random.randint(30, 65)
    cancel = random.randint(2, 8)
    chat = random.randint(120, 260)
    rec = random.randint(40, 90)
    comm = random.randint(5, 22)
    pos = int(comm * random.uniform(0.6, 0.8))
    neu = int(comm * random.uniform(0.1, 0.2))
    neg = comm - pos - neu
    avg = round(random.uniform(4.2, 4.8), 2)
    tokens = (chat + rec) * random.randint(1500, 2200)
    vals = [day_visit, user_total, new_user, 20, booking, cancel, chat, rec, comm, pos, neu, neg, avg, tokens]
    for k, key in enumerate(METRIC_KEYS):
        sid += 1
        stat_rows.append(f"({sid}, {sq(d.isoformat())}, {sq(key)}, {vals[k]}, NULL, '2026-08-08 00:00:00', '2026-08-08 00:00:00')")
w("INSERT INTO stat_daily (id, stat_date, metric_key, metric_value, extra_json, create_time, update_time) VALUES")
for i, r in enumerate(stat_rows):
    w(" " + r + ("" if i == len(stat_rows) - 1 else ","))
w()

# ---------- 9. 知识库文档 10 ----------
w("-- 14) knowledge_doc（10 篇）")
KNOWLEDGE = [
    ("香格里拉十月出行注意事项", "GUIDE", "香格里拉十月已进入深秋，日均气温 3℃ 至 15℃，昼夜温差大，建议携带羽绒服与保暖内衣。高原紫外线强烈，需备防晒霜与墨镜。普达措国家公园十月中旬进入最佳观赏期，湖水湛蓝，草甸金黄。前往梅里雪山需提前查询道路通行情况，连续降雨可能导致部分路段封闭。松赞林寺参观需脱帽、肃静，尊重藏传佛教习俗。"),
    ("大理洱海环湖骑行全攻略", "GUIDE", "洱海环湖全程约 120 公里，骑行建议分 2 天完成。西岸路况平缓，适合新手，可途经喜洲古镇、海舌公园；东岸坡多但风景更开阔，可看双廊日落。沿途补给点较多，租车价格 40-80 元/天。上午出发避开正午暴晒，骑行务必佩戴头盔并注意防晒。十月起湖边风大，体力消耗明显增加。"),
    ("云南-昆明-景点-滇池", "DESTINATION", "滇池是云南最大的淡水湖，有「高原明珠」之称。海埂公园是观赏滇池与西山全景的最佳位置，冬季可近距离观察红嘴鸥。环湖路适合骑行与散步，全长约 100 公里。周边配套餐饮完善，推荐品尝过桥米线与烤乳扇。景区免费开放，旺季（11月-次年3月）在翠湖公园亦可观赏海鸥。"),
    ("云南-大理-景点-崇圣寺三塔", "DESTINATION", "崇圣寺三塔始建于南诏国时期，是大理的地标建筑。主塔千寻塔高 69.13 米，历经千年地震仍屹立不倒。景区内可参观南诏建极大钟与雨铜观音殿，三塔倒影池是最佳拍照点。门票 75 元，开放时间 7:30-18:30，建议游玩 2 小时。龙王庙方向拍摄三塔与苍山同框效果最佳。"),
    ("云南-丽江-景点-玉龙雪山", "DESTINATION", "玉龙雪山是北半球最近赤道的雪山，主峰扇子陡海拔 5596 米。乘冰川大索道可达 4506 米观景台，需提前 1-3 天预订索道票。山下蓝月谷湖水呈翡翠色，被誉为「小九寨沟」。高原反应预防：建议提前一周服用红景天，山上减少剧烈运动。氧气瓶景区售价 60 元左右，山脚便利店更便宜。"),
    ("海南-三亚-景点-蜈支洲岛", "DESTINATION", "蜈支洲岛被誉为「中国的马尔代夫」，距三亚市区约 1 小时船程。岛上水质清澈，是海南最负盛名的潜水胜地，潜水分浮潜与深潜，价格 380-880 元不等。环岛电瓶车 120 元/人，可停靠多个观景台。冬季（11月-次年3月）水温适宜，是潜水最佳季节。岛上餐饮价格较高，建议自带零食。"),
    ("湖南-张家界-景点-天门山", "DESTINATION", "天门山因 1999 年飞机穿越天门洞而闻名。山顶玻璃栈道全长 60 米，悬于 1430 米峭壁之上。登顶需乘世界最长高山客运索道（7455 米），单程约 28 分钟。冬季可能出现雾凇景观，能见度低时索道可能停运。建议网上提前购票并关注景区公告，天门洞 999 级台阶体力消耗较大。"),
    ("广西-桂林-景点-漓江", "DESTINATION", "漓江桂林至阳朔段是最经典的山水画卷，全程83公里，乘竹筏游览精华段（杨堤-兴坪）约2.5小时。20元人民币背面图案取自兴坪古镇附近的漓江段。雨季水位上涨时竹筏可能停航，出发前建议查询漓江景区公告。阳朔西街夜晚热闹，推荐啤酒鱼与桂林米粉。"),
    ("陕西-西安-景点-兵马俑", "DESTINATION", "秦始皇帝陵博物院（兵马俑）是世界第八大奇迹，一号坑是目前发掘面积最大、兵马俑数量最多的坑。建议参观顺序：一号坑→三号坑→二号坑，全程约 2.5 小时。强烈建议租讲解器（30 元）或请讲解员，否则难以理解坑道布局与文物细节。门票 120 元，需提前 1-7 天实名预约。"),
    ("四川-成都-熊猫基地", "DESTINATION", "成都大熊猫繁育研究基地是观赏大熊猫的最佳去处。大熊猫活跃时段为清晨（8:30-10:00），建议开门即入园。月亮产房可近距离观看幼崽，成年大熊猫在太阳产房与熊猫别墅。园区电瓶车 10 元/人，可直达月亮产房。全程步行约 3 小时，请勿投喂与大声喧哗。门票 55 元，需提前预约。"),
]
doc_id = 0
w("INSERT INTO knowledge_doc (id, title, doc_type, source_type, content, file_path, chunk_count, vector_status, status, create_time, update_time, deleted) VALUES")
for i, (title, dtype, content) in enumerate(KNOWLEDGE):
    doc_id += 1
    comma = "," if i < 9 else ""
    w(f"({doc_id}, {sq(title)}, {sq(dtype)}, 'MANUAL', {sq(content)}, '', 0, 0, 1, '2026-08-10 00:00:00', '2026-08-10 00:00:00', 0){comma}")
w()

# ---------- 10. 轮播图 5 ----------
w("-- 15) banner（5 条）")
w("INSERT INTO banner (id, title, image_url, link_type, link_value, sort_no, status, start_time, end_time, create_time, update_time) VALUES")
BANNERS = [
    (1, "国庆云南专线 立减 500", "https://oss.example.com/trip/banner/b1.jpg", "ROUTE", "201", 1, 1, "2026-09-20 00:00:00", "2026-10-08 23:59:59"),
    (2, "三亚亲子季 酒店买二送一", "https://oss.example.com/trip/banner/b2.jpg", "ROUTE", "209", 2, 1, "2026-09-01 00:00:00", "2026-12-31 23:59:59"),
    (3, "稻城亚丁 纯玩五日", "https://oss.example.com/trip/banner/b3.jpg", "URL", "/destinations/1", 3, 1, None, None),
    (4, "成都美食地图更新", "https://oss.example.com/trip/banner/b4.jpg", "DESTINATION", "8", 4, 1, None, None),
    (5, "厦门鼓浪屿轮渡攻略", "https://oss.example.com/trip/banner/b5.jpg", "DESTINATION", "10", 5, 1, None, None),
]
for i, b in enumerate(BANNERS):
    comma = "," if i < 4 else ""
    start = sq(b[7]) if b[7] else "NULL"
    end = sq(b[8]) if b[8] else "NULL"
    w(f"({b[0]}, {sq(b[1])}, {sq(b[2])}, {sq(b[3])}, {sq(b[4])}, {b[5]}, {b[6]}, {start}, {end}, '2026-08-15 00:00:00', '2026-08-15 00:00:00'){comma}")
w()

# ---------- 11. AI 洞察 2 天 ----------
w("-- 16) ai_insight（近 2 天）")
w("INSERT INTO ai_insight (id, insight_date, content, highlights_json, model, generated_at, create_time, update_time) VALUES")
INSIGHTS = [
    (1, YESTERDAY.isoformat(),
     "昨日平台预约量环比上升 12.4%，主要由「云南大理丽江香格里拉 5 日纯玩」带动（+28.6%），其中华东地区用户贡献 43%。评论情感正面率 74.1%，负面关键词集中在「行程赶」（38 次）与「住宿差」（21 次）。建议优先核查 4 条 5 日以上线路的单日景点数量，并针对华东用户加大云南线路首页曝光。",
     '[{"type":"growth","text":"预约量环比 +12.4%"},{"type":"warning","text":"负面关键词「行程赶」38 次"},{"type":"suggestion","text":"核查 5 日以上线路单日景点数"}]',
     "deepseek-chat", dt(YESTERDAY, "02:10:33"), dt(YESTERDAY, "02:10:33"), dt(YESTERDAY, "02:10:33")),
    (2, (YESTERDAY - timedelta(days=1)).isoformat(),
     "本周平台预约量平稳，三亚亲子线路热度持续攀升（+15.2%），可能与寒暑假亲子出游需求相关。用户地域分布集中在广东（18%）与浙江（14%）。负面反馈中「购物点过多」出现 12 次，建议核查部分线路的购物点占比，评估是否压缩购物行程。",
     '[{"type":"growth","text":"三亚亲子线路热度 +15.2%"},{"type":"warning","text":"「购物点过多」反馈 12 次"},{"type":"suggestion","text":"评估压缩购物行程"}]',
     "deepseek-chat", dt(YESTERDAY - timedelta(days=1), "02:15:40"), dt(YESTERDAY - timedelta(days=1), "02:15:40"), dt(YESTERDAY - timedelta(days=1), "02:15:40")),
]
for i, r in enumerate(INSIGHTS):
    comma = "," if i == 0 else ""
    w(f"({r[0]}, {sq(r[1])}, {sq(r[2])}, {sq(r[3])}, {sq(r[4])}, {sq(r[5])}, {sq(r[6])}, {sq(r[7])}){comma}")
w()

# 收尾：为每条多行 INSERT 语句补上结束分号（最后一行以 ")" 结尾且下一行不是数据行时）
fixed = []
in_insert = False
for idx, ln in enumerate(lines):
    if ln.lstrip().startswith("INSERT INTO"):
        in_insert = True
    if in_insert and ln.rstrip().endswith(")") and not ln.rstrip().endswith(");"):
        nxt = lines[idx + 1] if idx + 1 < len(lines) else ""
        if in_insert and not nxt.lstrip().startswith("("):
            ln = ln.rstrip() + ";"
            in_insert = False
    fixed.append(ln)
lines = fixed

# 统计输出
with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")

print(f"[OK] 已生成 {OUT}")
print(f"   用户 21 ｜ 目的地 10 ｜ 景点 80 ｜ 路线 20 ｜ 行程天 {len(day_specs)} ｜ 行程项 {item_id} ｜ 规划 {plan_id}")
print(f"   点赞 180 ｜ 收藏 131 ｜ 预约 82 ｜ 评论 143 ｜ 行为 637 ｜ 统计 {len(stat_rows)} ｜ 知识文档 10 ｜ 轮播图 5 ｜ 洞察 2")