package org.likelion.likelionkakaologin;

import io.netty.handler.codec.http.HttpHeaderValues;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.likelion.likelionkakaologin.user.Role;
import org.likelion.likelionkakaologin.user.User;
import org.likelion.likelionkakaologin.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@RequiredArgsConstructor
@Service
public class KakaoService {

    @Value("${kakao.client_id}") // 임포트 lombok(X), spring(O)
    private String clientId;

    private final String KAUTH_TOKEN_URL_HOST = "https://kauth.kakao.com"; // 액세스 토큰을 발급받기 위한 서버
    private final String KAUTH_USER_URL_HOST = "https://kapi.kakao.com"; // 사용자 정보를 받아오기 위한 서버
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;

    // 인가코드를 이용해 액세스 토큰 받아오기
    public String getAccessToken(String code) {

        KakaoTokenResDto kakaoTokenResDto = WebClient.create(KAUTH_TOKEN_URL_HOST).post()// 카카오 인증 서버로 post 요청 준비
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .path("/oauth/token")
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("client_id", clientId)
                        .queryParam("code", code)
                        .build(true))
                .header(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString())
                .retrieve() // 요청을 보내고 응답 받기
                .bodyToMono(KakaoTokenResDto.class) // 카카오 서버로부터 받아온 응답 본문을 우리가 만든 dto로 변환
                .block(); // 비동기 방식으로 처리된 결과를 동기적으로 받을 수 있게

        return kakaoTokenResDto.getAccessToken();
    }

    // 추가된 부분
    // 액세스 토큰을 이용해 사용자 정보 가져오기
    public KakaoUserInfo getUserInfo(String accessToken) {

        KakaoUserInfo userInfo = WebClient.create(KAUTH_USER_URL_HOST)
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .path("/v2/user/me")
                        .build(true))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString())
                .retrieve()
                .bodyToMono(KakaoUserInfo.class)
                .block();

        return userInfo;
    }

    // 추가된 부분
    // 사용자 정보로 로그인 구현
    @Transactional
    public Token loginOrSignUp(String kakaoAccessToken) {
        KakaoUserInfo userInfo = getUserInfo(kakaoAccessToken);
        Long id = userInfo.getId();

        User user = userRepository.findById(id).orElseGet(() ->
                userRepository.save(User.builder()
                        .id(id)
                        .name(userInfo.getKakaoAccount().getProfile().getNickName())
                        // 이메일 추가
                        .email(userInfo.getKakaoAccount().getEmail())
                        .pictureUrl(userInfo.getKakaoAccount().getProfile().getProfileImageUrl())
                        .role(Role.ROLE_USER)
                        .build())

        );

        return tokenProvider.createToken(user);
    }
}