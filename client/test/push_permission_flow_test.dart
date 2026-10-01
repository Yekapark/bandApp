import 'package:bandapp_client/features/notification/application/push_permission_flow.dart';
import 'package:flutter_test/flutter_test.dart';

/// LAUNCH_REVIEW U21 — 안드로이드 권한 창은 앱 안내 시트에서 [알림 받기] 를 고른 뒤에만 띄운다.
void main() {
  late List<String> calls;
  late bool granted;
  late bool asked;
  late bool? primerAnswer;
  late bool requestResult;
  late Duration requestTakes;

  PushPermissionFlow flow() => PushPermissionFlow(
        isGranted: () async => granted,
        alreadyAsked: () async => asked,
        markAsked: () async {
          calls.add('markAsked');
          asked = true;
        },
        showPrimer: () async {
          calls.add('primer');
          return primerAnswer;
        },
        request: () async {
          calls.add('request');
          await Future<void>.delayed(requestTakes);
          return requestResult;
        },
        openSettings: () async => calls.add('openSettings'),
        instantDenial: const Duration(milliseconds: 50),
      );

  setUp(() {
    calls = [];
    granted = false;
    asked = false;
    primerAnswer = true;
    requestResult = true;
    requestTakes = const Duration(milliseconds: 100);
  });

  test('이미 허용돼 있으면 아무것도 묻지 않는다 (안드로이드 12 이하 포함)', () async {
    granted = true;
    expect(await flow().run(), isTrue);
    expect(calls, isEmpty);
  });

  test('처음이면 안내 시트 → [알림 받기] → 권한 창 순서', () async {
    expect(await flow().run(), isTrue);
    expect(calls, ['primer', 'markAsked', 'request']);
  });

  test('[나중에] 를 고르면 권한 창을 띄우지 않고, 다음에도 다시 묻지 않는다', () async {
    primerAnswer = false;
    expect(await flow().run(), isFalse);
    expect(calls, ['primer', 'markAsked']);

    calls.clear();
    expect(await flow().run(), isFalse);
    expect(calls, isEmpty);
  });

  test('나중에 휴대폰 설정에서 켜고 돌아오면 묻지 않고 바로 통과', () async {
    primerAnswer = false;
    await flow().run();
    calls.clear();
    granted = true;
    expect(await flow().run(), isTrue);
    expect(calls, isEmpty);
  });

  test('시트를 보여 주지 못했으면 물은 것으로 치지 않는다 — 다음 기회에 다시', () async {
    primerAnswer = null;
    expect(await flow().run(), isFalse);
    expect(calls, ['primer']);
    expect(asked, isFalse);
  });

  test('권한 창에서 "허용 안함" 을 누르면 그대로 끝 (휴대폰 설정을 열지 않는다)', () async {
    requestResult = false;
    expect(await flow().run(), isFalse);
    expect(calls, ['primer', 'markAsked', 'request']);
  });

  test('권한 창이 뜨지도 않고 바로 거절로 돌아오면 휴대폰 알림 설정을 연다', () async {
    requestResult = false;
    requestTakes = Duration.zero;
    expect(await flow().run(), isFalse);
    expect(calls, ['primer', 'markAsked', 'request', 'openSettings']);
  });
}
