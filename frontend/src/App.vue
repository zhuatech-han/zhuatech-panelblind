<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  EyeOff,
  Grid2X2,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Plus,
  Search,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
  RefreshCw,
  ExternalLink,
  Clock3,
  AlertCircle,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { actions, states, payload, canRate } from "./domain.js";
import { fields } from "./forms.js";
const lang = ref(localStorage.getItem("panelblind-language") || "zh"),
  me = ref(null),
  view = ref("sessions"),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  loginForm = ref({ username: "", password: "" }),
  options = ref({}),
  directories = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(0),
  search = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  stats = ref({}),
  modal = ref(null),
  form = ref({}),
  contact = ref(false),
  keyData = ref(null),
  tab = ref("plan"),
  selectedSheet = ref(null);
const t = (zh, en) => (lang.value === "zh" ? zh : en),
  can = (p) => me.value?.permissions?.includes(p),
  adminTypes = [
    "users",
    "roles",
    "departments",
    "menus",
    "permissions",
    "dictionaries",
    "settings",
  ];
const labels = {
  sessions: ["盲评方案", "Blind panels"],
  dashboard: ["盲评统计", "Statistics"],
  audit: ["操作审计", "Audit"],
  users: ["账号管理", "Accounts"],
  roles: ["角色与权限", "Roles"],
  departments: ["部门管理", "Departments"],
  menus: ["导航管理", "Navigation"],
  permissions: ["权限目录", "Permissions"],
  dictionaries: ["产品类型", "Product types"],
  settings: ["系统参数", "Settings"],
};
const icons = {
  sessions: EyeOff,
  dashboard: BarChart3,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
};
const title = computed(() =>
    t(...(labels[view.value] || ["PanelBlind", "PanelBlind"])),
  ),
  record = computed(() => detail.value?.session),
  statusName = (s) => t(...(states[s] || [s || "—", s || "—"]));
const actionNames = {
  submit: ["提交复核", "Submit"],
  approve: ["批准并生成盲码", "Approve & code"],
  return: ["退回修订", "Return"],
  cancel: ["取消方案", "Cancel panel"],
  start: ["开始评分", "Start evaluation"],
  end: ["结束并申请封存", "End for sealing"],
  abort: ["登记中止", "Abort evaluation"],
  seal: ["审签并封存评分", "Seal responses"],
  reopen: ["退回开评状态", "Reopen"],
  requestUnblind: ["申请解盲", "Request unblinding"],
  unblind: ["独立批准解盲", "Approve unblinding"],
  acknowledge: ["确认收悉说明", "Acknowledge"],
  accept: ["接受完整评分", "Accept response"],
  exclude: ["排除并保留评分", "Exclude response"],
  withdraw: ["退出本次盲评", "Withdraw"],
};
const actionName = (a) => t(...(actionNames[a] || [a, a]));
const failures = {
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired"],
  FORBIDDEN: ["当前岗位无此权限", "Permission required"],
  OUT_OF_SCOPE: ["超出账号授权范围", "Outside your scope"],
  KEY_ACCESS_DENIED: [
    "样品身份只向获授权保管岗位开放",
    "Identity mapping restricted",
  ],
  ASSIGNED_REVIEWER_REQUIRED: [
    "须由指定独立审签员操作",
    "Assigned reviewer required",
  ],
  ASSIGNED_CUSTODIAN_REQUIRED: [
    "须由指定样品保管员申请",
    "Assigned custodian required",
  ],
  ASSIGNED_RATER_REQUIRED: ["只能填写本人评分单", "Assigned rater required"],
  BLIND_ROLE_CONFLICT: [
    "评分员不能兼任方案编辑、保管或审签岗位",
    "Rater cannot edit, hold the key or review",
  ],
  INDEPENDENT_REVIEW_REQUIRED: [
    "须由未编辑方案且未参与评分的独立审签员操作",
    "Independent reviewer required",
  ],
  INCOMPLETE_DESIGN: [
    "至少2个样品、1个必录量表和4位评分员；人数为样品数整数倍",
    "Use 2+ samples, a required scale and 4+ raters; count must be a multiple of samples",
  ],
  INVALID_ROSTER: ["评分名单为4—32个不同账号", "Use 4–32 distinct accounts"],
  INCOMPLETE_RATINGS: [
    "补齐必录评分或明确缺测；正常结束须至少两张已接受评分单",
    "Complete required responses; finish needs at least two accepted sheets",
  ],
  VALUE_OR_MISSING_REQUIRED: [
    "分数与缺测原因须且只能填写一项",
    "Enter either a score or missing reason",
  ],
  OUT_OF_SCALE: ["评分超出量表范围", "Score outside scale"],
  INVALID_SCALE: [
    "量表须为0—10内的递增整数范围",
    "Use an integer range within 0–10",
  ],
  RATING_LOCKED: [
    "尚未收悉、已提交或方案已冻结，无法修改评分",
    "Response is locked or not acknowledged",
  ],
  FROZEN: ["方案已经冻结", "Protocol frozen"],
  STALE_VERSION: ["记录已变化，请刷新后重试", "Record changed; refresh first"],
  INELIGIBLE_ASSIGNMENT: [
    "账号已停用或缺少所需权限",
    "Account disabled or missing permission",
  ],
  PENDING_REVIEW: [
    "仍有待复核评分，先处理后中止",
    "Resolve submitted responses first",
  ],
  INVALID_STATE: ["当前状态不允许操作", "Action unavailable in this state"],
  INVALID_INPUT: ["检查必填字段与输入范围", "Check required fields"],
  CONFLICT: ["编号重复或记录被引用", "Duplicate or referenced record"],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect credentials"],
  LOGIN_THROTTLED: ["登录尝试过多，稍后重试", "Too many attempts"],
  LAST_ADMIN: [
    "至少保留一个启用的全范围管理员",
    "Keep an enabled full administrator",
  ],
  WEAK_PASSWORD: [
    "密码至少12位且包含大小写字母和数字",
    "Use 12+ characters, upper/lower case and digits",
  ],
  REQUEST_KEY_REUSED: [
    "请求键已用于其他内容，请重新打开表单",
    "Request key already used",
  ],
  IMMUTABLE_REFERENCE: [
    "方案编号和负责部门不可修改",
    "Reference and department cannot change",
  ],
};
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("panelblind-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
function clearSession() {
  me.value = null;
  detail.value = null;
  keyData.value = null;
  modal.value = null;
  rows.value = [];
  options.value = {};
  directories.value = {};
  resetCsrf();
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    return await fn();
  } catch (e) {
    error.value = failures[e.message]
      ? t(...failures[e.message])
      : t("操作失败：", "Action failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") clearSession();
    if (["FORBIDDEN", "KEY_ACCESS_DENIED", "OUT_OF_SCOPE"].includes(e.message))
      keyData.value = null;
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  options.value = await api("/options");
  if (can("admin") && me.value.scope === "ALL")
    for (const key of ["roles", "permissions", "departments"])
      directories.value[key] = await api("/admin/" + key);
}
async function load() {
  detail.value = null;
  keyData.value = null;
  if (view.value === "dashboard") {
    stats.value = await api("/dashboard");
    return;
  }
  if (view.value === "sessions") {
    const r = await api(
      "/sessions?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          page: String(page.value),
          size: "12",
          sort: sort.value,
        }),
    );
    rows.value = r.items;
    total.value = r.total;
  } else {
    const all = (
      await api(view.value === "audit" ? "/audit" : "/admin/" + view.value)
    ).filter((r) =>
      Object.values(r).some(
        (x) =>
          typeof x === "string" &&
          x.toLowerCase().includes(search.value.toLowerCase()),
      ),
    );
    all.sort((a, b) => (sort.value === "newest" ? b.id - a.id : a.id - b.id));
    total.value = all.length;
    rows.value = all.slice(page.value * 12, page.value * 12 + 12);
  }
}
async function navigate(code) {
  if (busy.value) return;
  // 清空上一页记录后切换列定义，避免加载期间按新表结构读取旧记录。
  rows.value = [];
  total.value = 0;
  view.value = code;
  page.value = 0;
  search.value = "";
  filter.value = "";
  await run(load);
  window.scrollTo(0, 0);
}
async function signIn() {
  await run(async () => {
    resetCsrf();
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    clearSession();
  });
}
async function open(row) {
  await run(async () => {
    detail.value = await api("/sessions/" + row.id);
    keyData.value = null;
    tab.value = detail.value.editable ? "plan" : "sheets";
    selectedSheet.value =
      detail.value.sheets.find((p) => p.raterId === me.value.id)?.id ||
      detail.value.sheets[0]?.id;
    window.scrollTo(0, 0);
  });
}
async function refresh() {
  await run(async () => {
    me.value = await api("/auth/me");
    keyData.value = null;
    await loadOptions();
    if (record.value) detail.value = await api("/sessions/" + record.value.id);
    else await load();
  });
}
const sheet = computed(() =>
    detail.value?.sheets.find((p) => p.id === selectedSheet.value),
  ),
  currentActions = computed(() =>
    actions("sessions", record.value, me.value, detail.value),
  ),
  modalFields = computed(() =>
    modal.value?.kind === "password"
      ? [
          ["oldPassword", "原密码", "Current password", "password"],
          ["newPassword", "新密码", "New password", "password"],
        ]
      : fields[modal.value?.type] || [],
  );
