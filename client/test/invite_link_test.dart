import 'package:bandapp_client/core/deeplink/invite_link_handler.dart';
import 'package:flutter_test/flutter_test.dart';

/// 초대 링크를 어떤 것만 받아들이고, 코드를 어떻게 떼어내는지.
///
/// 이 판정이 틀어지면 증상이 조용하다 — 링크를 눌러도 아무 일도 안 일어나거나(스킴이
/// 안 맞아서), 반대로 앱을 열 때마다 합류 화면이 뜬다(아무 링크나 받아서). 둘 다
/// 실기기에 넣어 봐야만 보인다.
void main() {
  group('우리 초대 링크인지', () {
    test('bandule://invite 만 받는다', () {
      expect(
        InviteLinkHandler.isInviteLink(Uri.parse('bandule://invite/ABCD2345')),
        isTrue,
      );
    });

    test('네이버 밴드 스킴은 우리 것이 아니다', () {
      // 이 값을 쓰다가 초대 링크가 네이버 밴드 앱을 열었다.
      expect(
        InviteLinkHandler.isInviteLink(Uri.parse('bandapp://invite/ABCD2345')),
        isFalse,
      );
    });

    test('웹 페이지 주소는 무시한다', () {
      // 웹에서는 페이지 주소 자체가 핸들러로 들어온다. 이걸 받으면 앱을 열 때마다
      // 합류 화면이 뜬다.
      expect(
        InviteLinkHandler.isInviteLink(Uri.parse('https://bandule.com/invite/ABCD2345')),
        isFalse,
      );
    });

    test('같은 스킴이어도 다른 용도면 무시한다', () {
      expect(
        InviteLinkHandler.isInviteLink(Uri.parse('bandule://settings')),
        isFalse,
      );
    });
  });

  group('로그인 전에 받은 코드', () {
    test('담아 뒀다가 꺼내면 비워진다', () {
      // 로그인할 때마다 합류 화면이 다시 뜨면 안 된다.
      InviteLinkHandler.pendingCode = 'ABCD2345';
      expect(InviteLinkHandler.takePendingCode(), 'ABCD2345');
      expect(InviteLinkHandler.takePendingCode(), isNull);
    });

    test('담아 둔 것이 없으면 null', () {
      InviteLinkHandler.pendingCode = null;
      expect(InviteLinkHandler.takePendingCode(), isNull);
    });
  });

  group('초대코드 떼어내기', () {
    test('첫 경로 조각이 코드다', () {
      expect(InviteLinkHandler.codeOf(Uri.parse('bandule://invite/ABCD2345')),
          'ABCD2345');
    });

    test('뒤에 슬래시가 붙어도 같다', () {
      expect(InviteLinkHandler.codeOf(Uri.parse('bandule://invite/ABCD2345/')),
          'ABCD2345');
    });

    test('코드가 없으면 null — 화면은 열되 입력칸을 비워 둔다', () {
      expect(InviteLinkHandler.codeOf(Uri.parse('bandule://invite')), isNull);
    });

    test('코드 형식은 여기서 따지지 않는다 — 서버가 판정한다', () {
      // 앱이 미리 거르면 서버가 코드 규칙을 바꿀 때 앱이 먼저 막는다.
      expect(InviteLinkHandler.codeOf(Uri.parse('bandule://invite/zzz')), 'zzz');
    });
  });
}
