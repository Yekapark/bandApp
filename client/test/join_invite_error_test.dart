import 'package:bandapp_client/core/network/api_exception.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/band/data/band_repository.dart';
import 'package:bandapp_client/features/band/presentation/join_band_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// QA BAND-04 — 만료·무효·소진 초대코드는 이유와 함께 "새 코드를 받으라" 는 다음 행동을 보인다.
void main() {
  testWidgets('만료된 코드면 서버 문구 + 새 코드 안내', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [bandRepositoryProvider.overrideWithValue(_ExpiredInvite())],
      child: const MaterialApp(home: JoinBandScreen(initialCode: 'ABCD2345')),
    ));
    await tester.tap(find.text('밴드 합류하기'));
    await tester.pumpAndSettle();

    expect(find.text('만료된 초대코드입니다. 밴드장에게 새 초대코드를 받아 주세요.'), findsOneWidget);
  });
}

class _ExpiredInvite extends BandRepository {
  _ExpiredInvite() : super(Dio());

  @override
  Future<Band> joinBand(String code) async => throw ApiException(
      code: 'INVITE_EXPIRED', message: '만료된 초대코드입니다.', statusCode: 410);
}
