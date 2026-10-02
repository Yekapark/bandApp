import 'package:bandapp_client/features/auth/presentation/terms_screen.dart';
import 'package:bandapp_client/features/board/presentation/post_detail_screen.dart';
import 'package:bandapp_client/shared/widgets/legal_link.dart';
import 'package:bandapp_client/shared/widgets/primary_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('약관 동의 화면 (QA-F06)', () {
    late List<Uri> opened;
    late Future<bool> Function(Uri) original;

    setUp(() {
      opened = [];
      original = legalLauncher;
      legalLauncher = (uri) async {
        opened.add(uri);
        return true;
      };
    });
    tearDown(() => legalLauncher = original);

    Future<void> pump(WidgetTester tester) =>
        tester.pumpWidget(const MaterialApp(home: TermsScreen()));

    testWidgets('"보기" 를 누르면 약관·방침 전문을 연다', (tester) async {
      await pump(tester);
      expect(find.text('보기'), findsNWidgets(2));

      await tester.tap(find.text('보기').at(0));
      await tester.tap(find.text('보기').at(1));
      await tester.pump();

      expect(opened, [LegalUrls.terms, LegalUrls.privacy]);
      expect(LegalUrls.terms.toString(), 'https://bandule.com/terms/');
      expect(LegalUrls.privacy.toString(), 'https://bandule.com/privacy/');
    });

    testWidgets('"보기" 는 체크를 바꾸지 않고, 줄을 누르면 체크가 바뀐다', (tester) async {
      await pump(tester);
      bool enabled() =>
          tester.widget<PrimaryButton>(find.byType(PrimaryButton)).enabled;

      await tester.tap(find.text('보기').at(0));
      await tester.pump();
      expect(enabled(), isFalse);

      await tester.tap(find.text('전체 동의'));
      await tester.pump();
      expect(enabled(), isTrue);
      expect(opened, [LegalUrls.terms]);
    });

    testWidgets('열기에 실패하면 주소를 안내한다', (tester) async {
      legalLauncher = (_) async => false;
      await pump(tester);
      await tester.tap(find.text('보기').at(0));
      await tester.pump();
      expect(find.textContaining('bandule.com/terms/'), findsOneWidget);
    });
  });

  testWidgets('세로 영상을 가로 화면에서 열어도 영상·탐색 막대가 화면 안에 든다 (QA-F04)',
      (tester) async {
    // S24 가로: 780x360 dp.
    tester.view.physicalSize = const Size(2340, 1080);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);

    const controlsKey = Key('controls');
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          appBar: AppBar(),
          body: const Center(
            child: FullVideoLayout(
              aspectRatio: 9 / 16,
              video: ColoredBox(color: Colors.black),
              controls: SizedBox(key: controlsKey, height: 4),
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    final screen = Offset.zero & const Size(780, 360);
    final video = tester.getRect(find.byType(AspectRatio));
    final controls = tester.getRect(find.byKey(controlsKey));
    expect(video.bottom, lessThan(controls.top), reason: '$video / $controls');
    expect(screen.contains(controls.bottomRight - const Offset(1, 1)), isTrue,
        reason: '$controls');
    expect(video.width / video.height, closeTo(9 / 16, 0.01));
  });
}
