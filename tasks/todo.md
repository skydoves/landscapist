# Landscapist Core: 벤치마크 신뢰성 개편 + Coil3 성능 격차 해소

자율 모드. 각 Phase 종료 시 리뷰어 3명(병렬, 독립 렌즈) 게이트 통과 후 다음 Phase 진행.
모든 Phase: 구현 → 단위 테스트 → build/test 검증 → 리뷰 3명 → 피드백 반영 → 진행.

## 검증 환경 현실
- JVM(desktop) `kotlin.test` 단위 테스트: 실행 가능 (검증 주력)
- Android 컴파일: SDK 존재 → 가능 (JDK17 toolchain)
- 디바이스/에뮬레이터: 없음 → Android instrumentation 퍼프 테스트 + macrobenchmark는 여기서 "실행" 불가
  - 대응: 벤치마크 코드는 정확/대칭으로 개편(메인테이너가 디바이스에서 실행), 핵심 로직은 JVM 마이크로벤치마크로 실측

---

## Phase 0 — 공개 벤치마크 신뢰성 싸그리 개편
- [ ] 0.1 app/androidTest 퍼프 테스트 9종 + macrobenchmark 전수 감사, 비대칭/결함 카탈로그
- [ ] 0.2 단일 공정 방법론 정의: 모든 라이브러리를 각자의 완료 신호(Coil listener, Glide RequestListener, Fresco controllerListener, Landscapist onImageStateChanged)로 setContent→디코드완료 측정. 대칭 warmup/캐시클리어/tail
- [ ] 0.3 비교 테스트 재작성(대칭 완료 감지). 오해 유발 파일(latch 미카운트다운 ImageLibraryComparisonReport 등) 정리/삭제
- [ ] 0.4 macrobenchmark 앱 수정: UIAutomator가 기대하는 탭+스크롤 LazyColumn/Grid 실제 렌더(현재 static Column → scroll no-op)
- [ ] 0.5 메모리 측정 수정: coerceAtLeast(0) 바닥 처리/“0 KB” 허위 제거, 전 라이브러리 동일 기준
- [ ] 0.6 문서 정합: docs/landscapist/performance-comparison.md, performance/README.md, root README.md. "2.6x faster" / "0 KB / streaming tiles" 허위 제거, 방법론 명시, 검증 가능한 주장(AAR 크기)만 유지
- [ ] 0.7 JVM 마이크로벤치마크 하니스(device-free, fake fetcher/decoder 결정론): dedup 효과(동시 N요청→fetch 횟수), 스케줄러 처리량, 캐시 hit latency. Phase1 개선 실측에 재사용
- [ ] **리뷰 게이트 0**: 리뷰어 3명(방법론 공정성 / 측정 정확성 / 문서 정직성)

## Phase 1 — 핵심 성능 격차 (HIGH) ✅ 완료
- [x] 1.1 In-flight dedup: load() 표준경로를 memoryKey로 coalesce. 참조카운트로 형제 생존 + 마지막 awaiter 취소 시 공유작업 취소
- [x] 1.2 progressiveEnabled 기본 false: data class + Builder 기본값, KDoc 갱신
- [x] 1.3 DecodeScheduler busy-spin 제거: PriorityGate(suspend, CompletableDeferred), 중복 async 제거, global() 동기화, NonCancellable로 permit 누수 방지
- [x] 1.4 단위 테스트: dedup 5(동시→1회, distinct, 순차재fetch, 형제생존, 유일취소중단), scheduler 4(cap/우선순위/취소/태그취소), progressive 기본값
- [x] **리뷰 게이트 1**: 3명 모두 PASS-WITH-NITS → MED(취소 의미) 참조카운트로 수정, grantNext complete()존중+granted플래그, KDoc/주석 보강. apiCheck 통과(ABI 유지)

## Phase 2 — 할당/메모리 (MEDIUM) ✅ 완료
- [x] 2.1 Painter 비트맵 스킵: composePainterPlugins를 @Composable 람다로 → PainterPlugin 없으면 비트맵 생성 생략 (.api 갱신). 5개 호출부 갱신
- [x] 2.2 메모리 캐시 heap 비율(Android 25%/256MB cap) + 허위 KDoc 수정
- [x] 2.3 코어 블로킹 I/O를 ioDispatcher(expect/actual jvm=IO, apple/wasm=Default)로, applyTransformations는 Default, 디코드는 DecodeScheduler Default
- [~] 2.4 보류(LOW): estimateBitmapSize Android 정밀화, filterIsInstance/eager 정리 — 효익 한계 + 위험
- [x] **리뷰 게이트 2**: 3명 PASS-WITH-NITS → applyTransformations Default로 수정, 라인래핑, 64MB 경계 주석
  - ⚠️ 사용자 결정 필요: CrossfadePlugin이 no-op PainterPlugin이라 흔한 crossfade 기본값에서 스킵 무력화. PainterPlugin 제거 시 스킵 확장되나 flagship 공개 API 바이너리 깨짐 → 보고만

