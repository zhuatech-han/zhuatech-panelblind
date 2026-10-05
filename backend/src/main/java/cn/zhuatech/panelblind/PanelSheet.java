// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import jakarta.persistence.*;
import java.time.Instant;

/** panel_sheet的持久化事实；身份映射仅由授权服务返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "panel_sheet")
public class PanelSheet {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "session_id", nullable = false)
  public Long sessionId;

  @Column(name = "rater_id", nullable = false)
  public Long raterId;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "version", nullable = false)
  public long version;

  @Column(name = "acknowledged_at", nullable = true)
  public Instant acknowledgedAt;

  @Column(name = "submitted_at", nullable = true)
  public Instant submittedAt;

  @Column(name = "accepted_by", nullable = true)
  public Long acceptedBy;

  @Column(name = "accepted_at", nullable = true)
  public Instant acceptedAt;

  @Column(name = "withdrawal_reason", nullable = true, length = 1000)
  public String withdrawalReason;

  @Column(name = "response_hash", nullable = true, length = 64)
  public String responseHash;
}
