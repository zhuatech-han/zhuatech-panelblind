// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { actions, payload, canRate } from "./domain.js";
// 服务端岗位标记与身份约束分别验收；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
test("writer flag required even with administrator permission", () =>
  assert.deepEqual(
    actions(
      "sessions",
      { status: "RUNNING" },
      { permissions: ["session.write"] },
      {},
    ),
    [],
  ));
test("assigned reviewer approves and returns the frozen request", () =>
  assert.deepEqual(
    actions(
      "sessions",
      { status: "SUBMITTED" },
      { permissions: ["session.review"] },
      { isReviewer: true },
    ),
    ["approve", "return"],
  ));
test("custodian requests release only after sealing", () => {
  const me = { permissions: ["session.key"] };
  assert.deepEqual(
    actions("sessions", { status: "SEALED" }, me, { isCustodian: true }),
    ["requestUnblind"],
  );
  assert.deepEqual(
    actions("sessions", { status: "RUNNING" }, me, { isCustodian: true }),
    [],
  );
});
test("final released scores never show editing commands", () =>
  assert.deepEqual(
    actions(
      "sheets",
      { raterId: 2, status: "ACCEPTED" },
      { id: 2, permissions: ["rating.write"] },
      { session: { status: "UNBLINDED" } },
    ),
    [],
  ));
test("another persons sheet never offers rating controls", () =>
  assert.equal(
    canRate(
      { raterId: 3, status: "DRAFT", acknowledgedAt: "now" },
      { status: "RUNNING" },
      { id: 2, permissions: ["rating.write"] },
    ),
    false,
  ));
test("acknowledgement and editable state both needed", () => {
  const me = { id: 2, permissions: ["rating.write"] };
  assert.equal(
    canRate({ raterId: 2, status: "DRAFT" }, { status: "RUNNING" }, me),
    false,
  );
  assert.equal(
    canRate(
      { raterId: 2, status: "DRAFT", acknowledgedAt: "now" },
      { status: "RUNNING" },
      me,
    ),
    true,
  );
});
test("blank score stays null but explicit zero stays zero", () => {
  const fields = [["value", "", "", "integer"]];
  assert.equal(payload({ value: "" }, fields).value, null);
  assert.equal(payload({ value: "0" }, fields).value, 0);
});
test("finite payload ignores client supplied state and identity mapping", () =>
  assert.deepEqual(
    payload({ value: "8", status: "ACCEPTED", sampleId: 10 }, [
      ["value", "", "", "integer"],
    ]),
    { value: 8 },
  ));
test("roster ids are numeric while permission codes remain strings", () =>
  assert.deepEqual(
    payload({ raterIds: ["2", "5"], permissions: ["session.read"] }, [
      ["raterIds", "", "", "ids"],
      ["permissions", "", "", "permissions"],
    ]),
    { raterIds: [2, 5], permissions: ["session.read"] },
  ));
