-- 用户级 SQLite 离线包记录表
CREATE TABLE IF NOT EXISTS bi_user_sqlite (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    minio_id BIGINT NOT NULL COMMENT 'MinIO中的文件ID',
    package_time DATETIME COMMENT '打包时间',
    update_time DATETIME COMMENT '最后更新时间',
    package_size VARCHAR(50) COMMENT '包大小',
    del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志'
) ENGINE=InnoDB COMMENT='用户级SQLite离线包记录';
