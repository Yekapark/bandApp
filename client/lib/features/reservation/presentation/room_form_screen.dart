import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../band/application/band_providers.dart';
import '../application/calendar_providers.dart';
import '../data/place_models.dart';
import '../data/room_models.dart';
import '../data/room_repository.dart';

/// 합주실 등록 폼. 성공하면 생성된 [Room] 을 들고 pop 한다(선택 시트에서 바로 고르도록).
///
/// 주소 칸은 네이버 지역검색과 연결돼 있다 — 두 글자 이상 입력하면 후보가 뜨고, 고르면
/// 이름·주소·연락처가 자동으로 채워진다. 서버에 검색 키가 없으면 후보가 안 뜰 뿐 직접 입력은 된다.
class RoomFormScreen extends ConsumerStatefulWidget {
  const RoomFormScreen({super.key});

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
    setState(() => _error = null);
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
    if (band == null) return;
    final seq = ++_searchSeq;
    setState(() => _searching = true);
    try {
      final results = await ref
          .read(roomRepositoryProvider)
          .searchPlaces(bandId: band.id, query: query);
      if (!mounted || seq != _searchSeq) return;
      setState(() {
        _suggestions = results;
        _searching = false;
      });
    } catch (_) {
      // 검색은 편의 기능 — 실패해도 조용히 넘어가고 직접 입력을 막지 않는다.
      if (!mounted || seq != _searchSeq) return;
      setState(() {
        _suggestions = const [];
        _searching = false;
      });
    }
  }

  void _pick(PlaceSuggestion s) {
    FocusScope.of(context).unfocus();
    setState(() {
      _address.text = s.bestAddress;
      if (_name.text.trim().isEmpty) _name.text = s.name;
      if (_phone.text.trim().isEmpty && (s.phone ?? '').isNotEmpty) {
        _phone.text = s.phone!;
      }
      _suggestions = const [];
      _searching = false;
    });
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
      final room = await ref.read(roomRepositoryProvider).create(
            bandId: band.id,
            name: name,
            address: _address.text.trim(),
            phone: _phone.text.trim(),
            memo: _memo.text.trim(),
          );
      ref.invalidate(roomsProvider(band.id));
      if (mounted) context.pop<Room>(room);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = '합주실을 등록하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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
              BackLink(label: '합주실 등록', onTap: () => context.pop()),
              const SizedBox(height: 14),
              Text(
                '우리 밴드 합주실 추가',
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
                    _searching ? '검색 중…' : '네이버로 검색',
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
                label: '합주실 저장',
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
