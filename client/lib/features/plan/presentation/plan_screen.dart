import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:in_app_purchase/in_app_purchase.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/legal_link.dart';
import '../../band/application/band_providers.dart';
import '../application/plan_providers.dart';
import '../application/purchase_sync.dart';
import '../data/iap_service.dart';
import '../data/plan_models.dart';
import '../data/plan_repository.dart';

/// 밴드 요금제 — FREE/PREMIUM 조회, Play 결제로 PREMIUM 전환(밴드장), 맛보기 쿠폰.
/// 해지·연장은 Play 스토어에서 하고 서버가 웹훅으로 받으므로 앱에는 버튼이 없다.
class PlanScreen extends ConsumerStatefulWidget {
  const PlanScreen({super.key});

  @override
  ConsumerState<PlanScreen> createState() => _PlanScreenState();
}

class _PlanScreenState extends ConsumerState<PlanScreen> {
  late final IapService _iap = ref.read(iapServiceProvider);
  StreamSubscription<PurchaseEvent>? _eventSub;
  /// 스토어에 올라가 있는 PREMIUM 상품들. 값이 모두 같아서 가격은 아무거나 하나로 보여 준다.
  Map<String, ProductDetails> _products = const {};
  ProductDetails? get _product =>
      _products.isEmpty ? null : _products.values.first;
  bool _storeReady = true;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    // 결제 결과는 앱 전역 PurchaseSync 가 받아 서버에 반영하고 안내도 띄운다(LAUNCH_REVIEW B2).
    // 이 화면은 버튼 잠금만 따라간다. 결과가 오면(승인 대기 포함) 푼다 — 승인 대기 중인 밴드를 또 결제하는 것은
    // PurchaseSync 가 막는다. 예전엔 승인 대기면 안내 없이 버튼이 계속 돌았다.
    _eventSub = ref.read(purchaseSyncProvider).events.listen((_) {
      if (mounted) setState(() => _busy = false);
    });
    _initStore();
  }

  @override
  void dispose() {
    _eventSub?.cancel();
    super.dispose();
  }

  Future<void> _initStore() async {
    var available = false;
    var products = <String, ProductDetails>{};
    try {
      available = await _iap.isAvailable();
      if (available) products = await _iap.loadProducts();
    } catch (_) {
      // 스토어 연결 실패 — 아래에서 "지금은 스토어 결제를 쓸 수 없어요" 로 보인다.
    }
    if (!mounted) return;
    setState(() {
      _storeReady = available && products.isNotEmpty;
      _products = products;
    });
  }

  /// Play 스토어의 이 앱 구독 관리 화면을 연다. 해지·결제 수단 변경·환불 요청이 모두 거기 있다.
  /// 결제한 Google 계정이 이 폰에 없으면 Play 가 구독 목록만 보여 준다(다른 사람이 결제한 밴드).
  Future<void> _openSubscriptionManagement() async {
    final ok = await IapService.openManageSubscriptions();
    if (!ok) {
      _toast('Play 스토어를 열지 못했어요. Play 스토어 › 결제 및 구독 › 구독에서 확인해 주세요.');
    }
  }

  Future<void> _startPurchase() async {
    if (_products.isEmpty) {
      _toast('지금은 결제를 시작할 수 없어요. 잠시 후 다시 시도해 주세요.');
      return;
    }
    final band = ref.read(currentBandProvider);
    // 두 번 빨리 누르면 화면이 다시 그려지기 전에 두 번째 탭도 들어온다.
    if (band == null || _busy) return;
    setState(() => _busy = true);
    try {
      // 이미 구독 중·결제 보류 중인지는 buy 가 서버에서 새로 받아 확인한다.
      await ref.read(purchaseSyncProvider).buy(_products, bandId: band.id);
    } on NoPremiumSlotException {
      if (mounted) setState(() => _busy = false);
      _toast(NoPremiumSlotException.message);
    } on PurchaseBlockedException catch (e) {
      if (mounted) setState(() => _busy = false);
      _toast(e.message);
    } on PurchaseInProgressException {
      if (mounted) setState(() => _busy = false);
      _toast(PurchaseInProgressException.message);
    } catch (_) {
      if (mounted) setState(() => _busy = false);
      _toast('결제를 시작하지 못했어요.');
    }
  }

  /// 구독 조건 고지 + 구매 버튼. 구독 조건은 구매 버튼을 누르기 **전에** 보여야 한다(Play 구독 정책 — 가격·주기·
  /// 자동 갱신·해지 방법). 예전에는 구매 뒤 화면(_ManageNotice)에만 있었다. LAUNCH_REVIEW P4.
  List<Widget> _purchaseSection() => [
        _SubscriptionTerms(price: _product?.price),
        const SizedBox(height: 12),
        _ActionButton(
          label: _product == null
              ? 'PREMIUM 시작'
              : 'PREMIUM 시작 · ${_product!.price} / 년',
          busy: _busy,
          onTap: _storeReady ? () => _startPurchase() : null,
        ),
        if (!_storeReady)
          const Padding(
            padding: EdgeInsets.only(top: 8),
            child: Text(
              '지금은 스토어 결제를 쓸 수 없어요. 잠시 후 다시 시도해 주세요.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 11, color: AppColors.textFaint),
            ),
          ),
      ];

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
    final planAsync = ref.watch(bandPlanProvider(band.id));

    return Scaffold(
      appBar: AppBar(
        title: const Text('요금제',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: planAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  e is ApiException ? e.message : '요금제를 불러오지 못했어요.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.textDim),
                ),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: () => ref.invalidate(bandPlanProvider(band.id)),
                  child: const Text('다시 시도'),
                ),
              ],
            ),
          ),
        ),
        data: (plan) => ListView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 40),
          children: [
            _CurrentCard(plan: plan),
            const SizedBox(height: 20),
            const _CompareTable(),
            const SizedBox(height: 24),
            // 갱신 결제가 보류된 구독 — 결제 버튼 대신 Play 에서 고치라고 안내한다. 결제한 사람이 밴드원일 수도
            // 있어서(밴드장이 바뀐 경우) 모두에게 보인다.
            if (plan.onHold)
              _HoldNotice(onManage: _openSubscriptionManagement)
            else if (!band.isLeader)
              const Text(
                '요금제 변경은 밴드장만 할 수 있어요.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 11.5, color: AppColors.textFaint),
              )
            else if (plan.isPremium && plan.canceled)
              _CanceledNotice(onManage: _openSubscriptionManagement)
            else if (plan.isPremium && plan.autoRenewing)
              _ManageNotice(onManage: _openSubscriptionManagement)
            else if (plan.isPremium) ...[
              // 쿠폰으로 받은 프리미엄 — 자동 갱신이 없다. 이어서 쓰려면 결제하고, 남은 쿠폰 기간은
              // 서버가 스토어 결제일을 미뤄 결제 기간 뒤에 붙인다(LAUNCH_REVIEW B7).
              const _CouponNotice(),
              const SizedBox(height: 12),
              ..._purchaseSection(),
            ] else
              ..._purchaseSection(),
            if (band.isLeader) ...[
              const SizedBox(height: 10),
              TextButton(
                onPressed: _busy ? null : () => _promptCoupon(band.id),
                child: const Text('쿠폰 코드 입력',
                    style: TextStyle(fontSize: 13, color: AppColors.textDim)),
              ),
            ],
            const SizedBox(height: 12),
            const Text(
              '결제는 Google Play 를 통해 진행돼요. 해지·환불도 Play 스토어 > 구독에서 해요.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 10.5, color: AppColors.textFaint),
            ),
          ],
        ),
      ),
    );
  }

  /// 쿠폰 코드를 받아 사용한다. 코드를 넣어야만 버튼이 살아난다.
  Future<void> _promptCoupon(int bandId) async {
    final controller = TextEditingController();
    final code = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('쿠폰 코드 입력', style: TextStyle(fontSize: 16)),
        // 키보드가 올라오면 다이얼로그가 눌리므로 스크롤을 열어 둔다.
        scrollable: true,
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '받은 쿠폰 코드를 넣으면 프리미엄 기간이 늘어나요.',
              style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              autofocus: true,
              textCapitalization: TextCapitalization.characters,
              maxLength: 8,
              decoration: const InputDecoration(
                hintText: '예: BANDULE7',
                counterText: '',
              ),
              onSubmitted: (v) => Navigator.pop(ctx, v.trim()),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          ValueListenableBuilder<TextEditingValue>(
            valueListenable: controller,
            builder: (_, value, __) => TextButton(
              onPressed: value.text.trim().isEmpty
                  ? null
                  : () => Navigator.pop(ctx, value.text.trim()),
              child: const Text('사용'),
            ),
          ),
        ],
      ),
    );
    if (code == null || code.isEmpty) return;
    await _redeemCoupon(bandId, code);
  }

  Future<void> _redeemCoupon(int bandId, String code) async {
    setState(() => _busy = true);
    try {
      await ref.read(planRepositoryProvider).redeemCoupon(bandId, code);
      ref.invalidate(bandPlanProvider(bandId));
      _toast('쿠폰을 사용했어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('쿠폰을 사용하지 못했어요.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _CurrentCard extends StatelessWidget {
  const _CurrentCard({required this.plan});
  final BandPlan plan;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: plan.isPremium
            ? AppColors.primary.withValues(alpha: 0.1)
            : AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: plan.isPremium ? AppColors.primary : AppColors.borderStrong,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('현재 요금제',
              style: TextStyle(fontSize: 11, color: AppColors.textDim)),
          const SizedBox(height: 6),
          Text(
            plan.tier,
            style: AppTypography.display(
              fontSize: 30,
              color: plan.isPremium ? AppColors.primary : AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          Text('첨부 미디어 보관: ${plan.retentionLabel}',
              style: const TextStyle(
                  fontSize: 12, color: AppColors.textSecondary)),
          if (plan.isPremium && plan.expiresAt != null) ...[
            const SizedBox(height: 3),
            Text('구독기간 종료: ${Fmt.dateKoWithYearUtc(plan.expiresAt!)}',
                style:
                    const TextStyle(fontSize: 11, color: AppColors.textFaint)),
          ],
        ],
      ),
    );
  }
}

class _CompareTable extends StatelessWidget {
  const _CompareTable();

  @override
  Widget build(BuildContext context) {
    Widget row(String label, String free, String premium) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Row(
            children: [
              Expanded(
                flex: 3,
                child: Text(label,
                    style: const TextStyle(
                        fontSize: 12, color: AppColors.textSecondary)),
              ),
              Expanded(
                flex: 2,
                child: Text(free,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                        fontSize: 12, color: AppColors.textDim)),
              ),
              Expanded(
                flex: 2,
                child: Text(premium,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: AppColors.primary)),
              ),
            ],
          ),
        );

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(13),
        border: Border.all(color: AppColors.borderFaint),
      ),
      child: Column(
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: [
                Expanded(flex: 3, child: SizedBox()),
                Expanded(
                  flex: 2,
                  child: Text('FREE',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 11, color: AppColors.textDim)),
                ),
                Expanded(
                  flex: 2,
                  child: Text('PREMIUM',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: AppColors.primary)),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AppColors.borderFaint),
          // 영상 업로드는 서버가 PREMIUM 에만 허용한다(MediaAttachmentService → PLAN_REQUIRED).
          // 이 행이 없으면 무료도 영상을 올릴 수 있는 것처럼 읽혔다. LAUNCH_REVIEW P4.
          row('영상 업로드', '—', '가능'),
          const Divider(height: 1, color: AppColors.borderFaint),
          row('사진·영상 보관', '30일', '무제한'),
          const Divider(height: 1, color: AppColors.borderFaint),
          row('정기 합주 자동 등록', '—', '무제한'),
        ],
      ),
    );
  }
}

