// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** 真实HTTP/JPA盲码、密封、版本、并发、岗位和完整闭环验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PanelIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("panelblind.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate sql;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, review, custodian, outside;
  List<MockHttpSession> raters = new ArrayList<>();
  List<Long> raterIds = new ArrayList<>();
  long reviewerId, custodianId;
  long mixedId;

  String key() {
    return UUID.randomUUID().toString();
  }

  MvcResult req(MockHttpSession who, String method, String path, Object value) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (who != null) b.session(who);
    b.with(csrf());
    if (value != null) b.contentType("application/json").content(json.writeValueAsString(value));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object v) throws Exception {
    var r = req(who, method, path, v);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession who, String method, String path, Object v, int status, String code)
      throws Exception {
    var r = req(who, method, path, v);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  MockHttpSession login(String name) throws Exception {
    var r = req(null, "POST", "/auth/login", Map.of("username", name, "password", PASSWORD));
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  Map<String, Object> cmd(JsonNode r) {
    return new HashMap<>(
        Map.of("requestKey", key(), "version", r.path("version").asLong(), "note", "TEST 核对事实"));
  }

  JsonNode command(MockHttpSession who, String type, JsonNode row, String action) throws Exception {
    return ok(
        who, "POST", "/" + type + "/" + row.path("id").asLong() + "/commands/" + action, cmd(row));
  }

  long role(String scope, Set<String> ps) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/roles",
            Map.of("name", "TEST" + key(), "scope", scope, "permissions", ps))
        .path("id")
        .asLong();
  }

  JsonNode user(long role, long dept) throws Exception {
    return ok(
        admin,
        "POST",
        "/admin/users",
        Map.of(
            "username",
            "u" + key().substring(0, 8),
            "displayName",
            "TEST内部岗位",
            "roleId",
            role,
            "departmentId",
            dept,
            "enabled",
            true,
            "password",
            PASSWORD));
  }

  @BeforeAll
  void setup() throws Exception {
    admin = login("admin");
    long rr =
        role(
            "ALL",
            Set.of("session.read", "session.review", "rating.review", "export", "dashboard"));
    var r = user(rr, 1);
    reviewerId = r.path("id").asLong();
    review = login(r.path("username").asString());
    long kr = role("SELF", Set.of("session.read", "session.key", "export", "dashboard"));
    var c = user(kr, 1);
    custodianId = c.path("id").asLong();
    custodian = login(c.path("username").asString());
    long pr = role("ALL", Set.of("session.read", "rating.write", "export", "dashboard"));
    for (int i = 0; i < 4; i++) {
      var u = user(pr, 1);
      raterIds.add(u.path("id").asLong());
      raters.add(login(u.path("username").asString()));
    }
    var u = user(1, 1);
    mixedId = u.path("id").asLong();
    raterIds.set(3, mixedId);
    raters.set(3, login(u.path("username").asString()));
    long d =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST外部" + key()))
            .path("id")
            .asLong();
    var x =
        user(role("DEPARTMENT", Set.of("session.read", "session.write", "export", "dashboard")), d);
    outside = login(x.path("username").asString());
  }

  JsonNode getSession(JsonNode s, MockHttpSession who) throws Exception {
    return ok(who, "GET", "/sessions/" + s.path("id").asLong(), null);
  }

  JsonNode current(JsonNode s) throws Exception {
    return getSession(s, admin).path("session");
  }

  Map<String, Object> sessionInput() {
    var m = new HashMap<String, Object>();
    m.put("requestKey", key());
    m.put("reference", "P" + key());
    m.put("name", "TEST包装盲评");
    m.put("departmentId", 1);
    m.put("category", "PACKAGING");
    m.put("instructions", "仅按盲码评价外观，未识别样品先向组织者确认。");
    m.put("reviewerId", reviewerId);
    m.put("custodianId", custodianId);
    return m;
  }

  JsonNode draft() throws Exception {
    var s = ok(admin, "POST", "/sessions", sessionInput());
    for (int n = 0; n < 2; n++) {
      var m = new HashMap<String, Object>();
      m.put("requestKey", key());
      m.put("version", current(s).path("version").asLong());
      m.put("sessionId", s.path("id").asLong());
      m.put("code", "PRODUCT" + n);
      m.put("name", "SECRET_NAME_" + n);
      m.put("description", "PRIVATE_DESCRIPTION_" + n);
      ok(admin, "POST", "/samples", m);
    }
    var scale = new HashMap<String, Object>();
    scale.put("requestKey", key());
    scale.put("sessionId", s.path("id").asLong());
    scale.put("version", current(s).path("version").asLong());
    scale.put("code", "LOOK");
    scale.put("name", "外观喜好");
    scale.put("minimum", 1);
    scale.put("maximum", 9);
    scale.put("lowAnchor", "不喜欢");
    scale.put("highAnchor", "喜欢");
    scale.put("required", true);
    ok(admin, "POST", "/scales", scale);
    ok(
        admin,
        "PUT",
        "/sessions/" + s.path("id").asLong() + "/roster",
        Map.of(
            "requestKey",
            key(),
            "version",
            current(s).path("version").asLong(),
            "raterIds",
            raterIds));
    return current(s);
  }

  JsonNode running() throws Exception {
    var s = draft();
    s = command(admin, "sessions", s, "submit");
    s = command(review, "sessions", s, "approve");
    return command(admin, "sessions", s, "start");
  }

  JsonNode own(JsonNode s, int person) throws Exception {
    return getSession(s, raters.get(person)).path("sheets").get(0);
  }

  JsonNode ratingSheet(JsonNode s, int person, int score, boolean missing) throws Exception {
    var p = own(s, person);
    p = command(raters.get(person), "sheets", p, "acknowledge");
    long scale = getSession(s, raters.get(person)).path("scales").get(0).path("id").asLong();
    for (var i : p.path("presentations")) {
      var v = new HashMap<String, Object>();
      v.put("requestKey", key());
      v.put("version", p.path("version").asLong());
      v.put("presentationId", i.path("id").asLong());
      v.put("scaleId", scale);
      v.put("note", "=TEST注释");
      if (missing) v.put("missingReason", "无法完成触感评价");
      else v.put("value", score);
      p = ok(raters.get(person), "POST", "/ratings", v);
    }
    return command(raters.get(person), "sheets", p, "submit");
  }

  JsonNode seal(JsonNode s) throws Exception {
    for (int i = 0; i < 2; i++)
      command(review, "sheets", ratingSheet(s, i, 3 + 2 * i, false), "accept");
    for (int i = 2; i < 4; i++) command(raters.get(i), "sheets", own(s, i), "withdraw");
    s = command(admin, "sessions", current(s), "end");
    return command(review, "sessions", s, "seal");
  }

  @Test
  void fullWorkflowUnblindsOnlyAfterIndependentRelease() throws Exception {
    var s = seal(running());
    assertEquals(0, getSession(s, admin).path("statistics").size());
    s = command(custodian, "sessions", s, "requestUnblind");
    fail(
        custodian,
        "POST",
        "/sessions/" + s.path("id").asLong() + "/commands/unblind",
        cmd(s),
        403,
        "FORBIDDEN");
    s = command(review, "sessions", s, "unblind");
    var d = getSession(s, admin);
    assertEquals("UNBLINDED", d.path("session").path("status").asString());
    assertEquals("FINISHED", d.path("session").path("outcome").asString());
    assertEquals(64, d.path("session").path("resultHash").asString().length());
    for (var m : d.path("statistics")) {
      assertEquals(2, m.path("n").asInt());
      assertEquals(4.0, m.path("mean").asDouble());
    }
    assertTrue(d.toString().contains("SECRET_NAME_"));
  }

  @Test
  void raterAndMixedAdministratorOnlySeeTheirOwnBlindSheet() throws Exception {
    var s = running();
    for (int person : List.of(0, 3)) {
      var d = getSession(s, raters.get(person));
      assertEquals(1, d.path("sheets").size());
      assertEquals(0, d.path("samples").size());
      assertFalse(d.toString().contains("SECRET_NAME"));
      assertFalse(d.toString().contains("PRIVATE_DESCRIPTION"));
      assertFalse(d.toString().contains("sampleId"));
      assertFalse(d.path("keyAllowed").asBoolean());
      fail(
          raters.get(person),
          "GET",
          "/sessions/" + s.path("id").asLong() + "/key",
          null,
          403,
          person == 0 ? "FORBIDDEN" : "KEY_ACCESS_DENIED");
    }
    fail(
        raters.get(0),
        "POST",
        "/sheets/" + own(s, 1).path("id").asLong() + "/commands/acknowledge",
        cmd(own(s, 1)),
        403,
        "OUT_OF_SCOPE");
  }

  @Test
  void independentReviewNeverReadsIdentityMapping() throws Exception {
    var s = running();
    var d = getSession(s, review);
    assertEquals(0, d.path("samples").size());
    assertFalse(d.toString().contains("SECRET_NAME"));
    fail(review, "GET", "/sessions/" + s.path("id").asLong() + "/key", null, 403, "FORBIDDEN");
    var k = ok(custodian, "GET", "/sessions/" + s.path("id").asLong() + "/key", null);
    assertEquals(8, k.path("mapping").size());
    assertTrue(k.toString().contains("SECRET_NAME"));
  }

  @Test
  void designerCannotReadScoresAndReviewerCannotReadDrafts() throws Exception {
    var s = running();
    var p = ratingSheet(s, 0, 7, false);
    assertEquals(0, getSession(s, admin).path("sheets").get(0).path("ratings").size());
    assertEquals(2, getSession(s, review).path("sheets").get(0).path("ratings").size());
    assertEquals(0, getSession(s, review).path("sheets").get(1).path("ratings").size());
    assertFalse(
        req(admin, "GET", "/sessions/" + s.path("id").asLong() + "/ratings.csv", null)
            .getResponse()
            .getContentAsString()
            .contains("=TEST注释"));
    assertEquals("SUBMITTED", p.path("status").asString());
  }

  @Test
  void approveFreezesSamplesScalesAndRoster() throws Exception {
    var s = running();
    var raw = ok(custodian, "GET", "/sessions/" + s.path("id").asLong() + "/key", null);
    fail(
        admin,
        "POST",
        "/samples/" + raw.path("samples").get(0).path("id").asLong() + "/delete",
        cmd(s),
        409,
        "FROZEN");
    fail(
        admin,
        "PUT",
        "/sessions/" + s.path("id").asLong() + "/roster",
        Map.of("requestKey", key(), "version", s.path("version").asLong(), "raterIds", raterIds),
        409,
        "FROZEN");
  }

  @Test
  void scoresNeedAcknowledgementAndRemainLockedAfterSubmit() throws Exception {
    var s = running();
    var p = own(s, 0);
    var m = new HashMap<String, Object>();
    m.put("requestKey", key());
    m.put("version", p.path("version").asLong());
    m.put("presentationId", p.path("presentations").get(0).path("id").asLong());
    m.put("scaleId", getSession(s, admin).path("scales").get(0).path("id").asLong());
    m.put("value", 5);
    fail(raters.get(0), "POST", "/ratings", m, 409, "RATING_LOCKED");
    p = ratingSheet(s, 0, 5, false);
    m.put("version", p.path("version").asLong());
    fail(raters.get(0), "POST", "/ratings", m, 409, "RATING_LOCKED");
  }

  @Test
  void missingResponseIsNotZeroAndHasItsOwnCount() throws Exception {
    var s = running();
    command(review, "sheets", ratingSheet(s, 0, 0, true), "accept");
    command(review, "sheets", ratingSheet(s, 1, 6, false), "accept");
    for (int n = 2; n < 4; n++) command(raters.get(n), "sheets", own(s, n), "withdraw");
    s = command(admin, "sessions", current(s), "end");
    s = command(review, "sessions", s, "seal");
    s = command(custodian, "sessions", s, "requestUnblind");
    s = command(review, "sessions", s, "unblind");
    for (var m : getSession(s, admin).path("statistics")) {
      assertEquals(1, m.path("n").asInt());
      assertEquals(1, m.path("missing").asInt());
      assertEquals(6, m.path("mean").asDouble());
    }
  }

  @Test
  void scoreRequiresExactlyOneNumberOrMissingReason() throws Exception {
    var s = running();
    var p = command(raters.get(0), "sheets", own(s, 0), "acknowledge");
    var m = new HashMap<String, Object>();
    m.put("requestKey", key());
    m.put("version", p.path("version").asLong());
    m.put("presentationId", p.path("presentations").get(0).path("id").asLong());
    m.put("scaleId", getSession(s, admin).path("scales").get(0).path("id").asLong());
    m.put("value", 5);
    m.put("missingReason", "缺测");
    fail(raters.get(0), "POST", "/ratings", m, 409, "VALUE_OR_MISSING_REQUIRED");
    m.remove("missingReason");
    m.put("value", 10);
    fail(raters.get(0), "POST", "/ratings", m, 409, "OUT_OF_SCALE");
    m.put("value", 5.5);
    assertEquals(400, req(raters.get(0), "POST", "/ratings", m).getResponse().getStatus());
  }

  @Test
  void incompleteSheetCannotSubmit() throws Exception {
    var s = running();
    var p = command(raters.get(0), "sheets", own(s, 0), "acknowledge");
    fail(
        raters.get(0),
        "POST",
        "/sheets/" + p.path("id").asLong() + "/commands/submit",
        cmd(p),
        409,
        "INCOMPLETE_RATINGS");
  }

  @Test
  void returnedScoreCanBeChangedButAcceptedScoreIsImmutable() throws Exception {
    var s = running();
    var p = ratingSheet(s, 0, 2, false);
    p = command(review, "sheets", p, "return");
    var values = p.path("ratings");
    assertEquals(0, values.size());
    p = own(s, 0);
    var i = p.path("presentations").get(0);
    p =
        ok(
            raters.get(0),
            "POST",
            "/ratings",
            Map.of(
                "requestKey",
                key(),
                "version",
                p.path("version").asLong(),
                "presentationId",
                i.path("id").asLong(),
                "scaleId",
                getSession(s, admin).path("scales").get(0).path("id").asLong(),
                "value",
                8));
    p = command(raters.get(0), "sheets", p, "submit");
    p = command(review, "sheets", p, "accept");
    fail(
        raters.get(0),
        "POST",
        "/sheets/" + p.path("id").asLong() + "/commands/withdraw",
        cmd(p),
        409,
        "INVALID_STATE");
    assertTrue(
        sql.queryForObject(
                "select count(*) from business_event where object_type='sheet' and object_id=? and action='RATING_SAVE'",
                Integer.class,
                p.path("id").asLong())
            >= 3);
  }

  @Test
  void normalEndNeedsTwoAcceptedAndNoUnfinishedSheets() throws Exception {
    var s = running();
    fail(
        admin,
        "POST",
        "/sessions/" + s.path("id").asLong() + "/commands/end",
        cmd(s),
        409,
        "INCOMPLETE_RATINGS");
  }

  @Test
  void abortRejectsPendingReviewAndPreservesDistinctOutcome() throws Exception {
    var s = running();
    var p = ratingSheet(s, 0, 5, false);
    fail(
        admin,
        "POST",
        "/sessions/" + s.path("id").asLong() + "/commands/abort",
        cmd(s),
        409,
        "PENDING_REVIEW");
    command(review, "sheets", p, "exclude");
    s = command(admin, "sessions", current(s), "abort");
    s = command(review, "sessions", s, "seal");
    assertEquals("ABORTED", getSession(s, admin).path("session").path("outcome").asString());
    assertEquals(4, getSession(s, admin).path("sheets").size());
  }

  @Test
  void repeatedCommandAndCreateHaveExactStableResponse() throws Exception {
    var m = sessionInput();
    var a = req(admin, "POST", "/sessions", m);
    var b = req(admin, "POST", "/sessions", m);
    assertEquals(a.getResponse().getContentAsString(), b.getResponse().getContentAsString());
    var s = running();
    var p = own(s, 0);
    var c = cmd(p);
    String path = "/sheets/" + p.path("id").asLong() + "/commands/acknowledge";
    var first = req(raters.get(0), "POST", path, c);
    var second = req(raters.get(0), "POST", path, c);
    assertEquals(
        first.getResponse().getContentAsString(), second.getResponse().getContentAsString());
  }

  @Test
  void reusedRequestKeyWithChangedPayloadIsRejected() throws Exception {
    var m = sessionInput();
    ok(admin, "POST", "/sessions", m);
    m.put("name", "改名");
    fail(admin, "POST", "/sessions", m, 409, "REQUEST_KEY_REUSED");
  }

  @Test
  void staleScoreVersionDoesNotOverwriteLaterFacts() throws Exception {
    var s = running();
    var p = own(s, 0);
    var stale = cmd(p);
    command(raters.get(0), "sheets", p, "acknowledge");
    fail(
        raters.get(0),
        "POST",
        "/sheets/" + p.path("id").asLong() + "/commands/withdraw",
        stale,
        409,
        "STALE_VERSION");
  }

  @Test
  void concurrentCommandsAtOneVersionHaveOneWinner() throws Exception {
    var s = running();
    var p = own(s, 0);
    String path = "/sheets/" + p.path("id").asLong() + "/commands/acknowledge";
    var a = cmd(p);
    var b = cmd(p);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var start = new CountDownLatch(1);
      var first =
          pool.submit(
              () -> {
                start.await();
                return req(raters.get(0), "POST", path, a).getResponse().getStatus();
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return req(raters.get(0), "POST", path, b).getResponse().getStatus();
              });
      start.countDown();
      assertEquals(
          Set.of(200, 409),
          Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void unassignedAndOutsideDepartmentCannotReadOrExport() throws Exception {
    var s = running();
    fail(outside, "GET", "/sessions/" + s.path("id").asLong(), null, 403, "OUT_OF_SCOPE");
    fail(
        outside,
        "GET",
        "/sessions/" + s.path("id").asLong() + "/report.json",
        null,
        403,
        "OUT_OF_SCOPE");
    assertEquals(0, ok(outside, "GET", "/sessions", null).path("total").asInt());
  }

  @Test
  void invalidRosterAndKnownEditorsAreRejected() throws Exception {
    var s = draft();
    var ids = new ArrayList<>(raterIds);
    ids.set(0, 1L);
    fail(
        admin,
        "PUT",
        "/sessions/" + s.path("id").asLong() + "/roster",
        Map.of("requestKey", key(), "version", s.path("version").asLong(), "raterIds", ids),
        409,
        "BLIND_ROLE_CONFLICT");
    ids.set(0, ids.get(1));
    fail(
        admin,
        "PUT",
        "/sessions/" + s.path("id").asLong() + "/roster",
        Map.of("requestKey", key(), "version", s.path("version").asLong(), "raterIds", ids),
        409,
        "INVALID_ROSTER");
  }

  @Test
  void ownCoordinatorCannotApproveByUsingAdministratorRole() throws Exception {
    var s = draft();
    var v = sessionInput();
    v.put("reference", s.path("reference").asString());
    v.put("requestKey", key());
    v.put("version", s.path("version").asLong());
    v.put("reviewerId", 1L);
    s = ok(admin, "PUT", "/sessions/" + s.path("id").asLong(), v);
    fail(
        admin,
        "POST",
        "/sessions/" + s.path("id").asLong() + "/commands/submit",
        cmd(s),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void csvEscapesFormulaNotesAndStaysBlindBeforeRelease() throws Exception {
    var s = running();
    ratingSheet(s, 0, 4, false);
    String csv =
        req(raters.get(0), "GET", "/sessions/" + s.path("id").asLong() + "/ratings.csv", null)
            .getResponse()
            .getContentAsString();
    assertTrue(csv.contains("'=TEST注释"));
    assertFalse(csv.contains("PRODUCT"));
    assertFalse(csv.contains("SECRET_NAME"));
    assertEquals(4, csv.split("\r\n", -1).length);
  }

  @Test
  void csrfAndLoginAreRequired() throws Exception {
    assertEquals(401, req(null, "GET", "/sessions", null).getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/sessions")
                    .session(admin)
                    .contentType("application/json")
                    .content(json.writeValueAsString(sessionInput())))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void accountResponsesNeverExposePasswordHash() throws Exception {
    String users = ok(admin, "GET", "/admin/users", null).toString();
    assertFalse(users.contains("password"));
    assertFalse(users.contains(PASSWORD));
  }

  @Test
  void migrationsCreateBothVersionsAndExpectedTables() {
    assertEquals(
        2,
        sql.queryForObject(
            "select count(*) from flyway_schema_history where version in ('1','2') and success=true",
            Integer.class));
    assertEquals(
        19,
        sql.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public'",
            Integer.class));
  }

  @Test
  void definitionDeletionRetryIsStableAndKeepsParentAuthorization() throws Exception {
    var s = draft();
    var sample = getSession(s, admin).path("samples").get(0);
    var body = cmd(s);
    String path = "/samples/" + sample.path("id").asLong() + "/delete";
    var first = req(admin, "POST", path, body);
    assertEquals(200, first.getResponse().getStatus());
    var second = req(admin, "POST", path, body);
    assertEquals(
        first.getResponse().getContentAsString(), second.getResponse().getContentAsString());
    assertEquals(1, getSession(s, admin).path("samples").size());
    fail(outside, "POST", path, body, 409, "REQUEST_KEY_REUSED");
  }
}
