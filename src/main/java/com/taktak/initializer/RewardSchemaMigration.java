package com.taktak.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RewardSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("ALTER TABLE coupons ALTER COLUMN source_order_id DROP NOT NULL");
        jdbc.execute("ALTER TABLE reward_campaigns ADD COLUMN IF NOT EXISTS participation_cooldown_days INTEGER NOT NULL DEFAULT 30");
    }
}
