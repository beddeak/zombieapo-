# 인수인계

## 현재 상태 (2026-10-25)

- 이 폴더는 `ZombieApocalypse` Paper 프로젝트다. 상위 `C:/dev/AGENTS.md`의 RandomMorph/Fabric 경로를 이 프로젝트에 적용하지 않는다.
- Paper API `26.2.build.129-stable`, Java 25, Gradle 9.6.0, 플러그인 **0.2.0**.
- PLAN의 확정 기능을 모두 구현했다(2~16C단계).
  - 월드별 start/stop과 data.yml
  - 몬스터 대체, 햇빛 면역, 추가 스폰
  - 일반 타입 19종과 보스 탱크
  - 날짜별 난이도, 블러드문, 호드(정기·스크리머·연속), 플레이어 좀비
  - 라디오 엔딩(구조 피날레 → 탈출)
  - 명령어 전체
- 첫 플레이 피드백 반영(같은 날, 버전 번호는 0.2.0 유지):
  - 주변 좀비 상한을 몬스터 대체와 자연 좀비 스폰에도 적용했다. 1분 만에 300마리가 쌓이던 문제다.
  - 동물(돼지·소·여우 등)은 원래 바꾸지 않았으며, 이를 확인하는 테스트를 추가했다.
  - 안내 문구를 모두 존댓말로 바꿨고, 날짜 타이틀은 "N일차"만 띄운다.
  - 라디오 조합법의 다이아몬드 블록을 2개로 늘렸다.
  - `/apoc egg` 테스트 스폰알을 추가했다. 사용자가 이 빌드를 직접 `server/plugins`에 넣어 스폰알로 테스트했다(07:13).
- 두 번째 피드백 반영(같은 날, 0.2.0 유지):
  - 시작 연출: "좀비 아포칼립스" 타이틀과 3, 2, 1 카운트다운 뒤 시작(기본 6초)
  - 라디오는 지면 높이의 땅 위에만 설치. 피날레 지키기는 높이 차이 8블록 안
  - 탈출 지점 발광 표시
  - 도둑은 반드시 훔침
  - 좀비끼리 공격 금지
  - 스크리머 워든 소리
  - "호드"를 "좀비 웨이브"로 이름 변경(`/apoc wave`)
  - 탱크 보상에서 다이아몬드 제외
  - 사용자는 서버를 PowerShell 없이 `java -jar`로 직접 켜고, 플러그인 JAR도 직접 넣는다. 설치 안내는 "서버를 끄고 `build/libs/ZombieApocalypse-0.2.0.jar`를 `server/plugins`에 덮어쓰기"로 한다.
  - 서버의 config.yml이 처음 기본값 그대로라 새 기본값으로 바꿨다(이전 파일은 임시 폴더에 백업).
- 세 번째 피드백 반영(같은 날, 0.2.0 유지):
  - 안테나(엔드 막대기): 라디오 바로 위나 옆(동서남북)에 붙어 있어야 구조를 요청할 수 있다. 폭발 보호, 웅크려 부수면 회수, 피날레·탈출 중 고정
  - 조립 시간: 라디오나 안테나를 조합하면 재료만 들어가고 그 작업대에서 조립한다(라디오 90초, 안테나 45초). 조립 중 좀비 웨이브가 계속 오고, 작업대 8블록 안에 생존자가 없으면 멈춘다. 진행은 data.yml에 저장되고 재시작 뒤 작업대 근처로 오면 이어진다. stop 때는 아이템을 바로 준다. 크래프터로는 만들 수 없다.
  - 조합법은 사용자가 준 그림대로 배치가 정해진 조합법(shaped)이다. 설정은 `shape` + `keys`.
    - 라디오 `DRD / RHR / TBT`: D 다이아몬드 블록, R 레드스톤 블록, H 바다의 심장, T 레드스톤 횃불, B 주크박스 (2026-10-31에 소리 블록에서 바꿈)
    - 안테나 ` C  / BRB /  C `: C 철 사슬, B 블레이즈 막대기, R 레드스톤 가루 (2026-10-29에 네더 석영에서 바꿈)
    - 피뢰침은 이 버전에서 산화되어 모양이 바뀌므로 안테나 블록은 엔드 막대기로 했다.
  - 시작하는 순간 그 월드의 로드된 바닐라 적대적 몹을 지운다. 동물, 주민, 골렘, 이름표 몹, 제외 몹은 남긴다.
  - 사용자 서버의 config.yml은 지난번에 넣은 기본값 그대로여서 새 기본값으로 다시 바꿨다(이전 파일은 임시 폴더에 백업).
  - **최신 JAR(`49601eee…`)는 사용자 서버가 실행 중이라 아직 넣지 않았다.** 사용자 서버는 08:16에 포트 25565로 켜졌고 이전 빌드(`911fa44a…`, 안테나와 조립 없음)를 쓰고 있다.
