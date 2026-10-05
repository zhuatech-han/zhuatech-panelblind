// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import java.util.*;
import java.util.random.RandomGenerator;

/** 循环置换让每个产品在每个呈现位置出现相同次数；不保证一阶顺序残留平衡。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class BlindAllocator {
  private BlindAllocator() {}

  /** 分配项包含内部样品索引，服务不得直接向评分端序列化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Entry(long raterId, int sampleIndex, int position, String code) {}

  /**
   * 接受显式随机源供确定性测试；实际服务使用SecureRandom且不发布随机源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  public static List<Entry> allocate(int samples, List<Long> raters, RandomGenerator random) {
    if (samples < 2
        || samples > 8
        || raters == null
        || raters.size() < 4
        || raters.size() > 32
        || raters.size() % samples != 0
        || new HashSet<>(raters).size() != raters.size()) throw new Problem(400, "INVALID_DESIGN");
    var products = new ArrayList<Integer>();
    for (int i = 0; i < samples; i++) products.add(i);
    shuffle(products, random);
    var people = new ArrayList<>(raters);
    shuffle(people, random);
    var codes = new ArrayList<Integer>();
    for (int i = 100; i < 1000; i++) codes.add(i);
    shuffle(codes, random);
    var out = new ArrayList<Entry>();
    int next = 0;
    for (int row = 0; row < people.size(); row++)
      for (int col = 0; col < samples; col++)
        out.add(
            new Entry(
                people.get(row),
                products.get((col + row) % samples),
                col + 1,
                codes.get(next++).toString()));
    return List.copyOf(out);
  }

  private static <T> void shuffle(List<T> list, RandomGenerator r) {
    for (int i = list.size() - 1; i > 0; i--) Collections.swap(list, i, r.nextInt(i + 1));
  }
}
