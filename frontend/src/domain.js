// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 页面状态词典；服务器仍独立校验实际授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const states = {
  DRAFT: ["草稿", "Draft"],
  RETURNED: ["已退回", "Returned"],
  SUBMITTED: ["待复核", "Submitted"],
  APPROVED: ["方案冻结", "Approved"],
  RUNNING: ["评分中", "Running"],
  REVIEW: ["待封存审签", "Seal review"],
  SEALED: ["评分已封存", "Sealed"],
  UNBLIND_PENDING: ["待独立解盲", "Unblind review"],
  UNBLINDED: ["已解盲冻结", "Unblinded"],
  CANCELLED: ["已取消", "Cancelled"],
  ACCEPTED: ["评分已接受", "Accepted"],
  WITHDRAWN: ["已退出／排除", "Withdrawn"],
  FINISHED: ["正常完成", "Finished"],
  ABORTED: ["已中止", "Aborted"],
};
/** 按实际返回的岗位标志显示动作，参与评分优先限定本人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(type, row, me, detail = {}) {
  if (!row || !me) return [];
  const has = (p) => me.permissions?.includes(p),
    out = [];
  if (type === "sessions") {
    if (has("session.write") && detail.isWriter) {
      if (["DRAFT", "RETURNED"].includes(row.status))
        out.push("submit", "cancel");
      if (row.status === "SUBMITTED") out.push("cancel");
      if (row.status === "APPROVED") out.push("start", "abort");
      if (row.status === "RUNNING") out.push("end", "abort");
    }
    if (has("session.review") && detail.isReviewer) {
      if (row.status === "SUBMITTED") out.push("approve", "return");
      if (row.status === "REVIEW") out.push("seal", "reopen");
      if (row.status === "UNBLIND_PENDING") out.push("unblind");
    }
    if (has("session.key") && detail.isCustodian && row.status === "SEALED")
      out.push("requestUnblind");
  }
  if (type === "sheets" && detail.session?.status === "RUNNING") {
    if (
      has("rating.write") &&
      row.raterId === me.id &&
      ["DRAFT", "RETURNED"].includes(row.status)
    ) {
      if (!row.acknowledgedAt) out.push("acknowledge");
      else if (row.complete) out.push("submit");
      out.push("withdraw");
    }
    if (has("rating.review") && detail.isReviewer && row.status === "SUBMITTED")
      out.push("accept", "return", "exclude");
  }
  return out;
}
/** 有限字段生成；空评分为null，名单为数字数组，防止把缺测转为零。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function payload(form, fields) {
  const out = {};
  for (const [key, , , type] of fields) {
    const v = form[key];
    out[key] = ["integer", "id"].includes(type)
      ? v == null || v === ""
        ? null
        : Number(v)
      : type === "boolean"
        ? Boolean(v)
        : ["ids", "permissions"].includes(type)
          ? (v || []).map((x) => (type === "ids" ? Number(x) : x))
          : (v ?? "");
  }
  return out;
}
/** 判定本人仍可编辑的评分格。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function canRate(sheet, session, me) {
  return (
    me?.permissions?.includes("rating.write") &&
    sheet?.raterId === me.id &&
    session?.status === "RUNNING" &&
    ["DRAFT", "RETURNED"].includes(sheet.status) &&
    Boolean(sheet.acknowledgedAt)
  );
}