- 검증:
  - `build.ps1 -Offline` 통과, 단위 테스트 66개 통과.
  - 별도 서버 복사본에서 확인: 준비 시간, 재시작 후 이어서 진행, 좀비끼리 폭발 피해 없음, 시작 시 몬스터 정리(이름표 몹, 동물, 주민, 골렘은 남음), 크래프터 차단(서버가 두 조합법을 인식한 뒤 막음).
  - 실제 Paper 서버에서 플레이어 없이 콘솔로 확인한 항목: 소환, 햇빛 면역, 날짜 배율, 블러드문, 탱크 처치, 증식 연쇄, stop 정리, 재시작 후 상태 유지. 결과는 [BUILD_REPORT](BUILD_REPORT.md).
- 2026-10-25 현재 서버에서 클라이언트 입장과 `/apoc start` 성공을 서버 로그로 확인했다. **전체 플레이 검증은 아직 하지 않았다.** 타깃이 필요한 능력, 호드, 엔딩은 코드와 단위 테스트로만 확인한 상태다.
- `server/plugins`에는 0.2.0 JAR(이전 빌드 `911fa44a…`)와 이번 기본값의 config.yml이 있다. 0.1.0 JAR와 0.1.0 config.yml은 지웠다.
- 서버 설정: 서바이벌/어려움, 10명, PvP 끔, 시야 8/시뮬레이션 6, 실행 스크립트 메모리 1~4GB, 정품 인증, 포트 25565(25599에서 되돌렸고, 08:16 재시작부터 25565로 열려 있음). EULA 동의 반영됨. 사용자는 서버를 `java -jar`로 직접 켠다.
- 포트 안내 문제 수정: 실행 스크립트가 `25565`를 고정 출력하던 것을 `server/server.properties`에서 읽도록 바꿨다. 같은 PC 접속 주소는 `localhost:25565`.
- 포트가 25599가 된 원인: 0.2.0 콘솔 검증을 기존 서버 폴더에서 `--port 25599`로 실행했고, Paper가 그 값을 server.properties에 저장했다. 사용자 요청으로 25565로 되돌렸다. 앞으로 검증용 서버 실행은 별도 복사본 폴더에서 한다.

- 2026-10-28: GitHub `beddeak/zombieapo-`의 스폰 상향 커밋을 받아오고 JAR를 다시 빌드해 커밋했다(`0d5663e`). 서버 폴더에 `맵 초기화.bat`(world와 data.yml 삭제, 서버가 켜져 있으면 거부)을 만들었다.
- 2026-10-28: 시작 후 3분 동안 지상 스폰을 조용히 막는다(`start.quiet-surface-seconds`, 기본 180). 자연 스폰·몬스터 대체·추가 스폰 모두 해당, 동굴과 스포너는 제외. 알림 없음, `/apoc status`에만 표시. 테스트 복사본에서 타이머 감소만 확인했고, 실제 스폰 차단은 플레이어 접속 확인이 필요하다.
- 2026-10-29 촬영용 보강(0.2.0 유지). 다른 PC에서 임시 JDK 25로 빌드했고 테스트 71개가 통과했다. 서버와 클라이언트 확인은 아직 하지 않았다.
  - 탈출 단계에서 남은 사람이 리스폰 화면에 있는 순간 엔딩이 끝나던 버그를 고쳤다(`EndingRules.escapeOver`).
  - 사망 메시지에 좀비 종류를 표시한다. `CombatListener`가 마지막으로 때린 커스텀 좀비를 10초 동안 기억한다. 폭발은 `Apocalypse.zombieBlast(type, …)`로 타입을 넘긴다.
  - 생존 기록: `WorldData.stats`(플레이어별 처치 타입·사망·도둑 피해, data.yml `stats`). 엔딩 3초 뒤 채팅으로 띄우고 `/apoc stats`로 중간에 본다. 표는 `logic/ResultBoard`가 만든다.
  - 안테나 재료를 블레이즈 막대기로 바꿨다(사용자 결정). 스포너 블레이즈는 좀비로 바꾸지 않는다(`replace.keep-from-spawner: [BLAZE]`).
  - **사용자 서버의 config.yml은 자동으로 바뀌지 않는다.** 안테나 조합법을 바꾸려면 `ending.antenna-recipe`를 새 값으로 고쳐야 한다. `keep-from-spawner`는 키가 없으면 기본값 BLAZE가 적용된다.
