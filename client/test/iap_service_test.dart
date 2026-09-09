import 'package:bandapp_client/features/plan/data/iap_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_purchase/in_app_purchase.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('Android server verification data를 구매 토큰으로 그대로 사용한다', () {
    final purchase = PurchaseDetails(
      productID: IapService.productId,
      verificationData: PurchaseVerificationData(
        localVerificationData: '{}',
        serverVerificationData: 'play-purchase-token',
        source: 'google_play',
      ),
      transactionDate: null,
      status: PurchaseStatus.purchased,
    );

    expect(IapService().purchaseToken(purchase), 'play-purchase-token');
  });
}
