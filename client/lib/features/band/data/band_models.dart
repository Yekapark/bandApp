import 'package:characters/characters.dart';

/// GET /bands/{id}, POST /bands, POST /bands/join 응답.
class Band {
  const Band({
    required this.id,
    required this.name,
    required this.leaderId,
    required this.reservationPermission,
    required this.createdAt,
  });

  final int id;
  final String name;
  final int leaderId;

  /// LEADER_ONLY | ANYONE | APPROVAL_REQUIRED
  final String reservationPermission;
  final DateTime createdAt;

  factory Band.fromJson(Map<String, dynamic> json) {
    return Band(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      leaderId: (json['leaderId'] as num).toInt(),
      reservationPermission: json['reservationPermission'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

/// GET /bands (밴드 전환 스위처용).
class MyBand {
  const MyBand({
    required this.id,
    required this.name,
    required this.myRole,
    required this.memberCount,
    required this.joinedAt,
  });

  final int id;
  final String name;

  /// LEADER | MEMBER
  final String myRole;
  final int memberCount;
  final DateTime joinedAt;

  bool get isLeader => myRole == 'LEADER';

  factory MyBand.fromJson(Map<String, dynamic> json) {
    return MyBand(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      myRole: json['myRole'] as String,
      memberCount: (json['memberCount'] as num).toInt(),
      joinedAt: DateTime.parse(json['joinedAt'] as String),
    );
  }
}

/// GET /bands/{id}/members.
class BandMember {
  const BandMember({
    required this.userId,
    required this.name,
    required this.role,
    required this.joinedAt,
  });

  final int userId;
  final String name;

  /// LEADER | MEMBER
  final String role;
  final DateTime joinedAt;

  bool get isLeader => role == 'LEADER';

  /// 아바타 이니셜 (이름 첫 글자).
  String get initial => name.isEmpty ? '?' : name.characters.first;

  factory BandMember.fromJson(Map<String, dynamic> json) {
    return BandMember(
      userId: (json['userId'] as num).toInt(),
      name: json['name'] as String? ?? '탈퇴한 사용자',
      role: json['role'] as String,
      joinedAt: DateTime.parse(json['joinedAt'] as String),
    );
  }
}
