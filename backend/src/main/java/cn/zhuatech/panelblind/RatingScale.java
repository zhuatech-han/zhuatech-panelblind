// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import jakarta.persistence.*;

/** rating_scale的持久化事实；身份映射仅由授权服务返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "rating_scale")
public class RatingScale {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "session_id", nullable = false)
  public Long sessionId;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "minimum", nullable = false)
  public int minimum;

  @Column(name = "maximum", nullable = false)
  public int maximum;

  @Column(name = "low_anchor", nullable = false, length = 120)
  public String lowAnchor;

  @Column(name = "high_anchor", nullable = false, length = 120)
  public String highAnchor;

  @Column(name = "required", nullable = false)
  public boolean required;
}
