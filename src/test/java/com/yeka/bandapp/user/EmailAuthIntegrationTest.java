package com.yeka.bandapp.user;

import com.yeka.bandapp.support.ApiIntegrationTest;
import com.yeka.bandapp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAuthIntegrationTest extends ApiIntegrationTest {

    @Autowired
    UserRepository userRepository;

    private static final String SIGNUP = """
            {"email":"a@band.app","password":"pw12345678","name":"에이"}
            """;

    @Test
    void duplicate_email_is_conflict() {
        assertThat(post("/api/v1/auth/signup", SIGNUP).getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> again = post("/api/v1/auth/signup", SIGNUP);
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(again)).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void wrong_password_and_unknown_email_both_401_without_distinction() {
        post("/api/v1/auth/signup", SIGNUP);

        ResponseEntity<String> wrongPw = post("/api/v1/auth/login",
                "{\"email\":\"a@band.app\",\"password\":\"wrongwrong\"}");
        ResponseEntity<String> unknown = post("/api/v1/auth/login",
                "{\"email\":\"nobody@band.app\",\"password\":\"pw12345678\"}");

        assertThat(wrongPw.getStatusCode().value()).isEqualTo(401);
        assertThat(unknown.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(wrongPw)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(errorCode(unknown)).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void short_password_is_400_with_field_error() {
        ResponseEntity<String> res = post("/api/v1/auth/signup",
                "{\"email\":\"a@band.app\",\"password\":\"short\",\"name\":\"에이\"}");

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("INVALID_INPUT");
        assertThat(body(res).at("/error/fieldErrors/0/field").asText()).isEqualTo("password");
    }

    @Test
    void password_is_stored_hashed_not_plaintext() {
        post("/api/v1/auth/signup", SIGNUP);

        String hash = userRepository.findByEmailAndSocialProviderIsNullAndDeletedAtIsNull("a@band.app")
                .orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("pw12345678");
    }

    @Test
    void withdrawn_email_can_register_again() {
        String access = body(post("/api/v1/auth/signup", SIGNUP)).at("/data/tokens/accessToken").asText();
        assertThat(post("/api/v1/users/me/withdraw", "{\"password\":\"pw12345678\"}", access)
                .getStatusCode().value()).isEqualTo(204);

        assertThat(post("/api/v1/auth/signup", SIGNUP).getStatusCode().value()).isEqualTo(201);
    }
}
