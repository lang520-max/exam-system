-- ============================================================================
--  V2 —— 主观题 AI 评分支持
--
--  背景：原先主观题（填空/简答）只被标记 is_correct = 2 表示「待复核」，
--  给 0 分，且没有任何接口能让教师真正改分（regrade 是死代码）。
--  现在接入 AI 评分：模型给出分数与理由，直接计为最终得分。
--
--  新增三列：
--    graded_by  评分来源。NULL 表示「尚未评分」（即待复核）；
--               AUTO = 客观题按标准答案自动判分；AI = 模型评分；TEACHER = 教师复核。
--               ★ 用 graded_by 表达「待复核」，而不是继续拿 is_correct = 2 兼职，
--                 因为 is_correct 的既有语义是 0-错误/1-正确/2-部分正确（见建表注释），
--                 原先代码把 2 当成「待复核」用，与列注释冲突，
--                 结果导致「主观题在成绩单里被显示成答错」。
--    ai_reason  AI 给出的评分理由（学生能看到，用于解释得分）
--    ai_model   评分所用模型名，便于排查「换了模型分数就变了」这类问题
--
--  reviewed_at 的语义相应放宽为「该题完成评分的时间」（自动/AI/教师都会写）；
--  reviewed_by 仍只由教师复核时写入。
-- ============================================================================

ALTER TABLE `student_answer`
    ADD COLUMN `graded_by` VARCHAR(16) DEFAULT NULL
        COMMENT '评分来源：AUTO-客观题自动 / AI-模型评分 / TEACHER-教师复核；NULL=待复核'
        AFTER `is_correct`,
    ADD COLUMN `ai_reason` TEXT DEFAULT NULL
        COMMENT 'AI 评分理由'
        AFTER `graded_by`,
    ADD COLUMN `ai_model` VARCHAR(64) DEFAULT NULL
        COMMENT 'AI 评分所用模型'
        AFTER `ai_reason`;