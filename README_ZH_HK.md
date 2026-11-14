# Neo ECO AE Extension · Minecraft 1.20.1 GTO 專版

[English](README.md) | [简体中文](README_ZH_CN.md) | 繁體中文 | [文言文](README_LZH.md)

Neo ECO AE Extension 是 Applied Energistics 2 的擴充模組，為 ME 網絡提供可擴展的合成、儲存和運算系統，以及用於加工進階組件的整合工作站。

本分支 `1.20.1-GTO` 以 `1.20.1` 為基礎，僅面向 **GregTech Odyssey（GTO）**。目前原始碼版本為 **`20.5.8-GTO`**，僅支援 **GregTech-Odyssey `0.6.0 dev30` 以上版本**。一般 AE2 整合包請使用 `1.20.1` 分支。

## 主要功能

| 系統 | 用途 |
| --- | --- |
| ECO 合成系統 | 透過樣板匯流排、工作核心與並行核心執行自動合成，支援批次處理、超頻與主動冷卻。 |
| ECO 儲存系統 | 透過儲存主機、驅動器和儲存矩陣擴展 ME 網絡容量，支援物品與流體。 |
| ECO 運算系統 | 為自動合成提供 CPU、任務執行緒、協同處理器和任務儲存空間，可隨結構擴展。 |
| 整合工作站 | 加工盈能材料、處理器、儲存組件和三套系統所需的進階部件。 |
| 大型整合工作站 | 提供批次加工、樣板自動化、超頻與冷卻，使用本工程與 GTO 加工配方。 |

合成、儲存和運算主機均提供自動搭建子視窗，可設定長度與鏡像。結構終端物品保留，開啟後提示使用對應主機 UI 內的搭建按鈕。

大型整合工作站使用本工程與 GTO 配方，無需附屬升級模組。

## 適配環境

下表列出本分支的相容範圍與鎖定編譯依賴版本：

| 項目 | 目標版本 |
| --- | --- |
| Minecraft | `1.20.1` |
| GregTech Odyssey | `0.6.0 dev30+` |
| Forge | `47.4.20` |
| Java | 遊戲執行使用 **Java 21** |
| GTOCore | `26.11.6` |
| GTO GTCEu | `26.11.20` |
| GTO AE2 | `15.2611.8` |

GuideME 與 LDLib 為必要基礎依賴，具體宣告見 [Mod 中繼資料](src/main/resources/META-INF/mods.toml)。本專版僅保留 GTO 聯動。

GTO 專版適配原生合成 CPU、樣板快取批次投料、容量檢查、程式電路、加工狀態、CPU 列表同步、精確數量及巢狀網絡儲存。GTO 樣板修改器 Pro 可修改通訊介面內的樣板，工作站配方支援重新載入。

## 安裝與使用

1. 從 GitHub Releases 下載對應版本，或按下文從目前原始碼建置 `neoecoae-20.5.8-GTO.jar`。
2. 將該 JAR 放入目標 GTO 實例的 `mods` 目錄，並移除舊版 NeoECOAE JAR。`-sources.jar` 是原始碼封存檔，不用於安裝。
3. 連線遊玩時，用戶端與伺服器均安裝相同版本。ECO 網絡握手要求兩端版本完全一致。
4. 使用 Java 21 啟動目標實例，在配方檢視器中查詢材料與機器配方。

初次遊玩可以從盈能水晶和整合工作站開始，再按需求製作儲存、運算或合成主機。多方塊搭建、冷卻與升級說明見遊戲內 GuideME，也可閱讀倉庫中的簡體中文指南：

- [入門與系統概覽](guidebook/_zh_cn/neoecoae_intro/index.md)
- [整合工作站](guidebook/_zh_cn/neoecoae_intro/integrated_working_station.md)與[大型整合工作站](guidebook/_zh_cn/neoecoae_intro/large_integrated_working_station.md)
- [合成系統](guidebook/_zh_cn/neoecoae_intro/crafting_system.md)與[合成控制器圖文教學](guidebook/_zh_cn/neoecoae_intro/crafting_controller.md)
- [運算系統](guidebook/_zh_cn/neoecoae_intro/computation_system.md)

指南中的互動結構場景和物品、配方組件需在遊戲內檢視。

## 從原始碼建置

需要 **JDK 17、JDK 21 和 Python 3**。`build-local.ps1` 使用 Java 17 啟動 Gradle；GTO AE2 依賴使用 Java 21 位元組碼，因此編譯工具鏈為 Java 21，ECO 本身仍以 `--release 17` 輸出。遊戲執行環境仍按上表使用 Java 21。

在專案根目錄使用 PowerShell 執行：

```powershell
# 找不到 Java 17 時，設定為本機實際安裝的 JDK 17 目錄
$env:NEOECOAE_JAVA17_HOME = 'C:\path\to\jdk-17'

# 首次取出程式碼：下載並驗證固定依賴，產生本機依賴鎖
python tools/compat/bootstrap.py --gto

# 打包安裝包與原始碼包
.\build-local.ps1 jar sourcesJar --console=plain

# 檢查發佈包內容
python tools/compat/check_artifacts.py --release-only
```

JDK 21 需能被 Gradle 工具鏈偵測；未自動識別時，可透過 Gradle 的 `org.gradle.java.installations.paths` 指定安裝目錄。Gradle 驗證應依次執行，避免共用輸出衝突。依賴快取齊全後，可為建置指令加入 `--offline`。

建置產物位於 `build/libs/`：

- `neoecoae-20.5.8-GTO.jar`：安裝包。
- `neoecoae-20.5.8-GTO-sources.jar`：原始碼封存檔。

編譯與實際 JAR 核對使用 GTO AE2 **`15.2611.8`**，見 [GTO 依賴鎖](porting/gto-dependencies.json)。`bootstrap.py --gto` 會還原建置依賴，不會自動安裝完整 GTO 整合包。

## 目錄結構

```text
src/main/java/cn/dancingsnow/neoecoae/
  api/                      ECO 業務與擴充契約
  blocks/                   機器、介面與方塊實體
  crafting/                 合成規劃與執行
  impl/storage/             儲存實作與精確數量處理
  compat/                   AE2、GTO 與可選附屬相容
  gui/                      主機頁面與搭建視窗
  platform/                 Forge 1.20.1 平台適配
src/main/resources/         Mod 中繼資料、資源與配方
guidebook/                  GuideME 指南與結構場景
docs/compat/                GTO 適配記錄與依賴鎖
docs/releases/              中英雙語發佈說明
tools/compat/               固定依賴還原與產物檢查工具
```

`libs/`、`build/` 和 `porting/` 為本機產生或工作目錄；其中固定依賴鎖 `porting/gto-dependencies.json` 納入 Git。乾淨取出程式碼後所需的建置依賴資訊由 `tools/compat/build-dependencies.json` 與該依賴鎖提供。

## 驗證狀態與問題回報

舊版 `20.5.4-gto.1` 的測試記錄見其 [發佈說明](docs/releases/20.5.4-gto.1.md)。目前工程已移除自動化測試原始碼與自訂 Gradle 測試工作，建置檢查使用 `jar sourcesJar` 及產物檢查腳本；客戶端、專服啟動與實際互動需要單獨驗收。

回報問題時，請附上 ECO 版本、GTO 整合包版本、實際安裝的 GTOCore／GTM／AE2 版本、重現步驟，以及 `logs/latest.log` 或對應崩潰報告。

## 作者與授權

作者：DancingSnow、ZhuRuoLing、Yang120231、y271727uy。

本專案採用 [GNU GPL v3](LICENSE)。
