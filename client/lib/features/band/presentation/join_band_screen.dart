import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';
import '../application/band_providers.dart';
import '../data/band_repository.dart';

/// 초대코드 8자(영숫자, 대소문자 무관) 입력 후 밴드 합류.
/// [initialCode] 가 있으면(초대 링크로 진입) 입력칸에 미리 채운다.
class JoinBandScreen extends ConsumerStatefulWidget {
  const JoinBandScreen({super.key, this.initialCode});

  final String? initialCode;

  @override
  ConsumerState<JoinBandScreen> createState() => _JoinBandScreenState();
}

class _JoinBandScreenState extends ConsumerState<JoinBandScreen> {
  static const _codeLength = 8;
  late final _code = TextEditingController(text: _sanitize(widget.initialCode));
  bool _loading = false;
  String? _error;

  static String _sanitize(String? raw) {
    if (raw == null) return '';
    final cleaned = raw.toUpperCase().replaceAll(RegExp('[^A-Z0-9]'), '');
    return cleaned.length > _codeLength
        ? cleaned.substring(0, _codeLength)
        : cleaned;
  }

  String get _value => _code.text;
  bool get _full => _value.length == _codeLength;

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_full) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final band = await ref.read(bandRepositoryProvider).joinBand(_value);
      ref.invalidate(myBandsProvider);
      ref.read(selectedBandIdProvider.notifier).select(band.id);
      if (mounted) context.go(Routes.home);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = '밴드에 합류하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: RadialGradient(
            center: Alignment(0, -1),
            radius: 0.95,
            colors: [Color(0xFF251427), AppColors.background],
            stops: [0, 0.62],
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(28, 24, 28, 28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                BackLink(label: '밴드 가입', onTap: () => context.pop()),
                const SizedBox(height: 30),
                Text('밴드장이 준\n초대코드를 입력해 주세요',
                    style: Theme.of(context).textTheme.headlineLarge),
                const SizedBox(height: 10),
                const Text(
                  '코드 8자를 넣으면 밴드에 바로 합류됩니다.\n합주 일정과 정산이 자동으로 공유돼요.',
                  style: TextStyle(
                      fontSize: 13.5, height: 1.6, color: AppColors.textDim),
                ),
                const SizedBox(height: 30),
                _CodeField(
                  controller: _code,
                  length: _codeLength,
                  onChanged: (_) => setState(() => _error = null),
                  onSubmitted: (_) => _submit(),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 14),
                  Text(_error!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                          fontSize: 12.5, color: AppColors.danger)),
                ],
                const SizedBox(height: 24),
                PrimaryButton(
                  label: '밴드 합류하기',
                  loading: _loading,
                  enabled: _full,
                  onPressed: _submit,
                ),
                const SizedBox(height: 14),
                const Text(
                  '초대코드가 없으면 밴드장에게 요청하세요.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 12, color: AppColors.textFaint),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CodeField extends StatelessWidget {
  const _CodeField({
    required this.controller,
    required this.length,
    required this.onChanged,
    required this.onSubmitted,
  });

  final TextEditingController controller;
  final int length;
  final ValueChanged<String> onChanged;
  final ValueChanged<String> onSubmitted;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      autofocus: true,
      maxLength: length,
      textAlign: TextAlign.center,
      textCapitalization: TextCapitalization.characters,
      onChanged: onChanged,
      onSubmitted: onSubmitted,
      inputFormatters: [
        FilteringTextInputFormatter.allow(RegExp('[a-zA-Z0-9]')),
        _UpperCaseFormatter(),
      ],
      style: AppTypography.mono(fontSize: 26, letterSpacing: 8),
      decoration: const InputDecoration(
        counterText: '',
        hintText: 'ABCD2345',
      ),
    );
  }
}

class _UpperCaseFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    return newValue.copyWith(text: newValue.text.toUpperCase());
  }
}
