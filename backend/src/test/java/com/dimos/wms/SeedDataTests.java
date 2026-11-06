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

	@Test
	void masterDataSeedExists() {
		assertThat(jdbc.queryForObject("select count(*) from equipment_type where is_seeded = true", Integer.class))
				.isEqualTo(13);
		assertThat(jdbc.queryForList("""
				select r.code from role_permission rp
				join app_role r on r.id = rp.role_id
				join app_permission p on p.id = rp.permission_id
				where p.code = 'MASTERDATA_VIEW' order by r.id
				""", String.class)).containsExactly("SUPER_ADMIN", "ADMIN", "OPERATOR", "VIEWER");
		assertThat(jdbc.queryForList("""
				select r.code from role_permission rp
				join app_role r on r.id = rp.role_id
				join app_permission p on p.id = rp.permission_id
				where p.code = 'MASTERDATA_EDIT' order by r.id
				""", String.class)).containsExactly("SUPER_ADMIN", "ADMIN");
		assertThat(jdbc.queryForObject("select count(*) from app_permission where code = 'USER_MANAGE'",
				Integer.class)).isEqualTo(1);
	}
}
