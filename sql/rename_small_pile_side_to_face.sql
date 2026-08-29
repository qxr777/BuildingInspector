USE bi;

START TRANSACTION;

-- 1. 桥梁模板：
--    小桩号侧     -> 小桩号面
--    盖梁小桩号侧 -> 盖梁小桩号面
UPDATE bi_template_object
SET name = CASE
        WHEN name = '小桩号侧' THEN '小桩号面'
        WHEN name = '盖梁小桩号侧' THEN '盖梁小桩号面'
        ELSE name
    END,
    update_time = NOW()
WHERE name IN ('小桩号侧', '盖梁小桩号侧');

SELECT ROW_COUNT() AS updated_template_count;


-- 2. 已有桥梁结构树：
--    小桩号侧     -> 小桩号面
--    盖梁小桩号侧 -> 盖梁小桩号面
UPDATE bi_object
SET name = CASE
        WHEN name = '小桩号侧' THEN '小桩号面'
        WHEN name = '盖梁小桩号侧' THEN '盖梁小桩号面'
        ELSE name
    END,
    update_time = NOW()
WHERE name IN ('小桩号侧', '盖梁小桩号侧');

SELECT ROW_COUNT() AS updated_object_count;


-- 3. 已有病害的位置：
--    小桩号侧     -> 小桩号面
--    盖梁小桩号侧 -> 盖梁小桩号面
UPDATE bi_disease
SET position = CASE
        WHEN position = '小桩号侧' THEN '小桩号面'
        WHEN position = '盖梁小桩号侧' THEN '盖梁小桩号面'
        ELSE position
    END,
    update_time = NOW()
WHERE position IN ('小桩号侧', '盖梁小桩号侧');

SELECT ROW_COUNT() AS updated_disease_count;

COMMIT;


-- 执行结果检查：以下三个数量正常应为 0。
SELECT COUNT(*) AS remaining_template_count
FROM bi_template_object
WHERE name IN ('小桩号侧', '盖梁小桩号侧');

SELECT COUNT(*) AS remaining_object_count
FROM bi_object
WHERE name IN ('小桩号侧', '盖梁小桩号侧');

SELECT COUNT(*) AS remaining_disease_count
FROM bi_disease
WHERE position IN ('小桩号侧', '盖梁小桩号侧');
