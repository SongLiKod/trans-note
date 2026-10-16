# 途记交通备忘录 TransNote

纯本地离线安卓工具 APP：公交 / 地铁线路个人记录、站点与地铁出入口精细化备注、离线双向检索、本地 ZIP 备份。
**无后端服务、无账号登录、不上传任何用户数据。**

- 包名：`com.transnote.traffic`
- 最低兼容：Android 10（API 29）；目标 SDK：Android 14（API 34）
- 语言：100% Kotlin 业务代码 + Compose（XML 仅资源）

---

## 1. 快速开始

```bash
# 构建 Debug APK
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

本仓库根目录已提供 `gradlew` 需要的 `gradle/wrapper/gradle-wrapper.properties`。
若使用本地 Gradle，可直接执行 `gradle :app:assembleDebug`。

---

## 2. 技术栈

| 类别 | 组件 |
| ---- | ---- |
| UI | Jetpack Compose + Material3（深浅色 / 跟随系统，实时切换） |
| 架构 | MVVM：UI → ViewModel（StateFlow）→ Repository → Room |
| 数据库 | Room（SQLite），7 张表 + 关系查询 |
| 异步 | Kotlin 协程 + Flow |
| 图片 | Coil-Compose（本地私有目录图片） |
| 相机 / OCR | CameraX + ML Kit 离线中文文字识别 |
| 地图 | osmdroid（OSM 瓦片，站点打点 + 轨迹绘制；瓦片需网络，业务全离线） |
| 配置存储 | DataStore Preferences（主题模式） |
| 文件处理 | Kotlin IO + ZIP（本地备份导入导出） |

未引入：Retrofit、推送 SDK、埋点、广告 SDK。

---

## 3. 目录结构

```
com.transnote.traffic
├── MainActivity.kt              // APP 入口，挂载 Compose + 全局主题
├── TransNoteApp.kt              // Application + 轻量依赖容器 AppContainer
├── ui
│   ├── theme                    // ColorScheme / Typography / Shapes（PRD 配色表）
│   ├── navigation               // 路由定义
│   ├── screens                  // 首页、线路编辑、线路详情、站点详情、搜索、设置、OCR
│   └── components               // 卡片、标签、图片网格、拖拽列表、地图、相机等
├── viewmodel                    // 全部 ViewModel（StateFlow）
├── repository                   // 本地仓库：Room + 文件 IO（无网络请求）
├── data
│   ├── database                 // Room Entity / DAO / Database / Relations
│   └── file                     // ImageStorage（图片读写）、BackupManager（ZIP 备份）
└── utils                        // OCR、搜索、坐标、拖拽、定位、主题管理
```

---

## 4. 功能实现对照

### 4.1 线路录入模块（PRD 3.1）

| 需求 | 实现 |
| ---- | ---- |
| 拍照录入 | `OcrScanScreen` + `CameraCaptureView`（CameraX）→ `OcrHelper`（ML Kit 中文离线识别） |
| 相册选图 | `ActivityResultContracts.PickVisualMedia` |
| 自动填充线路 / 站点 / 运营时间 | `StationBoardParser` 规则解析，回填 `LineEditScreen` 表单 |
| 识别结果人工修改 | 表单可任意编辑，站点可重命名 / 删除 |
| 手动录入 | `LineEditScreen`：类型、线路名、首末班、票价 |
| 逐站添加 + 拖拽排序 | `DragReorderColumn`（长按拖拽） |
| 公交上行 / 下行独立 | `BusStationEntity.direction` + 双 Tab 独立编辑 |

### 4.2 公交线路与站点管理（PRD 3.2）

| 需求 | 实现 |
| ---- | ---- |
| 一键切换上行 / 下行 | `BusLineDetailScreen` TabRow + `BusLineDetailViewModel.setDirection` |
| 列表展示站点 | 站点卡片（序号、名称、备注摘要、图片数） |
| 地图绘制站点轨迹 | `StationTrackMap`（Polyline + Marker，换乘站高亮） |
| 站点名称修改 / 删除 | `LineEditScreen` 重命名、`BusLineDetailScreen` 删除 |
| 文字备注 / 实景图片 / 经纬度 | `BusStationDetailScreen`（`ImageGrid`、`MapPickerDialog`、当前位置定位） |
| 绑定地铁出入口 + 【地铁换乘】标签 | `TransferPickerDialog` + `TransferTag`，点击跳转对应出口详情 |

### 4.3 地铁线路与出入口管理（PRD 3.3）

| 需求 | 实现 |
| ---- | ---- |
| 录入地铁线路、站点 | `LineEditScreen(METRO)` / `MetroLineDetailScreen` |
| 一个站点多个出入口 | `MetroStationDetailScreen` 新增 A口 / 1号口等 |
| 出入口独立编辑 | `EntranceScreen`：出口名称、对应道路、地标、通行备注、图片、地图定位 |

### 4.4 离线检索（PRD 3.4 / TechDoc 5.5）

| 需求 | 实现 |
| ---- | ---- |
| 正向搜索（线路） | `TransNoteRepository.searchLines`：BusLine / MetroLine 名称模糊匹配 |
| 反向搜索（地点） | `searchPlaces`：站点名、站点备注、地铁出口道路 / 地标 / 备注 |
| 匹配度排序 | `SearchMatcher`（完全相等 > 前缀 > 包含 > 子序列）+ 字段权重 |
| 点击跳转 | `SearchScreen` → 线路详情 / 站点详情 / 出口详情 |

### 4.5 数据备份（PRD 3.5 / TechDoc 5.6）

| 需求 | 实现 |
| ---- | ---- |
| 导出 ZIP（数据库 + 图片） | `BackupManager.exportBackup` + SAF `CreateDocument` |
| 导入 ZIP 恢复 | `BackupManager.importBackup`（关闭 Room → 覆盖 db / images → 自动重开） |

### 4.6 主题（PRD 3.6 / TechDoc 5.1）

- 三种模式：跟随系统 / 浅色 / 深色，存于 DataStore；
- 完整配色表落地于 `ui/theme/Color.kt`，组件一律引用主题色，无硬编码；
- 切换实时生效（Compose 重组 + 颜色补间动画），无需重启。

### 4.7 三期体验功能

- 收藏、置顶、线路分组（首页菜单 + 筛选 Chip）
- 站点拖拽排序、批量添加站点
- 页面左右切换、列表渐入、主题淡入淡出、点击水波纹

---

## 5. 权限说明

| 权限 | 用途 |
| ---- | ---- |
| CAMERA | 拍照录入站牌 / 站点实景图片 |
| READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE(≤32) | 相册选图 |
| ACCESS_FINE/COARSE_LOCATION | 地图打点定位 |
| INTERNET / ACCESS_NETWORK_STATE | 仅地图瓦片加载；业务逻辑全部离线 |

---

## 6. 数据安全

所有数据（Room 数据库、图片文件）保存在 App 私有目录，卸载即清除；
不上传服务器、无云同步、无广告、无推送、无埋点。请定期使用「设置 → 本地数据备份」导出 ZIP。
