-- ============================================================
-- 冗余列同步脚本（第 2 批验收后修复）
-- 背景：data.sql 中 route 表冗余列（like_count/favorite_count/comment_count/
--       booking_count/avg_score）初始均为 0，与互动明细表（route_like /
--       route_favorite / route_comment / route_booking）实际数据不同步，
--       导致列表/详情页计数显示偏低。
-- 用法：已有库直接执行本脚本即可；重跑 data.sql 后也需要再执行它。
--       （data.sql 末尾已附带同样逻辑）
-- 幂等：可重复执行（按明细表实时重算）。
-- ============================================================

UPDATE trip_llm.route r
SET r.like_count = (SELECT COUNT(*) FROM trip_llm.route_like rl WHERE rl.route_id = r.id),
    r.favorite_count = (SELECT COUNT(*) FROM trip_llm.route_favorite rf WHERE rf.route_id = r.id),
    r.comment_count = (SELECT COUNT(*) FROM trip_llm.route_comment rc WHERE rc.route_id = r.id AND rc.deleted = 0),
    r.booking_count = (SELECT COUNT(*) FROM trip_llm.route_booking rb WHERE rb.route_id = r.id AND rb.status IN (0,1,3)),
    r.avg_score = COALESCE((SELECT AVG(rc.score) FROM trip_llm.route_comment rc
                            WHERE rc.route_id = r.id AND rc.deleted = 0 AND rc.status = 1), r.avg_score);