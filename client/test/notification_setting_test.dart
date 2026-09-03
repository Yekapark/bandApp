import 'package:flutter_test/flutter_test.dart';
import 'package:bandapp_client/features/notification/data/notification_models.dart';

void main() {
  group('NotificationSetting.fromJson', () {
    test('parses pushEnabled and reminderOffsets', () {
      final s = NotificationSetting.fromJson({
        'pushEnabled': false,
        'reminderOffsets': [10, 60, 1440],
      });
      expect(s.pushEnabled, false);
      expect(s.reminderOffsets, [10, 60, 1440]);
    });

    test('defaults: pushEnabled true, offsets empty when keys missing', () {
      final s = NotificationSetting.fromJson({});
      expect(s.pushEnabled, true);
      expect(s.reminderOffsets, isEmpty);
    });

    test('empty reminderOffsets means no reminders', () {
      final s = NotificationSetting.fromJson({
        'pushEnabled': true,
        'reminderOffsets': <dynamic>[],
      });
      expect(s.reminderOffsets, isEmpty);
    });
  });
}
