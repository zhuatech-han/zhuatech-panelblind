// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import jakarta.persistence.*;
import java.time.Instant;

/** panel_session的持久化事实；身份映射仅由授权服务返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "panel_session")
public class PanelSession {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = false, length = 60)
  public String reference;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "instructions", nullable = false, length = 2000)
  public String instructions;

  @Column(name = "reviewer_id", nullable = false)
  public Long reviewerId;

  @Column(name = "custodian_id", nullable = false)
  public Long custodianId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "version", nullable = false)
  public long version;

  @Column(name = "outcome", nullable = true, length = 20)
  public String outcome;

  @Column(name = "layout_hash", nullable = true, length = 64)
  public String layoutHash;

  @Column(name = "sealed_hash", nullable = true, length = 64)
  public String sealedHash;

  @Column(name = "result_hash", nullable = true, length = 64)
  public String resultHash;

  @Column(name = "approved_by", nullable = true)
  public Long approvedBy;

  @Column(name = "approved_at", nullable = true)
  public Instant approvedAt;

  @Column(name = "sealed_by", nullable = true)
  public Long sealedBy;

  @Column(name = "sealed_at", nullable = true)
  public Instant sealedAt;

  @Column(name = "release_requested_by", nullable = true)
  public Long releaseRequestedBy;

  @Column(name = "released_by", nullable = true)
  public Long releasedBy;

  @Column(name = "released_at", nullable = true)
  public Instant releasedAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
