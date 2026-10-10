package com.localmarts.pos.report;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Only the new report index: SQLiteDialect skips composite @UniqueConstraint DDL.
 * Runs after Hibernate's existing schema initialization and before the application is ready.
 * Idempotent; never repairs, deletes, or rewrites existing data. */
@Component
@DependsOn("entityManagerFactory")
public class CashierReportSchema implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;
    public CashierReportSchema(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public void afterSingletonsInstantiated() {
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_cashier_report_period "
                + "ON cashier_sale_reports (cashier_id, start_date, end_date)");
    }
}
