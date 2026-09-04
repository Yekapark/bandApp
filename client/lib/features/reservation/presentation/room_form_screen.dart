import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:kakao_map_sdk/kakao_map_sdk.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../band/application/band_providers.dart';
import '../application/calendar_providers.dart';
import '../data/place_models.dart';
import '../data/room_models.dart';
import '../data/room_repository.dart';
import 'widgets/room_map_bits.dart';

/// 합주실 등록/수정 폼. 등록은 성공 시 생성된 [Room] 을 들고 pop 한다(선택 시트에서 바로 고르도록).
/// [existing] 이 있으면 수정 모드(PUT).
///
/// 주소 칸은 카카오 장소검색과 연결돼 있다 — 두 글자 이상 입력하면 후보가 뜨고, 고르면
/// 이름·주소·연락처가 자동으로 채워진다. 서버에 검색 키가 없으면 후보가 안 뜰 뿐 직접 입력은 된다.
///
/// 후보는 아래 지도에도 핀으로 찍힌다. 같은 이름의 합주실이 여럿일 때 주소 문자열만 보고 고르는
/// 대신 위치를 눈으로 확인하고 고르라는 것이고, **고른 핀의 좌표를 그대로 저장한다** — 화면에서
/// 본 위치와 저장되는 위치가 어긋나면 확인한 의미가 없다. 직접 타이핑한 주소는 좌표가 없으므로
/// 서버가 주소로 지오코딩한다.
class RoomFormScreen extends ConsumerStatefulWidget {
  const RoomFormScreen({super.key, this.existing});

  final Room? existing;

  @override
  ConsumerState<RoomFormScreen> createState() => _RoomFormScreenState();
}

class _RoomFormScreenState extends ConsumerState<RoomFormScreen> {
  final _name = TextEditingController();
  final _address = TextEditingController();
  final _phone = TextEditingController();
  final _memo = TextEditingController();
  bool _loading = false;
  String? _error;

  Timer? _debounce;
  List<PlaceSuggestion> _suggestions = const [];
  bool _searching = false;
  int _searchSeq = 0;

  KakaoMapController? _map;
  PoiStyle? _poiStyle;
  final List<Poi> _pins = [];

  /// 핀 갱신은 비동기라 빠르게 타이핑하면 겹쳐 돈다. 검색(_searchSeq)과 같은 방식으로
  /// 마지막 호출만 살려 유령 핀이 남지 않게 한다.
  int _pinSeq = 0;

  /// 검색에서 고른 후보의 좌표. 등록 시 그대로 서버로 보낸다.
  /// 주소를 손으로 고치면 버린다 — 옛 핀 좌표가 새 주소에 붙으면 안 된다.
  double? _pickedLat;
  double? _pickedLng;

  bool get _isEdit => widget.existing != null;
  bool get _mapAvailable => AppConfig.mapEnabled;

  @override
  void initState() {
    super.initState();
    final r = widget.existing;
    if (r != null) {
      _name.text = r.name;
      _address.text = r.address ?? '';
      _phone.text = r.phone ?? '';
      _memo.text = r.memo ?? '';
      _pickedLat = r.lat;
      _pickedLng = r.lng;
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _name.dispose();
    _address.dispose();
    _phone.dispose();
    _memo.dispose();
    super.dispose();
  }

  void _onAddressChanged(String value) {
    setState(() {
      _error = null;
      // 주소를 손으로 고쳤으면 직전에 고른 핀의 좌표는 더 이상 이 주소의 것이 아니다.
      _pickedLat = null;
      _pickedLng = null;
    });
    _debounce?.cancel();
    final q = value.trim();
    if (q.length < 2) {
      setState(() {
        _suggestions = const [];
        _searching = false;
      });
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 350), () => _search(q));
  }

  Future<void> _search(String query) async {
    final band = ref.read(currentBandProvider);
    if (band == null) {
      debugPrint('장소 검색 건너뜀: 선택된 밴드가 없다');
      return;
    }
    final seq = ++_searchSeq;
    setState(() => _searching = true);
    try {
      final results = await ref
          .read(roomRepositoryProvider)
          .searchPlaces(bandId: band.id, query: query);
      debugPrint('장소 검색 결과 ${results.length}건 (query=$query)');
      if (!mounted || seq != _searchSeq) return;
      setState(() {
        _suggestions = results;
        _searching = false;
      });
      await _syncPins(results);
    } catch (e) {
      // 검색은 편의 기능 — 실패해도 조용히 넘어가고 직접 입력을 막지 않는다.
      // 다만 원인은 남긴다. 화면에는 아무것도 안 뜨는 게 정상 동작이라, 로그가 없으면
      // "검색 결과가 없다"와 "서버에 못 붙었다"를 구분할 방법이 없다.
      debugPrint('장소 검색 실패 (query=$query): $e');
      if (!mounted || seq != _searchSeq) return;
      setState(() {
        _suggestions = const [];
        _searching = false;
      });
    }
  }

