import 'package:in_app_purchase/in_app_purchase.dart';

/// Play Billing(인앱결제)을 얇게 감싼다. 결제 자체는 스토어에서 일어나고, 성공하면 구매 토큰을
/// 서버(`/plan/google/verify`)로 보내 검증받는다 — 이 클래스는 스토어 쪽만 담당한다.
///
/// 결과가 [purchaseStream] 으로 비동기로 온다는 점이 특징이다: [buy] 를 부른 뒤,
/// 스트림에서 [productId] 에 해당하는 [PurchaseDetails] 를 받아 처리하고 [complete] 로 마무리한다.
class IapService {
  IapService({InAppPurchase? iap}) : _iap = iap ?? InAppPurchase.instance;

  final InAppPurchase _iap;

  /// Play Console 에 만든 구독 상품(기본 요금제)의 id. 서버·스토어와 정확히 같아야 한다.
  static const String productId = 'premium_yearly';

  Future<bool> isAvailable() => _iap.isAvailable();

  Stream<List<PurchaseDetails>> get purchaseStream => _iap.purchaseStream;

  /// 상품 정보(가격 표기 등). 스토어에 상품이 없거나 심사 전이면 null.
  Future<ProductDetails?> loadProduct() async {
    final resp = await _iap.queryProductDetails({productId});
    if (resp.productDetails.isEmpty) return null;
    return resp.productDetails.first;
  }

  /// 구매 시트를 띄운다. 실제 결과는 [purchaseStream] 으로 온다.
  Future<void> buy(ProductDetails product) {
    return _iap.buyNonConsumable(
      purchaseParam: PurchaseParam(productDetails: product),
    );
  }

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
