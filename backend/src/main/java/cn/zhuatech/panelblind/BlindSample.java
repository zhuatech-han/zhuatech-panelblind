// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import jakarta.persistence.*;

/** blind_sample的持久化事实；身份映射仅由授权服务返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "blind_sample")
public class BlindSample {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "session_id", nullable = false)
  public Long sessionId;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @Column(name = "description", nullable = false, length = 1000)
  public String description;
}