const canCreate = computed(() =>
  view.value === "sessions"
    ? can("session.write")
    : adminTypes.includes(view.value) &&
      !["permissions", "menus", "settings"].includes(view.value),
);
function choices(key) {
  if (key === "scope")
    return ["ALL", "DEPARTMENT", "SELF"].map((value) => ({
      value,
      label: {
        ALL: t("全部", "All"),
        DEPARTMENT: t("本部门", "Department"),
        SELF: t("本人创建或获指派", "Self or assigned"),
      }[value],
    }));
  let list = directories.value[key] || options.value[key] || [];
  if (["reviewers", "custodians", "raters"].includes(key)) {
    const permission = {
      reviewers: "session.review",
      custodians: "session.key",
      raters: "rating.write",
    }[key];
    list = (options.value.people || []).filter((p) =>
      p.permissions.includes(permission),
    );
    if (key === "reviewers")
      list = list.filter(
        (p) => p.id !== me.value.id && p.permissions.includes("rating.review"),
      );
    if (key === "raters")
      list = list.filter(
        (p) =>
          p.id !== me.value.id &&
          p.id !== record.value?.reviewerId &&
          p.id !== record.value?.custodianId,
      );
  }
  return list.map((v) => ({
    value: ["categories", "permissions"].includes(key) ? v.code : v.id,
    label:
      lang.value === "en" && v.nameEn
        ? v.nameEn
        : v.name || v.displayName || v.code,
  }));
}
function edit(type, row = null, extras = {}) {
  error.value = "";
  modal.value = { kind: "save", type, row, ...extras };
  form.value = {
    ...row,
    ...extras,
    requestKey: crypto.randomUUID(),
    version: record.value?.version,
    departmentId: me.value.departmentId,
    category: "PACKAGING",
    enabled: true,
    minimum: 1,
    maximum: 9,
    lowAnchor: t("非常不喜欢", "Dislike strongly"),
    highAnchor: t("非常喜欢", "Like strongly"),
    required: true,
    ...row,
  };
  if (type === "roster")
    form.value.raterIds = detail.value.sheets.map((p) => p.raterId);
  if (type === "users") form.value.password = "";
  if (type === "roles") form.value.permissions = [...(row?.permissions || [])];
}
function command(type, row, action) {
  modal.value = { kind: "command", type: "command", target: type, row, action };
  form.value = {
    requestKey: crypto.randomUUID(),
    version: row.version,
    note: "",
  };
  error.value = "";
}
function rate(p, i, scale) {
  const value = p.ratings.find(
    (r) => r.presentationId === i.id && r.scaleId === scale.id,
  );
  modal.value = {
    kind: "rating",
    type: "rating",
    row: p,
    presentation: i,
    scale,
  };
  form.value = {
    requestKey: crypto.randomUUID(),
    version: p.version,
    value: value?.value ?? "",
    missingReason: value?.missingReason || "",
    note: value?.note || "",
  };
  error.value = "";
}
function deleteItem(type, row) {
  modal.value = { kind: "delete", type: "command", target: type, row };
  form.value = {
    requestKey: crypto.randomUUID(),
    version: record.value?.version,
    note: "",
  };
}
async function save() {
  await run(async () => {
    const m = modal.value;
    let result;
    if (m.kind === "password") {
      await api("/auth/password", "POST", form.value);
      clearSession();
      notice.value = t(
        "密码已更改，请重新登录",
        "Password changed; sign in again",
      );
      return;
    }
    if (m.kind === "command") {
      result = await api(
        "/" + m.target + "/" + m.row.id + "/commands/" + m.action,
        "POST",
        form.value,
      );
    } else if (m.kind === "delete") {
      result = await api(
        "/" + m.target + "/" + m.row.id + "/delete",
        "POST",
        form.value,
      );
    } else if (m.kind === "adminDelete") {
      await api("/admin/" + m.target + "/" + m.row.id, "DELETE");
    } else if (m.kind === "rating") {
      result = await api("/ratings", "POST", {
        ...payload(form.value, modalFields.value),
        requestKey: form.value.requestKey,
        version: form.value.version,
        presentationId: m.presentation.id,
        scaleId: m.scale.id,
      });
    } else {
      const body = {
        ...payload(form.value, modalFields.value),
        requestKey: form.value.requestKey,
        version: form.value.version,
      };
      const isAdmin = adminTypes.includes(m.type);
      if (m.type === "roster")
        result = await api(
          "/sessions/" + record.value.id + "/roster",
          "PUT",
          body,
        );
      else {
        if (["samples", "scales"].includes(m.type))
          body.sessionId = record.value.id;
        result = await api(
          (isAdmin ? "/admin" : "") +
            "/" +
            m.type +
            (m.row ? "/" + m.row.id : ""),
          m.row ? "PUT" : "POST",
          body,
        );
      }
    }
    modal.value = null;
    keyData.value = null;
    notice.value = t("已保存", "Saved");
    await loadOptions();
    if (record.value) detail.value = await api("/sessions/" + record.value.id);
    else if (m.type === "sessions" && result) {
      detail.value = await api("/sessions/" + result.id);
      tab.value = "plan";
    } else await load();
  });
}
async function showKey() {
  await run(async () => {
    keyData.value = await api("/sessions/" + record.value.id + "/key");
  });
}
async function download(kind) {
  await run(async () => {
    const path =
      "/sessions/" +
      record.value.id +
      "/" +
      (kind === "json" ? "report.json" : "ratings.csv");
    let blob;
    if (kind === "json")
      blob = new Blob([JSON.stringify(await api(path), null, 2)], {
        type: "application/json",
      });
    else {
      const response = await fetch("/api" + path);
      if (!response.ok) throw new Error((await response.json()).code);
      blob = await response.blob();
    }
    const url = URL.createObjectURL(blob),
      a = document.createElement("a");
    a.href = url;
    a.download = "panel-" + record.value.reference + "." + kind;
    a.click();
    URL.revokeObjectURL(url);
  });
}
function formatTime(value) {
  return value
    ? new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
        timeZone: "Asia/Shanghai",
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
}
function value(row, key) {
  if (key.endsWith("At")) return formatTime(row[key]);
  if (key === "status") return statusName(row[key]);
  if (["enabled", "required"].includes(key))
    return row[key] ? t("是", "Yes") : t("否", "No");
  if (key === "permissions")
    return row.permissions.length + t(" 项权限", " permissions");
  const lookup = {
    roleId: directories.value.roles,
    departmentId: directories.value.departments || options.value.departments,
  }[key];
  return lookup?.find((v) => v.id === row[key])?.name ?? row[key] ?? "—";
}
const columns = computed(
  () =>
    ({
      sessions: [
        ["reference", "方案编号", "Reference"],
        ["name", "方案名称", "Name"],
        ["sampleCount", "样品", "Samples"],
        ["sheetCount", "评分员", "Raters"],
        ["acceptedCount", "已接受", "Accepted"],
        ["status", "状态", "Status"],
      ],
      settings: [
        ["code", "参数", "Setting"],
        ["value", "参数值", "Value"],
      ],
      audit: [
        ["createdAt", "时间", "Time"],
        ["actor", "账号", "Actor"],
        ["action", "动作", "Action"],
        ["objectId", "记录", "Record"],
      ],
    })[view.value] ||
    (fields[view.value] || []).filter((f) => f[0] !== "password").slice(0, 5),
);
const ratingValue = (p, i, scale) =>
  p.ratings.find((r) => r.presentationId === i.id && r.scaleId === scale.id);