/// 구매 전 고지. 가격은 스토어가 준 현지화 문자열(예: ₩19,000)을 쓰고, 아직 못 받았으면 빼고 쓴다.
/// 판매자 정보 — 구독을 파는 화면에 표시한다(전자상거래법 제10조, LAUNCH_REVIEW L3). 사이트 하단(site/build.py
/// `BUSINESS`)과 같게 유지한다.
const _sellerLine = '판매자: 밴듈(대표 박장언) · 사업자등록번호 425-45-01193 · '
    '통신판매업 제2026-서울성북-1209호 · 서울시 성북구 보문사길 111 상가동 10호 · '
    '010-6327-4017 · notice@bandule.com';

class _SubscriptionTerms extends StatelessWidget {
  const _SubscriptionTerms({required this.price});

  final String? price;

  @override
  Widget build(BuildContext context) {
    final priceLine =
        price == null ? '밴드당 연 구독' : '밴드당 연 $price (Google Play 결제)';
    Widget line(String text) => Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('· ',
                  style: TextStyle(fontSize: 12, color: AppColors.textDim)),
              Expanded(
                child: Text(text,
                    style: const TextStyle(
                        fontSize: 12, color: AppColors.textDim, height: 1.5)),
              ),
            ],
          ),
        );
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderFaint),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(priceLine,
              style: const TextStyle(
                  fontSize: 13, fontWeight: FontWeight.w700)),
          line('1년마다 자동으로 갱신되고, 갱신 때 Google Play 계정으로 청구돼요.'),
          line('Play 스토어 › 프로필 › 결제 및 구독 › 구독에서 언제든 해지할 수 있어요.'),
          line('해지해도 결제한 기간이 끝날 때까지는 프리미엄이 유지돼요.'),
          line('구독은 이 밴드 전체에 적용되고 밴드마다 따로 결제해요. 한 Google 계정으로 밴드 '
              '${IapService.productIds.length}개까지 결제할 수 있어요.'),
          // 글자만 있으면 눌러도 안 열린다(QA-F06 과 같은 문제) — 구독 고지의 약관은 열려야 한다.
          Wrap(
            children: [
              LegalLink(label: '이용약관', uri: LegalUrls.terms),
              LegalLink(label: '개인정보처리방침', uri: LegalUrls.privacy),
            ],
          ),
          line(_sellerLine),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.label,
    required this.busy,
    required this.onTap,
  });

  final String label;
  final bool busy;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return FilledButton(
      onPressed: busy ? null : onTap,
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: AppColors.onPrimary,
        minimumSize: const Size.fromHeight(52),
      ),
      child: busy
          ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(
                  strokeWidth: 2, color: AppColors.onPrimary),
            )
          : Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
    );
  }
}

