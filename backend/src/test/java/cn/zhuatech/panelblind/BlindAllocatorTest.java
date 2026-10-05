// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.panelblind;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;

/** 独立核验位置次数、完整覆盖、盲码唯一性与非法设计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class BlindAllocatorTest {
  @Test
  void everyDesignBalancesAllProductsAtEachPosition() {
    for (int k = 2; k <= 8; k++)
      for (int n = k; n <= 32; n += k) {
        if (n < 4) continue;
        var people = LongStream.rangeClosed(1, n).boxed().toList();
        var rows = BlindAllocator.allocate(k, people, new Random(10L * k + n));
        assertEquals(k * n, rows.size());
        assertEquals(k * n, rows.stream().map(BlindAllocator.Entry::code).distinct().count());
        for (long person : people) {
          var per = rows.stream().filter(r -> r.raterId() == person).toList();
          assertEquals(k, per.stream().map(BlindAllocator.Entry::sampleIndex).distinct().count());
          assertEquals(k, per.stream().map(BlindAllocator.Entry::position).distinct().count());
        }
        for (int sample = 0; sample < k; sample++)
          for (int position = 1; position <= k; position++) {
            int s = sample, p = position;
            assertEquals(
                n / k,
                rows.stream().filter(r -> r.sampleIndex() == s && r.position() == p).count());
          }
        assertTrue(rows.stream().allMatch(r -> r.code().matches("[1-9][0-9]{2}")));
      }
  }

  @Test
  void deterministicOnlyWithAnExplicitTestRandomSource() {
    var ids = List.of(3L, 5L, 9L, 11L);
    assertEquals(
        BlindAllocator.allocate(2, ids, new Random(71)),
        BlindAllocator.allocate(2, ids, new Random(71)));
    assertNotEquals(
        BlindAllocator.allocate(2, ids, new Random(71)),
        BlindAllocator.allocate(2, ids, new Random(72)));
  }

  @Test
  void insufficientPanelRejected() {
    assertThrows(Problem.class, () -> BlindAllocator.allocate(2, List.of(1L, 2L), new Random(1)));
  }

  @Test
  void unbalancedPanelRejected() {
    assertThrows(
        Problem.class, () -> BlindAllocator.allocate(3, List.of(1L, 2L, 3L, 4L), new Random(1)));
  }

  @Test
  void duplicatePeopleRejected() {
    assertThrows(
        Problem.class, () -> BlindAllocator.allocate(2, List.of(1L, 2L, 1L, 4L), new Random(1)));
  }

  @Test
  void outsideBoundsRejected() {
    assertThrows(
        Problem.class,
        () ->
            BlindAllocator.allocate(
                9, LongStream.rangeClosed(1, 18).boxed().toList(), new Random(1)));
  }
}
