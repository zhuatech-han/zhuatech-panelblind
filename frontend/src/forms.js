// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 方案、样品、量表与身份管理的有限表单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const fields = {
  sessions: [
    ["reference", "方案编号", "Reference"],
    ["name", "方案名称", "Name"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["category", "产品类型", "Product type", "select", "categories"],
    ["reviewerId", "独立审签员", "Independent reviewer", "id", "reviewers"],
    ["custodianId", "样品保管员", "Sample custodian", "id", "custodians"],
    ["instructions", "盲评操作说明", "Evaluation instructions", "textarea"],
  ],
  samples: [
    ["code", "内部样品编号", "Internal sample code"],
    ["name", "真实产品名称", "Product identity"],
    ["description", "样品说明", "Sample description", "textarea"],
  ],
  scales: [
    ["code", "量表编号", "Scale code"],
    ["name", "评价维度", "Rating dimension"],
    ["minimum", "最低整数分（0—9）", "Minimum (0–9)", "integer"],
    ["maximum", "最高整数分（1—10）", "Maximum (1–10)", "integer"],
    ["lowAnchor", "最低分含义", "Low anchor"],
    ["highAnchor", "最高分含义", "High anchor"],
    ["required", "提交前必录", "Required before submission", "boolean"],
  ],
  roster: [
    [
      "raterIds",
      "内部评分员（4—32，人数为样品数整数倍）",
      "Internal raters (4–32, multiple of sample count)",
      "ids",
      "raters",
    ],
  ],
  rating: [
    [
      "value",
      "整数评分（缺测时留空）",
      "Integer score (blank if missing)",
      "integer",
    ],
    [
      "missingReason",
      "缺测原因（有分数时留空）",
      "Missing reason (blank with score)",
      "textarea",
    ],
    ["note", "本人评价说明", "My evaluation note", "textarea"],
  ],
  command: [["note", "操作事实／原因", "Reason / facts", "textarea"]],
  users: [
    ["username", "登录名", "Username"],
    ["displayName", "姓名", "Name"],
    [
      "password",
      "新密码（编辑时留空保留）",
      "New password (optional when editing)",
      "password",
    ],
    ["roleId", "角色", "Role", "id", "roles"],
    ["departmentId", "部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  roles: [
    ["name", "角色名称", "Role name"],
    ["scope", "数据范围", "Data scope", "select", "scope"],
    ["permissions", "接口权限", "API permissions", "permissions"],
  ],
  departments: [["name", "部门名称", "Department name"]],
  menus: [
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
    [
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ],
    ["position", "排序", "Order", "integer"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  permissions: [["name", "权限说明", "Permission description"]],
  dictionaries: [
    ["type", "字典类型", "Dictionary type"],
    ["code", "编码", "Code"],
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
  ],
  settings: [["value", "参数值", "Value"]],
};