- 2026-10-29 버그 검토(에이전트 3개가 엔딩, 스폰·성능, 능력·전투를 읽음) 후 수정. 테스트 71개 통과, 게임 안 확인은 아직이다.
  - 도둑: 손 드롭 확률을 0으로 두고 `ThiefAbility.died`가 원래 아이템을 직접 드롭한다(바닐라 1.0은 플레이어 처치 때만, 내구도 무작위). 훔친 토템으로 부활하지 않는다(`CombatListener.resurrect`).
  - 안테나를 들고 라디오를 우클릭하면 취소하지 않아 그 면에 설치된다.
  - 웨이브는 `global-cap`에서 멈추고, 연속 웨이브는 그 웨이브 좀비가 `max-wave-total` 이상이면 미룬다. 자연 좀비 태깅도 `global-cap`을 지킨다.
  - 기록만 바뀌면 `Apocalypse.statsChanged()`로 30초마다 저장한다.
  - 연속 웨이브 중에는 정기 웨이브를 건너뛰고, 엔딩(DONE) 뒤에는 정기 웨이브·N일차 탱크가 없다.
  - 커스텀 좀비는 아이템을 줍지 않는다(`ZombieFactory.apply`).
  - 네더·엔드는 시계가 없어 `Apocalypse.fullTime`/`day`가 메인 오버월드를 따른다. 잠은 2026-10-31부터 막는다(`allow-sleep`, 사용자 결정 변경).
  - 엔딩에서 관전·크리에이티브는 낙오가 아니다. 탈출 타이머는 남은 사람이 리스폰 화면에 있어도 흐른다.
  - 남은 검토 결과(아직 안 고침): 바닐라 스폰 취소가 매 틱 반복되는 비용(`PreCreatureSpawnEvent`로 미리 막기), 탱크 수 제한이 로드된 탱크만 셈, `/time set` 되돌리면 날짜 고정, 탱크가 좁은 곳에 끼어 스폰, 시련 스포너 미완료, 엔딩 후 10초 안 재시작 시 관전자 유지, 네크로맨서 붕괴 처치 기록, 용암 속 증식, `/apoc wave stop`이 피날레 웨이브도 끔, 월드 보더 밖 탈출 지점, 엔딩 뒤 10명이면 생존 기록이 채팅창보다 김.
- 2026-10-29 새 타입 위치(`WITCH`, 레프트 4 데드 위치)를 추가했다(일반 타입 20종, 0.2.0 유지). 빌드와 테스트 71개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - `ability/WitchAbility`: 평소 `setAware(false)`로 제자리에서 운다(눈물 파티클, 가스트 소리). 생존자가 `startle-distance`(5블록, 웅크리면 절반) 안에 오거나 때리면 깨운 사람만 쫓는다. 속도·공격력 modifier는 `MOD_TYPE_SPEED`/`MOD_TYPE_DAMAGE`.
  - 상태는 PDC: `Keys.VICTIM`(쫓는 플레이어), `Keys.MARK`(분노 끝 시각). 그 사람이 죽거나 나가거나 `rage-seconds`(30초)가 지나면 modifier를 지우고 다시 앉는다. 재시작해도 이어진다.
  - 좀비 웨이브에는 나오지 않는다(`ZombieFactory.pick`에서 `context.horde() != 0`이면 제외). 땅 좀비 몸만 쓴다(`ZombieType.fits`, 드라운드·피글린 제외).
  - 깨울 때 액션바 문구 "위치를 깨웠습니다! 님은 좆됏어요"는 사용자가 직접 쓴 것이라 그대로 뒀다.
  - **사용자 서버의 config.yml에는 `types.witch`가 자동으로 추가되지 않는다.** 키가 없으면 코드 기본값(10-31부터 가중치 2)으로 나오며, 수치를 바꾸려면 번들 config.yml의 `witch:` 블록을 복사해 넣는다.
- 2026-10-30 플레이 피드백 "낮에 돌아다닐 수가 없다" 반영: 낮 지상 스폰 감소(0.2.0 유지). 빌드와 테스트 71개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - 낮(시각 23000~13000)이고 하늘빛이 닿는 곳이 `Apocalypse.daylight`다. 블러드문 중에는 해당하지 않는다. 판정은 시작 직후 조용한 지상(`quietSurface`)과 같은 하늘빛 기준이다.
  - 그곳의 주변 상한은 `crowdCap × spawning.daylight.cap-multiplier`(0.25, 1일차 10)다. `crowded()`를 거치는 몬스터 대체와 자연 좀비 태깅에도 적용된다.
  - `ExtraSpawner`는 낮 지상 자리에 1회 `daylight.per-attempt`(1)마리까지만 만든다. 플레이어가 동굴이나 집 안에 있어도 자리 기준으로 센다.
  - 좀비 웨이브, 밤에 생긴 좀비(햇빛 면역이라 아침에도 남음)는 이 설정과 상관없다. 아침에도 많으면 다음 후보는 해 뜰 때 플레이어와 먼 지상 좀비를 정리하거나 정기 웨이브를 밤에만 오게 하는 것이다.
  - 사용자 서버의 config.yml에 `spawning.daylight`가 없어도 코드 기본값이 적용된다.
  - 사용자가 1(아침에 남은 좀비)과 2(낮 정기 웨이브)는 일부러 그대로 두기로 했다. 바꾸지 않는다.
