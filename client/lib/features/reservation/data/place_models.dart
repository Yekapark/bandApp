/// GET /bands/{bandId}/rooms/search 의 항목 — 카카오 로컬 장소 검색 후보 한 건.
class PlaceSuggestion {
  const PlaceSuggestion({
    required this.name,
    this.roadAddress,
    this.address,
    this.category,
    this.phone,
    this.lat,
    this.lng,
  });

  final String name;
  final String? roadAddress;
  final String? address;
  final String? category;
  final String? phone;

  /// 검색 API가 준 좌표. 등록/수정 요청에 그대로 실어 보내면 서버가 지오코딩 없이 저장한다 —
  /// 지도에서 확인한 위치와 저장되는 위치가 어긋나지 않는다. 후보에 좌표가 없을 수도 있다.
  final double? lat;
  final double? lng;

  bool get hasLocation => lat != null && lng != null;

  /// 폼 주소 칸에 채울 값 (도로명 우선, 없으면 지번).
  String get bestAddress => (roadAddress != null && roadAddress!.isNotEmpty)
      ? roadAddress!
      : (address ?? '');

  factory PlaceSuggestion.fromJson(Map<String, dynamic> json) {
    return PlaceSuggestion(
      name: json['name'] as String? ?? '',
      roadAddress: json['roadAddress'] as String?,
      address: json['address'] as String?,
      category: json['category'] as String?,
      phone: json['phone'] as String?,
      lat: (json['lat'] as num?)?.toDouble(),
      lng: (json['lng'] as num?)?.toDouble(),
    );
  }
}
