// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import jakarta.persistence.*;

/** session_editor的持久化事实；身份映射仅由授权服务返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "session_editor")
public class SessionEditor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "session_id", nullable = false)
  public Long sessionId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;
}