- 2026-10-30 실시간 조정 `/apoc set [설정] [값]`을 추가했다(0.2.0 유지). 빌드와 테스트 79개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - `config/LiveConfig`: 받는 키는 번들 config.yml의 끝 키(`getKeys(true)`에서 섹션 제외)뿐이라, 서버 config.yml에 없는 새 키도 넣을 수 있다. 값은 `value: <입력>`을 YAML로 읽어 숫자·true/false·[목록]을 구분한다.
  - 바꾼 YAML로 `Settings.load`를 돌려 원래 없던 경고가 생기면 거절하고 파일을 건드리지 않는다. 통과하면 `ApocalypseCommand`가 `core.settings(...)`로 바로 적용하고 `YamlConfiguration.save`로 저장한다. 주석은 남지만 목록 표기 같은 서식은 바뀔 수 있다. 첫 변경 전 파일은 `config.yml.bak`으로 한 번만 남긴다.
  - 이미 스폰된 좀비의 체력·속도 같은 값은 그대로이고, 스폰 상한·주기처럼 매번 읽는 값은 바로 반영된다.
  - 인자 없이 `/apoc set`을 쓰면 `ApocalypseCommand.MAIN_SETTINGS`의 10개 설정과 현재 값을 보여 주고, 클릭하면 입력창에 명령이 들어간다.
- 2026-10-31 플레이 피드백 "위치가 구분이 안 되고 밸붕, 스크리머 어그로 반경이 너무 넓다" 반영(0.2.0 유지). 빌드와 테스트 79개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - 위치 표시: 연보라 이름표 "위치 (가까이 가지 마세요)"를 항상 띄우고 울 때 `Particle.WITCH`를 더한다. 울음소리는 1/3 확률, 볼륨 1. 분노 중에는 이름표가 "분노한 위치"(빨강)로 바뀌고 발광하며, `calm`에서 되돌린다. `/apoc stop`은 커스텀 좀비를 지우므로 발광이 남지 않는다.
  - 위치 너프(코드 기본값과 번들 config.yml): 가중치 3→2, `startle-distance` 5→4, 속도 2.0→1.5, 공격력 2.5→1.8, 체력 2.0→1.5, `rage-seconds` 30→15.
  - 스크리머: `detect-distance` 16→10, `radius` 24→12.
  - 사용자 서버 `server/plugins/ZombieApocalypse/config.yml`은 10-28 버전이라 `types.witch`가 없어 위치는 새 코드 기본값을 쓴다. 스크리머 값(16/24)은 파일에 적혀 있어 10/12로 직접 고쳤다. 그 파일의 나머지 값은 건드리지 않았다.
- 2026-10-31 스크리머 피드백 "3번은 질러야 웨이브가 온다, 비명이 둔하다" 반영(사용자가 직접 수정, 0.2.0 유지). 빌드와 테스트 79개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - `horde.screamer-chance` 0.25→1.0(코드 기본값과 번들 config.yml). 첫 비명에 바로 좀비 웨이브가 온다. 웨이브 진행 중이거나 `screamer-cooldown-seconds`(300초) 안에는 오지 않는다.
  - `SimpleAbilities.Screamer.scream`: 워든 포효를 빼고 `ENTITY_ENDERMAN_SCREAM`(1.4), `ENTITY_FOX_SCREECH`(1.8), `BLOCK_SCULK_SHRIEKER_SHRIEK`(1.6)를 볼륨 3으로 겹쳤다.
  - 새 JAR를 `server/plugins`에 복사했고, 서버 config.yml의 `screamer-chance`도 1.0으로 고쳤다(서버가 꺼져 있을 때).
- 2026-10-31 피드백 "비명이 짧고 맛이 없다, 위치는 뒤에서 가도 깬다" 반영(0.2.0 유지). 빌드와 테스트 80개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - 스크리머 비명: `ENTITY_ENDERMAN_STARE`(1.3)와 `ENTITY_ELDER_GUARDIAN_CURSE`(1.4) 위에 `ENTITY_GHAST_SCREAM`을 5틱 간격 6번(피치 1.0→1.75) `runLater`로 겹친다. 지연 소리는 좀비가 아직 유효할 때만 낸다.
  - 위치: `types.witch.view-angle`(120°) 추가. `AbilityRules.inFront`로 머리 방향(`getLocation().getYaw()`) 기준 앞쪽 원뿔 안에 있을 때만 거리로 깬다. 뒤·옆은 붙어도 안 깨고, 때리면 방향과 상관없이 깬다. 360이면 예전처럼 사방. `RulesTest.witchOnlyNoticesPlayersInFrontOfHer`.
  - 위치 이름표가 벽을 뚫고 보여 동굴 속 위치가 드러나던 문제: 평소에 `setSneaking(true)`(몹은 이름표만 흐려지고 벽 뒤에서 안 보이며 32블록까지만 보임)와 `setPose(Pose.SNEAKING, true)`(웅크린 자세)를 건다. 분노하면 둘 다 풀고, 진정하면 다시 건다. 플래그와 자세는 저장되지 않아 `tick`에서 `isSneaking()`이 아니면 다시 건다(재시작·청크 재로드 대비). 분노 중 발광은 벽 너머로 보인다(의도).
  - 라디오 조합법 가운데 아래 `B`를 소리 블록에서 주크박스(`JUKEBOX`)로 바꿨다(코드 기본값, 번들 config.yml, `SettingsTest`, 사용자 서버 config.yml).
  - 10-31 17:02 서버가 꺼진 뒤 이날 변경이 모두 들어간 JAR(테스트 85개 통과)를 `server/plugins`에 복사했다.