## Phase 3 — KMP 완성도 (MEDIUM) ✅ 완료
- [x] 3.1 non-Android 디코더 실제 dims: 순수 Kotlin 헤더 파서(PNG/JPEG/GIF/WebP, 의존성 0=경량 유지)로 apple/wasm/skia-progressive가 실제 width/height 반환 → estimateBitmapSize!=0 → LRU eviction 정상화(OOM 수정)
- [~] 3.2 desktop: 이미 실제 dims 반환 + 다운샘플(full-res 디코드 peak memory는 후속). BufferedImage 미렌더는 별도 이슈로 보고
- [x] 3.3 getLandscapist() Android 프로세스 싱글톤(@Volatile DCL) — 파편화 제거, scope lifecycle O(N)→O(1)
- [x] 3.4 단위 테스트: ImageDimensionsTest 15개(PNG/JPEG/GIF/WebP-VP8/VP8L/VP8X/progressive/truncation/0-len/대형/빈입력)
- [x] **리뷰 게이트 3**: 3명 PASS-WITH-NITS → PNG/GIF 시그니처 엄격화, 고위험(VP8L/VP8X/JPEG edge) 테스트 8개 추가, setInstance KDoc 주석. (서브샘플링 "회귀"는 false alarm=독립 디코더, autoAspectRatio는 fix)
  - 후속(보고): estimateBitmapSize는 디코드 크기 추정(RawImageData는 인코딩 바이트라 보수적=안전); desktop full-res 디코드 peak memory; BufferedImage 미렌더

## 마무리
- [ ] 전체 JVM 테스트 + 마이크로벤치마크 실행, before/after 수치 캡처
- [ ] binary-compatibility-validator(.api) 갱신, explicit-api 준수 확인
- [ ] 최종 요약: 측정 가능한 before/after + 메인테이너용 on-device macrobenchmark 실행 가이드
- [ ] 문서 최종 정합

## 주의/제약
- explicit-api=strict (core main): 새 public 선언은 가시성+반환타입 명시
- binary compat: *.api 파일 있으면 갱신 필수
- 경량성 유지: 새 의존성 0 목표. 변경은 로직 위주(중복 작업 제거 = 경량 강화)
- KMP expect/actual 정합 유지

## Review (각 Phase 종료 시 채움)

### Phase 0 (완료, 게이트 통과)
구현: 대칭 `ImageLibraryBenchmark`(4 래퍼, url-gated 종료감지, timeout=실패기록, RUN_ID cold보장),
엔진레벨 `UnitPerformanceTest` 유지, 깨진 8개 삭제. macrobenchmark 앱 탭+스크롤 리스트화 + 태그버그 수정 +
생성기 여정. 문서 4종 정직화(실측 AAR 313/468/693/~1.0 MiB, 날조 perf수치/메커니즘 제거). 스크립트 통합.
리뷰어 3명(공정성/런타임정확성/문서정직성) 모두 NEEDS-FIXES → 반영 완료:
- waitUntil timeout throw → try/catch 실패기록 (HIGH)
- cold-load 미보장 → RUN_ID nonce (HIGH)
- "in-flight coalescing: Yes" 거짓 → 문서서 제거, Phase1서 복원 (HIGH)
- stale-callback 레이스 → url-gating (MED)
- Fresco config/ previewPlaceholder/ scrollPerfComparison 비대칭 → 주석/제거 (MED)
- 메모리압력 트리밍 opt-in 단서, Fresco AAR 표기, core 크기 재현성, Locale.US (MED/LOW)
검증: spotless(JDK17) + 컴파일(androidTest/benchmark app/macrobench) 통과. 디바이스 없어 on-device 실행은 메인테이너 몫.
남은 LOW(비차단): baseline-prof.txt는 device서 재생성, size-request(400) vs render(200dp)는 균일이라 무편향.

### Phase 1 (완료, 게이트 통과)
구현: dedup(memoryKey coalesce, InFlightLoad 참조카운트), progressive 기본 off, DecodeScheduler 재작성(PriorityGate).
검증: desktopTest dedup 5 + scheduler 4 + ImageRequestBuilder 22 통과, apiCheck 통과(ABI 무변경), android/ios/wasm/image 컴파일 통과.
리뷰 3명 PASS-WITH-NITS → 반영: (MED) 분리 async 취소 미전파 → 참조카운트로 마지막 awaiter 취소 시 공유작업 cancel + 테스트 2종 추가;
(MED) grantNext가 complete() 반환 무시 → 루프+granted 플래그; (LOW) invokeOnCompletion 위치 주석, activeCount 근사 명시, coalescing 동일파라미터 가정 KDoc.
후속(미적용, 별도 major): PrioritizedRequest @Deprecated (현재 제거 시 ABI 깨짐).
