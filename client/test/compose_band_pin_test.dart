import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/board/data/board_models.dart';
import 'package:bandapp_client/features/board/data/board_repository.dart';
import 'package:bandapp_client/features/board/presentation/post_compose_screen.dart';
import 'package:bandapp_client/features/plan/application/plan_providers.dart';
import 'package:bandapp_client/features/plan/data/plan_models.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// POST-10·UI-06 — 글 작성 중 "현재 밴드" 가 바뀌어도(밴드에서 나가 첫 밴드로 돌아감, 다른 밴드 알림을 누름)
/// 글은 화면을 연 밴드에 올라간다. 예전에는 등록 시점의 현재 밴드에 올라갔다.
void main() {
  MyBand band(int id) => MyBand(
      id: id,
      name: '밴드$id',
      myRole: 'MEMBER',
      memberCount: 3,
      joinedAt: DateTime(2026));

  testWidgets('작성 중 밴드가 바뀌어도 연 밴드에 등록한다', (tester) async {
    final repo = _FakeBoard();
    final current = StateProvider<MyBand?>((_) => band(1));
    final container = ProviderContainer(overrides: [
      currentBandProvider.overrideWith((ref) => ref.watch(current)),
      boardRepositoryProvider.overrideWithValue(repo),
      bandPlanProvider
          .overrideWith((ref, id) async => const BandPlan(tier: 'PREMIUM')),
    ]);
    addTearDown(container.dispose);

    await tester.pumpWidget(UncontrolledProviderScope(
      container: container,
      child: const MaterialApp(home: PostComposeScreen()),
    ));
    await tester.enterText(find.byType(TextField).at(0), '제목');
    await tester.enterText(find.byType(TextField).at(1), '본문');

    container.read(current.notifier).state = band(2);
    await tester.pump();
    await tester.tap(find.text('등록'));
    await tester.pumpAndSettle();

    expect(repo.createdIn, [1]);
  });
}

class _FakeBoard extends BoardRepository {
  _FakeBoard() : super(Dio());

  final createdIn = <int>[];

  @override
  Future<PostDetail> create({
    required int bandId,
    required String title,
    required String content,
  }) async {
    createdIn.add(bandId);
    return PostDetail(
        id: 9,
        bandId: bandId,
        authorId: 1,
        authorName: '나',
        title: title,
        content: content,
        createdAt: DateTime(2026),
        editable: true,
        media: const []);
  }

  @override
  Future<PostPage> list(
          {required int bandId, String? cursor, int limit = 20}) async =>
      const PostPage(posts: [], nextCursor: null, hasNext: false);
}