- 2026-10-31 "사막에 해골(Parched)이 나온다" 수정: 낙타 허스크(`CAMEL_HUSK`, Camel이라 Enemy가 아님)가 허스크와 파치드(사막 해골)를 태우고 나오는데, 타는 몹은 `JOCKEY` 원인이라 대체 대상이 아니었다. `replace.reasons` 기본값에 `JOCKEY`, `MOUNT`를 더했다(코드, 번들·사용자 서버 config.yml). 파치드·거미 기수 해골은 좀비로 바뀌고, 탄 허스크는 커스텀 타입을 받는다. 낙타 허스크 자체는 그대로 둔다. `ReplaceRulesTest.ridersOfDesertCamelsAndSpidersAreReplaced`. 빌드와 테스트 86개 통과, 서버 확인 전. 로직은 그대로라 켜진 서버에서는 `/apoc reload`로 적용된다.
- 2026-10-31 화염병 버프("탱크한테 그냥 때리는 게 훨씬 낫다"). 빌드와 테스트 86개 통과, 서버 확인 전.
  - 원인: 1초 2 피해(탱크 ×2)뿐이라 30일차 탱크(약 980)에 한 병 5% 미만, 탱크가 불을 금방 지나가면 피해가 끝났고, 칼로 때리는 중에는 무적 시간에 막혔다.
  - `Burn` 효과: 불을 밟은 몹(플레이어 제외)은 `burn-seconds`(8초) 동안 계속 타며 1초마다 `damage`(4) + 최대 체력×`max-health-percent`(1.5%)를 `IN_FIRE`로 받는다(탱크는 `fire-damage-multiplier` 2배). 피해 직전에 무적 시간을 0으로 하고 뒤에 원래 무적 시간과 마지막 피해량을 되돌려 플레이어 타격을 방해하지 않는다. 다시 밟으면 연장, 물에 들어가면 꺼짐. 바닥 불은 3.5블록·10초로 늘렸다. `fire-seconds` 키는 `burn-seconds`로 바뀌었다.
  - 계산(탱크가 불을 1초 밟고 지나감, 약 9초 탐): 1일차 탱크(400) 약 180(45%), 30일차(980) 약 340(35%).
- 2026-10-31 탱크 블록 파괴("괴수처럼 지나가는 곳 다 파괴")와 새 타입 텔레포터(`TELEPORTER`, 일반 타입 23종, 스폰알 24종). 빌드와 테스트 86개 통과, 서버 확인 전.
  - 탱크: `TankAbility.tick`(10틱)에서 쫓는 플레이어가 있으면 `smash`. 히트박스를 옆으로 0.3, 플레이어 쪽으로 1블록 넓힌 상자 안의 블록을 최대 64개 부순다. 바닥 칸은 제외하고, 플레이어가 0.8블록 넘게 위면 발 높이 칸도 남겨 계단·언덕을 오를 수 있게 한다. 공기·액체·부술 수 없는 블록(단단함 < 0)·`break-max-hardness`(50) 초과는 건너뛴다. 라디오·안테나(`EndingManager.protect`)와 조립 중 작업대(`AssemblyManager.protects`)는 제외. 컨테이너는 `breakNaturally`(내용물 떨어짐), 나머지는 파티클과 함께 공기로(드롭 없음). `tank.break-blocks`(기본 true).
  - 텔레포터(`ability/TeleporterAbility`): 10틱마다 보라 입자. 쫓는 플레이어가 `min-distance`(6)~`max-distance`(32)면 그 사람이 보는 방향 반대쪽 2~3.5블록의 안전한 칸(발·머리 칸 통과 가능, 액체 아님, 아래 단단함)으로 순간이동하고 그 사람을 본다(`cooldown-ticks` 120). 맞으면 `dodge-chance`(0.5)로: 투사체는 피해 취소 후 5~9블록 무작위로, 근접(ENTITY_ATTACK/SWEEP)은 맞은 뒤 1틱 후 때린 사람 등 뒤로. 화염병 불 피해(원인 FIRE)에는 반응하지 않는다.
