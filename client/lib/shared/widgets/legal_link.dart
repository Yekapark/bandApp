import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/theme/app_colors.dart';

/// 공개 약관 페이지(`site/terms`, `site/privacy`). 스토어 심사·가입자가 앱 안에서 전문을
/// 열 수 있어야 한다 — "아래 링크에서 볼 수 있어요" 라고만 쓰고 링크가 없던 게 QA-F06.
class LegalUrls {
  static final terms = Uri.parse('https://bandule.com/terms/');
  static final privacy = Uri.parse('https://bandule.com/privacy/');
}

/// 테스트에서 바꿔 끼운다. 실제 앱은 외부 브라우저로 연다.
@visibleForTesting
Future<bool> Function(Uri) legalLauncher =
    (uri) => launchUrl(uri, mode: LaunchMode.externalApplication);

Future<void> openLegalPage(BuildContext context, Uri uri) async {
  final ok = await legalLauncher(uri).catchError((_) => false);
  if (!ok && context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('페이지를 열지 못했어요. 브라우저에서 ${uri.host}${uri.path} 를 열어 주세요.'),
      ),
    );
  }
}

/// 밑줄 친 작은 링크 글자. 눌러야 할 영역이 작아 위아래로 여유를 준다.
class LegalLink extends StatelessWidget {
  const LegalLink({
    super.key,
    required this.label,
    required this.uri,
    this.fontSize = 12,
  });

  final String label;
  final Uri uri;
  final double fontSize;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      link: true,
      child: InkWell(
        onTap: () => openLegalPage(context, uri),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 4),
          child: Text(
            label,
            style: TextStyle(
              fontSize: fontSize,
              color: AppColors.textDim,
              decoration: TextDecoration.underline,
              decorationColor: AppColors.textDim,
            ),
          ),
        ),
      ),
    );
  }
}
