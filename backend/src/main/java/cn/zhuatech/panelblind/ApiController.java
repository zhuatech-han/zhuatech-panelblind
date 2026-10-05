// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 盲评和身份入口使用真实授权、指定岗位与密封范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final PanelService panel;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(PanelService panel, AdminService admin, AccessService access, Store db) {
    this.panel = panel;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 安全表单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return panel.options();
  }

  /** 范围内搜索和分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/sessions")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return panel.list(search, status, page, size, sort);
  }

  /** 普通详情与映射分开授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/sessions/{id}")
  public Object detail(@PathVariable Long id) {
    return panel.detail(id);
  }

  /** 映射访问记录在审计中。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/sessions/{id}/key")
  public Object key(@PathVariable Long id) {
    return panel.key(id);
  }

  /** 新建草稿或定义。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:sessions|samples|scales}")
  public Object create(@PathVariable String type, @RequestBody PanelService.Input v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    return type.equals("sessions")
        ? panel.saveSession(null, v)
        : panel.saveDefinition(type, null, v);
  }

  /** 更新草稿及其父方案版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/{type:sessions|samples|scales}/{id}")
  public Object edit(
      @PathVariable String type, @PathVariable Long id, @RequestBody PanelService.Input v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    return type.equals("sessions") ? panel.saveSession(id, v) : panel.saveDefinition(type, id, v);
  }

  /** 仅删除未冻结定义，命令保留幂等键与理由。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:samples|scales}/{id}/delete")
  public Object delete(
      @PathVariable String type, @PathVariable Long id, @RequestBody PanelService.Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    return panel.deleteDefinition(type, id, v);
  }

  /** 固定内部名单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/sessions/{id}/roster")
  public Object roster(@PathVariable Long id, @RequestBody PanelService.Input v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    return panel.saveRoster(id, v);
  }

  /** 本人逐样评分。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/ratings")
  public Object rating(@RequestBody PanelService.RatingInput v) {
    return panel.saveRating(v);
  }

  /** 方案和评分单显式状态命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:sessions|sheets}/{id}/commands/{action}")
  public Object command(
      @PathVariable String type,
      @PathVariable Long id,
      @PathVariable String action,
      @RequestBody PanelService.Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    return type.equals("sessions")
        ? panel.sessionCommand(id, action, v)
        : panel.sheetCommand(id, action, v);
  }

  /** 导出与详情范围一致。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/sessions/{id}/report.json")
  public ResponseEntity<Object> report(@PathVariable Long id) {
    access.require("export");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=panel-" + id + ".json")
        .body(panel.detail(id));
  }

  /** 密封与已解盲评分CSV。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/sessions/{id}/ratings.csv")
  public ResponseEntity<String> csv(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=panel-" + id + "-ratings.csv")
        .body(panel.csv(id));
  }

  /** 授权统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return panel.dashboard();
  }

  /** 审计目录限制到授权部门及本人范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db
        .jpql(
            AuditEvent.class,
            "from AuditEvent where (?1=true or departmentId=?2) and (?3=false or actor=?4) order by id desc")
        .setParameter(1, access.role().scope.equals("ALL"))
        .setParameter(2, access.current().departmentId)
        .setParameter(3, access.role().scope.equals("SELF"))
        .setParameter(4, access.current().username)
        .setMaxResults(500)
        .getResultList()
        .stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .toList();
  }

  /** 管理资源真实读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminEdit(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 未引用的管理资源删除，外键保护业务历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
