import 'package:bandapp_client/features/notification/application/notification_route.dart';
import 'package:bandapp_client/features/settlement/application/settlement_providers.dart';
import 'package:bandapp_client/features/settlement/data/settlement_models.dart';
import 'package:bandapp_client/features/settlement/data/settlement_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

/// U38·QA-R04 — 다른 멤버가 정산을 재계산했는데 알림을 눌러 다시 열면 옛 금액·납부 체크가 보였다.
void main() {
  const key = (bandId: 1, reservationId: 7);

  test('정산 화면을 닫았다 다시 열면 서버에서 새로 받는다', () async {
    final repo = _CountingRepo();
    final c = ProviderContainer(
        overrides: [settlementRepositoryProvider.overrideWithValue(repo)]);
    addTearDown(c.dispose);

    var sub = c.listen(settlementProvider(key), (_, __) {});
    await c.read(settlementProvider(key).future);
    sub.close(); // 화면 닫힘
    await Future<void>.delayed(Duration.zero);

    sub = c.listen(settlementProvider(key), (_, __) {});
    await c.read(settlementProvider(key).future);
    expect(repo.calls, 2);
  });

  test('정산 화면이 열린 채로 알림을 누르면 다시 받는다', () async {
    final repo = _CountingRepo();
    final c = ProviderContainer(
        overrides: [settlementRepositoryProvider.overrideWithValue(repo)]);
    addTearDown(c.dispose);
    c.listen(settlementProvider(key), (_, __) {}); // 뒤에 열려 있는 정산 화면
    await c.read(settlementProvider(key).future);

    openNotification(_NoNav(), c.invalidate, '/settlements/7/x');
    await c.read(settlementProvider(key).future);
    expect(repo.calls, 2);
  });
}

class _CountingRepo extends SettlementRepository {
  _CountingRepo() : super(Dio());
  int calls = 0;

  @override
  Future<Settlement?> get({required int bandId, required int reservationId}) async {
    calls++;
    return null;
  }
}

/// 이동은 이 테스트의 관심사가 아니다 — 캐시만 본다.
class _NoNav extends Fake implements GoRouter {
  @override
  Future<T?> push<T extends Object?>(String location, {Object? extra}) async =>
      null;
}
