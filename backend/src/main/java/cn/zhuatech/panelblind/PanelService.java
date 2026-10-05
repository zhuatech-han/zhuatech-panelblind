// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 独立审签、盲码隔离、评分封存及双人解盲事务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PanelService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper json;

  public PanelService(Store db, AccessService access, Clock clock, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.json = json;
  }

  /** 有限方案、样品、量表及名单字段；不接受盲码、状态和系统时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String requestKey,
      Long version,
      String reference,
      String name,
      Long departmentId,
      String category,
      String instructions,
      Long reviewerId,
      Long custodianId,
      Long sessionId,
      String code,
      String description,
      Integer minimum,
      Integer maximum,
      String lowAnchor,
      String highAnchor,
      Boolean required,
      List<Long> raterIds) {}

  /** 单项评分与明确缺测互斥；整张评分单版本保护同时编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record RatingInput(
      String requestKey,
      Long version,
      Long presentationId,
      Long scaleId,
      Integer value,
      String missingReason,
      String note) {}

  /** 命令只接受请求键、预期版本和说明。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(String requestKey, Long version, String note) {}

  private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED");

  private void check(boolean ok, String code) {
    if (!ok) throw new Problem(409, code);
  }

  private String text(String s, int n) {
    return AdminService.text(s, n);
  }

  private String optional(String s, int n) {
    if (s == null) return "";
    if (s.length() > n) throw new Problem(400, "INVALID_INPUT");
    return s.trim();
  }

  private Long who() {
    return access.current().id;
  }

  private Instant now() {
    return BusinessTime.now(clock);
  }

  private void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  private void version(long actual, Long expected) {
    check(expected != null && expected == actual, "STALE_VERSION");
  }

  private boolean scope(PanelSession s) {
    return access.visible(s.departmentId)
        && (!access.role().scope.equals("SELF") || Objects.equals(who(), s.createdBy));
  }

  private List<PanelSheet> sheets(Long id) {
    return db.query(PanelSheet.class, "from PanelSheet where sessionId=?1 order by id", id);
  }

  private List<BlindSample> samples(Long id) {
    return db.query(BlindSample.class, "from BlindSample where sessionId=?1 order by id", id);
  }

  private List<RatingScale> scales(Long id) {
    return db.query(RatingScale.class, "from RatingScale where sessionId=?1 order by code", id);
  }

  private List<Presentation> presentations(Long id) {
    return db.query(Presentation.class, "from Presentation where sheetId=?1 order by position", id);
  }

  private List<RatingValue> ratings(Long sheet) {
    return db.query(
        RatingValue.class,
        "from RatingValue where presentationId in(select id from Presentation where sheetId=?1) order by id",
        sheet);
  }

  private boolean member(PanelSession s) {
    return sheets(s.id).stream().anyMatch(x -> Objects.equals(x.raterId, who()));
  }

  private boolean scoringOnly() {
    var ps = access.role().permissions;
    return ps.contains("rating.write")
        && Collections.disjoint(
            ps, Set.of("session.write", "session.review", "session.key", "rating.review", "admin"));
  }

  private boolean visible(PanelSession s) {
    return member(s)
        || Objects.equals(who(), s.reviewerId)
        || Objects.equals(who(), s.custodianId)
        || !scoringOnly() && scope(s);
  }

  private PanelSession session(Long id) {
    var s = db.get(PanelSession.class, id);
    if (!visible(s)) throw new Problem(403, "OUT_OF_SCOPE");
    return s;
  }

  private boolean editor(PanelSession s, Long actor) {
    return !db.query(
            SessionEditor.class,
            "from SessionEditor where sessionId=?1 and actorId=?2",
            s.id,
            actor)
        .isEmpty();
  }

  private void editMark(PanelSession s) {
    if (!editor(s, who())) {
      var e = new SessionEditor();
      e.sessionId = s.id;
      e.actorId = who();
      db.save(e);
    }
  }

  private void writer(PanelSession s) {
    access.require("session.write");
    if (!scope(s) || member(s)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  private void reviewer(PanelSession s, String permission) {
    access.require(permission);
    if (!Objects.equals(who(), s.reviewerId)) throw new Problem(403, "ASSIGNED_REVIEWER_REQUIRED");
    check(
        !editor(s, who()) && !Objects.equals(who(), s.custodianId) && !member(s),
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  private void rater(PanelSession s, PanelSheet p) {
    access.require("rating.write");
    if (!Objects.equals(who(), p.raterId)) throw new Problem(403, "ASSIGNED_RATER_REQUIRED");
    check(
        !editor(s, who())
            && !Objects.equals(who(), s.reviewerId)
            && !Objects.equals(who(), s.custodianId),
        "BLIND_ROLE_CONFLICT");
  }

  private void keyAccess(PanelSession s) {
    access.require("session.key");
    if (member(s) || !(Objects.equals(who(), s.custodianId) || editor(s, who()) && scope(s)))
      throw new Problem(403, "KEY_ACCESS_DENIED");
  }

  private void eligible(Long id, String permission) {
    var a = db.get(Account.class, id);
    check(
        a.enabled && db.get(AccessRole.class, a.roleId).permissions.contains(permission),
        "INELIGIBLE_ASSIGNMENT");
  }

  private int cap() {
    return Integer.parseInt(
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "maxRecords")
            .getFirst()
            .value);
  }

  private void limit(Class<?> type, int cap) {
    check(db.all(type).size() < cap, "RECORD_LIMIT");
  }

  private String encode(Object o) {
    return json.writeValueAsString(o);
  }

  private String hash(Object o) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(encode(o).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Object write(String key, Object payload, Supplier<Object> action) {
    try {
      if (key == null || !UUID.fromString(key).toString().equals(key))
        throw new Problem(400, "INVALID_REQUEST_KEY");
    } catch (IllegalArgumentException e) {
      throw new Problem(400, "INVALID_REQUEST_KEY");
    }
    String fingerprint = hash(List.of(who(), payload));
    var prior = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (!prior.isEmpty()) {
      check(prior.getFirst().fingerprint.equals(fingerprint), "REQUEST_KEY_REUSED");
      return json.readerFor(Map.class)
          .with(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .readValue(prior.getFirst().responseJson);
    }
    var result = action.get();
    db.flush();
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fingerprint;
    c.responseJson = encode(result);
    db.save(c);
    return result;
  }

  private void event(
      PanelSession s, String type, Long id, String action, String note, Object snapshot) {
    var e = new BusinessEvent();
    e.objectType = type;
    e.objectId = id;
    e.actorId = who();
    e.action = action;
    e.note = note;
    e.snapshot = encode(snapshot);
    e.createdAt = now();
    db.save(e);
    access.audit(action, s.id, s.departmentId);
  }

  private Map<String, Object> saved(PanelSession s) {
    return Map.of("id", s.id, "version", s.version, "status", s.status);
  }

  /** 新建和编辑方案；历史编辑者不允许被选为评分员或独立审签员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveSession(Long id, Input v) {
    lock();
    access.require("session.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var s = id == null ? new PanelSession() : session(id);
    if (id != null) writer(s);
    else {
      access.department(v.departmentId);
      s.departmentId = v.departmentId;
      s.createdBy = who();
      s.reference = text(v.reference, 60);
      s.status = "DRAFT";
      s.createdAt = now();
    }
    return write(
        v.requestKey,
        List.of("session.save", id == null ? 0 : id, v),
        () -> {
          if (id != null) {
            version(s.version, v.version);
            check(EDITABLE.contains(s.status), "FROZEN");
            check(
                Objects.equals(s.reference, v.reference)
                    && Objects.equals(s.departmentId, v.departmentId),
                "IMMUTABLE_REFERENCE");
          } else limit(PanelSession.class, cap());
          s.name = text(v.name, 160);
          s.category = text(v.category, 60);
          check(
              !db.query(
                      DictionaryEntry.class,
                      "from DictionaryEntry where type='category' and code=?1",
                      s.category)
                  .isEmpty(),
              "INVALID_CATEGORY");
          s.instructions = text(v.instructions, 2000);
          eligible(v.reviewerId, "session.review");
          eligible(v.reviewerId, "rating.review");
          eligible(v.custodianId, "session.key");
          s.reviewerId = v.reviewerId;
          s.custodianId = v.custodianId;
          if (id == null) db.save(s);
          s.version++;
          editMark(s);
          event(s, "session", s.id, "SESSION_SAVE", "方案草稿修改", saved(s));
          return saved(s);
        });
  }

  /** 草稿样品身份或整数量表维护；预期版本为父方案版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveDefinition(String type, Long id, Input v) {
    lock();
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var s = session(v.sessionId);
    writer(s);
    return write(
        v.requestKey,
        List.of(type + ".save", id == null ? 0 : id, v),
        () -> {
          version(s.version, v.version);
          check(EDITABLE.contains(s.status), "FROZEN");
          String code = text(v.code, 60);
          check(code.matches("[A-Za-z0-9_.-]{1,60}"), "INVALID_CODE");
          Long objectId;
          if (type.equals("samples")) {
            var row = id == null ? new BlindSample() : db.get(BlindSample.class, id);
            if (id != null) check(Objects.equals(row.sessionId, s.id), "WRONG_SESSION");
            else check(samples(s.id).size() < 8, "SAMPLE_LIMIT");
            row.sessionId = s.id;
            row.code = code;
            row.name = text(v.name, 160);
            row.description = text(v.description, 1000);
            if (id == null) db.save(row);
            objectId = row.id;
          } else {
            var row = id == null ? new RatingScale() : db.get(RatingScale.class, id);
            if (id != null) check(Objects.equals(row.sessionId, s.id), "WRONG_SESSION");
            else check(scales(s.id).size() < 8, "SCALE_LIMIT");
            check(
                v.minimum != null
                    && v.maximum != null
                    && v.minimum >= 0
                    && v.maximum <= 10
                    && v.minimum < v.maximum,
                "INVALID_SCALE");
            row.sessionId = s.id;
            row.code = code;
            row.name = text(v.name, 120);
            row.minimum = v.minimum;
            row.maximum = v.maximum;
            row.lowAnchor = text(v.lowAnchor, 120);
            row.highAnchor = text(v.highAnchor, 120);
            row.required = Boolean.TRUE.equals(v.required);
            if (id == null) db.save(row);
            objectId = row.id;
          }
          s.version++;
          editMark(s);
          event(
              s,
              "session",
              s.id,
              "DEFINITION_SAVE",
              "维护方案定义",
              Map.of("kind", type, "id", objectId));
          return Map.of("id", objectId, "sessionId", s.id, "version", s.version);
        });
  }

  /** 只删除尚未冻结的定义，保留操作者审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object deleteDefinition(String type, Long id, Command v) {
    lock();
    access.require("session.write");
    var payload = List.of(type + ".delete", id, v);
    var prior =
        db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", v.requestKey);
    if (!prior.isEmpty()) {
      check(
          prior.getFirst().fingerprint.equals(hash(List.of(who(), payload))), "REQUEST_KEY_REUSED");
      var cached = json.readTree(prior.getFirst().responseJson);
      var parent = session(cached.path("sessionId").asLong());
      writer(parent);
      return write(
          v.requestKey,
          payload,
          () -> {
            throw new IllegalStateException();
          });
    }
    var row =
        type.equals("samples") ? db.get(BlindSample.class, id) : db.get(RatingScale.class, id);
    Long sessionId = row instanceof BlindSample b ? b.sessionId : ((RatingScale) row).sessionId;
    var s = session(sessionId);
    writer(s);
    return write(
        v.requestKey,
        List.of(type + ".delete", id, v),
        () -> {
          version(s.version, v.version);
          check(EDITABLE.contains(s.status), "FROZEN");
          db.delete(row);
          s.version++;
          editMark(s);
          event(
              s,
              "session",
              s.id,
              "DEFINITION_DELETE",
              text(v.note, 1000),
              Map.of("kind", type, "id", id));
          return Map.of("id", id, "sessionId", s.id, "version", s.version, "deleted", true);
        });
  }

  /** 设置唯一内部评价名单；一旦审批冻结，账号和顺序不可替换。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveRoster(Long id, Input v) {
    lock();
    var s = session(id);
    writer(s);
    return write(
        v.requestKey,
        List.of("roster.save", id, v),
        () -> {
          version(s.version, v.version);
          check(EDITABLE.contains(s.status), "FROZEN");
          check(
              v.raterIds != null
                  && v.raterIds.size() >= 4
                  && v.raterIds.size() <= 32
                  && new HashSet<>(v.raterIds).size() == v.raterIds.size(),
              "INVALID_ROSTER");
          for (Long r : v.raterIds) {
            eligible(r, "rating.write");
            check(
                !editor(s, r)
                    && !Objects.equals(r, s.reviewerId)
                    && !Objects.equals(r, s.custodianId)
                    && !Objects.equals(r, who()),
                "BLIND_ROLE_CONFLICT");
          }
          for (var old : sheets(s.id)) if (!v.raterIds.contains(old.raterId)) db.delete(old);
          for (Long r : v.raterIds)
            if (sheets(s.id).stream().noneMatch(x -> Objects.equals(x.raterId, r))) {
              limit(PanelSheet.class, 10000);
              var p = new PanelSheet();
              p.sessionId = s.id;
              p.raterId = r;
              p.status = "DRAFT";
              db.save(p);
            }
          s.version++;
          editMark(s);
          event(s, "session", s.id, "ROSTER_SAVE", "更新内部评价名单", Map.of("count", v.raterIds.size()));
          return saved(s);
        });
  }

  private void ready(PanelSession s) {
    var products = samples(s.id);
    var metrics = scales(s.id);
    var people = sheets(s.id);
    check(
        products.size() >= 2
            && products.size() <= 8
            && metrics.size() >= 1
            && metrics.stream().anyMatch(m -> m.required)
            && people.size() >= 4
            && people.size() <= 32
            && people.size() % products.size() == 0,
        "INCOMPLETE_DESIGN");
    eligible(s.reviewerId, "session.review");
    eligible(s.reviewerId, "rating.review");
    eligible(s.custodianId, "session.key");
    check(
        !editor(s, s.reviewerId) && !Objects.equals(s.reviewerId, s.custodianId),
        "INDEPENDENT_REVIEW_REQUIRED");
    for (var p : people) {
      eligible(p.raterId, "rating.write");
      check(
          !editor(s, p.raterId)
              && !Objects.equals(p.raterId, s.reviewerId)
              && !Objects.equals(p.raterId, s.custodianId),
          "BLIND_ROLE_CONFLICT");
    }
  }

  private void allocate(PanelSession s) {
    var products = samples(s.id);
    var people = sheets(s.id);
    var allocations =
        BlindAllocator.allocate(
            products.size(), people.stream().map(p -> p.raterId).toList(), new SecureRandom());
    for (var a : allocations) {
      var p = new Presentation();
      p.sheetId =
          people.stream().filter(x -> x.raterId == a.raterId()).findFirst().orElseThrow().id;
      p.sampleId = products.get(a.sampleIndex()).id;
      p.position = a.position();
      p.blindCode = a.code();
      db.save(p);
    }
    db.flush();
    s.layoutHash =
        hash(
            people.stream()
                .map(
                    p ->
                        Map.of(
                            "sheetId", p.id, "raterId", p.raterId, "items", blindItems(p, false)))
                .toList());
  }

  private boolean complete(PanelSheet p) {
    var metrics = scales(p.sessionId).stream().filter(m -> m.required).toList();
    var values = ratings(p.id);
    return presentations(p.id).stream()
        .allMatch(
            i ->
                metrics.stream()
                    .allMatch(
                        m ->
                            values.stream()
                                .anyMatch(
                                    v ->
                                        Objects.equals(v.presentationId, i.id)
                                            && Objects.equals(v.scaleId, m.id))));
  }

  private Map<String, Object> sealedFacts(PanelSession s) {
    return Map.of(
        "layoutHash",
        s.layoutHash,
        "sheets",
        sheets(s.id).stream()
            .map(
                p ->
                    Map.of(
                        "id",
                        p.id,
                        "status",
                        p.status,
                        "responseHash",
                        p.responseHash == null ? "" : p.responseHash,
                        "withdrawalReason",
                        p.withdrawalReason == null ? "" : p.withdrawalReason))
            .toList());
  }

  /** 提交、审批布局、开评、封存与双人解盲；幂等重放前仍检查权限和岗位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object sessionCommand(Long id, String action, Command v) {
    lock();
    var s = session(id);
    if (Set.of("approve", "return", "seal", "reopen", "unblind").contains(action))
      reviewer(s, "session.review");
    else if (action.equals("requestUnblind")) {
      keyAccess(s);
      if (!Objects.equals(who(), s.custodianId))
        throw new Problem(403, "ASSIGNED_CUSTODIAN_REQUIRED");
    } else writer(s);
    return write(
        v.requestKey,
        List.of("session.command", id, action, v),
        () -> {
          version(s.version, v.version);
          String note = text(v.note, 1000);
          switch (action) {
            case "submit" -> {
              check(EDITABLE.contains(s.status), "INVALID_STATE");
              ready(s);
              s.status = "SUBMITTED";
            }
            case "return" -> {
              check(s.status.equals("SUBMITTED"), "INVALID_STATE");
              s.status = "RETURNED";
            }
            case "approve" -> {
              check(s.status.equals("SUBMITTED"), "INVALID_STATE");
              ready(s);
              allocate(s);
              s.status = "APPROVED";
              s.approvedBy = who();
              s.approvedAt = now();
            }
            case "start" -> {
              check(s.status.equals("APPROVED"), "INVALID_STATE");
              ready(s);
              s.status = "RUNNING";
            }
            case "end" -> {
              check(s.status.equals("RUNNING"), "INVALID_STATE");
              check(
                  sheets(s.id).stream()
                          .allMatch(p -> Set.of("ACCEPTED", "WITHDRAWN").contains(p.status))
                      && sheets(s.id).stream().filter(p -> p.status.equals("ACCEPTED")).count()
                          >= 2,
                  "INCOMPLETE_RATINGS");
              s.status = "REVIEW";
              s.outcome = "FINISHED";
            }
            case "abort" -> {
              check(Set.of("APPROVED", "RUNNING").contains(s.status), "INVALID_STATE");
              check(
                  sheets(s.id).stream().noneMatch(p -> p.status.equals("SUBMITTED")),
                  "PENDING_REVIEW");
              for (var p : sheets(s.id))
                if (EDITABLE.contains(p.status)) {
                  p.status = "WITHDRAWN";
                  p.withdrawalReason = "中止：" + note;
                  p.version++;
                }
              s.status = "REVIEW";
              s.outcome = "ABORTED";
            }
            case "reopen" -> {
              check(s.status.equals("REVIEW"), "INVALID_STATE");
              s.status = "RUNNING";
              s.outcome = null;
            }
            case "seal" -> {
              check(s.status.equals("REVIEW"), "INVALID_STATE");
              check(
                  sheets(s.id).stream()
                      .allMatch(p -> Set.of("ACCEPTED", "WITHDRAWN").contains(p.status)),
                  "INCOMPLETE_RATINGS");
              s.sealedHash = hash(sealedFacts(s));
              s.sealedBy = who();
              s.sealedAt = now();
              s.status = "SEALED";
            }
            case "requestUnblind" -> {
              check(s.status.equals("SEALED"), "INVALID_STATE");
              s.releaseRequestedBy = who();
              s.status = "UNBLIND_PENDING";
            }
            case "unblind" -> {
              check(s.status.equals("UNBLIND_PENDING"), "INVALID_STATE");
              check(!Objects.equals(who(), s.releaseRequestedBy), "INDEPENDENT_REVIEW_REQUIRED");
              check(Objects.equals(s.sealedHash, hash(sealedFacts(s))), "SEALED_DATA_CHANGED");
              s.status = "UNBLINDED";
              s.releasedBy = who();
              s.releasedAt = now();
              s.resultHash =
                  hash(
                      Map.of(
                          "sealedHash", s.sealedHash, "key", keyRows(s), "statistics", summary(s)));
            }
            case "cancel" -> {
              check(EDITABLE.contains(s.status) || s.status.equals("SUBMITTED"), "INVALID_STATE");
              s.status = "CANCELLED";
              s.outcome = "CANCELLED";
            }
            default -> throw new Problem(404, "NOT_FOUND");
          }
          s.version++;
          event(s, "session", s.id, "SESSION_" + action.toUpperCase(Locale.ROOT), note, saved(s));
          return saved(s);
        });
  }

  private PanelSheet sheet(Long id) {
    var p = db.get(PanelSheet.class, id);
    var s = session(p.sessionId);
    if (member(s) && !Objects.equals(p.raterId, who())) throw new Problem(403, "OUT_OF_SCOPE");
    if (scoringOnly() && !Objects.equals(p.raterId, who())) throw new Problem(403, "OUT_OF_SCOPE");
    return p;
  }

  /** 本人逐样整数评分，缺测不计为零；不可绕过收悉或冻结。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveRating(RatingInput v) {
    lock();
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var i = db.get(Presentation.class, v.presentationId);
    var p = sheet(i.sheetId);
    var s = session(p.sessionId);
    rater(s, p);
    return write(
        v.requestKey,
        List.of("rating.save", v),
        () -> {
          version(p.version, v.version);
          check(
              s.status.equals("RUNNING") && EDITABLE.contains(p.status) && p.acknowledgedAt != null,
              "RATING_LOCKED");
          var scale = db.get(RatingScale.class, v.scaleId);
          check(Objects.equals(scale.sessionId, s.id), "WRONG_SESSION");
          String missing = optional(v.missingReason, 500), note = optional(v.note, 500);
          check(
              (v.value == null && !missing.isBlank()) || (v.value != null && missing.isBlank()),
              "VALUE_OR_MISSING_REQUIRED");
          if (v.value != null)
            check(v.value >= scale.minimum && v.value <= scale.maximum, "OUT_OF_SCALE");
          var rows =
              db.query(
                  RatingValue.class,
                  "from RatingValue where presentationId=?1 and scaleId=?2",
                  i.id,
                  scale.id);
          var r = rows.isEmpty() ? new RatingValue() : rows.getFirst();
          if (rows.isEmpty()) {
            limit(RatingValue.class, 10000);
            r.presentationId = i.id;
            r.scaleId = scale.id;
          }
          r.value = v.value;
          r.missingReason = missing;
          r.note = note;
          r.updatedAt = now();
          if (rows.isEmpty()) db.save(r);
          p.version++;
          event(
              s,
              "sheet",
              p.id,
              "RATING_SAVE",
              "本人评分修订",
              Map.of(
                  "blindCode",
                  i.blindCode,
                  "scaleCode",
                  scale.code,
                  "value",
                  v.value == null ? "MISSING" : v.value));
          return sheetView(s, p);
        });
  }

  /** 评分收悉、提交、退回、接受和退出；复核只检查完整性，不改主观数值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object sheetCommand(Long id, String action, Command v) {
    lock();
    var p = sheet(id);
    var s = session(p.sessionId);
    if (Set.of("accept", "return", "exclude").contains(action)) reviewer(s, "rating.review");
    else rater(s, p);
    return write(
        v.requestKey,
        List.of("sheet.command", id, action, v),
        () -> {
          version(p.version, v.version);
          check(s.status.equals("RUNNING"), "RATING_LOCKED");
          String note = text(v.note, 1000);
          switch (action) {
            case "acknowledge" -> {
              check(EDITABLE.contains(p.status) && p.acknowledgedAt == null, "INVALID_STATE");
              p.acknowledgedAt = now();
            }
            case "submit" -> {
              check(EDITABLE.contains(p.status) && p.acknowledgedAt != null, "INVALID_STATE");
              check(complete(p), "INCOMPLETE_RATINGS");
              p.status = "SUBMITTED";
              p.submittedAt = now();
              p.responseHash = hash(ratings(p.id));
            }
            case "return" -> {
              check(p.status.equals("SUBMITTED"), "INVALID_STATE");
              p.status = "RETURNED";
            }
            case "accept" -> {
              check(p.status.equals("SUBMITTED") && complete(p), "INVALID_STATE");
              check(Objects.equals(p.responseHash, hash(ratings(p.id))), "SUBMITTED_DATA_CHANGED");
              p.status = "ACCEPTED";
              p.acceptedBy = who();
              p.acceptedAt = now();
            }
            case "withdraw" -> {
              check(EDITABLE.contains(p.status), "INVALID_STATE");
              p.status = "WITHDRAWN";
              p.withdrawalReason = note;
            }
            case "exclude" -> {
              check(p.status.equals("SUBMITTED"), "INVALID_STATE");
              p.status = "WITHDRAWN";
              p.withdrawalReason = note;
            }
            default -> throw new Problem(404, "NOT_FOUND");
          }
          p.version++;
          event(
              s,
              "sheet",
              p.id,
              "SHEET_" + action.toUpperCase(Locale.ROOT),
              note,
              Map.of("status", p.status, "version", p.version));
          return sheetView(s, p);
        });
  }

  private Map<String, Object> publicSession(PanelSession s) {
    var map = new LinkedHashMap<String, Object>();
    map.put("id", s.id);
    map.put("reference", s.reference);
    map.put("name", s.name);
    map.put("departmentId", s.departmentId);
    map.put("category", s.category);
    map.put("instructions", s.instructions);
    map.put("reviewerId", s.reviewerId);
    map.put("custodianId", s.custodianId);
    map.put("createdBy", s.createdBy);
    map.put("status", s.status);
    map.put("version", s.version);
    map.put("outcome", s.outcome);
    map.put("layoutHash", s.layoutHash);
    map.put("sealedHash", s.sealedHash);
    map.put("resultHash", s.resultHash);
    map.put("approvedAt", s.approvedAt);
    map.put("sealedAt", s.sealedAt);
    map.put("releasedAt", s.releasedAt);
    map.put("createdAt", s.createdAt);
    map.put("sampleCount", samples(s.id).size());
    map.put("sheetCount", sheets(s.id).size());
    map.put(
        "acceptedCount", sheets(s.id).stream().filter(p -> p.status.equals("ACCEPTED")).count());
    return map;
  }

  private boolean scoresVisible(PanelSession s, PanelSheet p) {
    if (Objects.equals(p.raterId, who())) return true;
    if (s.status.equals("UNBLINDED")) return true;
    return Objects.equals(who(), s.reviewerId)
        && access.role().permissions.contains("rating.review")
        && Set.of("SUBMITTED", "ACCEPTED", "WITHDRAWN").contains(p.status)
        && !member(s);
  }

  private List<?> blindItems(PanelSheet p, boolean unblind) {
    return presentations(p.id).stream()
        .map(
            i -> {
              var m = new LinkedHashMap<String, Object>();
              m.put("id", i.id);
              m.put("position", i.position);
              m.put("blindCode", i.blindCode);
              if (unblind) {
                var b = db.get(BlindSample.class, i.sampleId);
                m.put("sampleCode", b.code);
                m.put("sampleName", b.name);
              }
              return m;
            })
        .toList();
  }

  private Map<String, Object> sheetView(PanelSession s, PanelSheet p) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", p.id);
    m.put("sessionId", p.sessionId);
    m.put("raterId", p.raterId);
    m.put("raterName", db.get(Account.class, p.raterId).displayName);
    m.put("status", p.status);
    m.put("version", p.version);
    m.put("acknowledgedAt", p.acknowledgedAt);
    m.put("submittedAt", p.submittedAt);
    m.put("acceptedAt", p.acceptedAt);
    m.put(
        "withdrawalReason",
        Objects.equals(who(), p.raterId) || !member(s) ? p.withdrawalReason : null);
    m.put("responseHash", p.responseHash);
    m.put("complete", complete(p));
    m.put("scoresVisible", scoresVisible(s, p));
    m.put("presentations", blindItems(p, s.status.equals("UNBLINDED")));
    m.put("ratings", scoresVisible(s, p) ? ratings(p.id) : List.of());
    return m;
  }

  private List<?> keyRows(PanelSession s) {
    return sheets(s.id).stream()
        .flatMap(
            p ->
                presentations(p.id).stream()
                    .map(
                        i -> {
                          var b = db.get(BlindSample.class, i.sampleId);
                          return Map.of(
                              "sheetId",
                              p.id,
                              "raterId",
                              p.raterId,
                              "position",
                              i.position,
                              "blindCode",
                              i.blindCode,
                              "sampleId",
                              b.id,
                              "sampleCode",
                              b.code,
                              "sampleName",
                              b.name);
                        }))
        .toList();
  }

  private List<?> summary(PanelSession s) {
    var values =
        sheets(s.id).stream()
            .filter(p -> p.status.equals("ACCEPTED"))
            .flatMap(p -> ratings(p.id).stream())
            .toList();
    var out = new ArrayList<Map<String, Object>>();
    for (var b : samples(s.id))
      for (var scale : scales(s.id)) {
        var rows =
            values.stream()
                .filter(
                    r ->
                        r.scaleId.equals(scale.id)
                            && db.get(Presentation.class, r.presentationId).sampleId.equals(b.id))
                .toList();
        var numbers = rows.stream().filter(r -> r.value != null).map(r -> r.value).toList();
        var m = new LinkedHashMap<String, Object>();
        m.put("sampleCode", b.code);
        m.put("sampleName", b.name);
        m.put("scaleCode", scale.code);
        m.put("scaleName", scale.name);
        m.put("n", numbers.size());
        m.put("missing", rows.stream().filter(r -> r.value == null).count());
        m.put(
            "mean",
            numbers.isEmpty()
                ? null
                : BigDecimal.valueOf(numbers.stream().mapToInt(Integer::intValue).sum())
                    .divide(BigDecimal.valueOf(numbers.size()), 4, RoundingMode.HALF_UP));
        m.put("min", numbers.stream().min(Integer::compare).orElse(null));
        m.put("max", numbers.stream().max(Integer::compare).orElse(null));
        out.add(m);
      }
    return out;
  }

  private List<BusinessEvent> history(PanelSession s) {
    if (member(s))
      return db.jpql(
              BusinessEvent.class,
              "from BusinessEvent e where e.objectType='sheet' and e.objectId in(select id from PanelSheet where sessionId=?1 and raterId=?2) order by e.id desc")
          .setParameter(1, s.id)
          .setParameter(2, who())
          .setMaxResults(200)
          .getResultList();
    return db.jpql(
            BusinessEvent.class,
            "from BusinessEvent e where (e.objectType='session' and e.objectId=?1) or (e.objectType='sheet' and e.objectId in(select id from PanelSheet where sessionId=?1)) order by e.id desc")
        .setParameter(1, s.id)
        .setMaxResults(200)
        .getResultList();
  }

  /** 授权方案详情；参与评分身份始终优先限制，管理员权限不会扩大本人评分范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    access.require("session.read");
    var s = session(id);
    var result = new LinkedHashMap<String, Object>();
    result.put("session", publicSession(s));
    result.put("scales", scales(id));
    result.put(
        "sheets",
        sheets(id).stream()
            .filter(p -> !member(s) && !scoringOnly() || Objects.equals(p.raterId, who()))
            .map(p -> sheetView(s, p))
            .toList());
    boolean canEdit =
        access.role().permissions.contains("session.write")
            && scope(s)
            && !member(s)
            && EDITABLE.contains(s.status);
    result.put("samples", canEdit ? samples(id) : List.of());
    result.put("statistics", s.status.equals("UNBLINDED") ? summary(s) : List.of());
    result.put("editable", canEdit);
    result.put(
        "isReviewer", Objects.equals(who(), s.reviewerId) && !editor(s, who()) && !member(s));
    result.put("isCustodian", Objects.equals(who(), s.custodianId) && !member(s));
    result.put(
        "isWriter", access.role().permissions.contains("session.write") && scope(s) && !member(s));
    result.put(
        "keyAllowed",
        !member(s)
            && access.role().permissions.contains("session.key")
            && (Objects.equals(who(), s.custodianId) || editor(s, who()) && scope(s)));
    var ownIds =
        sheets(id).stream().filter(p -> Objects.equals(p.raterId, who())).map(p -> p.id).toList();
    result.put(
        "events",
        history(s).stream()
            .filter(
                e ->
                    e.objectType.equals("session") && e.objectId.equals(id)
                        || e.objectType.equals("sheet")
                            && sheets(id).stream().anyMatch(p -> p.id.equals(e.objectId)))
            .filter(e -> !member(s) || e.objectType.equals("sheet") && ownIds.contains(e.objectId))
            .map(
                e -> {
                  var m = new LinkedHashMap<String, Object>();
                  m.put("id", e.id);
                  m.put("objectType", e.objectType);
                  m.put("objectId", e.objectId);
                  m.put("actorId", e.actorId);
                  m.put("action", e.action);
                  m.put("createdAt", e.createdAt);
                  m.put("note", e.note);
                  return m;
                })
            .toList());
    return result;
  }

  /** 身份映射单独检查岗位并记录访问，不混入普通详情或导出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object key(Long id) {
    lock();
    access.require("session.read");
    var s = session(id);
    keyAccess(s);
    access.audit("KEY_READ", id, s.departmentId);
    return Map.of("samples", samples(id), "mapping", keyRows(s));
  }

  /** 有界列表进行授权后搜索，评分身份看不到未分配方案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    access.require("session.read");
    if (search.length() > 100
        || page < 0
        || size < 1
        || size > 100
        || !Set.of("newest", "oldest").contains(sort)) throw new Problem(400, "INVALID_INPUT");
    var rows =
        db.all(PanelSession.class).stream()
            .filter(this::visible)
            .filter(s -> status.isBlank() || s.status.equals(status))
            .filter(
                s ->
                    (s.reference + " " + s.name)
                        .toLowerCase(Locale.ROOT)
                        .contains(search.toLowerCase(Locale.ROOT)))
            .sorted(
                sort.equals("newest")
                    ? Comparator.comparing((PanelSession s) -> s.id).reversed()
                    : Comparator.comparing(s -> s.id))
            .map(this::publicSession)
            .toList();
    long start = (long) page * size;
    return Map.of(
        "items",
        rows.subList((int) Math.min(start, rows.size()), (int) Math.min(start + size, rows.size())),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 表单目录不暴露口令；评分员不读取全员清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("session.read");
    boolean reduced = scoringOnly() || access.role().scope.equals("SELF");
    var people =
        db.all(Account.class).stream()
            .filter(a -> a.enabled && (!reduced || a.id.equals(who())))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "name",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList();
    return Map.of(
        "people",
        people,
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "categories",
        db.query(DictionaryEntry.class, "from DictionaryEntry where type='category'"),
        "settings",
        db.all(SystemSetting.class));
  }

  /** 范围内进度与解盲后统计，不在封存前展示汇总分数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var rows = db.all(PanelSession.class).stream().filter(this::visible).toList();
    return Map.of(
        "sessions",
        rows.size(),
        "running",
        rows.stream().filter(s -> s.status.equals("RUNNING")).count(),
        "sealed",
        rows.stream().filter(s -> Set.of("SEALED", "UNBLIND_PENDING").contains(s.status)).count(),
        "unblinded",
        rows.stream().filter(s -> s.status.equals("UNBLINDED")).count(),
        "recent",
        rows.stream()
            .sorted(Comparator.comparing((PanelSession s) -> s.id).reversed())
            .limit(8)
            .map(this::publicSession)
            .toList());
  }

  private String cell(Object value) {
    if (value == null) return "";
    String s = value.toString();
    if (!s.stripLeading().isEmpty() && "=+-@".indexOf(s.stripLeading().charAt(0)) >= 0) s = "'" + s;
    return '"' + s.replace("\"", "\"\"") + '"';
  }

  /** 评分CSV在解盲前仍按身份密封，不把缺测变成零，不插入品牌广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String csv(Long id) {
    access.require("export");
    access.require("session.read");
    var s = session(id);
    var text =
        new StringBuilder(
            "\uFEFFsheetId,raterId,status,position,blindCode,sampleCode,scaleCode,value,missingReason,note\r\n");
    for (var p : sheets(id)) {
      if (member(s) && !Objects.equals(who(), p.raterId)
          || scoringOnly() && !Objects.equals(who(), p.raterId)
          || !scoresVisible(s, p)) continue;
      for (var r : ratings(p.id)) {
        var i = db.get(Presentation.class, r.presentationId);
        var scale = db.get(RatingScale.class, r.scaleId);
        Object[] row = {
          p.id,
          p.raterId,
          p.status,
          i.position,
          i.blindCode,
          s.status.equals("UNBLINDED") ? db.get(BlindSample.class, i.sampleId).code : "",
          scale.code,
          r.value,
          r.missingReason,
          r.note
        };
        for (int k = 0; k < row.length; k++) {
          if (k > 0) text.append(',');
          text.append(cell(row[k]));
        }
        text.append("\r\n");
      }
    }
    return text.toString();
  }
}
