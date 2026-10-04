import 'dart:async';

import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/band/data/band_repository.dart';
import 'package:bandapp_client/features/band/presentation/join_band_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// 요청 중에 화면을 닫아도 응답 뒤 목록 갱신·선택은 반영된다. 예전에는 닫힌 화면의 ref 가
/// StateError 를 던져 저장은 됐는데 목록이 옛것으로 남았다(일정 등록·밴드 합류·나가기 등 공통).
void main() {
  testWidgets('합류 요청 중 뒤로 가도 새 밴드가 목록에 들어오고 선택된다', (tester) async {
    final reply = Completer<Band>();
    var bandListLoads = 0;
    final container = ProviderContainer(overrides: [
      bandRepositoryProvider.overrideWithValue(_SlowJoin(reply)),
      myBandsProvider.overrideWith((ref) async {
        bandListLoads++;
        return const <MyBand>[];
      }),
      selectedBandIdProvider.overrideWith(_Selected.new),
    ]);
    addTearDown(container.dispose);
    container.listen(myBandsProvider, (_, __) {}); // 화면 밖에서도 목록을 지켜보는 쪽(홈)

    await tester.pumpWidget(UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(
                builder: (_) => const JoinBandScreen(initialCode: 'ABCD2345'))),
            child: const Text('열기'),
          ),
        ),
      ),
    ));
    await tester.tap(find.text('열기'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('밴드 합류하기'));
    await tester.pump();
    final before = bandListLoads;

    // 응답 전에 화면을 닫는다.
    Navigator.of(tester.element(find.byType(JoinBandScreen))).pop();
    await tester.pumpAndSettle();
    reply.complete(Band(
        id: 42, name: '새밴드', leaderId: 1, reservationPermission: 'ANYONE', createdAt: DateTime(2026)));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(container.read(selectedBandIdProvider), 42);
    expect(bandListLoads, greaterThan(before));
  });
}

class _SlowJoin extends BandRepository {
  _SlowJoin(this.reply) : super(Dio());
  final Completer<Band> reply;

  @override
  Future<Band> joinBand(String code) => reply.future;
}

class _Selected extends SelectedBandId {
  @override
  int? build() => null;

  @override
  void select(int bandId) => state = bandId;
}
