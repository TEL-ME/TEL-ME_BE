package com.telme.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 마이그레이션(V29)이 넣은 시드 15종으로 요금제 조회 API를 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanApiIntegrationTest {

    static final String PLANS_URL = "/api/v1/plans";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanRepository planRepository;

    @Test
    void 로그인_없이_판매_중인_요금제_15종을_정렬_순서대로_내려준다() throws Exception {
        mvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.plans.length()").value(15))
                .andExpect(jsonPath("$.result.plans[0].code").value("FIVE_G_UNLIMITED"))
                .andExpect(jsonPath("$.result.plans[14].code").value("BUDGET_SENIOR"));
    }

    @Test
    void 무제한_요금제는_dataGb가_null이고_unlimited가_true() throws Exception {
        mvc.perform(get(PLANS_URL).param("networkType", "FIVE_G").param("targetGroup", "GENERAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.plans[0].unlimited").value(true))
                .andExpect(jsonPath("$.result.plans[0].dataGb").doesNotExist())
                .andExpect(jsonPath("$.result.plans[1].unlimited").value(false))
                .andExpect(jsonPath("$.result.plans[1].dataGb").value(150.0));
    }

    @Test
    void 망_구분과_대상_구분으로_거른다() throws Exception {
        mvc.perform(get(PLANS_URL).param("targetGroup", "SENIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.plans.length()").value(3))
                .andExpect(jsonPath("$.result.plans[*].targetGroup", everyItem(is("SENIOR"))));
        mvc.perform(get(PLANS_URL).param("networkType", "BUDGET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.plans[*].code",
                        contains("BUDGET_SAVE", "BUDGET_MINI", "BUDGET_YOUTH", "BUDGET_SENIOR")));
    }

    @Test
    void 판매_종료된_요금제는_목록과_상세에서_빠진다() throws Exception {
        long planId = planId("LTE_BASIC");
        jdbc.update("UPDATE plans SET status = 'DISCONTINUED' WHERE plan_id = ?", planId);

        mvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.plans.length()").value(14));
        mvc.perform(get(PLANS_URL + "/" + planId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN404-0"));
    }

    @Test
    void 상세는_요금제_한_건을_내려준다() throws Exception {
        mvc.perform(get(PLANS_URL + "/" + planId("BUDGET_MINI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("알뜰 미니"))
                .andExpect(jsonPath("$.result.networkType").value("BUDGET"))
                .andExpect(jsonPath("$.result.targetGroup").value("GENERAL"))
                .andExpect(jsonPath("$.result.monthlyFee").value(15000))
                .andExpect(jsonPath("$.result.dataGb").value(1.5))
                .andExpect(jsonPath("$.result.unlimited").value(false));
    }

    @Test
    void 통화_문자_제공량은_무제한이면_null과_플래그로_내려준다() throws Exception {
        mvc.perform(get(PLANS_URL + "/" + planId("BUDGET_SAVE")))
                .andExpect(jsonPath("$.result.voiceMinutes").value(500))
                .andExpect(jsonPath("$.result.voiceUnlimited").value(false))
                .andExpect(jsonPath("$.result.smsCount").value(300))
                .andExpect(jsonPath("$.result.smsUnlimited").value(false));
        mvc.perform(get(PLANS_URL + "/" + planId("LTE_SENIOR")))
                .andExpect(jsonPath("$.result.voiceMinutes").doesNotExist())
                .andExpect(jsonPath("$.result.voiceUnlimited").value(true))
                .andExpect(jsonPath("$.result.smsCount").value(300))
                .andExpect(jsonPath("$.result.smsUnlimited").value(false));
    }

    @Test
    void 없는_요금제는_404_ID_형식_오류와_잘못된_필터값은_400() throws Exception {
        mvc.perform(get(PLANS_URL + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN404-0"));
        mvc.perform(get(PLANS_URL + "/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mvc.perform(get(PLANS_URL + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mvc.perform(get(PLANS_URL).param("networkType", "WIFI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    // 시드 값이 정책 문서(PLAN-05, PLAN-06 표)와 어긋나면 FAQ 답변과 카탈로그가 모순되므로 표를 직접 읽어 대조한다
    @Test
    void 시드_값은_POLICY_md의_요금제_표와_같다() throws Exception {
        Map<String, int[]> policy = new LinkedHashMap<>();
        Map<String, BigDecimal> policyData = new LinkedHashMap<>();
        Pattern row = Pattern.compile("^\\| (\\S+ \\S+) \\| ([\\d,]+)원 \\| (무제한|[\\d.]+GB) \\|$");
        List<String> lines = Files.readAllLines(Path.of("docs/POLICY.md"));
        boolean inPlanTable = false;
        for (String line : lines) {
            if (line.startsWith("#### ")) {
                inPlanTable = line.startsWith("#### PLAN-05") || line.startsWith("#### PLAN-06");
            }
            Matcher m = row.matcher(line);
            if (inPlanTable && m.matches()) {
                policy.put(m.group(1), new int[] {Integer.parseInt(m.group(2).replace(",", ""))});
                policyData.put(m.group(1), m.group(3).equals("무제한") ? null
                        : new BigDecimal(m.group(3).replace("GB", "")));
            }
        }
        assertThat(policy).hasSize(15);

        Map<String, int[]> seeded = new LinkedHashMap<>();
        Map<String, BigDecimal> seededData = new LinkedHashMap<>();
        jdbc.query("SELECT name, monthly_fee, data_gb FROM plans", rs -> {
            seeded.put(rs.getString("name"), new int[] {rs.getInt("monthly_fee")});
            seededData.put(rs.getString("name"), rs.getBigDecimal("data_gb"));
        });

        assertThat(seeded.keySet()).containsExactlyInAnyOrderElementsOf(policy.keySet());
        policy.forEach((name, fee) -> {
            assertThat(seeded.get(name)[0]).as("%s 월정액", name).isEqualTo(fee[0]);
            BigDecimal expected = policyData.get(name);
            if (expected == null) {
                assertThat(seededData.get(name)).as("%s 데이터", name).isNull();
            } else {
                assertThat(seededData.get(name)).as("%s 데이터", name).isEqualByComparingTo(expected);
            }
        });
    }

    // 통화·문자 시드(V33)가 POLICY.md PLAN-07 표와 같은지 엔티티 매핑까지 포함해 확인한다
    @Test
    void 통화_문자_시드는_POLICY_md_PLAN_07_표와_같다() throws Exception {
        Pattern row = Pattern.compile("^\\| ((?:5G|LTE|알뜰) \\S+) \\| ([^|]+?) \\| ([^|]+?) \\|$");
        Map<String, String[]> policy = new LinkedHashMap<>();
        boolean inPlan07 = false;
        for (String line : Files.readAllLines(Path.of("docs/POLICY.md"))) {
            if (line.startsWith("#### ")) {
                inPlan07 = line.startsWith("#### PLAN-07");
            }
            Matcher m = row.matcher(line);
            if (inPlan07 && m.matches()) {
                policy.put(m.group(1), new String[] {m.group(2).trim(), m.group(3).trim()});
            }
        }
        assertThat(policy).hasSize(15);

        List<Plan> plans = planRepository.findAll();
        assertThat(plans.stream().map(Plan::getName)).containsExactlyInAnyOrderElementsOf(policy.keySet());
        for (Plan plan : plans) {
            String[] expected = policy.get(plan.getName());
            assertLimit(plan.getName() + " 통화", expected[0], plan.getVoiceMinutes(), plan.isVoiceUnlimited());
            assertLimit(plan.getName() + " 문자", expected[1], plan.getSmsCount(), plan.isSmsUnlimited());
        }
    }

    private void assertLimit(String label, String policyValue, Integer limit, boolean unlimited) {
        if (policyValue.equals("무제한")) {
            assertThat(unlimited).as("%s 무제한 플래그", label).isTrue();
            assertThat(limit).as("%s 한도", label).isNull();
        } else {
            assertThat(unlimited).as("%s 무제한 플래그", label).isFalse();
            assertThat(limit).as("%s 한도", label).isEqualTo(Integer.parseInt(policyValue.replaceAll("[^\\d]", "")));
        }
    }

    private long planId(String code) {
        return jdbc.queryForObject("SELECT plan_id FROM plans WHERE code = ?", Long.class, code);
    }
}
