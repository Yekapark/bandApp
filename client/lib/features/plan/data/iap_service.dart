import 'package:in_app_purchase/in_app_purchase.dart';

/// Play Billing(인앱결제)을 얇게 감싼다. 결제 자체는 스토어에서 일어나고, 성공하면 구매 토큰을
/// 서버(`/plan/google/restore`·`/plan/google/verify`)로 보내 검증받는다 — 이 클래스는 스토어 쪽만 담당한다.
///
/// 결과가 [purchaseStream] 으로 비동기로 온다는 점이 특징이다: [buy] 를 부른 뒤,
/// 스트림에서 [productId] 에 해당하는 [PurchaseDetails] 를 받아 처리하고 [complete] 로 마무리한다.
class IapService {
  IapService({InAppPurchase? iap}) : _override = iap;

  final InAppPurchase? _override;

  /// 처음 쓸 때 만든다 — `InAppPurchase.instance` 는 만들자마자 Play 결제 서비스에 연결하므로,
  /// 쓰지 않는 곳(테스트의 가짜, 토큰만 꺼내는 경우)에서 연결이 일어나지 않게.
  InAppPurchase get _iap => _override ?? InAppPurchase.instance;

  /// Play Console 에 만든 구독 상품(기본 요금제)의 id. 서버·스토어와 정확히 같아야 한다.
  static const String productId = 'premium_yearly';

  /// Play 스토어의 이 앱 구독 관리 화면. 해지·결제 수단·환불 요청이 여기 있다.
  static final Uri manageSubscriptionUrl = Uri.parse(
      'https://play.google.com/store/account/subscriptions'
      '?sku=$productId&package=com.yeka.bandule');

  Future<bool> isAvailable() => _iap.isAvailable();

  Stream<List<PurchaseDetails>> get purchaseStream => _iap.purchaseStream;

  /// 상품 정보(가격 표기 등). 스토어에 상품이 없거나 심사 전이면 null.
  Future<ProductDetails?> loadProduct() async {
    final resp = await _iap.queryProductDetails({productId});
    if (resp.productDetails.isEmpty) return null;
    return resp.productDetails.first;
  }

  /// 구매에 적는 "어느 밴드를 위해 결제했나" 표시. Play 의 obfuscatedAccountId 로 들어가고, 서버는
  /// 검증·복구 때 이 값으로 밴드를 정한다(LAUNCH_REVIEW B3). **서버 `PurchaseBandTag` 와 형식이 같아야 한다.**
  /// 개인정보(이메일·이름)는 넣지 않는다 — Play 정책상 평문 개인정보를 넣으면 안 된다.
  static String bandTag(int bandId) => 'band-$bandId';

  /// 구매 시트를 띄운다. 실제 결과는 [purchaseStream] 으로 온다.
  /// [bandId] 는 구매 기록에 남는다 — 검증 전에 앱이 꺼져도 다음 실행 때 서버가 이 밴드로 반영한다.
  Future<void> buy(ProductDetails product, {required int bandId}) {
    return _iap.buyNonConsumable(
      purchaseParam: PurchaseParam(
        productDetails: product,
        applicationUserName: bandTag(bandId),
      ),
    );
  }

  /// 이 Google 계정이 가진 구매를 스토어에서 다시 받아 [purchaseStream] 으로 흘려보낸다
  /// (상태 `restored`). 검증을 못 끝낸 구매를 앱 시작·복귀 때 찾는 데 쓴다.
  Future<void> restorePurchases() => _iap.restorePurchases();

  /// 처리를 끝낸 구매를 스토어에 알린다(안 하면 스트림에 계속 다시 뜬다).
  Future<void> complete(PurchaseDetails purchase) =>
      _iap.completePurchase(purchase);

  /// Android 구매 토큰. 서버 검증에 이 값을 넘긴다. (iOS 는 별도 — 이번 릴리스는 Android 만.)
  /// `in_app_purchase_android` 는 serverVerificationData 에 토큰 자체를 넣는다.
  String? purchaseToken(PurchaseDetails purchase) {
    final token = purchase.verificationData.serverVerificationData;
    return token.isEmpty ? null : token;
  }
}
