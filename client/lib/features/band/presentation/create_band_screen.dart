import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../routing/app_router.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';
import '../application/band_providers.dart';
import '../data/band_repository.dart';

class CreateBandScreen extends ConsumerStatefulWidget {
  const CreateBandScreen({super.key});

  @override
  ConsumerState<CreateBandScreen> createState() => _CreateBandScreenState();
}

class _CreateBandScreenState extends ConsumerState<CreateBandScreen> {
  final _name = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final name = _name.text.trim();
    if (name.isEmpty) return;

    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final band = await ref.read(bandRepositoryProvider).createBand(name);
      ref.invalidate(myBandsProvider);
      ref.read(selectedBandIdProvider.notifier).select(band.id);
      if (mounted) context.go(Routes.home);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = '밴드를 만들지 못했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(22, 24, 22, 34),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              BackLink(label: '밴드 만들기', onTap: () => context.pop()),
              const SizedBox(height: 16),
              Text('밴드 정보', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 20),
              const Text('밴드 이름',
                  style: TextStyle(fontSize: 11.5, color: AppColors.textDim)),
              const SizedBox(height: 8),
              TextField(
                controller: _name,
                maxLength: 50,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _submit(),
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(
                  hintText: '예: 새벽 네시',
                  counterText: '',
                ),
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(13),
                  border: Border.all(color: AppColors.borderFaint),
                ),
                child: const Text(
                  '밴드를 만들면 자동으로 밴드장이 되고, 초대코드가 발급됩니다. '
                  '장르·파트 설정은 추후 지원 예정이에요.',
                  style: TextStyle(
                      fontSize: 11.5, height: 1.6, color: AppColors.textDim),
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 14),
                Text(_error!,
                    style:
                        const TextStyle(fontSize: 12, color: AppColors.danger)),
              ],
              const SizedBox(height: 22),
              PrimaryButton(
                label: '밴드 만들기',
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
