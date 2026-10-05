// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import jakarta.persistence.*;
import java.time.Instant;

/** rating_value的持久化事实；身份映射仅由授权服务返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "rating_value")
public class RatingValue {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "presentation_id", nullable = false)
  public Long presentationId;

  @Column(name = "scale_id", nullable = false)
  public Long scaleId;

  @Column(name = "score_value", nullable = true)
  public Integer value;

  @Column(name = "missing_reason", nullable = false, length = 500)
  public String missingReason;

  @Column(name = "note", nullable = false, length = 500)
  public String note;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