- 2026-10-31 위치 강화(SCP-096)와 잠 금지. 빌드와 테스트 86개 통과, 서버 확인 전.
  - 위치: 깨는 조건에 "얼굴을 쳐다보기"를 더했다. 얼굴 앞 `view-angle`(120°) 안의 플레이어가 `look-distance`(24) 안에서 시선이 머리 0.9블록 안을 지나고 `hasLineOfSight`이면 깬다(10틱마다 확인). 가까이 오기·때리기도 그대로.
  - 깨면 `provoke`: 일어서고 발광, 이름표 "비명 지르는 위치", `Keys.WIND_UP`까지 `scream-seconds`(3초) 동안 그 사람을 보며(`setRotation`) 가스트 비명. 끝나면 `chase`: 속도 `speed-multiplier`(1.5→2.2), `setAware(true)`, 이름표 "분노한 위치". `Keys.MARK`는 비명+`rage-seconds`. 공격력 배율 대신 `hit-damage`(8, 하트 4칸)를 `attacked`에서 피해로 정한다(`damage-multiplier` 키는 없어짐). 진정하면 `WIND_UP`도 지운다. 이름표는 "위치 (쳐다보지 마세요)".
  - 잠: `PlayerListener.bed`가 아포칼립스 월드에서 `PlayerBedEnterEvent`를 취소하고 액션바로 알린다. 리스폰 지점은 이벤트 전에 정해지므로 그대로다. `allow-sleep`(기본 false).
- 2026-10-31 "횃불을 놓아도 좀비가 생긴다": `ExtraSpawner`가 블록 빛(`getLightFromBlocks`)이 `spawning.extra.max-block-light`(7)보다 밝은 자리를 건너뛴다(코드 기본값, 번들·사용자 서버 config.yml). 웨이브·탱크·네크로맨서 등은 빛과 상관없다. 손 횃불은 클라이언트 가짜 블록이라 영향이 없다. 빌드와 테스트 87개 통과, 서버 확인 전.
- 2026-10-31 "아침이 안 온다" 문의: 플러그인은 시간·게임 규칙을 바꾸지 않는다. 로그상 플레이어가 19:02에 `/time set midnight`을 세 번 쳤고, 잠 금지로 밤을 넘길 수 없어 원래 길이대로 흐른 것이었다. 버그 아님.
- 2026-10-31 시작 타이틀 제작자 표시, 쓰러짐과 살리기, 다 같이 탈출, 화면 엔딩 크레딧. 빌드와 테스트 87개 통과, 서버 확인 전.
  - 시작: `Msg.opening`이 제목 줄 "plugin made by beddeak(침댁)"(회색), 부제 줄 "좀비 아포칼립스"(굵은 빨강). 사용자 요청 "제작자 표시 밑에 타이틀"을 그대로 따랐다.
  - 쓰러짐(`Downed`, `listener/DownedListener`): 사용자 결정으로 게임 내내 적용. 아포칼립스 월드의 서바이벌·모험 플레이어가 죽을 피해(`getFinalDamage`, HIGHEST)를 받으면 취소하고 쓰러뜨린다(공허·KILL·자살·월드보더, 토템을 든 경우는 제외). 체력을 가득 채운 뒤 `bleed-seconds`(60) 동안 5틱마다 줄여 0이면 `setHealth(0)`으로 진짜 사망(기존 플레이어 좀비 흐름). 쓰러진 동안 맞는 피해는 그대로라 더 빨리 죽는다. 이동 속도 `crawl-speed`(×0.15)·점프 0(`MOD_DOWNED_SPEED/JUMP`), 고정 자세 SWIMMING, 발광. 회복(`EntityRegainHealthEvent`)·먹기 취소. 서 있는 서바이벌 플레이어가 `revive-distance`(2.5) 안에서 웅크린 채 `revive-seconds`(5) 지나면 `revive-health`(6)로 일어난다. 사망·나가기·월드 꺼짐·플러그인 종료 때 상태를 되돌린다. 상태는 메모리에만 있어 재시작하면 풀린다.
  - 다 같이 탈출: `EndingManager.escapeSecond`에서 쓰러지지 않은 생존자 전원이 헬기장 반경 안이면(`EndingRules.helicopterLeaves`) 헬기장 안 모두(쓰러진 사람 포함)를 `escaped`로 바꾸고 `finish`. 시간이 0이면 그때 안에 있는 사람만. 보스바·액션바에 "헬기장 x/y명". 개별 탈출 안내는 없앴다(`escapeOver` 삭제).
  - 크레딧: `finish`에서 4초 뒤부터 3.5초 간격으로 `EndingRules.credits`(버틴 날짜, 탈출자, 낙오자, 결과판 수상, 제작자)를 `Msg.credit` 타이틀로 보낸다. 자동 종료는 크레딧이 끝난 뒤(`auto-stop-delay-seconds`와 크레딧 길이 중 긴 쪽).
