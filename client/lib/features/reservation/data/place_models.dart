/// GET /bands/{bandId}/rooms/search 의 항목 — 네이버 지역검색 후보 한 건.
class PlaceSuggestion {
  const PlaceSuggestion({
    required this.name,
    this.roadAddress,
    this.address,
    this.category,
    this.phone,
  });

  final String name;
  final String? roadAddress;
  final String? address;
  final String? category;
  final String? phone;

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
    );
  }
}
