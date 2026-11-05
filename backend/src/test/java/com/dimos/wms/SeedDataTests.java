package com.dimos.wms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SeedDataTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void seedSiteRolesAndUsersExist() {
		assertThat(jdbc.queryForObject("select name from site where code = 'DXB-TEST'", String.class))
				.isEqualTo("DIMOS Test Site");
		assertThat(jdbc.queryForList("select code from app_role order by id", String.class))
				.containsExactly("SUPER_ADMIN", "ADMIN", "OPERATOR", "VIEWER");
		assertThat(jdbc.queryForObject("""
				select count(*) from app_user u
				join user_role ur on ur.user_id = u.id
				join app_role r on r.id = ur.role_id
				where u.username in ('admin', 'rasheed') and r.code = 'SUPER_ADMIN'
				""", Integer.class)).isEqualTo(2);
	}
}
