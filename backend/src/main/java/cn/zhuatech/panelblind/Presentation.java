// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import jakarta.persistence.*;

/** presentation的持久化事实；身份映射仅由授权服务返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "presentation")
public class Presentation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "sheet_id", nullable = false)
  public Long sheetId;

  @Column(name = "sample_id", nullable = false)
  public Long sampleId;

  @Column(name = "blind_code", nullable = false, length = 3)
  public String blindCode;

  @Column(name = "position", nullable = false)
  public int position;
}
