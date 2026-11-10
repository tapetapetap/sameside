# 같은편: 현대차 Pleos 공모전 픽업 앱 (작업 이름)

- 설계 초안: `docs/superpowers/specs/2026-11-09-pickup-app-design.md` (2026-11-09 사용자 승인)
- 3차 설계 초안: `docs/superpowers/specs/2026-11-11-pickup-group-design.md` (2026-11-11 사용자 위임, 명세 `specs/003-group-pickup`)
- 이 폴더는 첫 번째 앱 저장소를 함께 쓰는 Orca 워크트리다. 브랜치 `pickup`은 첫 앱과 기록을 나눈 orphan 브랜치다.
- Pleos 개발 환경, 에뮬레이터, SDK 사실은 아래 문서를 참고한다.
  - DRIVE DNA 워크트리(`/Users/jiwon/orca/workspaces/unused-feature-app/drive-dna`): `HANDOFF.md`, `specs/001-drive-dna/research.md`
  - 첫 앱: `specs/002-redesign-21-features/verification.md`
- 첫 번째 앱과 DRIVE DNA 워크트리는 참고만 하고 고치지 않는다.
- 원격 `sameside` = GitHub 비공개 `tapetapetap/sameside`. `git push`는 그 저장소의 `main`으로 간다(`remote.sameside.push refs/heads/pickup:refs/heads/main`).
- 커밋·푸시는 사용자가 요청할 때만 한다.