  void _pick(PlaceSuggestion s) {
    if (!mounted) return; // 지도 마커 탭은 네이티브에서 오므로 화면이 사라진 뒤에도 올 수 있다
    FocusScope.of(context).unfocus();
    setState(() {
      _address.text = s.bestAddress;
      if (_name.text.trim().isEmpty) _name.text = s.name;
      if (_phone.text.trim().isEmpty && (s.phone ?? '').isNotEmpty) {
        _phone.text = s.phone!;
      }
      // 고른 핀의 좌표를 그대로 저장한다(_submit 에서 서버로 넘어간다).
      _pickedLat = s.lat;
      _pickedLng = s.lng;
      _suggestions = const [];
      _searching = false;
    });
    // 후보 목록은 닫되 고른 곳 핀 하나는 남겨 "여기 맞다"를 계속 보여준다.
    // 카메라 이동은 _syncPins 가 단일 후보일 때 알아서 한다.
    _syncPins([s]);
  }

  /// 지도의 핀을 [places] 로 교체한다. 좌표가 없는 후보는 찍을 수 없으니 건너뛴다.
  Future<void> _syncPins(List<PlaceSuggestion> places) async {
    final map = _map;
    if (map == null) return;
    final seq = ++_pinSeq;

    final style = _poiStyle ??= await roomPoiStyle();
    if (seq != _pinSeq) return;

    // 지울 목록을 통째로 넘겨받고 비운다 — 뒤이은 호출이 같은 핀을 두 번 지우지 않게.
    final stale = List<Poi>.of(_pins);
    _pins.clear();
    for (final pin in stale) {
      await map.labelLayer.removePoi(pin);
    }

    final located = places.where((p) => p.hasLocation).toList();
    for (final p in located) {
      if (seq != _pinSeq) return; // 더 최신 검색이 그리는 중이면 멈춘다
      final poi = await map.labelLayer.addPoi(
        LatLng(p.lat!, p.lng!),
        style: style,
        text: p.name,
        onClick: () => _pick(p),
      );
      _pins.add(poi);
    }
    if (seq != _pinSeq) return;
    if (located.isNotEmpty) {
      await map.moveCamera(
        located.length == 1
            ? CameraUpdate.newCenterPosition(
                LatLng(located.first.lat!, located.first.lng!),
                zoomLevel: 16,
              )
            : CameraUpdate.fitMapPoints(
                [for (final p in located) LatLng(p.lat!, p.lng!)],
                padding: 48,
              ),
      );
    }
  }