- 2026-10-31 "높이 쌓고 도망가는 것 방지" 요청: 점퍼 강화와 새 타입 클라이머(`CLIMBER`, 일반 타입 21종, 스폰알 22종). 빌드와 테스트 81개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - 점퍼: 타깃이 2블록 이상 위이고 `rise + 1 <= types.jumper.max-height`(24)이면 `AbilityRules.jumpVelocity(rise + 1)`로 딱 그 높이까지 뛰고, 수평 속도는 꼭대기 도달 틱에 맞춘다(`glideSpeed`). 기둥 옆면에 부딪히면 수평 속도가 사라지므로 `Leap` 효과가 타깃 발 높이를 넘은 뒤 매 틱 타깃 쪽으로 밀어 머리 위에 내려앉게 한다(최대 60틱, 착지하면 끝). 먼 거리 점프는 예전 그대로.
  - 클라이머(`ability/ClimberAbility`): 타깃이 1.5블록 이상 위이고 타깃 쪽 0.75블록 앞(발·머리 높이)에 단단한 블록이 있으면 `Climb` 효과 시작. 매 틱 벽 쪽 0.05 + 위로 `climb-speed`(0.2). 벽이 없어지면(꼭대기 넘음) 앞으로 0.3·위로 0.25 밀고 끝. 최대 400틱. 같은 좀비 중복 방지는 `climbing` UUID 집합이고 `end()`에서 지운다. 낙하 피해 면역(`MOD_NO_FALL`).
  - 사용자 서버 config.yml에 `types.climber`, `types.jumper.max-height`가 없어도 코드 기본값이 적용된다.
- 2026-10-31 서버 전체 좀비 상한 500→1000(코드 기본값, 번들·사용자 서버 config.yml)과 `/apoc limit` 추가. 빌드와 테스트 83개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - `/apoc limit`은 `spawning.extra.global-cap`을 보여 주고, `/apoc limit <수>`는 `set`과 같은 경로(`LiveConfig` 검사, 바로 적용, 저장)로 바꾼다. `/apoc limit day <n> [월드]`는 `day set`으로 넘긴다. 사용자가 "날짜 설정"은 지금 날짜 바꾸기라고 답했다(날짜별 상한이 아님).
  - 플레이어 주변 상한(`per-player-cap` 40 + 날짜)은 그대로라, 한 사람 주변이 갑자기 꽉 차지는 않는다.
- 2026-10-31 특수 타입 가중치 통일과 피날레 블러드문 효과. 빌드와 테스트 83개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - 가중치: 위치(2)와 기본(40)을 빼고 19종 모두 6(코드 기본값, 번들 config.yml). 합 116이라 1일차 기본 좀비 비율은 약 26%로 전과 비슷하다. `TypePickerTest`가 6과 위치 2를 확인한다.
  - 사용자 서버 config.yml의 `types`는 10-28 기본값(가중치 제각각, `min-day` 1~6)이 그대로 들어 있어서 가중치를 6으로, `min-day`를 모두 0으로 고쳤다(코드 기본값의 "1일차부터 전부"와 맞춤). 위치·클라이머 항목은 없어서 코드 기본값을 쓴다.
  - 피날레 블러드문: `Apocalypse.bloodMoon(world)`이 `ending.finale.blood-moon`(기본 true)이고 피날레 중이면 참. 스폰 주기 ×0.4, 주변 상한 ×2, 특수 비율, 체력 ×1.3, 속도 ×1.15, 낮 감소 해제가 그대로 적용된다. `BloodMoonManager` 상태는 건드리지 않아 블러드문 보스바·블러드문 탱크는 없다(피날레 보스바·탱크 유지). 시작 때 위더 소리와 안내를 더했다. 탈출 단계에는 적용하지 않는다.
- 2026-10-31 화염병, 새 타입 블레이즈(`BLAZE`, 일반 타입 22종, 스폰알 23종), 바다 라디오 금지, 손 횃불 밝기. 빌드와 테스트 85개 통과. 서버와 클라이언트 확인은 아직 하지 않았다.
  - 화염병(`weapon/Molotov`): 사용자 요청대로 화염 저항 투척 포션 모양(효과 없는 `SPLASH_POTION`, 색 `#FF9900`, `TOOLTIP_DISPLAY`로 포션 줄 숨김, 최대 16개)이고 조합법은 부싯돌 + 실 + 유리병 shapeless(`Keys.MOLOTOV`, 라디오 조합법과 함께 등록·발견). 효과가 없는 포션이라 바닐라는 깨지는 소리와 주황 파티클만 낸다. `CombatListener.landed`에서 화염병이면 `Molotov.landed`. 아포칼립스 월드에서만 불이 붙는다.
  - 불은 `Fire` 효과: 반지름 안 지면의 빈 칸에 `sendBlockChange`로 가짜 불 블록을 1초마다 보내고(96블록 안 플레이어), 끝나면 실제 블록으로 되돌린다. 10틱마다 범위 안 생물에 불(`fire-seconds`), 20틱마다 플레이어가 아닌 생물에 `IN_FIRE` 피해(던진 사람이 원인이라 처치 기록이 남고 탱크 불 배율이 적용된다). `burn-players`면 플레이어는 불만 붙는다. 물에 떨어지면 꺼진다. `/apoc molotov [수] [플레이어]`.
  - 블레이즈(`ability/BlazeAbility`): `setVisualFire(TriState.TRUE)`(tick에서도 다시 걸음), FIRE/FIRE_TICK/LAVA/HOT_FLOOR 피해 취소. `Volley` 효과: 12틱 예고(불꽃·연기, 블레이즈 소리) 뒤 6틱 간격으로 `SmallFireball`을 `shots`(3)발, 조준에 약간 흔들림, `setIsIncendiary(false)`. 좀비끼리는 기존 `noFriendlyFire`가 막는다.
  - 바다 라디오: `EndingManager.placed`에서 설치 블록의 바이옴이 바다 9종(OCEAN, DEEP_*, COLD/FROZEN/LUKEWARM/WARM 계열)이면 막는다. 강·해변은 허용.
  - 손 횃불(`HandLight`, 2틱마다): 활성 월드에서 주손·보조손에 횃불(14)·랜턴(15)·영혼 횃불/랜턴(10)을 든 플레이어의 눈 높이 블록이 공기나 물 소스면 그 플레이어에게만 `LIGHT` 블록을 보낸다. 움직이면 이전 칸을 되돌리고, 20틱마다 다시 보낸다. 내려놓거나 월드가 바뀌거나 꺼지면 되돌린다. `hand-light`(기본 true).

