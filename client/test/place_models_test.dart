import 'package:bandapp_client/features/reservation/data/place_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('PlaceSuggestion.fromJson', () {
    test('검색 응답의 좌표를 읽는다', () {
      final s = PlaceSuggestion.fromJson({
        'name': '그루브 합주실',
        'roadAddress': '서울 마포구 와우산로 1',
        'address': '서울 마포구 서교동 1-1',
        'category': '문화,예술',
        'phone': '02-334-1082',
        'lat': 37.5559,
        'lng': 126.9236,
      });

      expect(s.lat, 37.5559);
      expect(s.lng, 126.9236);
      expect(s.hasLocation, isTrue);
      expect(s.bestAddress, '서울 마포구 와우산로 1');
    });

    test('좌표가 없는 후보는 null 이고 hasLocation 이 false', () {
      final s = PlaceSuggestion.fromJson({'name': '좌표없는곳'});

      expect(s.lat, isNull);
      expect(s.lng, isNull);
      expect(s.hasLocation, isFalse);
    });

    test('한쪽 좌표만 있으면 지도에 찍을 수 없으므로 hasLocation 이 false', () {
      final s = PlaceSuggestion.fromJson({'name': '반쪽', 'lat': 37.5});

      expect(s.hasLocation, isFalse);
    });

    test('정수로 와도 double 로 읽는다', () {
      final s = PlaceSuggestion.fromJson({'name': '정수', 'lat': 37, 'lng': 127});

      expect(s.lat, 37.0);
      expect(s.lng, 127.0);
    });

    test('도로명이 없으면 지번을 쓴다', () {
      final s = PlaceSuggestion.fromJson({
        'name': '지번만',
        'address': '서울 마포구 서교동 1-1',
      });

      expect(s.bestAddress, '서울 마포구 서교동 1-1');
    });
  });
}
