/// GET /bands/{bandId}/rooms 의 항목 · POST /bands/{bandId}/rooms 응답.
class Room {
  const Room({
    required this.id,
    required this.name,
    this.address,
    this.phone,
    this.memo,
    this.lat,
    this.lng,
    this.usageCount = 0,
  });

  final int id;
  final String name;
  final String? address;
  final String? phone;
  final String? memo;

  /// 지오코딩 결과. 없으면 지도에 못 찍는다(목록·선택에는 지장 없음).
  final double? lat;
  final double? lng;
  final int usageCount;

  bool get hasLocation => lat != null && lng != null;

  /// 목록에서 부제로 쓸 한 줄 (주소 > 메모 > 사용 횟수).
  String get subtitle {
    if (address != null && address!.trim().isNotEmpty) return address!;
    if (memo != null && memo!.trim().isNotEmpty) return memo!;
    if (usageCount > 0) return '합주 $usageCount회 사용';
    return '주소 미등록';
  }

  factory Room.fromJson(Map<String, dynamic> json) {
    return Room(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String? ?? '합주실',
      address: json['address'] as String?,
      phone: json['phone'] as String?,
      memo: json['memo'] as String?,
      lat: (json['lat'] as num?)?.toDouble(),
      lng: (json['lng'] as num?)?.toDouble(),
      usageCount: (json['usageCount'] as num?)?.toInt() ?? 0,
    );
  }
}