  Future<void> _submit() async {
    final band = ref.read(currentBandProvider);
    final name = _name.text.trim();
    if (band == null || name.isEmpty) return;

    FocusScope.of(context).unfocus();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = ref.read(roomRepositoryProvider);
      final room = _isEdit
          ? await repo.update(
              bandId: band.id,
              roomId: widget.existing!.id,
              name: name,
              address: _address.text.trim(),
              phone: _phone.text.trim(),
              memo: _memo.text.trim(),
              lat: _pickedLat,
              lng: _pickedLng,
            )
          : await repo.create(
              bandId: band.id,
              name: name,
              address: _address.text.trim(),
              phone: _phone.text.trim(),
              memo: _memo.text.trim(),
              lat: _pickedLat,
              lng: _pickedLng,
            );
      ref.invalidate(roomsProvider(band.id));
      if (mounted) context.pop<Room>(room);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(
          () => _error = _isEdit ? '합주실을 수정하지 못했습니다.' : '합주실을 등록하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// 검색 후보를 눈으로 확인하는 지도. 스크롤 뷰 안이라 높이를 고정해야 플랫폼 뷰 레이아웃이
  /// 깨지지 않는다. 지도를 못 쓰면(웹·키 미설정·인증 실패) 안내만 남기고 폼은 그대로 쓴다.
  Widget _buildMap() {
    if (!_mapAvailable) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(13),
        child: MapUnavailableNote(message: mapUnavailableMessage()),
      );
    }

    final start = (_pickedLat != null && _pickedLng != null)
        ? LatLng(_pickedLat!, _pickedLng!)
        : kMapFallbackCenter;

    return ClipRRect(
      borderRadius: BorderRadius.circular(13),
      child: SizedBox(
        height: 180,
        child: KakaoMap(
          option: KakaoMapOption(
            position: start,
            zoomLevel: (_pickedLat != null && _pickedLng != null) ? 16 : 11,
          ),
          // 인증 실패로 폼 전체가 깨지는 대신 안내로 폴백한다.
          onMapError: (_) => setState(() => AppConfig.mapAuthFailed = true),
          onMapReady: (controller) {
            _map = controller;
            // 지도가 늦게 준비돼도 이미 나와 있는 후보를 놓치지 않는다.
            if (_suggestions.isNotEmpty) _syncPins(_suggestions);
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(22, 20, 22, 34),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              BackLink(
                  label: _isEdit ? '합주실 수정' : '합주실 등록',
                  onTap: () => context.pop()),
              const SizedBox(height: 14),
              Text(
                _isEdit ? '합주실 정보 고치기' : '우리 밴드 합주실 추가',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 6),
              const Text(
                '한 번 등록하면 합주 등록할 때 목록에서 바로 고를 수 있어요. '
                '이름만 필수예요.',
                style: TextStyle(
                  fontSize: 12,
                  height: 1.6,
                  color: AppColors.textDim,
                ),
              ),
              const SizedBox(height: 22),
              const _Label('합주실 이름'),
              const SizedBox(height: 8),
              TextField(
                controller: _name,
                maxLength: 50,
                textInputAction: TextInputAction.next,
                onChanged: (_) => setState(() => _error = null),
                decoration: const InputDecoration(
                  hintText: '예: 사운드박스 합주실',
                  counterText: '',
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  const _Label('주소 (선택)'),
                  const SizedBox(width: 8),
                  Text(
                    _searching ? '검색 중…' : '이름·주소로 검색',
                    style: const TextStyle(
                      fontSize: 10.5,
                      color: AppColors.textFaint,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _address,
                maxLength: 255,
                textInputAction: TextInputAction.next,
                onChanged: _onAddressChanged,
                decoration: const InputDecoration(
                  hintText: '합주실 이름·주소로 검색하거나 직접 입력',
                  counterText: '',
                  prefixIcon: Icon(Icons.search, size: 18),
                ),
              ),
              const SizedBox(height: 10),
              _buildMap(),
              if (_suggestions.isNotEmpty) ...[
                const SizedBox(height: 8),
                _SuggestionList(items: _suggestions, onTap: _pick),
              ],
              const SizedBox(height: 16),
              const _Label('연락처 (선택)'),
              const SizedBox(height: 8),
              TextField(
                controller: _phone,
                maxLength: 30,
                keyboardType: TextInputType.phone,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  hintText: '예: 02-334-1082',
                  counterText: '',
                ),
              ),
              const SizedBox(height: 16),
              const _Label('메모 (선택)'),
              const SizedBox(height: 8),
              TextField(
                controller: _memo,
                maxLength: 500,
                maxLines: 3,
                decoration: const InputDecoration(
                  hintText: '주차·장비·요금 등 자유롭게',
                  counterText: '',
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 14),
                Text(
                  _error!,
                  style: const TextStyle(fontSize: 12, color: AppColors.danger),
                ),
              ],
              const SizedBox(height: 22),
              PrimaryButton(
                label: _isEdit ? '수정 저장' : '합주실 저장',
                loading: _loading,
                enabled: _name.text.trim().isNotEmpty,
                onPressed: _submit,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SuggestionList extends StatelessWidget {
  const _SuggestionList({required this.items, required this.onTap});
  final List<PlaceSuggestion> items;
  final ValueChanged<PlaceSuggestion> onTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceRaised,
        borderRadius: BorderRadius.circular(13),
        border: Border.all(color: AppColors.borderStrong),
      ),
      child: Column(
        children: [
          for (var i = 0; i < items.length; i++) ...[
            if (i > 0) const Divider(height: 1, color: AppColors.borderFaint),
            InkWell(
              onTap: () => onTap(items[i]),
              child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      items[i].name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13.5,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    if (items[i].bestAddress.isNotEmpty) ...[
                      const SizedBox(height: 2),
                      Text(
                        items[i].bestAddress,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 11,
                          color: AppColors.textDim,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _Label extends StatelessWidget {
  const _Label(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(fontSize: 11.5, color: AppColors.textDim),
    );
  }
}
