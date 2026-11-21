# AGENTS.md — 作業規約

仕様は `SPEC.md` を正とする。仕様と違う実装をする場合は、先に `SPEC.md` を更新し、PR 本文に理由を書く。

## コマンド

```bash
./gradlew :core:test             # ロジックの単体テスト
./gradlew :app:testDebugUnitTest # アプリ側の単体テスト
./gradlew :app:assembleDebug     # APK ビルド
./gradlew lint                   # Lint
```

## 規約

- 数えるロジックは `core`（純粋 Kotlin/JVM）に置き、Android 依存を持ち込まない。
  ロジックを変えたら必ず `core` の単体テストで既知ケースを検証する
- Kotlin の型は素直に。`!!` の乱用禁止
- コミットは Conventional Commits（`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `ci:`）。1コミット1論点
- 変更は作業ブランチに積み、`main` へ PR を出す。squash せずマージコミットで履歴を残す
- CI が赤いまま完了報告しない
- 精度に関する表現は盛らない。分からない誤差は「分からない」と書く
- 依存追加は最小限にし、理由を PR 本文に書く