onMounted(async () => {
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
  try {
    me.value = await api("/auth/me");
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED")
      error.value = t("连接失败，请刷新页面", "Connection failed; refresh");
  }
});
</script>
<template>
  <div v-if="!me" class="login-layout">
    <section class="panel-art" aria-hidden="true">
      <div class="art-word">PanelBlind</div>
      <div class="art-caption">PRODUCT EVALUATION / BLIND RESPONSES</div>
      <div class="blind-art">
        <div v-for="n in 3" :key="n">
          <EyeOff :size="48" /><span>{{ ["A", "B", "C"][n - 1] }}</span
          ><i></i><i></i><i></i>
        </div>
      </div>
      <p>
        {{
          t("产品盲评与评分闭环", "Blind product evaluation & response review")
        }}
      </p>
    </section>
    <main class="login-panel">
      <div class="login-top">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" /><button
          class="plain"
          @click="language"
        >
          {{ lang === "zh" ? "EN" : "中文" }}
        </button>
      </div>
      <div class="login-heading">
        <span class="eyebrow">PANELBLIND</span>
        <h1>{{ t("产品盲评与评分", "Blind product evaluation") }}</h1>
        <p>{{ t("登录你的业务工作台", "Sign in to your workspace") }}</p>
      </div>
      <form @submit.prevent="signIn">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <button class="primary login-submit" :disabled="busy">
          {{ busy ? t("正在登录", "Signing in") : t("登录", "Sign in")
          }}<ArrowRight :size="18" />
        </button>
      </form>
      <div class="login-footer">
        <button class="plain" @click="contact = true">
          {{ t("知华科技 · 商业咨询", "ZhuaTech · Commercial enquiries") }}
        </button>
        <p>
          {{
            t("公开源码学习版／非商业源码版", "Non-commercial source edition")
          }}
        </p>
      </div>
    </main>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" />
        <div>
          <strong>PanelBlind</strong
          ><span>{{ t("产品盲评与评分", "Product evaluation") }}</span>
        </div>
      </div>
      <nav :aria-label="t('主要导航', 'Main navigation')">
        <template v-for="(m, i) in me.menus" :key="m.code"
          ><p
            v-if="i === 0 || m.code === 'dashboard' || m.code === 'users'"
            class="nav-section"
          >
            {{
              i === 0
                ? t("盲评协同", "OPERATIONS")
                : m.code === "dashboard"
                  ? t("统计与审计", "OVERVIEW")
                  : t("系统管理", "ADMINISTRATION")
            }}
          </p>
          <button
            :class="{ active: view === m.code }"
            :disabled="busy"
            @click="navigate(m.code)"
          >
            <component :is="icons[m.code] || Settings" :size="18" /><span>{{
              lang === "en" ? m.nameEn : m.name
            }}</span>
          </button></template
        >
      </nav>
      <div class="sidebar-footer">
        <button class="plain" @click="contact = true">
          <ExternalLink :size="14" />{{
            t("知华科技 · 咨询", "ZhuaTech · Enquiries")
          }}</button
        ><span>{{ t("非商业源码版 0.1.0", "Non-commercial 0.1.0") }}</span>
      </div>
    </aside>
    <div class="work-area">
      <header class="topbar">
        <span>{{
          options.settings?.find((s) => s.code === "companyName")?.value ||
          "PanelBlind"
        }}</span>
        <div>
          <button class="plain" @click="language">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><button
            class="plain"
            @click="
              modal = { kind: 'password', type: 'password' };
              form = { oldPassword: '', newPassword: '' };
            "
          >
            {{ me.displayName }}</button
          ><button
            class="icon-button"
            :aria-label="t('退出登录', 'Sign out')"
            :disabled="busy"
            @click="signOut"
          >
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <main class="main-content">
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <div class="page-heading">
          <div>
            <span class="eyebrow">{{
              record ? "PANEL / " + record.reference : "PANELBLIND / WORKSPACE"
            }}</span>
            <h1>{{ record ? record.name : title }}</h1>
            <p>
              {{
                record
                  ? t(
                      "方案、呈现与评分记录",
                      "Protocol, presentation & responses",
                    )
                  : t(
                      "管理授权范围内的业务记录",
                      "Records within your authorized scope",
                    )
              }}
            </p>
          </div>
          <div class="heading-actions">
            <button v-if="record" class="secondary" @click="run(load)">
              <ChevronLeft :size="17" />{{ t("返回列表", "Back") }}</button
            ><button
              class="icon-button"
              :aria-label="t('刷新', 'Refresh')"
              :disabled="busy"
              @click="refresh"
            >
              <RefreshCw :size="18" /></button
            ><button
              v-if="!record && canCreate"
              class="primary"
              :disabled="busy"
              @click="edit(view)"
            >
              <Plus :size="17" />{{ t("新建", "New") }}
            </button>
          </div>
        </div>
        <template v-if="record"
          ><section class="record-strip">
            <div>
              <small>{{ t("当前状态", "Status") }}</small
              ><span class="status" :data-status="record.status">{{
                statusName(record.status)
              }}</span>
            </div>
            <div>
              <small>{{ t("样品 / 评分员", "Samples / raters") }}</small
              ><strong
                >{{ record.sampleCount }} / {{ record.sheetCount }}</strong
              >
            </div>
            <div>
              <small>{{ t("已接受评分单", "Accepted sheets") }}</small
              ><strong
                >{{ record.acceptedCount }} / {{ record.sheetCount }}</strong
              >
            </div>
            <div>
              <small>{{ t("结局", "Outcome") }}</small
              ><strong>{{ statusName(record.outcome) }}</strong>
            </div>
          </section>
          <div class="action-row">
            <button
              v-for="a in currentActions"
              :key="a"
              class="secondary"
              :disabled="busy"
              @click="command('sessions', record, a)"
            >
              {{ actionName(a) }}</button
            ><button
              v-if="detail.keyAllowed"
              class="secondary"
              :disabled="busy"
              @click="showKey"
            >
              <EyeOff :size="16" />{{
                t("查看样品身份", "Sample identity")
              }}</button
            ><template v-if="can('export')"
              ><button class="plain" @click="download('json')">
                <Download :size="15" />JSON</button
              ><button class="plain" @click="download('csv')">
                <Download :size="15" />CSV
              </button></template
            >
          </div>
          <nav class="detail-tabs" :aria-label="t('方案详情', 'Panel details')">
            <button
              v-for="(name, k) in {
                plan: t('方案与量表', 'Protocol'),
                sheets: t('盲码与评分单', 'Blind responses'),
                result: t('解盲结果', 'Released results'),
                events: t('操作轨迹', 'History'),
              }"
              :key="k"
              :class="{ active: tab === k }"
              @click="tab = k"
            >
              {{ name }}
            </button>
          </nav>
          <section v-if="tab === 'plan'" class="plan-grid">
            <article class="panel">
              <div class="panel-heading">
                <h2>{{ t("评价方案", "Evaluation protocol") }}</h2>
                <button
                  v-if="detail.editable"
                  class="plain"
                  @click="edit('sessions', record)"
                >
                  {{ t("编辑", "Edit") }}
                </button>
              </div>
              <dl>
                <dt>{{ t("方案编号", "Reference") }}</dt>
                <dd>{{ record.reference }}</dd>
                <dt>{{ t("类型", "Category") }}</dt>
                <dd>
                  {{
                    options.categories?.find((c) => c.code === record.category)
                      ?.name || record.category
                  }}
                </dd>
                <dt>{{ t("批准时间", "Approved") }}</dt>
                <dd>{{ formatTime(record.approvedAt) }}</dd>
                <dt>{{ t("封存时间", "Sealed") }}</dt>
                <dd>{{ formatTime(record.sealedAt) }}</dd>
              </dl>
              <h3>{{ t("盲评操作说明", "Evaluation instructions") }}</h3>
              <p class="protocol-text">{{ record.instructions }}</p>
              <div v-if="record.layoutHash" class="digest">
                <small>{{ t("盲码布局摘要", "Blind layout digest") }}</small
                ><code>{{ record.layoutHash }}</code>
              </div>
            </article>
            <article class="panel">
              <div class="panel-heading">
                <h2>{{ t("评价量表", "Rating scales") }}</h2>
                <button
                  v-if="detail.editable"
                  class="plain"
                  @click="edit('scales')"
                >
                  <Plus :size="16" />{{ t("新增量表", "Add scale") }}
                </button>
              </div>
              <div v-for="s in detail.scales" :key="s.id" class="scale-card">
                <div>
                  <strong>{{ s.name }}</strong
                  ><small
                    >{{ s.code }} ·
                    {{
                      s.required ? t("必录", "Required") : t("可选", "Optional")
                    }}</small
                  >
                </div>
                <p>
                  {{ s.minimum }} · {{ s.lowAnchor }} <span>—</span>
                  {{ s.maximum }} · {{ s.highAnchor }}
                </p>
                <div v-if="detail.editable" class="inline-actions">
                  <button class="plain" @click="edit('scales', s)">
                    {{ t("编辑", "Edit") }}</button
                  ><button
                    class="plain danger"
                    @click="deleteItem('scales', s)"
                  >
                    {{ t("删除", "Delete") }}
                  </button>
                </div>
              </div>
              <p v-if="!detail.scales.length" class="empty">
                {{ t("尚未设置量表", "No rating scales") }}
              </p>
            </article>
            <article v-if="detail.editable" class="panel full-width">
              <div class="panel-heading">
                <h2>{{ t("内部样品身份", "Internal sample identities") }}</h2>
                <button class="plain" @click="edit('samples')">
                  <Plus :size="16" />{{ t("新增样品", "Add sample") }}
                </button>
              </div>
              <div class="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("编号", "Code") }}</th>
                      <th>{{ t("产品名称", "Product identity") }}</th>
                      <th>{{ t("说明", "Description") }}</th>
                      <th>{{ t("操作", "Actions") }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="s in detail.samples" :key="s.id">
                      <td>{{ s.code }}</td>
                      <td>{{ s.name }}</td>
                      <td>{{ s.description }}</td>
                      <td>
                        <button class="plain" @click="edit('samples', s)">
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          class="plain danger"
                          @click="deleteItem('samples', s)"
                        >
                          {{ t("删除", "Delete") }}
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </article>
            <article class="panel full-width">
              <div class="panel-heading">
                <h2>{{ t("内部评价名单", "Internal panel") }}</h2>
                <button
                  v-if="detail.editable"
                  class="plain"
                  @click="edit('roster')"
                >
                  {{ t("设置名单", "Set panel") }}
                </button>
              </div>
              <div class="roster-grid">
                <div v-for="p in detail.sheets" :key="p.id">
                  <Users :size="18" /><strong>{{ p.raterName }}</strong
                  ><span class="status" :data-status="p.status">{{
                    statusName(p.status)
                  }}</span>
                </div>
              </div>
              <p v-if="!detail.sheets.length" class="empty">
                {{ t("尚未设置评价名单", "No panel members") }}
              </p>
            </article>
          </section>
          <section v-if="tab === 'sheets'" class="panel">
            <div class="panel-heading">
              <h2>{{ t("盲码评分单", "Blind response sheet") }}</h2>
              <select
                v-if="detail.sheets.length > 1"
                v-model="selectedSheet"
                :aria-label="t('选择评分单', 'Choose response sheet')"
              >
                <option v-for="p in detail.sheets" :key="p.id" :value="p.id">
                  {{ p.raterName }} · {{ statusName(p.status) }}
                </option>
              </select>
            </div>
            <template v-if="sheet"
              ><div class="sheet-heading">
                <div>
                  <strong>{{ sheet.raterName }}</strong
                  ><span class="status" :data-status="sheet.status">{{
                    statusName(sheet.status)
                  }}</span>
                </div>
                <div class="inline-actions">
                  <button
                    v-for="a in actions('sheets', sheet, me, detail)"
                    :key="a"
                    class="secondary"
                    :disabled="busy"
                    @click="command('sheets', sheet, a)"
                  >
                    {{ actionName(a) }}
                  </button>
                </div>
              </div>
              <p v-if="sheet.withdrawalReason" class="muted">
                {{ sheet.withdrawalReason }}
              </p>
              <p v-if="!sheet.scoresVisible" class="sealed-note">
                <ShieldCheck :size="18" />{{
                  t(
                    "评分保持密封；当前岗位只查看进度与呈现盲码",
                    "Scores remain sealed; this role sees progress and blind codes",
                  )
                }}
              </p>
              <div class="table-scroll">
                <table class="rating-table">
                  <thead>
                    <tr>
                      <th>{{ t("呈现顺序", "Position") }}</th>
                      <th>{{ t("样品盲码", "Blind code") }}</th>
                      <th v-for="s in detail.scales" :key="s.id">
                        {{ s.name
                        }}<small>{{ s.minimum }}—{{ s.maximum }}</small>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="i in sheet.presentations" :key="i.id">
                      <td>
                        <span class="position">{{
                          String(i.position).padStart(2, "0")
                        }}</span>
                      </td>
                      <td>
                        <strong class="blind-code">{{ i.blindCode }}</strong
                        ><small v-if="i.sampleName"
                          >{{ i.sampleCode }} · {{ i.sampleName }}</small
                        >
                      </td>
                      <td v-for="s in detail.scales" :key="s.id">
                        <div class="score-cell">
                          <span v-if="!sheet.scoresVisible" class="muted">{{
                            t("密封", "Sealed")
                          }}</span
                          ><template v-else
                            ><strong
                              v-if="ratingValue(sheet, i, s)?.value != null"
                              >{{ ratingValue(sheet, i, s).value }}</strong
                            ><span
                              v-else-if="
                                ratingValue(sheet, i, s)?.missingReason
                              "
                              class="missing"
                              >{{ t("明确缺测", "Missing") }}</span
                            ><span v-else class="muted">—</span
                            ><button
                              v-if="canRate(sheet, record, me)"
                              class="plain"
                              :aria-label="
                                t('填写盲码', 'Rate code') +
                                ' ' +
                                i.blindCode +
                                ' · ' +
                                s.name
                              "
                              @click="rate(sheet, i, s)"
                            >
                              {{
                                ratingValue(sheet, i, s)
                                  ? t("修改", "Edit")
                                  : t("填写", "Rate")
                              }}
                            </button></template
                          >
                        </div>
                        <small
                          v-if="
                            sheet.scoresVisible &&
                            ratingValue(sheet, i, s)?.missingReason
                          "
                          >{{ ratingValue(sheet, i, s).missingReason }}</small
                        ><small
                          v-if="
                            sheet.scoresVisible &&
                            ratingValue(sheet, i, s)?.note
                          "
                          >{{ ratingValue(sheet, i, s).note }}</small
                        >
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p v-if="!sheet.presentations.length" class="empty">
                {{
                  t(
                    "方案批准后生成盲码与呈现顺序",
                    "Blind codes are generated on approval",
                  )
                }}
              </p></template
            >
            <p v-else class="empty">
              {{ t("暂无授权评分单", "No assigned response sheet") }}
            </p>
          </section>
          <section v-if="tab === 'result'" class="panel">
            <div class="panel-heading">
              <h2>
                {{ t("解盲描述统计", "Released descriptive statistics") }}
              </h2>
              <span
                v-if="record.status === 'UNBLINDED'"
                class="status"
                data-status="UNBLINDED"
                >{{ t("数据已冻结", "Data frozen") }}</span
              >
            </div>
            <template v-if="record.status === 'UNBLINDED'"
              ><div class="result-grid">
                <div
                  v-for="m in detail.statistics"
                  :key="m.sampleCode + '-' + m.scaleCode"
                  class="result-card"
                >
                  <small>{{ m.sampleCode }} · {{ m.scaleName }}</small>
                  <h3>{{ m.sampleName }}</h3>
                  <strong
                    >{{ m.mean ?? "—"
                    }}<small>{{ t("均分", "Mean") }}</small></strong
                  >
                  <div class="score-bar">
                    <i
                      :style="{
                        width:
                          (m.mean == null
                            ? 0
                            : (100 * m.mean) /
                              (detail.scales.find((s) => s.code === m.scaleCode)
                                ?.maximum || 10)) + '%',
                      }"
                    ></i>
                  </div>
                  <p>
                    N={{ m.n }} · {{ t("缺测", "Missing") }} {{ m.missing }} ·
                    {{ t("范围", "Range") }} {{ m.min ?? "—" }}—{{
                      m.max ?? "—"
                    }}
                  </p>
                </div>
              </div>
              <div class="digest">
                <small>{{
                  t("封存摘要 / 结果摘要", "Sealed / result digests")
                }}</small
                ><code>{{ record.sealedHash }}</code
                ><code>{{ record.resultHash }}</code>
              </div>
              <p class="muted">
                {{
                  t(
                    "只计入已接受评分单的非缺测值；退出记录保留，不参与统计",
                    "Only accepted nonmissing responses are included; withdrawn records are retained",
                  )
                }}
              </p></template
            >
            <p v-else class="empty">
              <EyeOff :size="28" />{{
                t(
                  "完成封存和独立解盲后显示样品对应结果",
                  "Results are available after sealing and independent unblinding",
                )
              }}
            </p>
          </section>
          <section v-if="tab === 'events'" class="panel">
            <div class="panel-heading">
              <h2>{{ t("操作轨迹", "Action history") }}</h2>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("动作", "Action") }}</th>
                    <th>{{ t("账号ID", "Actor ID") }}</th>
                    <th>{{ t("事实说明", "Reason / facts") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="e in [...detail.events].reverse()" :key="e.id">
                    <td>{{ formatTime(e.createdAt) }}</td>
                    <td>{{ e.action }}</td>
                    <td>{{ e.actorId }}</td>
                    <td>{{ e.note }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>
        <template v-else-if="view === 'dashboard'"
          ><div class="metric-grid">
            <article
              v-for="(label, k) in {
                sessions: t('授权方案', 'Authorized panels'),
                running: t('正在评分', 'Running'),
                sealed: t('已封存待解盲', 'Sealed'),
                unblinded: t('已解盲', 'Unblinded'),
              }"
              :key="k"
              class="metric"
            >
              <component
                :is="
                  k === 'sessions'
                    ? EyeOff
                    : k === 'running'
                      ? Grid2X2
                      : k === 'sealed'
                        ? ShieldCheck
                        : BarChart3
                "
                :size="21"
              /><span>{{ label }}</span
              ><strong>{{ stats[k] || 0 }}</strong>
            </article>
          </div>
          <section class="panel">
            <div class="panel-heading">
              <h2>{{ t("近期方案进度", "Recent panel progress") }}</h2>
            </div>
            <div
              v-for="r in stats.recent || []"
              :key="r.id"
              class="progress-row"
            >
              <div>
                <strong>{{ r.name }}</strong
                ><small>{{ r.reference }} · {{ statusName(r.status) }}</small>
              </div>
              <div class="score-bar">
                <i
                  :style="{
                    width:
                      (r.sheetCount
                        ? (100 * r.acceptedCount) / r.sheetCount
                        : 0) + '%',
                  }"
                ></i>
              </div>
              <span>{{ r.acceptedCount }} / {{ r.sheetCount }}</span>
            </div>
            <p v-if="!stats.recent?.length" class="empty">
              {{ t("尚无授权方案", "No authorized panels") }}
            </p>
          </section></template
        >
        <section v-else class="panel">
          <form
            class="filter-bar"
            @submit.prevent="
              page = 0;
              run(load);
            "
          >
            <label class="search"
              ><Search :size="17" /><input
                v-model="search"
                :placeholder="t('搜索记录', 'Search records')"
                :aria-label="t('搜索记录', 'Search records')"
                maxlength="100" /></label
            ><select
              v-if="view === 'sessions'"
              v-model="filter"
              :aria-label="t('状态筛选', 'Filter status')"
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option
                v-for="s in [
                  'DRAFT',
                  'RETURNED',
                  'SUBMITTED',
                  'APPROVED',
                  'RUNNING',
                  'REVIEW',
                  'SEALED',
                  'UNBLIND_PENDING',
                  'UNBLINDED',
                  'CANCELLED',
                ]"
                :key="s"
                :value="s"
              >
                {{ statusName(s) }}
              </option></select
            ><select v-model="sort" :aria-label="t('排序', 'Sort')">
              <option value="newest">
                {{ t("最新优先", "Newest first") }}
              </option>
              <option value="oldest">
                {{ t("最早优先", "Oldest first") }}
              </option></select
            ><button class="secondary" :disabled="busy">
              {{ t("查询", "Search") }}
            </button>
          </form>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th v-for="c in columns" :key="c[0]">{{ t(c[1], c[2]) }}</th>
                  <th v-if="view !== 'audit'">{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td v-for="c in columns" :key="c[0]">
                    <span
                      v-if="c[0] === 'status'"
                      class="status"
                      :data-status="r.status"
                      >{{ statusName(r.status) }}</span
                    ><template v-else>{{ value(r, c[0]) }}</template>
                  </td>
                  <td v-if="view !== 'audit'">
                    <button
                      v-if="view === 'sessions'"
                      class="plain"
                      @click="open(r)"
                    >
                      {{ t("打开方案", "Open panel")
                      }}<ArrowRight :size="14" /></button
                    ><template v-else
                      ><button class="plain" @click="edit(view, r)">
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="
                          !['menus', 'permissions', 'settings'].includes(view)
                        "
                        class="plain danger"
                        @click="
                          modal = {
                            kind: 'adminDelete',
                            type: 'command',
                            target: view,
                            row: r,
                          };
                          form = { note: '' };
                        "
                      >
                        {{ t("删除", "Delete") }}
                      </button></template
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="!rows.length" class="empty">
            <EyeOff :size="30" />
            <h3>{{ t("暂无记录", "No records") }}</h3>
            <p>
              {{
                t(
                  "当前筛选和授权范围内没有记录",
                  "No records within this filter and scope",
                )
              }}
            </p>
          </div>
          <footer class="pagination">
            <span
              >{{ t("共", "Total") }} {{ total }}
              {{ t("条记录", "records") }}</span
            >
            <div>
              <button
                class="icon-button"
                :aria-label="t('上一页', 'Previous page')"
                :disabled="page === 0 || busy"
                @click="
                  page--;
                  run(load);
                "
              >
                <ChevronLeft :size="16" /></button
              ><span>{{ page + 1 }}</span
              ><button
                class="icon-button"
                :aria-label="t('下一页', 'Next page')"
                :disabled="(page + 1) * 12 >= total || busy"
                @click="
                  page++;
                  run(load);
                "
              >
                <ChevronRight :size="16" />
              </button>
            </div>
          </footer>
        </section>
      </main>
    </div>
  </div>
  <div v-if="modal" class="modal-backdrop" @click.self="modal = null">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('编辑与操作', 'Edit or action')"
    >
      <header>
        <h2>
          {{
            modal.kind === "command"
              ? actionName(modal.action)
              : modal.kind === "rating"
                ? t("填写盲码", "Rate code") +
                  " " +
                  modal.presentation.blindCode
                : modal.kind === "password"
                  ? t("修改密码", "Change password")
                  : ["delete", "adminDelete"].includes(modal.kind)
                    ? t("确认删除", "Confirm deletion")
                    : t("编辑记录", "Edit record")
          }}
        </h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="modal = null"
        >
          <X :size="19" />
        </button>
      </header>
      <p v-if="modal.kind === 'rating'" class="scale-note">
        {{ modal.scale.name }} · {{ modal.scale.minimum }}
        {{ modal.scale.lowAnchor }} — {{ modal.scale.maximum }}
        {{ modal.scale.highAnchor }}
      </p>
      <form @submit.prevent="save">
        <div class="form-grid">
          <label
            v-for="f in modalFields"
            :key="f[0]"
            :class="{
              wide: ['textarea', 'permissions', 'ids'].includes(f[3]),
              check: f[3] === 'boolean',
            }"
            ><span>{{ t(f[1], f[2]) }}</span
            ><input
              v-if="f[3] === 'boolean'"
              v-model="form[f[0]]"
              type="checkbox" />
            <div
              v-else-if="['ids', 'permissions'].includes(f[3])"
              class="check-list"
            >
              <label
                v-for="c in f[3] === 'permissions'
                  ? (directories.permissions || []).map((p) => ({
                      value: p.code,
                      label: p.name,
                    }))
                  : choices(f[4])"
                :key="c.value"
                ><input
                  v-model="form[f[0]]"
                  type="checkbox"
                  :value="c.value"
                />{{ c.label }}</label
              >
            </div>
            <select
              v-else-if="['id', 'select'].includes(f[3])"
              v-model="form[f[0]]"
              required
              :disabled="
                modal.row &&
                modal.type === 'sessions' &&
                f[0] === 'departmentId'
              "
            >
              <option value="">{{ t("请选择", "Select") }}</option>
              <option
                v-for="c in choices(f[4])"
                :key="c.value"
                :value="c.value"
              >
                {{ c.label }}
              </option></select
            ><textarea
              v-else-if="f[3] === 'textarea'"
              v-model="form[f[0]]"
              rows="3"
              :required="!(modal.kind === 'rating')"
              :maxlength="
                f[0] === 'instructions'
                  ? 2000
                  : f[0] === 'description' ||
                      (f[0] === 'note' && modal.kind !== 'rating')
                    ? 1000
                    : 500
              "
            ></textarea
            ><input
              v-else
              v-model="form[f[0]]"
              :type="
                f[3] === 'password'
                  ? 'password'
                  : f[3] === 'integer'
                    ? 'number'
                    : 'text'
              "
              :step="f[3] === 'integer' ? 1 : undefined"
              :required="
                !(
                  modal.kind === 'rating' ||
                  (modal.type === 'users' && modal.row && f[0] === 'password')
                )
              "
              :disabled="
                modal.row && modal.type === 'sessions' && f[0] === 'reference'
              "
              :maxlength="
                f[3] === 'password'
                  ? 72
                  : f[0] === 'reference' ||
                      f[0] === 'code' ||
                      f[0] === 'username'
                    ? 60
                    : 200
              "
              autocomplete="off"
          /></label>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <footer>
          <button type="button" class="secondary" @click="modal = null">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? t("保存中", "Saving") : t("确认保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="keyData" class="modal-backdrop" @click.self="keyData = null">
    <section
      class="modal key-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('样品身份映射', 'Sample identity key')"
    >
      <header>
        <h2>{{ t("样品身份映射", "Sample identity key") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="keyData = null"
        >
          <X :size="19" />
        </button>
      </header>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>{{ t("评分单ID", "Sheet ID") }}</th>
              <th>{{ t("位置", "Position") }}</th>
              <th>{{ t("盲码", "Blind code") }}</th>
              <th>{{ t("内部编号", "Internal code") }}</th>
              <th>{{ t("真实产品", "Product identity") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="i in keyData.mapping"
              :key="i.sheetId + '-' + i.position"
            >
              <td>{{ i.sheetId }}</td>
              <td>{{ i.position }}</td>
              <td>
                <strong>{{ i.blindCode }}</strong>
              </td>
              <td>{{ i.sampleCode }}</td>
              <td>{{ i.sampleName }}</td>
            </tr>
          </tbody>
        </table>
        <div
          v-for="s in keyData.mapping.length ? [] : keyData.samples"
          :key="s.id"
          class="scale-card"
        >
          <strong>{{ s.code }} · {{ s.name }}</strong>
          <p>{{ s.description }}</p>
        </div>
      </div>
    </section>
  </div>
  <div v-if="contact" class="modal-backdrop" @click.self="contact = false">
    <section
      class="modal contact-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('联系知华科技', 'Contact ZhuaTech')"
    >
      <header>
        <h2>{{ t("联系知华科技", "Contact ZhuaTech") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="contact = false"
        >
          <X :size="19" />
        </button>
      </header>
      <img class="contact-logo" src="/brand/logo.jpg" alt="知华科技 LOGO" />
      <h3>上海如静知华信息科技有限公司</h3>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >https://www.zhuatech.cn/ <ExternalLink :size="15"
      /></a>
      <p>
        {{
          t(
            "商业授权、定制开发、部署与系统集成",
            "Commercial licensing, custom development, deployment & integration",
          )
        }}
      </p>
      <div class="qr-grid">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech 二维码" />
          <figcaption>微信 zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2 二维码" />
          <figcaption>微信 zhuatech2</figcaption>
        </figure>
      </div>
      <p class="license-note">
        {{
          t(
            "公开源码学习版／非商业源码版；未经书面授权不得商用",
            "Non-commercial source edition; commercial use requires written authorization",
          )
        }}
      </p>
    </section>
  </div>
</template>
