import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// 화면에 나가는 문구가 사용자 말인지 지킨다.
///
/// 사람이 눈으로 훑으면 반드시 새어 나간다. 실제로 "앱 알림 권한과 Firebase 설정이 필요해요",
/// "카카오 네이티브 앱 키(KAKAO_NATIVE_APP_KEY)를 설정하면", "카카오 개발자 콘솔에 이 앱의
/// 패키지명과 키 해시가 등록돼 있는지" 같은 문구가 릴리스 직전까지 남아 있었다.
///
/// 잡는 것은 둘이다.
///   1. 사용자가 할 수 있는 게 없는 **내부 용어**
///   2. 앱 말투(`~어요`)와 어긋나는 **격식체 어미** — 성공은 "했어요", 실패는 "못했습니다"로
///      갈리던 것을 통일했다. 새 화면에서 다시 섞이지 않게 막는다
void main() {
  /// 사용자 화면 문구에 있으면 안 되는 말. 값은 "왜 안 되는지".
  const forbidden = <String, String>{
    'Firebase': '사용자가 할 수 있는 일이 아니다',
    'KAKAO_NATIVE_APP_KEY': '환경변수 이름',
    '개발자 콘솔': '개발자가 할 일을 사용자에게 시킨다',
    '키 해시': '개발자 용어',
    '에뮬레이터': '개발 환경 이야기',
    'presigned': '내부 구현',
    '프로바이더': '내부 구현',
    '마이그레이션': '내부 구현',
  };

  /// 앱 말투와 어긋나는 어미.
  const formalEndings = <String>[
    '못했습니다',
    '없습니다',
    '있습니다',
    '됩니다',
    '합니다',
    '입니다',
  ];

  /// 검사에서 빼는 줄.
  ///
  /// `debugPrint` 는 **개발자에게 보내는 로그**다. 오히려 내부 용어가 거기 있어야 맞는다 —
  /// 화면 문구에서 원인 설명을 걷어내면서 그쪽으로 내린 것이라, 여기서 잡으면 앞뒤가 안 맞는다.
  bool isAllowed(String line) {
    final t = line.trimLeft();
    return t.startsWith('//') ||
        t.startsWith('///') ||
        t.startsWith('*') ||
        line.contains('debugPrint(');
  }

  List<File> screenFiles() {
    final dir = Directory('lib/features');
    return dir
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => f.path.endsWith('.dart'))
        .where((f) => f.path.contains('presentation'))
        .toList();
  }

  test('사용자 화면에 내부 용어가 남아 있지 않다', () {
    final offenders = <String>[];
    for (final file in screenFiles()) {
      final lines = file.readAsLinesSync();
      for (var i = 0; i < lines.length; i++) {
        final line = lines[i];
        if (isAllowed(line)) continue;
        // 문자열 리터럴 안만 본다 — 클래스명·import 는 대상이 아니다.
        if (!line.contains("'")) continue;
        forbidden.forEach((word, why) {
          if (line.contains(word)) {
            offenders.add('${file.path}:${i + 1}  "$word" ($why)\n    ${line.trim()}');
          }
        });
      }
    }
    expect(offenders, isEmpty,
        reason: '사용자에게 보일 문구에 내부 용어가 있다:\n${offenders.join('\n')}');
  });

  test('사용자 화면 문구가 ~어요 체를 지킨다', () {
    final offenders = <String>[];
    for (final file in screenFiles()) {
      final lines = file.readAsLinesSync();
      for (var i = 0; i < lines.length; i++) {
        final line = lines[i];
        if (isAllowed(line)) continue;
        if (!line.contains("'")) continue;
        for (final ending in formalEndings) {
          if (line.contains(ending)) {
            offenders.add('${file.path}:${i + 1}  "$ending"\n    ${line.trim()}');
          }
        }
      }
    }
    expect(offenders, isEmpty,
        reason: '앱은 ~어요 체로 통일했다. 격식체가 남아 있다:\n${offenders.join('\n')}');
  });
}