/// 정상 PREMIUM. 갱신은 자동이고 해지·환불은 Play 스토어에서 한다 — 버튼은 그 화면으로 보낸다.
class _ManageNotice extends StatelessWidget {
  const _ManageNotice({required this.onManage});

  final VoidCallback onManage;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.primary.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('프리미엄 이용 중',
              style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700)),
          const SizedBox(height: 5),
          const Text(
            '기간이 끝나면 Google Play 가 자동으로 1년씩 갱신해요. 해지하거나 환불받으려면 '
            '아래 버튼으로 Play 스토어 구독 화면에서 하면 돼요. 구독은 결제한 사람의 '
            'Google 계정에 있어서, 밴드장이 바뀌어도 그 계정에서만 관리할 수 있어요. 결제한 사람이 탈퇴하거나 '
            '밴드를 나가면 다음 자동 결제를 멈추도록 Google Play 에 요청해요(이미 낸 기간은 유지, 환불 없음).',
            style:
                TextStyle(fontSize: 12, color: AppColors.textDim, height: 1.5),
          ),
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton.icon(
              onPressed: onManage,
              icon: const Icon(Icons.open_in_new, size: 16),
              label: const Text('Google Play 에서 구독 관리'),
            ),
          ),
        ],
      ),
    );
  }
}

