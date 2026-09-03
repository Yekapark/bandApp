import '../../../core/storage/token_storage.dart';

/// GET /users/me, 그리고 auth 응답의 user.
class AppUser {
  const AppUser({
    required this.id,
    required this.name,
    this.email,
    this.socialProvider,
  });

  final int id;
  final String name;
  final String? email;

  /// null = 이메일 가입, "KAKAO" = 카카오 가입.
  final String? socialProvider;

  factory AppUser.fromJson(Map<String, dynamic> json) {
    return AppUser(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String? ?? '',
      email: json['email'] as String?,
      socialProvider: json['socialProvider'] as String?,
    );
  }
}

/// POST /auth/signup · /auth/login · /auth/kakao 응답.
class AuthResult {
  const AuthResult({
    required this.user,
    required this.tokens,
    required this.newUser,
  });

  final AppUser user;
  final Tokens tokens;
  final bool newUser;

  factory AuthResult.fromJson(Map<String, dynamic> json) {
    final t = json['tokens'] as Map<String, dynamic>;
    return AuthResult(
      user: AppUser.fromJson(json['user'] as Map<String, dynamic>),
      tokens: Tokens(
        accessToken: t['accessToken'] as String,
        refreshToken: t['refreshToken'] as String,
      ),
      newUser: json['newUser'] as bool? ?? false,
    );
  }
}