## 코드 구조

| 패키지 | 내용 |
| --- | --- |
| `zombie` (루트) | `ZombieApocalypsePlugin`(연결), `Apocalypse`(활성 월드, 메인 틱 루프, start/stop), `Msg`, `WorldBar` |
| `config` | `Settings`/`TypeSettings`(검증된 설정), `ConfigReader`(범위 검사, 모르는 키 경고) |
| `data` | `DataStore`(data.yml), `WorldData`, `EndingData`, `BlockPos` |
| `logic` | Bukkit 없는 규칙: 날짜, 타입 가중치, 호드 규모, 스폰 위치, 능력 판정, 라디오 지면과 안테나 부착 판정, 탈출 화살표 |
| `zombie` | `ZombieType`, `ZombieFactory`(스폰과 타입 적용), `ZombieRegistry`(로드된 커스텀 좀비), `Tags`/`Keys`/`Attributes` |
| `ability` | 타입별 능력, `Effects`(틱 단위 효과: 퓨즈, 갈고리, 탑승, 일으키기, 바위) |
| `event` | `WorldClock`(날짜 타이틀, 정기 호드, N일차 탱크), `BloodMoonManager`, `HordeManager`, `TankManager` |
| `ending` | `EndingItem`(라디오·안테나 아이템과 조합법), `AssemblyManager`(조합 후 조립 시간과 웨이브), `EndingManager`(설치·안테나·보호·피날레·탈출) |
| `spawn`, `listener`, `command` | 추가 스폰과 대체 규칙, 이벤트 연결, `/apocalypse` |

- 플러그인 소유 데이터는 모두 `zombieapoc` 네임스페이스(PDC와 속성 modifier)를 쓴다. 다른 플러그인의 modifier는 건드리지 않는다.
- 새 좀비 타입 추가 순서:
  1. `ZombieType`에 항목 추가
  2. `TypeSettings`에 설정 레코드 추가
  3. `config.yml`의 `types`에 같은 키 추가. `SettingsTest`가 번들 설정과 코드 기본값이 같은지 검사한다.
  4. `ability`에 능력 클래스를 만들고 `Abilities`에 등록

## 다음 작업

1. 서버 콘솔에서 `stop` → `build/libs/ZombieApocalypse-0.2.0.jar`를 `server/plugins`에 덮어쓰기(같은 파일 이름) → 사용자 bat로 다시 켜기. 서버 config.yml의 `ending.antenna-recipe`를 `shape: [" C ", "BRB", " C "]`, `keys: {C: IRON_CHAIN, B: BLAZE_ROD, R: REDSTONE}`로 고친다(아니면 계속 네더 석영).
2. 접속 → `/apoc start` → [TESTING](TESTING.md) 체크리스트 확인. 이번에는 엔딩의 조립(작업대 보스바, 멈춤, 완성 지급)과 안테나 설치를 먼저 본다. `/apoc egg all`로 타입별 확인(새로 넣은 위치 포함). 결과는 BUILD_REPORT와 TESTING에 기록한다.
3. 사용자에게 제안한 몰입·연출 아이디어 중 고른 것을 PLAN에 적고 단계별로 구현한다.
4. 플레이 결과에 따라 밸런스 조정. 수치는 대부분 `server/plugins/ZombieApocalypse/config.yml`에서 바꾼다.
5. PLAN "확인이 필요한 점" 결정. 새 후보 타입, 블러드문 주기(현재 7일), 호드 보상(현재 없음).

상세 설계: [PLAN](../PLAN.md). 검증: [BUILD_REPORT](BUILD_REPORT.md), 절차: [TESTING](TESTING.md).