/// 갱신 결제가 실패해 Google 이 구독을 보류한 밴드(서버는 FREE 로 내렸다). 결제 수단을 고치면 그 구독이 다시
/// 이어지므로 새로 결제하게 두지 않는다 — 새 구독이 생기면 옛 것이 되살아날 때 두 번 청구된다.
class _HoldNotice extends StatelessWidget {
  const _HoldNotice({required this.onManage});

  final VoidCallback onManage;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('결제 보류 중',
              style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700)),
          const SizedBox(height: 5),
          const Text(
            '카드 결제가 실패해 프리미엄이 잠시 멈췄어요. Google Play 에서 결제 수단을 고치면 바로 다시 '
            '이어져요. 구독은 결제한 사람의 Google 계정에 있어서, 밴드장이 바뀌어도 그 계정에서만 '
            '고칠 수 있어요. 새로 결제하면 두 번 청구될 수 있으니 결제 수단만 고쳐 주세요.',
            style:
                TextStyle(fontSize: 12, color: AppColors.textDim, height: 1.5),
          ),
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton.icon(
              onPressed: onManage,
              icon: const Icon(Icons.open_in_new, size: 16),
              label: const Text('Google Play 에서 구독 관리'),
            ),
          ),
        ],
      ),
    );
  }
}

/// 해지 예약된 PREMIUM. 남은 기간을 알려준다. 결제한 기간이 끝나면 서버가 만료 배치로 FREE 로
/// 내린다. 마음이 바뀌면 Play 구독 화면에서 다시 구독할 수 있으니 같은 버튼을 둔다.
/// 쿠폰으로 받은 프리미엄 안내 — 자동 갱신되지 않고, 결제하면 남은 쿠폰 기간이 결제 기간 뒤에 붙는다.
class _CouponNotice extends StatelessWidget {
  const _CouponNotice();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '쿠폰으로 받은 프리미엄',
            style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700),
          ),
          SizedBox(height: 5),
          Text(
            '위에 적힌 날짜까지 프리미엄이에요. 자동으로 갱신되지 않아요. 계속 쓰려면 지금 결제해도 돼요 — '
            '남은 쿠폰 기간은 결제한 1년 뒤에 그대로 붙어요.',
            style: TextStyle(fontSize: 12, color: AppColors.textDim, height: 1.5),
          ),
        ],
      ),
    );
  }
}

class _CanceledNotice extends StatelessWidget {
  const _CanceledNotice({required this.onManage});

  final VoidCallback onManage;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '해지 예약됨',
            style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 5),
          const Text(
            '위에 적힌 날짜까지는 프리미엄 그대로예요. 그 뒤에 무료로 바뀌고, 30일이 더 지나면 '
            '사진·영상이 차례로 사라져요.',
            style:
                TextStyle(fontSize: 12, color: AppColors.textDim, height: 1.5),
          ),
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton.icon(
              onPressed: onManage,
              icon: const Icon(Icons.open_in_new, size: 16),
              label: const Text('Google Play 에서 구독 관리'),
            ),
          ),
        ],
      ),
    );
  }
}
