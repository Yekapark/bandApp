import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../routing/app_router.dart';
import '../../band/application/band_providers.dart';
import '../application/calendar_providers.dart';
import '../data/room_models.dart';
import '../data/room_repository.dart';

/// 합주실 지도 — 좌표가 있는 합주실을 네이버 지도 마커로, 아래에 목록.
///
/// 네이버 지도 SDK는 Android/iOS 전용이라, 웹이나 클라이언트 ID 미설정 시에는
/// 지도 없이 목록만 보여준다. `GET /bands/{id}/rooms` 하나만 쓴다.
class MapScreen extends ConsumerStatefulWidget {
  const MapScreen({super.key});

  @override
  ConsumerState<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends ConsumerState<MapScreen> {
  NaverMapController? _map;

  /// 서울시청 — 좌표가 하나도 없을 때 지도 초기 위치.
  static const _fallback = NLatLng(37.5666, 126.9784);

  bool get _mapAvailable => !kIsWeb && AppConfig.naverMapEnabled;

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text('밴드를 먼저 선택해 주세요.',
              style: TextStyle(color: AppColors.textDim)),
        ),
      );
    }

    final roomsAsync = ref.watch(roomsProvider(band.id));

    return Scaffold(
      appBar: AppBar(
        title: const Text('합주실 지도',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: roomsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => _ErrorState(
          onRetry: () => ref.invalidate(roomsProvider(band.id)),
        ),
        data: (rooms) {
          if (rooms.isEmpty) return const _EmptyState();
          final located = rooms.where((r) => r.hasLocation).toList();
          return Column(
            children: [
              if (_mapAvailable)
                Expanded(flex: 3, child: _buildMap(located))
              else
                const _MapUnavailableNote(),
              Expanded(
                flex: 2,
                child: _RoomList(
                  rooms: rooms,
                  onTapLocated: _mapAvailable ? _moveTo : null,
                  onEdit: (r) async {
                    final updated = await context
                        .push<Room>(Routes.editRoom(r.id), extra: r);
                    if (updated != null && context.mounted) {
                      ref.invalidate(roomsProvider(band.id));
                    }
                  },
                  onDelete: (r) => _deleteRoom(band.id, r),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildMap(List<Room> located) {
    final start = located.isEmpty
        ? _fallback
        : NLatLng(located.first.lat!, located.first.lng!);
    return NaverMap(
      options: NaverMapViewOptions(
        initialCameraPosition: NCameraPosition(target: start, zoom: 12),
        rotationGesturesEnable: false,
        tiltGesturesEnable: false,
      ),
      onMapReady: (controller) {
        _map = controller;
        for (final r in located) {
          controller.addOverlay(
            NMarker(
              id: 'room-${r.id}',
              position: NLatLng(r.lat!, r.lng!),
              caption: NOverlayCaption(text: r.name),
            ),
          );
        }
      },
    );
  }

  Future<void> _deleteRoom(int bandId, Room room) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title:
            Text('${room.name} 삭제할까요?', style: const TextStyle(fontSize: 16)),
        content: const Text(
          '이미 등록된 일정에는 영향이 없어요. 앞으로 이 합주실은 목록·지도에서 빠집니다.',
          style:
              TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.5),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('삭제', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref
          .read(roomRepositoryProvider)
          .delete(bandId: bandId, roomId: room.id);
      ref.invalidate(roomsProvider(bandId));
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('합주실을 삭제했어요.')));
      }
    } on ApiException catch (e) {
      _snack(e.message);
    } catch (_) {
      _snack('삭제하지 못했습니다.');
    }
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }

  void _moveTo(Room room) {
    _map?.updateCamera(
      NCameraUpdate.scrollAndZoomTo(
        target: NLatLng(room.lat!, room.lng!),
        zoom: 15,
      ),
    );
  }
}

class _RoomList extends StatelessWidget {
  const _RoomList({
    required this.rooms,
    this.onTapLocated,
    required this.onEdit,
    required this.onDelete,
  });

  final List<Room> rooms;

  /// 좌표가 있는 합주실을 탭했을 때(지도 사용 가능 시에만). null 이면 탭 비활성.
  final void Function(Room)? onTapLocated;
  final void Function(Room) onEdit;
  final void Function(Room) onDelete;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.background,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 20),
        itemCount: rooms.length + 1,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (_, i) {
          if (i == rooms.length) {
            return _AddRoomButton(
              onTap: () => context.push(Routes.newRoom),
            );
          }
          final r = rooms[i];
          return _RoomTile(
            room: r,
            onTap: (r.hasLocation && onTapLocated != null)
                ? () => onTapLocated!(r)
                : null,
            onEdit: () => onEdit(r),
            onDelete: () => onDelete(r),
          );
        },
      ),
    );
  }
}

class _RoomTile extends StatelessWidget {
  const _RoomTile({
    required this.room,
    this.onTap,
    required this.onEdit,
    required this.onDelete,
  });
  final Room room;
  final VoidCallback? onTap;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        decoration: BoxDecoration(
          color: AppColors.surfaceRaised,
          borderRadius: BorderRadius.circular(13),
          border: Border.all(color: AppColors.borderFaint),
        ),
        child: Row(
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(10),
              ),
              alignment: Alignment.center,
              child: Icon(
                room.hasLocation ? Icons.place : Icons.music_note,
                size: 16,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    room.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        fontSize: 13.5, fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    room.subtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        fontSize: 10.5, color: AppColors.textFaint),
                  ),
                ],
              ),
            ),
            if (!room.hasLocation)
              const Padding(
                padding: EdgeInsets.only(right: 4),
                child: Text(
                  '위치 없음',
                  style: TextStyle(fontSize: 10, color: AppColors.textFaint),
                ),
              ),
            PopupMenuButton<String>(
              color: AppColors.surface,
              icon: const Icon(Icons.more_horiz,
                  size: 18, color: AppColors.textDim),
              onSelected: (v) => v == 'edit' ? onEdit() : onDelete(),
              itemBuilder: (_) => [
                const PopupMenuItem(value: 'edit', child: Text('수정')),
                const PopupMenuItem(
                  value: 'delete',
                  child: Text('삭제', style: TextStyle(color: AppColors.danger)),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _AddRoomButton extends StatelessWidget {
  const _AddRoomButton({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 48,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(13),
          border: Border.all(color: AppColors.primary.withValues(alpha: 0.45)),
        ),
        child: const Text(
          '＋ 새 합주실 등록',
          style: TextStyle(
            fontSize: 13.5,
            fontWeight: FontWeight.w700,
            color: AppColors.primary,
          ),
        ),
      ),
    );
  }
}

class _MapUnavailableNote extends StatelessWidget {
  const _MapUnavailableNote();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      color: AppColors.surface,
      child: Text(
        kIsWeb
            ? '지도는 모바일 앱에서만 표시됩니다. 아래 목록으로 확인하세요.'
            : '네이버 지도 클라이언트 ID(NAVER_MAP_CLIENT_ID)를 설정하면 '
                '지도에 마커가 표시됩니다.',
        style: const TextStyle(fontSize: 12, color: AppColors.textDim),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text(
            '등록된 합주실이 없어요.',
            style: TextStyle(color: AppColors.textDim),
          ),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () => context.push(Routes.newRoom),
            child: const Text('＋ 합주실 등록'),
          ),
        ],
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('합주실을 불러오지 못했습니다.',
              style: TextStyle(color: AppColors.textDim)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    );
  }
}
